package app.drivehome

import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.service.notification.NotificationListenerService
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Service vide : il sert uniquement à obtenir l'accès « notifications »,
 * indispensable pour lire les sessions média des autres applications.
 */
class MediaListener : NotificationListenerService()

data class MediaInfo(
    val access: Boolean = false,
    val hasSession: Boolean = false,
    val title: String? = null,
    val artist: String? = null,
    val art: Bitmap? = null,
    val playing: Boolean = false,
    val pkg: String? = null,
)

class MediaWatcher(context: Context) {
    private val appContext = context.applicationContext
    private val manager =
        appContext.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
    private val component = ComponentName(appContext, MediaListener::class.java)

    var info by mutableStateOf(MediaInfo())
        private set

    private var controller: MediaController? = null
    private var started = false

    private val callback = object : MediaController.Callback() {
        override fun onMetadataChanged(metadata: MediaMetadata?) = refresh()
        override fun onPlaybackStateChanged(state: PlaybackState?) = refresh()
        override fun onSessionDestroyed() = pick(emptyList())
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
        } catch (e: SecurityException) {
            info = MediaInfo(access = false)
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
        val next = list.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING }
            ?: list.firstOrNull()
        if (next?.sessionToken != controller?.sessionToken) {
            controller?.unregisterCallback(callback)
            controller = next
            controller?.registerCallback(callback)
        }
        refresh()
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
        info = MediaInfo(
            access = true,
            hasSession = true,
            title = title,
            artist = artist,
            art = art,
            playing = c.playbackState?.state == PlaybackState.STATE_PLAYING,
            pkg = c.packageName,
        )
    }

    fun playPause() {
        val c = controller ?: return
        if (c.playbackState?.state == PlaybackState.STATE_PLAYING) {
            c.transportControls.pause()
        } else {
            c.transportControls.play()
        }
    }

    fun next() {
        controller?.transportControls?.skipToNext()
    }

    fun previous() {
        controller?.transportControls?.skipToPrevious()
    }
}
