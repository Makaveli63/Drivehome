package app.drivehome

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
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

    fun voiceAssistant(context: Context) {
        val intent = Intent(Intent.ACTION_VOICE_COMMAND).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            toast(context, "Aucun assistant vocal disponible")
        }
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
