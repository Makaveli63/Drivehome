package app.drivehome

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Réglages de l'utilisateur, observables par Compose et sauvegardés. */
class Prefs(context: Context) {
    private val sp = context.applicationContext
        .getSharedPreferences("drivehome", Context.MODE_PRIVATE)

    // 0 = automatique (sombre la nuit), 1 = sombre, 2 = clair
    var themeMode by mutableIntStateOf(sp.getInt("themeMode", 0))
        private set

    var accentIndex by mutableIntStateOf(sp.getInt("accentIndex", 0))
        private set

    var wallpaperIndex by mutableIntStateOf(sp.getInt("wallpaperIndex", 0))
        private set

    // 0 = gauche, 1 = droite, 2 = bas
    var dockPos by mutableIntStateOf(sp.getInt("dockPos", 0))
        private set

    // taille des icônes en %, 80..140
    var iconScale by mutableIntStateOf(sp.getInt("iconScale", 100))
        private set

    var showNav by mutableStateOf(sp.getBoolean("showNav", true))
        private set

    var showMedia by mutableStateOf(sp.getBoolean("showMedia", true))
        private set

    var showClock by mutableStateOf(sp.getBoolean("showClock", true))
        private set

    var clock24 by mutableStateOf(sp.getBoolean("clock24", true))
        private set

    var wallpaperUri by mutableStateOf(sp.getString("wallpaperUri", "") ?: "")
        private set

    var navApp by mutableStateOf(sp.getString("navApp", "") ?: "")
        private set

    var musicApp by mutableStateOf(sp.getString("musicApp", "") ?: "")
        private set

    var homeAddr by mutableStateOf(sp.getString("homeAddr", "") ?: "")
        private set

    var workAddr by mutableStateOf(sp.getString("workAddr", "") ?: "")
        private set

    /** null = jamais configuré : on choisit des favoris par défaut. */
    var favorites by mutableStateOf(readList("favorites"))
        private set

    var hidden by mutableStateOf(readList("hidden") ?: emptyList())
        private set

    private fun readList(key: String): List<String>? {
        val raw = sp.getString(key, null) ?: return null
        return raw.split(",").filter { it.isNotEmpty() }
    }

    private fun putInt(key: String, v: Int) = sp.edit().putInt(key, v).apply()
    private fun putBool(key: String, v: Boolean) = sp.edit().putBoolean(key, v).apply()
    private fun putStr(key: String, v: String) = sp.edit().putString(key, v).apply()

    fun setThemeMode(v: Int) { themeMode = v; putInt("themeMode", v) }
    fun setAccentIndex(v: Int) { accentIndex = v; putInt("accentIndex", v) }
    fun setWallpaperIndex(v: Int) { wallpaperIndex = v; putInt("wallpaperIndex", v) }
    fun setDockPos(v: Int) { dockPos = v; putInt("dockPos", v) }
    fun setIconScale(v: Int) { iconScale = v; putInt("iconScale", v) }

    fun setShowNav(v: Boolean) { showNav = v; putBool("showNav", v) }
    fun setShowMedia(v: Boolean) { showMedia = v; putBool("showMedia", v) }
    fun setShowClock(v: Boolean) { showClock = v; putBool("showClock", v) }
    fun setClock24(v: Boolean) { clock24 = v; putBool("clock24", v) }

    fun setWallpaperUri(v: String) { wallpaperUri = v; putStr("wallpaperUri", v) }
    fun setNavApp(v: String) { navApp = v; putStr("navApp", v) }
    fun setMusicApp(v: String) { musicApp = v; putStr("musicApp", v) }
    fun setHomeAddr(v: String) { homeAddr = v; putStr("homeAddr", v) }
    fun setWorkAddr(v: String) { workAddr = v; putStr("workAddr", v) }

    fun setFavorites(list: List<String>) {
        favorites = list
        putStr("favorites", list.joinToString(","))
    }

    fun setHidden(list: List<String>) {
        hidden = list
        putStr("hidden", list.joinToString(","))
    }
}
