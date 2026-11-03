package app.drivehome

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

/** Destination enregistrée (libellé + adresse). */
data class Place(val label: String, val address: String)

/** Contact favori (nom + numéro). */
data class Contact(val name: String, val number: String)

/** Cartes possibles sur l'écran d'accueil. */
enum class CardType(val id: String, val label: String, val defW: Int, val defH: Int) {
    NAV("nav", "Navigation", 2, 2),
    CAR("car", "Ma voiture", 1, 2),
    MEDIA("media", "Musique", 1, 2),
    SPEED("speed", "Compteur de vitesse", 1, 1),
    CLOCK("clock", "Horloge", 1, 1),
    WEATHER("weather", "Météo", 1, 1),
    TRIP("trip", "Ordinateur de bord", 1, 1),
    ENGINE("engine", "Moteur (OBD)", 2, 1),
    MESSAGES("messages", "Messages", 2, 1),
    CONTACTS("contacts", "Contacts favoris", 2, 1),
    APPS("apps", "Raccourcis d'apps", 2, 1),
    ;

    companion object {
        fun of(id: String): CardType? = entries.firstOrNull { it.id == id }
    }
}

/** Réglages de l'utilisateur, observables par Compose et sauvegardés automatiquement. */
class Prefs(context: Context) {
    private val sp: SharedPreferences = context.applicationContext
        .getSharedPreferences("drivehome", Context.MODE_PRIVATE)

    // ---- délégués : lecture observable + écriture persistante -------------

    private inner class IntPref(private val key: String, def: Int) : ReadWriteProperty<Any?, Int> {
        private val s = mutableIntStateOf(sp.getInt(key, def))
        override fun getValue(thisRef: Any?, property: KProperty<*>): Int = s.intValue
        override fun setValue(thisRef: Any?, property: KProperty<*>, value: Int) {
            s.intValue = value
            sp.edit().putInt(key, value).apply()
        }
    }

    private inner class LongPref(private val key: String, def: Long) : ReadWriteProperty<Any?, Long> {
        private val s = mutableLongStateOf(sp.getLong(key, def))
        override fun getValue(thisRef: Any?, property: KProperty<*>): Long = s.longValue
        override fun setValue(thisRef: Any?, property: KProperty<*>, value: Long) {
            s.longValue = value
            sp.edit().putLong(key, value).apply()
        }
    }

    private inner class FloatPref(private val key: String, def: Float) : ReadWriteProperty<Any?, Float> {
        private val s = mutableFloatStateOf(sp.getFloat(key, def))
        override fun getValue(thisRef: Any?, property: KProperty<*>): Float = s.floatValue
        override fun setValue(thisRef: Any?, property: KProperty<*>, value: Float) {
            s.floatValue = value
            sp.edit().putFloat(key, value).apply()
        }
    }

    private inner class BoolPref(private val key: String, def: Boolean) : ReadWriteProperty<Any?, Boolean> {
        private val s = mutableStateOf(sp.getBoolean(key, def))
        override fun getValue(thisRef: Any?, property: KProperty<*>): Boolean = s.value
        override fun setValue(thisRef: Any?, property: KProperty<*>, value: Boolean) {
            s.value = value
            sp.edit().putBoolean(key, value).apply()
        }
    }

    private inner class StrPref(private val key: String, def: String) : ReadWriteProperty<Any?, String> {
        private val s = mutableStateOf(sp.getString(key, def) ?: def)
        override fun getValue(thisRef: Any?, property: KProperty<*>): String = s.value
        override fun setValue(thisRef: Any?, property: KProperty<*>, value: String) {
            s.value = value
            sp.edit().putString(key, value).apply()
        }
    }

    // ---- Apparence ----------------------------------------------------------

    /** 0 = auto (horaires), 1 = sombre, 2 = clair, 3 = auto (lever/coucher du soleil). */
    var themeMode by IntPref("themeMode", 0)
    var nightStart by IntPref("nightStart", 19)
    var nightEnd by IntPref("nightEnd", 7)
    var accentIndex by IntPref("accentIndex", 0)
    var wallpaperIndex by IntPref("wallpaperIndex", 0)
    var wallpaperUri by StrPref("wallpaperUri", "")
    /** Voile sur l'image perso, en %. */
    var wallpaperDim by IntPref("wallpaperDim", 40)
    /** Opacité des cartes, en %. */
    var cardOpacity by IntPref("cardOpacity", 55)
    /** Échelle globale de l'interface, en % (80..150). */
    var uiScale by IntPref("uiScale", 100)
    /** Taille des icônes, en % (80..150). */
    var iconScale by IntPref("iconScale", 100)
    /** 0 = gauche, 1 = droite, 2 = bas. */
    var dockPos by IntPref("dockPos", 0)
    var showStatusBar by BoolPref("showStatusBar", true)
    var fullscreen by BoolPref("fullscreen", false)
    var keepScreenOn by BoolPref("keepScreenOn", true)
    var clock24 by BoolPref("clock24", true)
    var showSeconds by BoolPref("showSeconds", false)

