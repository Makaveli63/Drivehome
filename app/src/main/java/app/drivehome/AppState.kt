package app.drivehome

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import java.util.Calendar

enum class Screen { Home, Drawer, Phone, Messages, Settings }

/** État global de l'appli : réglages, apps, média, GPS, météo, écran courant. */
class AppState(context: Context) {
    private val appContext = context.applicationContext

    val prefs = Prefs(appContext)
    val media = MediaWatcher(appContext)
    val drive = DriveTracker(appContext, prefs)
    val weather = WeatherRepo(prefs)
    val voice = Voice(appContext)

    var apps by mutableStateOf<List<AppItem>>(emptyList())
        private set

    var screen by mutableStateOf(Screen.Home)
    var controlCenter by mutableStateOf(false)

    /** Heure courante en ms, rafraîchie chaque seconde par l'interface. */
    var now by mutableLongStateOf(System.currentTimeMillis())

    /** Dernier contact avec l'écran (pour l'écran de veille). */
    var lastTouch by mutableLongStateOf(System.currentTimeMillis())
    var screensaverForced by mutableStateOf(false)

    /** Résultat du calcul jour/nuit automatique (ne change qu'aux bascules). */
    var autoNight by mutableStateOf(false)

    var batteryLevel by mutableIntStateOf(-1)
        private set
    var charging by mutableStateOf(false)
        private set

    private var batteryRegistered = false
    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, i: Intent?) {
            if (i == null) return
            val present = i.getBooleanExtra(BatteryManager.EXTRA_PRESENT, true)
            val level = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
            batteryLevel = if (!present || level < 0 || scale <= 0) -1 else level * 100 / scale
            charging = i.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0
        }
    }

    init {
        autoNight = computeNight(System.currentTimeMillis())
        NotifStore.onNew = { m -> onNewMessage(m) }
    }

    val visibleApps: List<AppItem>
        get() {
            val hidden = prefs.hidden
            val list = apps.filter { it.pkg !in hidden }
            if (prefs.drawerSort != 1) return list
            val counts = prefs.launchCounts
            return list.sortedWith(compareByDescending<AppItem> { counts[it.pkg] ?: 0 }.thenBy { it.label.lowercase() })
        }

    fun reload() {
        apps = Apps.load(appContext)
        if (prefs.favorites == null) {
            val defaults = listOfNotNull(
                Apps.firstInstalled(apps, Apps.NAV_PKGS),
                Apps.firstInstalled(apps, Apps.MUSIC_PKGS),
                Apps.firstInstalled(apps, Apps.PHONE_PKGS),
            )
            prefs.setFavorites(defaults)
        }
    }

    fun onResume() {
        reload()
        media.start()
        drive.start()
        voice.rate = prefs.speechRate / 100f
        if (!batteryRegistered) {
            try {
                ContextCompat.registerReceiver(
                    appContext,
                    batteryReceiver,
                    IntentFilter(Intent.ACTION_BATTERY_CHANGED),
                    ContextCompat.RECEIVER_NOT_EXPORTED,
                )
                batteryRegistered = true
            } catch (e: Exception) {
                // pas de batterie sur cet autoradio
            }
        }
    }

    fun onPause() {
        media.stop()
    }

    fun onDestroy() {
        drive.stop()
        voice.shutdown()
        if (batteryRegistered) {
            try {
                appContext.unregisterReceiver(batteryReceiver)
            } catch (e: Exception) {
                // ignoré
            }
            batteryRegistered = false
        }
        if (NotifStore.onNew != null) NotifStore.onNew = null
    }

    /** Lance une app et compte l'utilisation (pour le tri « les plus utilisées »). */
    fun launch(pkg: String) {
        prefs.countLaunch(pkg)
        Apps.launch(appContext, pkg)
    }

    fun navPackage(): String? =
        prefs.navApp.ifEmpty { null }?.takeIf { p -> apps.any { it.pkg == p } }
            ?: Apps.firstInstalled(apps, Apps.NAV_PKGS)

    fun musicPackage(): String? =
        prefs.musicApp.ifEmpty { null }?.takeIf { p -> apps.any { it.pkg == p } }
            ?: Apps.firstInstalled(apps, Apps.MUSIC_PKGS)

    fun labelOf(pkg: String?): String =
        apps.firstOrNull { it.pkg == pkg }?.label ?: "Automatique"

    fun navigateTo(address: String) {
        Apps.startNavigation(appContext, navPackage(), address)
    }

    fun toggleFavorite(pkg: String) {
        val cur = prefs.favorites ?: emptyList()
        prefs.setFavorites(if (pkg in cur) cur - pkg else (cur + pkg).takeLast(MAX_FAVORITES))
    }

    fun toggleHidden(pkg: String) {
        val cur = prefs.hidden
        prefs.setHidden(if (pkg in cur) cur - pkg else cur + pkg)
    }

    fun enabledCards(): List<CardType> {
        val off = prefs.cardsOff
        return prefs.cardOrder.filter { it !in off }
    }

    // ---- jour / nuit --------------------------------------------------------

    fun computeNight(millis: Long): Boolean {
        val cal = Calendar.getInstance().apply { timeInMillis = millis }
        val minutes = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
        if (prefs.themeMode == 3) {
            val ll = drive.bestLatLon()
            if (ll != null) {
                val t = Sun.times(ll.first, ll.second, cal)
                if (t != null) return minutes < t.first || minutes >= t.second
            }
        }
        val start = prefs.nightStart * 60
        val end = prefs.nightEnd * 60
        return if (start == end) false
        else if (start > end) minutes >= start || minutes < end
        else minutes in start until end
    }

    fun isNight(): Boolean = when (prefs.themeMode) {
        1 -> true
        2 -> false
        else -> autoNight
    }

    // ---- messages -----------------------------------------------------------

    private fun onNewMessage(m: Msg) {
        if (prefs.readAloud) speakMessage(m)
    }

    fun speakMessage(m: Msg) {
        voice.rate = prefs.speechRate / 100f
        val who = m.title.ifEmpty { m.app }
        voice.speak(if (prefs.hideMsgContent) "Nouveau message de $who sur ${m.app}" else "Message de $who. ${m.text}")
    }

    companion object {
        const val MAX_FAVORITES = 8
    }
}
