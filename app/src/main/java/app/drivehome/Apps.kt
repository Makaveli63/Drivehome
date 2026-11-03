package app.drivehome

import android.content.ActivityNotFoundException
import android.content.Context
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.media.audiofx.AudioEffect
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import java.util.concurrent.ConcurrentHashMap

data class AppItem(val pkg: String, val label: String)

object Apps {
    val NAV_PKGS = listOf(
        "com.google.android.apps.maps",
        "com.waze",
        "com.here.app.maps",
        "net.osmand.plus",
        "net.osmand",
        "com.sygic.aura",
        "com.tomtom.gplay.navapp",
    )

    val MUSIC_PKGS = listOf(
        "com.spotify.music",
        "com.google.android.apps.youtube.music",
        "deezer.android.app",
        "com.apple.android.music",
        "com.amazon.mp3",
        "com.soundcloud.android",
        "com.aspiro.tidal",
        "org.videolan.vlc",
        "com.google.android.music",
    )

    val PHONE_PKGS = listOf(
        "com.google.android.dialer",
        "com.samsung.android.dialer",
        "com.android.dialer",
        "com.android.contacts",
    )

    fun load(context: Context): List<AppItem> {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return pm.queryIntentActivities(intent, 0)
            .map { AppItem(it.activityInfo.packageName, it.loadLabel(pm).toString()) }
            .filter { it.pkg != context.packageName }
            .distinctBy { it.pkg }
            .sortedBy { it.label.lowercase() }
    }

    fun firstInstalled(apps: List<AppItem>, candidates: List<String>): String? =
        candidates.firstOrNull { c -> apps.any { it.pkg == c } }

    fun launch(context: Context, pkg: String) {
        val intent = context.packageManager.getLaunchIntentForPackage(pkg)
        if (intent == null) {
            toast(context, "Application introuvable")
            return
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        safeStart(context, intent)
    }

    fun startNavigation(context: Context, navPkg: String?, address: String) {
        val uri = if (navPkg == "com.waze") {
            Uri.parse("waze://?q=${Uri.encode(address)}&navigate=yes")
        } else {
            Uri.parse("google.navigation:q=${Uri.encode(address)}")
        }
        val intent = Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (navPkg != null) intent.setPackage(navPkg)
        try {
            context.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            // Repli : n'importe quelle appli de navigation
            val fallback = Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=${Uri.encode(address)}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            safeStart(context, fallback)
        }
    }

    /** Recherche à proximité (station, parking…) dans l'appli de navigation. */
    fun searchNearby(context: Context, navPkg: String?, query: String) {
        val uri = if (navPkg == "com.waze") {
            Uri.parse("waze://?q=${Uri.encode(query)}")
        } else {
            Uri.parse("geo:0,0?q=${Uri.encode(query)}")
        }
        val intent = Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (navPkg != null) intent.setPackage(navPkg)
        try {
            context.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            intent.setPackage(null)
            safeStart(context, intent)
        }
    }

    /** Appelle directement si l'autorisation est donnée, sinon ouvre le clavier du téléphone. */
    fun call(context: Context, number: String) {
        val uri = Uri.parse("tel:" + Uri.encode(number.trim()))
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE) ==
            PackageManager.PERMISSION_GRANTED
        val intent = Intent(if (granted) Intent.ACTION_CALL else Intent.ACTION_DIAL, uri)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            safeStart(context, Intent(Intent.ACTION_DIAL, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    fun hasCallPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE) ==
            PackageManager.PERMISSION_GRANTED

    fun openDialer(context: Context) {
        safeStart(context, Intent(Intent.ACTION_DIAL).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    fun openWifi(context: Context) =
        safeStart(context, Intent(Settings.ACTION_WIFI_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))

    fun openBluetooth(context: Context) =
        safeStart(context, Intent(Settings.ACTION_BLUETOOTH_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))

    fun openDisplay(context: Context) =
        safeStart(context, Intent(Settings.ACTION_DISPLAY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))

    fun openSound(context: Context) =
        safeStart(context, Intent(Settings.ACTION_SOUND_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))

    fun openLocationSettings(context: Context) =
        safeStart(context, Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))

    fun openEqualizer(context: Context) {
        val intent = Intent(AudioEffect.ACTION_DISPLAY_AUDIO_EFFECT_CONTROL_PANEL)
            .putExtra(AudioEffect.EXTRA_CONTENT_TYPE, AudioEffect.CONTENT_TYPE_MUSIC)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            toast(context, "Aucun égaliseur sur cet appareil")
        }
    }

    fun openWriteSettings(context: Context) {
        val intent = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:" + context.packageName))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        safeStart(context, intent)
    }

    fun voiceAssistant(context: Context) {
        val candidates = listOf(
            Intent(Intent.ACTION_VOICE_COMMAND),
            Intent("android.intent.action.ASSIST"),
            Intent(android.speech.RecognizerIntent.ACTION_WEB_SEARCH),
        )
        for (i in candidates) {
            try {
                context.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return
            } catch (e: Exception) {
                // on essaie le suivant
            }
        }
        toast(context, "Aucun assistant vocal disponible")
    }

    fun openAppInfo(context: Context, pkg: String) {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$pkg"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        safeStart(context, intent)
    }

    fun openNotificationAccess(context: Context) {
        val intent = Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        safeStart(context, intent)
    }

    fun openHomeSettings(context: Context) {
        val intent = Intent(Settings.ACTION_HOME_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            safeStart(context, Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    fun openSystemSettings(context: Context) {
        safeStart(context, Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    private fun safeStart(context: Context, intent: Intent) {
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            toast(context, "Impossible d'ouvrir cet écran")
        }
    }

    fun toast(context: Context, text: String) {
        Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
    }
}

object IconCache {
    private val cache = ConcurrentHashMap<String, ImageBitmap>()

    fun peek(pkg: String): ImageBitmap? = cache[pkg]

    fun get(context: Context, pkg: String, px: Int = 144): ImageBitmap? {
        cache[pkg]?.let { return it }
        return try {
            val drawable = context.packageManager.getApplicationIcon(pkg)
            val bmp = drawable.toBitmap(px, px).asImageBitmap()
            cache[pkg] = bmp
            bmp
        } catch (e: Exception) {
            null
        }
    }
}