    // ---- Accueil --------------------------------------------------------------

    var cardOrderRaw by StrPref("cardOrder", CardType.entries.joinToString(",") { it.id })
    var cardsOffRaw by StrPref("cardsOff", "engine,messages,contacts,apps")
    private var cardSizesRaw by StrPref("cardSizes", "")
    /** Grille de l'accueil en mode paysage. */
    var homeCols by IntPref("homeCols", 4)
    var homeRows by IntPref("homeRows", 2)

    val cardOrder: List<CardType>
        get() {
            val saved = cardOrderRaw.split(",").mapNotNull { CardType.of(it) }.toMutableList()
            // nouvelle carte « Ma voiture » : placée juste après la première
            if (CardType.CAR !in saved && saved.isNotEmpty()) saved.add(1, CardType.CAR)
            return saved + CardType.entries.filter { it !in saved }
        }

    /** Taille d'une tuile en cellules (largeur, hauteur). */
    fun cardSize(c: CardType): Pair<Int, Int> {
        val m = cardSizesRaw.split(",").mapNotNull {
            val kv = it.split("=")
            val wh = kv.getOrNull(1)?.split("x")
            val w = wh?.getOrNull(0)?.toIntOrNull()
            val h = wh?.getOrNull(1)?.toIntOrNull()
            if (kv.size == 2 && w != null && h != null) kv[0] to (w to h) else null
        }.toMap()
        return m[c.id] ?: (c.defW to c.defH)
    }

    fun setCardSize(c: CardType, w: Int, h: Int) {
        val all = CardType.entries.associate { it.id to cardSize(it) }.toMutableMap()
        all[c.id] = w.coerceIn(1, 4) to h.coerceIn(1, 3)
        cardSizesRaw = all.entries.joinToString(",") { "${it.key}=${it.value.first}x${it.value.second}" }
    }

    val cardsOff: Set<CardType>
        get() = cardsOffRaw.split(",").mapNotNull { CardType.of(it) }.toSet()

    fun setCardOrder(list: List<CardType>) {
        cardOrderRaw = list.joinToString(",") { it.id }
    }

    fun setCardEnabled(c: CardType, on: Boolean) {
        val cur = cardsOff
        cardsOffRaw = (if (on) cur - c else cur + c).joinToString(",") { it.id }
    }

    // ---- Ma voiture -----------------------------------------------------------

    var carName by StrPref("carName", "Passat Variant")
    /** Affichage : 3 = mes photos détourées (défaut), 4 = modèle 3D importé, 0 = dessin de profil, 1 = vue 3/4 dessus, 2 = photo choisie. */
    var carView by IntPref("carViewMode", 3)
    var carPhotoUri by StrPref("carPhotoUri", "")
    var carPlate by StrPref("carPlate", "")
    /** 0 = diesel, 1 = essence, 2 = hybride, 3 = électrique. */
    var carFuel by IntPref("carFuel", 0)
    var carColor by IntPref("carColor", 0)
    var obdAddress by StrPref("obdAddress", "")
    var obdName by StrPref("obdName", "")
    var obdAuto by BoolPref("obdAuto", true)
    var doorVoice by BoolPref("doorVoice", true)

    /** Modèle 3D importé : on l'utilise à la place du dessin. */
    var use3d by BoolPref("use3d", true)
    var car3dOnHome by BoolPref("car3dOnHome", true)
    /** Repeindre la carrosserie du modèle 3D avec la couleur choisie. */
    var repaint by BoolPref("repaint", true)
    var paintMaterial by StrPref("paintMaterial", "")
    /** Change à chaque import pour recharger le modèle. */
    var car3dVersion by LongPref("car3dVersion", 0L)
    private var partsRaw by StrPref("carParts", "")

    private fun partsMap(): Map<String, String> = partsRaw.split("\n").mapNotNull {
        val i = it.indexOf('=')
        if (i <= 0) null else it.substring(0, i) to it.substring(i + 1)
    }.toMap()

    private fun putPart(key: String, value: String) {
        val m = partsMap().toMutableMap()
        if (value.isEmpty()) m.remove(key) else m[key] = value
        partsRaw = m.entries.joinToString("\n") { "${it.key}=${it.value}" }
    }

    /** Nom de la pièce du modèle 3D associée (vide = détection automatique). */
    fun partNode(p: CarPart): String = partsMap()[p.key].orEmpty()
    fun setPartNode(p: CarPart, name: String) = putPart(p.key, name)
    fun partInvert(p: CarPart): Boolean = partsMap()[p.key + "_inv"] == "1"
    fun setPartInvert(p: CarPart, inv: Boolean) = putPart(p.key + "_inv", if (inv) "1" else "")

    // ---- Dock -------------------------------------------------------------------

