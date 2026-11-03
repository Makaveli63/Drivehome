package app.drivehome

import android.app.Notification
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.SystemClock
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.speech.tts.TextToSpeech
import android.view.KeyEvent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.Locale

// =============================================================================
// Notifications : accès aux sessions média + messages entrants
// =============================================================================

data class Msg(
    val key: String,
    val pkg: String,
    val app: String,
    val title: String,
    val text: String,
    val time: Long,
    val intent: PendingIntent?,
)

/** Messages reçus, partagés entre le service et l'interface (thread principal). */
object NotifStore {
    val messages = mutableStateListOf<Msg>()
    var connected by mutableStateOf(false)
    var service: MediaListener? = null

    /** Appelé pour chaque nouveau message (lecture vocale, etc.). */
    var onNew: ((Msg) -> Unit)? = null

    fun put(m: Msg, isNew: Boolean) {
        val i = messages.indexOfFirst { it.key == m.key }
        val changed = i < 0 || messages[i].text != m.text
        if (i >= 0) messages.removeAt(i)
        messages.add(0, m)
        while (messages.size > 40) messages.removeAt(messages.lastIndex)
        if (isNew && changed) onNew?.invoke(m)
    }

    fun remove(key: String) {
        messages.removeAll { it.key == key }
    }

    fun dismiss(m: Msg) {
        remove(m.key)
        try {
            service?.cancelNotification(m.key)
        } catch (e: Exception) {
            // ignoré
        }
    }

    fun clearAll() {
        val keys = messages.map { it.key }
        messages.clear()
        keys.forEach {
            try {
                service?.cancelNotification(it)
            } catch (e: Exception) {
                // ignoré
            }
        }
    }
}

class MediaListener : NotificationListenerService() {
    private val ignoredCategories: Set<String?> = setOf(
        Notification.CATEGORY_TRANSPORT,
        Notification.CATEGORY_PROGRESS,
        Notification.CATEGORY_SERVICE,
        Notification.CATEGORY_SYSTEM,
        Notification.CATEGORY_NAVIGATION,
        Notification.CATEGORY_STATUS,
        Notification.CATEGORY_ALARM,
    )

    override fun onListenerConnected() {
        NotifStore.service = this
        NotifStore.connected = true
        try {
            activeNotifications?.sortedBy { it.postTime }?.forEach { handle(it, false) }
        } catch (e: Exception) {
            // ignoré
        }
    }

    override fun onListenerDisconnected() {
        NotifStore.connected = false
        if (NotifStore.service === this) NotifStore.service = null
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) = handle(sbn, true)

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        NotifStore.remove(sbn.key)
    }

    private fun handle(sbn: StatusBarNotification, isNew: Boolean) {
        try {
            if (sbn.packageName == packageName || sbn.isOngoing) return
            val n = sbn.notification ?: return
            if (n.flags and Notification.FLAG_GROUP_SUMMARY != 0) return
            if (n.category in ignoredCategories) return
            val ex = n.extras ?: return
            val title = ex.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim().orEmpty()
            val text = (ex.getCharSequence(Notification.EXTRA_BIG_TEXT) ?: ex.getCharSequence(Notification.EXTRA_TEXT))
                ?.toString()?.trim().orEmpty()
            if (title.isEmpty() && text.isEmpty()) return
            val app = try {
                packageManager.getApplicationLabel(packageManager.getApplicationInfo(sbn.packageName, 0)).toString()
            } catch (e: Exception) {
                sbn.packageName
            }
            NotifStore.put(Msg(sbn.key, sbn.packageName, app, title, text, sbn.postTime, n.contentIntent), isNew)
        } catch (e: Exception) {
            // une notification malformée ne doit pas faire planter le service
        }
    }
}

// =============================================================================
// Lecture vocale des messages
// =============================================================================

class Voice(context: Context) {
    private val appContext = context.applicationContext
    private var tts: TextToSpeech? = null
    private var ready = false
    private val pending = mutableListOf<String>()
    var rate = 1f

    fun speak(text: String) {
        if (text.isBlank()) return
        if (tts == null) {
            pending.add(text)
            tts = TextToSpeech(appContext) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    ready = true
                    tts?.language = Locale.getDefault().takeIf { it.language == "fr" } ?: Locale.FRANCE
                    pending.forEach { say(it) }
                }
                pending.clear()
            }
        } else if (ready) {
            say(text)
        } else {
            pending.add(text)
        }
    }

    private fun say(text: String) {
        tts?.setSpeechRate(rate)
        tts?.speak(text, TextToSpeech.QUEUE_ADD, null, "dh" + SystemClock.elapsedRealtime())
    }

    fun stop() {
        tts?.stop()
    }

    fun shutdown() {
        tts?.shutdown()
        tts = null
        ready = false
    }
}

// =============================================================================
// Média : lecture en cours, commandes, progression, choix de la source
// =============================================================================

data class MediaInfo(
    val access: Boolean = false,
    val hasSession: Boolean = false,
    val title: String? = null,
    val artist: String? = null,
    val album: String? = null,
    val art: Bitmap? = null,
    val playing: Boolean = false,
    val pkg: String? = null,
    val position: Long = 0L,
    val positionAt: Long = 0L,
    val speed: Float = 1f,
    val duration: Long = 0L,
    val canSeek: Boolean = false,
    val sources: List<String> = emptyList(),
) {
    /** Position estimée maintenant, en ms. */
    fun currentPosition(): Long {
        if (!playing || positionAt == 0L) return position
        val p = position + ((SystemClock.elapsedRealtime() - positionAt) * speed).toLong()
        return if (duration > 0) p.coerceAtMost(duration) else p
    }
}

