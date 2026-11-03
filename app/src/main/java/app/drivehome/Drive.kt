package app.drivehome

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Bundle
import android.os.Looper
import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Calendar
import java.util.Locale
import kotlin.math.PI
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.tan

// =============================================================================
// GPS : vitesse, cap, altitude, ordinateur de bord, alerte de vitesse
// =============================================================================

class DriveTracker(context: Context, private val prefs: Prefs) {
    private val appContext = context.applicationContext
    private val lm = appContext.getSystemService(Context.LOCATION_SERVICE) as LocationManager

    var permission by mutableStateOf(false)
        private set
    var gpsFix by mutableStateOf(false)
        private set
    var speedKmh by mutableFloatStateOf(0f)
        private set
    /** Cap en degrés, ou -1 si inconnu. */
    var bearing by mutableFloatStateOf(-1f)
        private set
    var altitude by mutableStateOf<Int?>(null)
        private set
    var location by mutableStateOf<Location?>(null)
        private set
    var overLimit by mutableStateOf(false)
        private set

    private var prevGps: Location? = null
    private var lastGpsAt = 0L
    private var lastSave = 0L
    private var lastBeep = 0L
    private var started = false
    private var tone: ToneGenerator? = null

    private val listener = object : LocationListener {
        override fun onLocationChanged(l: Location) = onFix(l)

        @Deprecated("Deprecated in Java")
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}

        override fun onProviderEnabled(provider: String) {}
        override fun onProviderDisabled(provider: String) {}
    }

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    fun start() {
        permission = hasPermission()
        if (!permission || started) return
        started = true
        try {
            val providers = lm.getProviders(true)
            for (p in providers) {
                lm.getLastKnownLocation(p)?.let { if (location == null) setLocation(it) }
            }
            if (LocationManager.GPS_PROVIDER in lm.allProviders) {
                lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 0f, listener, Looper.getMainLooper())
            }
            if (LocationManager.NETWORK_PROVIDER in lm.allProviders) {
                lm.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 60_000L, 50f, listener, Looper.getMainLooper())
            }
        } catch (e: Exception) {
            started = false
        }
    }

    fun stop() {
        try {
            lm.removeUpdates(listener)
        } catch (e: Exception) {
            // ignoré
        }
        started = false
        saveTrip()
        tone?.release()
        tone = null
    }

    /** Appelé chaque seconde : remet la vitesse à zéro si le GPS se tait. */
    fun tick() {
        if (gpsFix && SystemClock.elapsedRealtime() - lastGpsAt > 6000) {
            gpsFix = false
            speedKmh = 0f
            overLimit = false
        }
    }

    private fun setLocation(l: Location) {
        location = l
        prefs.lastLat = l.latitude.toFloat()
        prefs.lastLon = l.longitude.toFloat()
    }

    private fun onFix(l: Location) {
        if (l.provider != LocationManager.GPS_PROVIDER) {
            // position réseau : utile pour la météo et le soleil, pas pour la vitesse
            if (!gpsFix) setLocation(l)
            return
        }
        val now = SystemClock.elapsedRealtime()
        lastGpsAt = now
        gpsFix = true
        location = l

        var spd = if (l.hasSpeed()) l.speed * 3.6f else 0f
        val p = prevGps
        if (p != null) {
            val d = p.distanceTo(l)
            val dtMs = l.time - p.time
            if (!l.hasSpeed() && dtMs > 0) spd = d / (dtMs / 1000f) * 3.6f
            val accurate = !l.hasAccuracy() || l.accuracy < 50f
            if (spd > 3f && accurate && d < 1000f && dtMs in 1..15_000) {
                prefs.tripDistance = prefs.tripDistance + d
                prefs.tripMovingMs = prefs.tripMovingMs + dtMs
            }
        }
        prevGps = l
        if (spd < 1.5f) spd = 0f
        speedKmh = spd
        if (spd > prefs.tripMaxKmh) prefs.tripMaxKmh = spd
        if (l.hasBearing() && spd > 3f) bearing = l.bearing
        if (l.hasAltitude()) altitude = l.altitude.roundToInt()

        if (now - lastSave > 60_000) {
            lastSave = now
            prefs.lastLat = l.latitude.toFloat()
            prefs.lastLon = l.longitude.toFloat()
        }
        checkLimit(now)
    }

    private fun checkLimit(now: Long) {
        val limit = prefs.speedLimit
        val shown = displaySpeed(speedKmh, prefs.speedUnit)
        val over = limit > 0 && shown > limit
        if (over && prefs.speedBeep && (!overLimit || now - lastBeep > 10_000)) {
            lastBeep = now
            beep()
        }
        overLimit = over
    }

    private fun beep() {
        try {
            if (tone == null) tone = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 90)
            tone?.startTone(ToneGenerator.TONE_PROP_BEEP2, 400)
        } catch (e: Exception) {
            // pas de son disponible
        }
    }

    fun saveTrip() {
        // les valeurs sont déjà persistées à chaque modification
    }

    fun resetTrip() {
        prefs.tripDistance = 0f
        prefs.tripMovingMs = 0L
        prefs.tripMaxKmh = 0f
        prefs.tripStart = System.currentTimeMillis()
    }

    /** Position connue (GPS, réseau, ou dernière sauvegardée), sinon la ville choisie. */
    fun bestLatLon(): Pair<Double, Double>? {
        val city = prefs.cityLat to prefs.cityLon
        if (!city.first.isNaN() && !city.second.isNaN() && prefs.weatherCity.isNotEmpty()) {
            return city.first.toDouble() to city.second.toDouble()
        }
        location?.let { return it.latitude to it.longitude }
        if (!prefs.lastLat.isNaN() && !prefs.lastLon.isNaN()) {
            return prefs.lastLat.toDouble() to prefs.lastLon.toDouble()
        }
        return null
    }
}

