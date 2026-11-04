package app.drivehome

import android.graphics.Bitmap
import android.graphics.Paint
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.TilesOverlay
import java.io.File

/** Guidage en cours, lu dans la notification de l'appli de navigation (Waze, Google Maps…). */
data class NavInfo(
    val key: String,
    val pkg: String,
    val title: String,
    val text: String,
    val sub: String,
    val icon: Bitmap?,
    val time: Long,
)

/** Flèche de position (bleue cerclée de blanc). */
private fun positionArrow(sizePx: Int, color: Int): Bitmap {
    val b = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
    val c = android.graphics.Canvas(b)
    val p = Paint(Paint.ANTI_ALIAS_FLAG)
    val r = sizePx / 2f
    p.color = 0x55000000
    c.drawCircle(r, r + 2, r * 0.92f, p)
    p.color = android.graphics.Color.WHITE
    c.drawCircle(r, r, r * 0.88f, p)
    p.color = color
    c.drawCircle(r, r, r * 0.72f, p)
    val path = android.graphics.Path().apply {
        moveTo(r, r * 0.45f)
        lineTo(r * 1.4f, r * 1.45f)
        lineTo(r, r * 1.2f)
        lineTo(r * 0.6f, r * 1.45f)
        close()
    }
    p.color = android.graphics.Color.WHITE
    c.drawPath(path, p)
    return b
}

/**
 * Carte OpenStreetMap en direct centrée sur la position GPS (sombre la nuit).
 * Non interactive : la tuile reste cliquable par-dessus.
 */
@Composable
fun LiveMap(state: AppState, modifier: Modifier, zoom: Double = 16.5) {
    val night = state.isNight()
    val loc = state.drive.location
    val fallback = state.drive.bestLatLon()
    val bearing = state.drive.bearing
    val accent = MaterialTheme.colorScheme.primary

    AndroidView(
        modifier = modifier,
        factory = { c ->
            Configuration.getInstance().apply {
                userAgentValue = c.packageName
                osmdroidBasePath = File(c.filesDir, "osm")
                osmdroidTileCache = File(c.cacheDir, "osm-tiles")
            }
            MapView(c).apply {
                setTileSource(TileSourceFactory.MAPNIK)
                setMultiTouchControls(false)
                zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
                isTilesScaledToDpi = true
                controller.setZoom(zoom)
                val marker = Marker(this).apply {
                    icon = android.graphics.drawable.BitmapDrawable(c.resources, positionArrow(72, android.graphics.Color.rgb(10, 132, 255)))
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                    setInfoWindow(null)
                }
                overlays.add(marker)
                tag = marker
                onResume()
            }
        },
        update = { mv ->
            mv.overlayManager.tilesOverlay.setColorFilter(if (night) TilesOverlay.INVERT_COLORS else null)
            val gp = when {
                loc != null -> GeoPoint(loc.latitude, loc.longitude)
                fallback != null -> GeoPoint(fallback.first, fallback.second)
                else -> null
            }
            val marker = mv.tag as? Marker
            if (gp != null) {
                mv.controller.setCenter(gp)
                marker?.position = gp
            }
            marker?.rotation = if (bearing >= 0) -bearing else 0f
            @Suppress("UNUSED_VARIABLE") val a = accent
            mv.invalidate()
        },
        onRelease = { mv -> mv.onDetach() },
    )
}

/** Bandeau de guidage : flèche de manœuvre + distance + instruction + arrivée. */
@Composable
fun GuidanceBanner(nav: NavInfo, modifier: Modifier = Modifier) {
    val accent = MaterialTheme.colorScheme.primary
    val onAccent = MaterialTheme.colorScheme.onPrimary
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(accent)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(64.dp).clip(RoundedCornerShape(14.dp)).background(Color.Black.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center,
        ) {
            val ic = nav.icon
            if (ic != null) {
                Image(ic.asImageBitmap(), contentDescription = null, modifier = Modifier.size(56.dp))
            } else {
                Text("⬆", color = onAccent, fontSize = 34.sp)
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(nav.title, color = onAccent, fontSize = 24.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (nav.text.isNotEmpty()) {
                Text(nav.text, color = onAccent, fontSize = 16.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            if (nav.sub.isNotEmpty()) {
                Text(nav.sub, color = onAccent.copy(alpha = 0.8f), fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}
