package app.drivehome

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.osmdroid.util.GeoPoint
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale

/**
 * Itinéraire affiché sur la carte de DriveHome (tracé violet), calculé avec
 * OpenStreetMap : Nominatim pour l'adresse, OSRM pour le trajet.
 */
class RouteRepo {
    var points by mutableStateOf<List<GeoPoint>>(emptyList())
        private set
    var destination by mutableStateOf<GeoPoint?>(null)
        private set
    var label by mutableStateOf("")
        private set
    /** Durée (s) et distance (m) totales prévues. */
    var durationS by mutableStateOf(0.0)
        private set
    var distanceM by mutableStateOf(0.0)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var plannedAt by mutableStateOf(0L)
        private set

    fun clear() {
        points = emptyList()
        destination = null
        label = ""
        error = null
    }

    suspend fun plan(fromLat: Double, fromLon: Double, address: String) {
        error = null
        try {
            val dest = withContext(Dispatchers.IO) { geocode(address, fromLat, fromLon) }
            if (dest == null) {
                error = "Adresse introuvable pour le tracé"
                return
            }
            val url = String.format(
                Locale.US,
                "https://router.project-osrm.org/route/v1/driving/%.6f,%.6f;%.6f,%.6f?overview=full&geometries=geojson",
                fromLon, fromLat, dest.longitude, dest.latitude,
            )
            val json = withContext(Dispatchers.IO) { httpGet(url) }
            val route = JSONObject(json).getJSONArray("routes").getJSONObject(0)
            val coords: JSONArray = route.getJSONObject("geometry").getJSONArray("coordinates")
            val pts = ArrayList<GeoPoint>(coords.length())
            for (i in 0 until coords.length()) {
                val c = coords.getJSONArray(i)
                pts.add(GeoPoint(c.getDouble(1), c.getDouble(0)))
            }
            points = pts
            destination = dest
            label = address
            durationS = route.optDouble("duration", 0.0)
            distanceM = route.optDouble("distance", 0.0)
            plannedAt = System.currentTimeMillis()
        } catch (e: Exception) {
            error = "Tracé indisponible (connexion ?)"
        }
    }

    /** « Arr. 10:16 · 11 min · 8,6 km » d'après le trajet calculé. */
    fun summary(): String? {
        if (points.isEmpty() || durationS <= 0) return null
        val arr = java.text.SimpleDateFormat("HH:mm", Locale.FRANCE).format(java.util.Date(plannedAt + (durationS * 1000).toLong()))
        val min = (durationS / 60).toInt()
        val dur = if (min >= 60) "${min / 60} h ${min % 60} min" else "$min min"
        val km = String.format(Locale.FRANCE, "%.1f km", distanceM / 1000)
        return "Arr. $arr · $dur · $km"
    }

    /** Efface le tracé une fois arrivé. */
    fun checkArrival(lat: Double, lon: Double) {
        val d = destination ?: return
        if (d.distanceToAsDouble(GeoPoint(lat, lon)) < 60.0) clear()
    }

    private fun geocode(q: String, nearLat: Double, nearLon: Double): GeoPoint? {
        // biais vers la position actuelle (zone de ±1°)
        val box = String.format(Locale.US, "%.4f,%.4f,%.4f,%.4f", nearLon - 1, nearLat + 1, nearLon + 1, nearLat - 1)
        val url = "https://nominatim.openstreetmap.org/search?format=json&limit=1&accept-language=fr&viewbox=$box&q=" +
            URLEncoder.encode(q, "UTF-8")
        val arr = JSONArray(httpGet(url))
        if (arr.length() == 0) return null
        val o = arr.getJSONObject(0)
        return GeoPoint(o.getString("lat").toDouble(), o.getString("lon").toDouble())
    }

    private fun httpGet(url: String): String {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 12_000
        conn.readTimeout = 15_000
        conn.setRequestProperty("User-Agent", "DriveHome/2.5 (Android car launcher)")
        try {
            return conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }
}