fun displaySpeed(kmh: Float, unit: Int): Int =
    (if (unit == 1) kmh / 1.609344f else kmh).roundToInt()

fun speedUnitLabel(unit: Int) = if (unit == 1) "mph" else "km/h"

fun compassLabel(deg: Float): String {
    if (deg < 0) return "—"
    val names = listOf("N", "NE", "E", "SE", "S", "SO", "O", "NO")
    return names[(((deg % 360) + 22.5f) / 45f).toInt() % 8]
}

// =============================================================================
// Lever / coucher du soleil (algorithme NOAA simplifié, hors ligne)
// =============================================================================

object Sun {
    /** Minutes depuis minuit (heure locale) du lever et du coucher, ou null (nuit/jour polaire). */
    fun times(lat: Double, lon: Double, cal: Calendar): Pair<Int, Int>? {
        val day = cal.get(Calendar.DAY_OF_YEAR)
        val tzMin = (cal.get(Calendar.ZONE_OFFSET) + cal.get(Calendar.DST_OFFSET)) / 60000.0
        val g = 2 * PI / 365 * (day - 1)
        val eqTime = 229.18 * (0.000075 + 0.001868 * cos(g) - 0.032077 * sin(g) -
            0.014615 * cos(2 * g) - 0.040849 * sin(2 * g))
        val decl = 0.006918 - 0.399912 * cos(g) + 0.070257 * sin(g) - 0.006758 * cos(2 * g) +
            0.000907 * sin(2 * g) - 0.002697 * cos(3 * g) + 0.00148 * sin(3 * g)
        val latR = Math.toRadians(lat)
        val cosHa = cos(Math.toRadians(90.833)) / (cos(latR) * cos(decl)) - tan(latR) * tan(decl)
        if (cosHa < -1 || cosHa > 1) return null
        val ha = Math.toDegrees(acos(cosHa))
        val rise = 720 - 4 * (lon + ha) - eqTime + tzMin
        val set = 720 - 4 * (lon - ha) - eqTime + tzMin
        return rise.roundToInt() to set.roundToInt()
    }

    fun format(min: Int): String {
        val m = ((min % 1440) + 1440) % 1440
        return String.format(Locale.FRANCE, "%02d:%02d", m / 60, m % 60)
    }
}

// =============================================================================
// Météo (Open-Meteo : gratuit, sans clé)
// =============================================================================