    var dockPhone by BoolPref("dockPhone", true)
    var dockCar by BoolPref("dockCar", true)
    var dockMic by BoolPref("dockMic", true)
    var dockControl by BoolPref("dockControl", true)
    var dockAdd by BoolPref("dockAdd", true)

    // ---- Applications --------------------------------------------------------

    var navApp by StrPref("navApp", "")
    var musicApp by StrPref("musicApp", "")
    /** 0 = alphabétique, 1 = les plus utilisées d'abord. */
    var drawerSort by IntPref("drawerSort", 0)
    var drawerLabels by BoolPref("drawerLabels", true)

    private var favoritesRaw by StrPref("favorites", "\u0000")
    private var hiddenRaw by StrPref("hidden", "")
    private var launchCountsRaw by StrPref("launchCounts", "")

    /** null = jamais configuré : on choisit des favoris par défaut. */
    val favorites: List<String>?
        get() = if (favoritesRaw == "\u0000") null else favoritesRaw.split(",").filter { it.isNotEmpty() }

    val hidden: List<String>
        get() = hiddenRaw.split(",").filter { it.isNotEmpty() }

    fun setFavorites(list: List<String>) {
        favoritesRaw = list.joinToString(",")
    }

    fun setHidden(list: List<String>) {
        hiddenRaw = list.joinToString(",")
    }

    val launchCounts: Map<String, Int>
        get() = launchCountsRaw.split(",").mapNotNull {
            val i = it.lastIndexOf('=')
            if (i <= 0) null else it.substring(0, i) to (it.substring(i + 1).toIntOrNull() ?: 0)
        }.toMap()

    fun countLaunch(pkg: String) {
        val m = launchCounts.toMutableMap()
        m[pkg] = (m[pkg] ?: 0) + 1
        launchCountsRaw = m.entries.sortedByDescending { it.value }.take(200)
            .joinToString(",") { "${it.key}=${it.value}" }
    }

    // ---- Navigation -------------------------------------------------------------

    var homeAddr by StrPref("homeAddr", "")
    var workAddr by StrPref("workAddr", "")
    private var placesRaw by StrPref("places", "")

    val places: List<Place>
        get() = decode(placesRaw).map { Place(it.first, it.second) }

    fun setPlaces(list: List<Place>) {
        placesRaw = encode(list.map { it.label to it.address })
    }

    // ---- Téléphone ---------------------------------------------------------------

    private var contactsRaw by StrPref("contacts", "")

    val contacts: List<Contact>
        get() = decode(contactsRaw).map { Contact(it.first, it.second) }

    fun setContacts(list: List<Contact>) {
        contactsRaw = encode(list.map { it.name to it.number })
    }

    // ---- Conduite -------------------------------------------------------------------

    /** 0 = km/h, 1 = mph. */
    var speedUnit by IntPref("speedUnit", 0)
    /** Seuil d'alerte dans l'unité choisie ; 0 = désactivée. */
    var speedLimit by IntPref("speedLimit", 0)
    var speedBeep by BoolPref("speedBeep", true)
    var tripDistance by FloatPref("tripDistance", 0f)
    var tripMovingMs by LongPref("tripMovingMs", 0L)
    var tripMaxKmh by FloatPref("tripMaxKmh", 0f)
    var tripStart by LongPref("tripStart", System.currentTimeMillis())

    // ---- Météo -------------------------------------------------------------------------

    var fahrenheit by BoolPref("fahrenheit", false)
    var weatherCity by StrPref("weatherCity", "")
    var cityLat by FloatPref("cityLat", Float.NaN)
    var cityLon by FloatPref("cityLon", Float.NaN)
    var lastLat by FloatPref("lastLat", Float.NaN)
    var lastLon by FloatPref("lastLon", Float.NaN)

    // ---- Messages -----------------------------------------------------------------------

    var readAloud by BoolPref("readAloud", false)
    var hideMsgContent by BoolPref("hideMsgContent", false)
    var speechRate by IntPref("speechRate", 100)

    // ---- Démarrage & veille ----------------------------------------------------------

    var autoResumeMusic by BoolPref("autoResumeMusic", false)
    var autoLaunchNav by BoolPref("autoLaunchNav", false)
    /** Écran de veille après N minutes sans toucher l'accueil ; 0 = jamais. */
    var screensaverMin by IntPref("screensaverMin", 0)
    var firstRunDone by BoolPref("firstRunDone", false)

    // ---- Encodage des listes (tabulation / retour à la ligne) -----------------------------

    private fun encode(list: List<Pair<String, String>>): String =
        list.joinToString("\n") { "${clean(it.first)}\t${clean(it.second)}" }

    private fun decode(raw: String): List<Pair<String, String>> =
        raw.split("\n").mapNotNull {
            val parts = it.split("\t")
            if (parts.size < 2 || parts[1].isBlank()) null else parts[0] to parts[1]
        }

    private fun clean(s: String) = s.replace("\t", " ").replace("\n", " ").trim()
}
