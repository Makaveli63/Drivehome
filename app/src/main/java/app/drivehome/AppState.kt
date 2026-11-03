package app.drivehome

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

enum class Screen { Home, Drawer, Settings }

/** État global de l'appli : réglages, liste d'apps, média, écran courant. */
class AppState(context: Context) {
    private val appContext = context.applicationContext

    val prefs = Prefs(appContext)
    val media = MediaWatcher(appContext)

    var apps by mutableStateOf<List<AppItem>>(emptyList())
        private set

    var screen by mutableStateOf(Screen.Home)

    /** Heure courante en ms, rafraîchie chaque seconde par l'interface. */
    var now by mutableLongStateOf(System.currentTimeMillis())

    /** Vrai entre 19h et 7h : ne change qu'aux bascules jour/nuit. */
    var autoNight by mutableStateOf(isNightAt(System.currentTimeMillis()))

    val visibleApps: List<AppItem>
        get() = apps.filter { it.pkg !in prefs.hidden }

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

    fun navPackage(): String? =
        prefs.navApp.ifEmpty { null }?.takeIf { p -> apps.any { it.pkg == p } }
            ?: Apps.firstInstalled(apps, Apps.NAV_PKGS)

    fun musicPackage(): String? =
        prefs.musicApp.ifEmpty { null }?.takeIf { p -> apps.any { it.pkg == p } }
            ?: Apps.firstInstalled(apps, Apps.MUSIC_PKGS)

    fun labelOf(pkg: String?): String =
        apps.firstOrNull { it.pkg == pkg }?.label ?: "Automatique"

    fun toggleFavorite(pkg: String) {
        val cur = prefs.favorites ?: emptyList()
        prefs.setFavorites(if (pkg in cur) cur - pkg else (cur + pkg).takeLast(MAX_FAVORITES))
    }

    fun toggleHidden(pkg: String) {
        val cur = prefs.hidden
        prefs.setHidden(if (pkg in cur) cur - pkg else cur + pkg)
    }

    companion object {
        const val MAX_FAVORITES = 5
    }
}