data class Weather(
    val temp: Double,
    val code: Int,
    val wind: Double,
    val isDay: Boolean,
    val min: Double?,
    val max: Double?,
    val rainChance: Int?,
    val fetchedAt: Long,
)

class WeatherRepo(private val prefs: Prefs) {
    var current by mutableStateOf<Weather?>(null)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var loading by mutableStateOf(false)
        private set
    private var lastKey = ""

    fun isStale(): Boolean {
        val w = current ?: return true
        return System.currentTimeMillis() - w.fetchedAt > 30 * 60_000L
    }

    suspend fun refresh(lat: Double, lon: Double, force: Boolean = false) {
        val key = "%.2f,%.2f,%b".format(Locale.US, lat, lon, prefs.fahrenheit)
        if (!force && key == lastKey && !isStale()) return
        loading = true
        try {
            val unit = if (prefs.fahrenheit) "&temperature_unit=fahrenheit" else ""
            val url = "https://api.open-meteo.com/v1/forecast?latitude=%.4f&longitude=%.4f".format(Locale.US, lat, lon) +
                "&current=temperature_2m,weather_code,wind_speed_10m,is_day" +
                "&daily=temperature_2m_max,temperature_2m_min,precipitation_probability_max" +
                "&timezone=auto&forecast_days=1$unit"
            val json = withContext(Dispatchers.IO) { httpGet(url) }
            val o = JSONObject(json)
            val c = o.getJSONObject("current")
            val d = o.optJSONObject("daily")
            current = Weather(
                temp = c.getDouble("temperature_2m"),
                code = c.getInt("weather_code"),
                wind = c.optDouble("wind_speed_10m", 0.0),
                isDay = c.optInt("is_day", 1) == 1,
                min = d?.optJSONArray("temperature_2m_min")?.optDouble(0),
                max = d?.optJSONArray("temperature_2m_max")?.optDouble(0),
                rainChance = d?.optJSONArray("precipitation_probability_max")?.optInt(0),
                fetchedAt = System.currentTimeMillis(),
            )
            lastKey = key
            error = null
        } catch (e: Exception) {
            error = "Météo indisponible (connexion ?)"
        } finally {
            loading = false
        }
    }

    /** Cherche une ville et renvoie (nom affiché, lat, lon). */
    suspend fun geocode(name: String): Triple<String, Double, Double>? = try {
        val url = "https://geocoding-api.open-meteo.com/v1/search?count=1&language=fr&name=" +
            URLEncoder.encode(name, "UTF-8")
        val json = withContext(Dispatchers.IO) { httpGet(url) }
        val r = JSONObject(json).optJSONArray("results")?.optJSONObject(0)
        if (r == null) {
            null
        } else {
            val label = listOfNotNull(r.optString("name").ifEmpty { null }, r.optString("country").ifEmpty { null })
                .joinToString(", ")
            Triple(label, r.getDouble("latitude"), r.getDouble("longitude"))
        }
    } catch (e: Exception) {
        null
    }

    private fun httpGet(url: String): String {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 10_000
        conn.readTimeout = 10_000
        conn.setRequestProperty("User-Agent", "DriveHome")
        try {
            return conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }
}

/** Libellé et pictogramme pour un code météo WMO. */
fun weatherLabel(code: Int, day: Boolean = true): Pair<String, String> = when (code) {
    0 -> (if (day) "☀️" else "🌙") to "Ciel dégagé"
    1 -> (if (day) "🌤️" else "🌙") to "Peu nuageux"
    2 -> "⛅" to "Partiellement nuageux"
    3 -> "☁️" to "Couvert"
    45, 48 -> "🌫️" to "Brouillard"
    51, 53, 55, 56, 57 -> "🌦️" to "Bruine"
    61, 63, 65, 66, 67 -> "🌧️" to "Pluie"
    71, 73, 75, 77 -> "🌨️" to "Neige"
    80, 81, 82 -> "🌧️" to "Averses"
    85, 86 -> "🌨️" to "Averses de neige"
    95, 96, 99 -> "⛈️" to "Orage"
    else -> "🌡️" to "—"
}