class MediaWatcher(context: Context) {
    private val appContext = context.applicationContext
    private val manager =
        appContext.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
    private val audio = appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val component = ComponentName(appContext, MediaListener::class.java)

    var info by mutableStateOf(MediaInfo())
        private set

    private var sessions: List<MediaController> = emptyList()
    private var controller: MediaController? = null
    private var pinnedPkg: String? = null
    private var started = false

    private val callback = object : MediaController.Callback() {
        override fun onMetadataChanged(metadata: MediaMetadata?) = refresh()
        override fun onPlaybackStateChanged(state: PlaybackState?) = refresh()
        override fun onSessionDestroyed() = pick(sessions.filter { it != controller })
    }

    private val sessionsListener =
        MediaSessionManager.OnActiveSessionsChangedListener { list ->
            pick(list ?: emptyList())
        }

    fun start() {
        if (started) stop()
        try {
            manager.addOnActiveSessionsChangedListener(sessionsListener, component)
            started = true
            pick(manager.getActiveSessions(component))
        } catch (e: Exception) {
            info = MediaInfo(access = false)
        }
    }

    fun stop() {
        try {
            manager.removeOnActiveSessionsChangedListener(sessionsListener)
        } catch (e: Exception) {
            // rien à faire
        }
        controller?.unregisterCallback(callback)
        controller = null
        started = false
    }

    private fun pick(list: List<MediaController>) {
        sessions = list
        val next = list.firstOrNull { it.packageName == pinnedPkg }
            ?: list.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING }
            ?: list.firstOrNull()
        if (next?.sessionToken != controller?.sessionToken) {
            controller?.unregisterCallback(callback)
            controller = next
            controller?.registerCallback(callback)
        }
        refresh()
    }

    /** Passe à la source audio suivante (Spotify → radio → YouTube Music…). */
    fun nextSource() {
        if (sessions.size < 2) return
        val i = sessions.indexOfFirst { it.sessionToken == controller?.sessionToken }
        val n = sessions[(i + 1) % sessions.size]
        pinnedPkg = n.packageName
        pick(sessions)
    }

    private fun refresh() {
        val c = controller
        if (c == null) {
            info = MediaInfo(access = true, hasSession = false)
            return
        }
        val m = c.metadata
        val art = m?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: m?.getBitmap(MediaMetadata.METADATA_KEY_ART)
            ?: m?.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
        val title = m?.getString(MediaMetadata.METADATA_KEY_TITLE)
            ?: m?.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE)
        val artist = m?.getString(MediaMetadata.METADATA_KEY_ARTIST)
            ?: m?.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)
            ?: m?.getString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE)
        val ps = c.playbackState
        info = MediaInfo(
            access = true,
            hasSession = true,
            title = title,
            artist = artist,
            album = m?.getString(MediaMetadata.METADATA_KEY_ALBUM),
            art = art,
            playing = ps?.state == PlaybackState.STATE_PLAYING,
            pkg = c.packageName,
            position = ps?.position ?: 0L,
            positionAt = ps?.lastPositionUpdateTime ?: 0L,
            speed = ps?.playbackSpeed?.takeIf { it > 0f } ?: 1f,
            duration = m?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L,
            canSeek = ((ps?.actions ?: 0L) and PlaybackState.ACTION_SEEK_TO) != 0L,
            sources = sessions.map { it.packageName }.distinct(),
        )
    }

    fun playPause() {
        val c = controller
        if (c == null) {
            mediaKey(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
            return
        }
        if (c.playbackState?.state == PlaybackState.STATE_PLAYING) {
            c.transportControls.pause()
        } else {
            c.transportControls.play()
        }
    }

    fun next() {
        controller?.transportControls?.skipToNext() ?: mediaKey(KeyEvent.KEYCODE_MEDIA_NEXT)
    }

    fun previous() {
        controller?.transportControls?.skipToPrevious() ?: mediaKey(KeyEvent.KEYCODE_MEDIA_PREVIOUS)
    }

    fun seekTo(ms: Long) {
        controller?.transportControls?.seekTo(ms)
    }

    fun skipBy(deltaMs: Long) {
        val i = info
        val target = (i.currentPosition() + deltaMs).coerceAtLeast(0L)
        seekTo(if (i.duration > 0) target.coerceAtMost(i.duration) else target)
    }

    /** Reprend la dernière lecture (même sans session visible). */
    fun resume() {
        val c = controller
        if (c != null) c.transportControls.play() else mediaKey(KeyEvent.KEYCODE_MEDIA_PLAY)
    }

    private fun mediaKey(code: Int) {
        try {
            audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, code))
            audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, code))
        } catch (e: Exception) {
            // ignoré
        }
    }
}

fun formatDuration(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    val h = s / 3600
    val m = (s % 3600) / 60
    val sec = s % 60
    return if (h > 0) String.format(Locale.FRANCE, "%d:%02d:%02d", h, m, sec)
    else String.format(Locale.FRANCE, "%d:%02d", m, sec)
}
