package app.drivehome

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

// ---------------------------------------------------------------------------
// Navigation
// ---------------------------------------------------------------------------

private val NEARBY = listOf(
    "🔌 Recharge" to "borne de recharge",
    "🅿️ Parking" to "parking",
    "🍴 Restaurant" to "restaurant",
    "☕ Café" to "café",
    "🛒 Supermarché" to "supermarché",
    "🏥 Hôpital" to "hôpital",
    "🔧 Garage" to "garage automobile",
)

@Composable
fun NavCard(state: AppState, modifier: Modifier) {
    val ctx = LocalContext.current
    val p = LocalPalette.current
    val accent = MaterialTheme.colorScheme.primary
    val navPkg = state.navPackage()
    val pf = state.prefs
    var search by remember { mutableStateOf(false) }

    val nav = NotifStore.nav
    CardShell(modifier, background = {
        // carte en direct en fond de tuile
        if (pf.liveMap) {
            Box(Modifier.matchParentSize()) {
                LiveMap(state, Modifier.fillMaxSize())
                Box(
                    Modifier.fillMaxSize().background(
                        androidx.compose.ui.graphics.Brush.verticalGradient(
                            listOf(Color.Black.copy(alpha = if (p.night) 0.55f else 0.25f), Color.Transparent, Color.Black.copy(alpha = if (p.night) 0.65f else 0.35f)),
                        ),
                    ),
                )
                // la carte n'est pas manipulable : un appui ouvre l'appli de navigation
                Box(
                    Modifier.fillMaxSize().clickable(
                        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                        indication = null,
                    ) { navPkg?.let { state.launch(it) } },
                )
            }
        } else {
            Canvas(Modifier.matchParentSize()) {
                val w = this.size.width
                val h = this.size.height
                val road = accent.copy(alpha = 0.12f)
                drawLine(road, Offset(0f, h * 0.78f), Offset(w, h * 0.30f), strokeWidth = 30f, cap = StrokeCap.Round)
                drawLine(road, Offset(w * 0.28f, 0f), Offset(w * 0.58f, h), strokeWidth = 20f, cap = StrokeCap.Round)
            }
        }
    }) {

        if (nav != null) {
            // ---- guidage en cours, façon CarPlay
            Column(Modifier.fillMaxSize()) {
                GuidanceBanner(nav)
                Spacer(Modifier.weight(1f))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = { state.launch(nav.pkg) },
                        modifier = Modifier.weight(1f).height(50.dp),
                        shape = RoundedCornerShape(50),
                    ) { Text("Ouvrir le guidage", fontSize = 16.sp, fontWeight = FontWeight.SemiBold) }
                }
            }
            return@CardShell
        }

        Column(Modifier.fillMaxSize()) {
            CardHeader("Navigation", navPkg)

            Column(
                Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                DestinationRow("🏠 Domicile", pf.homeAddr) {
                    if (pf.homeAddr.isEmpty()) state.screen = Screen.Settings else state.navigateTo(pf.homeAddr)
                }
                DestinationRow("💼 Travail", pf.workAddr) {
                    if (pf.workAddr.isEmpty()) state.screen = Screen.Settings else state.navigateTo(pf.workAddr)
                }
                pf.places.forEach { pl ->
                    DestinationRow("⭐ ${pl.label}", pl.address) { state.navigateTo(pl.address) }
                }
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    (listOf("⛽ " + (if (state.prefs.carFuel == 0) "Gazole" else "Essence") to state.fuelStationQuery()) + NEARBY).forEach { (label, q) ->
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(50))
                                .background(p.chip)
                                .clickable { Apps.searchNearby(ctx, navPkg, q) }
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                        ) { Text(label, color = p.text, fontSize = 14.sp) }
                    }
                }
            }

            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = { search = true },
                    modifier = Modifier.weight(1f).height(50.dp),
                    shape = RoundedCornerShape(50),
                ) { Text("Rechercher", fontSize = 16.sp, fontWeight = FontWeight.SemiBold) }
                Button(
                    onClick = {
                        if (navPkg != null) state.launch(navPkg)
                        else Apps.toast(ctx, "Aucune application de navigation trouvée")
                    },
                    modifier = Modifier.weight(1f).height(50.dp),
                    shape = RoundedCornerShape(50),
                ) { Text("Carte", fontSize = 16.sp, fontWeight = FontWeight.SemiBold) }
            }
        }
    }

    if (search) {
        var q by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { search = false },
            title = { Text("Où allons-nous ?") },
            text = {
                OutlinedTextField(
                    value = q,
                    onValueChange = { q = it },
                    singleLine = true,
                    placeholder = { Text("Adresse, lieu, ville…") },
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (q.isNotBlank()) state.navigateTo(q.trim())
                    search = false
                }) { Text("Y aller") }
            },
            dismissButton = {
                TextButton(onClick = {
                    if (q.isNotBlank()) Apps.searchNearby(ctx, navPkg, q.trim())
                    search = false
                }) { Text("Chercher autour") }
            },
        )
    }
}

@Composable
private fun DestinationRow(label: String, address: String, onClick: () -> Unit) {
    val p = LocalPalette.current
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(p.chip)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Text(label, color = p.text, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
        Text(
            address.ifEmpty { "Définir dans Réglages" },
            color = p.sub,
            fontSize = 13.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

// ---------------------------------------------------------------------------
// Musique
// ---------------------------------------------------------------------------

@Composable
fun MediaCard(state: AppState, modifier: Modifier) {
    val ctx = LocalContext.current
    val p = LocalPalette.current
    val m = state.media.info
    val musicPkg = state.musicPackage()

    CardShell(modifier) {
        when {
            !m.access -> Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
                CardHeader("Musique", musicPkg)
                Text(
                    "Autorise l'accès aux notifications pour afficher et piloter la lecture en cours (et lire tes messages).",
                    color = p.sub,
                    fontSize = 14.sp,
                )
                Button(onClick = { Apps.openNotificationAccess(ctx) }, shape = RoundedCornerShape(50)) {
                    Text("Autoriser")
                }
            }

            !m.hasSession -> Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
                CardHeader("Musique", musicPkg)
                Text("Rien en lecture", color = p.sub, fontSize = 15.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(onClick = { state.media.resume() }, shape = RoundedCornerShape(50)) { Text("▶ Reprendre") }
                    Button(
                        onClick = {
                            if (musicPkg != null) state.launch(musicPkg)
                            else Apps.toast(ctx, "Aucune application de musique trouvée")
                        },
                        shape = RoundedCornerShape(50),
                    ) { Text("Ouvrir") }
                }
            }

            else -> Row(
                Modifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                val art = m.art
                Box(
                    Modifier
                        .fillMaxHeight()
                        .aspectRatio(1f, matchHeightConstraintsFirst = true)
                        .clip(RoundedCornerShape(18.dp))
                        .background(p.chip)
                        .clickable { m.pkg?.let { state.launch(it) } },
                ) {
                    if (art != null) {
                        Image(
                            bitmap = art.asImageBitmap(),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else if (m.pkg != null) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { AppIcon(m.pkg, 56.dp) }
                    }
                }

                Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.SpaceBetween) {
                    Row(verticalAlignment = Alignment.Top) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                m.title ?: "Lecture en cours",
                                color = p.text,
                                fontSize = 19.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            val sub = listOfNotNull(m.artist, m.album).filter { it.isNotEmpty() }.joinToString(" · ")
                            if (sub.isNotEmpty()) {
                                Text(sub, color = p.sub, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                        if (m.sources.size > 1) {
                            Box(
                                Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable { state.media.nextSource() }
                                    .padding(4.dp),
                            ) {
                                m.pkg?.let { AppIcon(it, 30.dp) }
                            }
                        }
                    }

                    if (m.duration > 0) {
                        // lire state.now pour rafraîchir la progression chaque seconde
                        @Suppress("UNUSED_VARIABLE") val tick = state.now
                        val pos = m.currentPosition()
                        Column {
                            Slider(
                                value = pos.toFloat().coerceIn(0f, m.duration.toFloat()),
                                onValueChange = { if (m.canSeek) state.media.seekTo(it.toLong()) },
                                valueRange = 0f..m.duration.toFloat(),
                                enabled = m.canSeek,
                                modifier = Modifier.height(24.dp),
                            )
                            Row(Modifier.fillMaxWidth()) {
                                Text(formatDuration(pos), color = p.sub, fontSize = 12.sp, modifier = Modifier.weight(1f))
                                Text("-" + formatDuration(m.duration - pos), color = p.sub, fontSize = 12.sp)
                            }
                        }
                    }

                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RoundButton(onClick = { state.media.previous() }, size = 50.dp) { PrevGlyph(p.text, 22.dp) }
                        if (m.canSeek) {
                            RoundButton(onClick = { state.media.skipBy(-15_000) }, size = 44.dp) {
                                Text("-15", color = p.text, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        RoundButton(onClick = { state.media.playPause() }, filled = true, size = 60.dp) {
                            val on = MaterialTheme.colorScheme.onPrimary
                            if (m.playing) PauseGlyph(on) else PlayGlyph(on)
                        }
                        if (m.canSeek) {
                            RoundButton(onClick = { state.media.skipBy(30_000) }, size = 44.dp) {
                                Text("+30", color = p.text, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        RoundButton(onClick = { state.media.next() }, size = 50.dp) { NextGlyph(p.text, 22.dp) }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Compteur de vitesse
// ---------------------------------------------------------------------------

@Composable
fun SpeedCard(state: AppState, modifier: Modifier, onRequestPermissions: (Array<String>) -> Unit) {
    val p = LocalPalette.current
    val d = state.drive
    val pf = state.prefs
    val accent = MaterialTheme.colorScheme.primary
    val speed = displaySpeed(d.speedKmh, pf.speedUnit)
    val over = d.overLimit

    CardShell(modifier, tint = if (over) DANGER.copy(alpha = 0.35f) else Color.Transparent) {
        if (!d.permission) {
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
                CardHeader("Vitesse")
                Text("Autorise la localisation pour afficher la vitesse GPS.", color = p.sub, fontSize = 14.sp)
                Button(onClick = { onRequestPermissions(MainActivity.LOCATION_PERMISSIONS) }, shape = RoundedCornerShape(50)) {
                    Text("Autoriser")
                }
            }
            return@CardShell
        }

        Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            // jauge
            Box(Modifier.fillMaxHeight().aspectRatio(1f, matchHeightConstraintsFirst = true), contentAlignment = Alignment.Center) {
                val maxGauge = if (pf.speedUnit == 1) 140f else 220f
                val frac = (speed / maxGauge).coerceIn(0f, 1f)
                val track = p.chip
                val limitFrac = if (pf.speedLimit > 0) (pf.speedLimit / maxGauge).coerceIn(0f, 1f) else -1f
                Canvas(Modifier.fillMaxSize().padding(6.dp)) {
                    val sw = size.minDimension * 0.09f
                    drawArc(track, 135f, 270f, false, style = Stroke(sw, cap = StrokeCap.Round))
                    drawArc(if (over) DANGER else accent, 135f, 270f * frac, false, style = Stroke(sw, cap = StrokeCap.Round))
                    if (limitFrac >= 0f) {
                        rotate(135f + 270f * limitFrac) {
                            drawLine(
                                DANGER,
                                Offset(center.x + size.minDimension / 2 - sw * 1.2f, center.y),
                                Offset(center.x + size.minDimension / 2 + sw * 0.2f, center.y),
                                strokeWidth = sw * 0.35f,
                            )
                        }
                    }
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        if (d.gpsFix) "$speed" else "--",
                        color = if (over) DANGER else p.text,
                        fontSize = 52.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(speedUnitLabel(pf.speedUnit), color = p.sub, fontSize = 14.sp)
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                InfoLine("Cap", compassLabel(d.bearing) + if (d.bearing >= 0) " ${d.bearing.toInt()}°" else "")
                InfoLine("Altitude", d.altitude?.let { "$it m" } ?: "—")
                InfoLine("Alerte", if (pf.speedLimit > 0) "${pf.speedLimit} ${speedUnitLabel(pf.speedUnit)}" else "Off")
                if (!d.gpsFix) Text("Recherche GPS…", color = p.sub, fontSize = 13.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(0, 50, 80, 90, 110, 130).forEach { v ->
                        val sel = pf.speedLimit == v
                        Box(
                            Modifier
                                .clip(CircleShape)
                                .background(if (sel) DANGER else p.chip)
                                .clickable { pf.speedLimit = v }
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                        ) {
                            Text(if (v == 0) "Off" else "$v", color = if (sel) Color.White else p.text, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun InfoLine(label: String, value: String) {
    val p = LocalPalette.current
    Row {
        Text(label, color = p.sub, fontSize = 14.sp, modifier = Modifier.width(76.dp))
        Text(value, color = p.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

// ---------------------------------------------------------------------------
// Horloge
// ---------------------------------------------------------------------------

@Composable
fun ClockCard(state: AppState, modifier: Modifier) {
    val p = LocalPalette.current
    val pf = state.prefs
    val pattern = if (pf.clock24) (if (pf.showSeconds) "HH:mm:ss" else "HH:mm") else (if (pf.showSeconds) "h:mm:ss a" else "h:mm a")
    val time = SimpleDateFormat(pattern, Locale.getDefault()).format(Date(state.now))
    val date = SimpleDateFormat("EEEE d MMMM", Locale.getDefault())
        .format(Date(state.now))
        .replaceFirstChar { it.uppercase() }
    val sun = state.drive.bestLatLon()?.let { Sun.times(it.first, it.second, Calendar.getInstance()) }

    CardShell(modifier) {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
            Text(time, color = p.text, fontSize = 56.sp, fontWeight = FontWeight.Light, maxLines = 1)
            Text(date, color = p.sub, fontSize = 18.sp)
            if (sun != null) {
                Spacer(Modifier.height(6.dp))
                Text("🌅 ${Sun.format(sun.first)}   🌇 ${Sun.format(sun.second)}", color = p.sub, fontSize = 14.sp)
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Météo
// ---------------------------------------------------------------------------

@Composable
fun WeatherCard(state: AppState, modifier: Modifier, onRequestPermissions: (Array<String>) -> Unit) {
    val p = LocalPalette.current
    val w = state.weather.current
    val pf = state.prefs
    val located = state.drive.bestLatLon() != null

    CardShell(modifier) {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
            CardHeader(if (pf.weatherCity.isNotEmpty()) "Météo · ${pf.weatherCity}" else "Météo")
            when {
                w != null -> {
                    val (icon, label) = weatherLabel(w.code, w.isDay)
                    val unit = if (pf.fahrenheit) "°F" else "°C"
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(icon, fontSize = 48.sp)
                        Spacer(Modifier.width(14.dp))
                        Column {
                            Text("${w.temp.toInt()}$unit", color = p.text, fontSize = 40.sp, fontWeight = FontWeight.Light)
                            Text(label, color = p.sub, fontSize = 15.sp)
                        }
                    }
                    Text(
                        listOfNotNull(
                            if (w.min != null && w.max != null && !w.min.isNaN() && !w.max.isNaN()) "↓ ${w.min.toInt()}°  ↑ ${w.max.toInt()}°" else null,
                            "💨 ${w.wind.toInt()} km/h",
                            w.rainChance?.let { "☔ $it %" },
                        ).joinToString("   "),
                        color = p.sub,
                        fontSize = 14.sp,
                    )
                }
                !located && !state.drive.permission -> {
                    Text("Autorise la localisation, ou choisis une ville dans Réglages → Conduite & météo.", color = p.sub, fontSize = 14.sp)
                    Button(onClick = { onRequestPermissions(MainActivity.LOCATION_PERMISSIONS) }, shape = RoundedCornerShape(50)) {
                        Text("Autoriser")
                    }
                }
                state.weather.error != null -> Text(state.weather.error ?: "", color = p.sub, fontSize = 14.sp)
                else -> Text("Chargement…", color = p.sub, fontSize = 14.sp)
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Ordinateur de bord
// ---------------------------------------------------------------------------

@Composable
fun TripCard(state: AppState, modifier: Modifier) {
    val p = LocalPalette.current
    val pf = state.prefs
    val mph = pf.speedUnit == 1
    val km = pf.tripDistance / 1000f
    val dist = if (mph) km / 1.609344f else km
    val hours = pf.tripMovingMs / 3_600_000f
    val avg = if (hours > 0.001f) dist / hours else 0f
    val max = displaySpeed(pf.tripMaxKmh, pf.speedUnit)
    val unitD = if (mph) "mi" else "km"
    var confirm by remember { mutableStateOf(false) }

    CardShell(modifier) {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
            CardHeader("Ordinateur de bord", trailing = {
                Text(
                    "Remise à 0",
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 14.sp,
                    modifier = Modifier.clip(RoundedCornerShape(50)).clickable { confirm = true }.padding(8.dp),
                )
            })
            Row(Modifier.fillMaxWidth()) {
                TripStat("Distance", String.format(Locale.FRANCE, "%.1f", dist), unitD, Modifier.weight(1f))
                TripStat("En mouvement", formatDuration(pf.tripMovingMs), "", Modifier.weight(1f))
            }
            Row(Modifier.fillMaxWidth()) {
                TripStat("Moyenne", "${avg.toInt()}", speedUnitLabel(pf.speedUnit), Modifier.weight(1f))
                TripStat("Max", "$max", speedUnitLabel(pf.speedUnit), Modifier.weight(1f))
            }
            Text(
                "Depuis le " + SimpleDateFormat("d MMM à HH:mm", Locale.getDefault()).format(Date(pf.tripStart)),
                color = p.sub,
                fontSize = 12.sp,
            )
        }
    }

    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text("Remettre le trajet à zéro ?") },
            confirmButton = { TextButton(onClick = { state.drive.resetTrip(); confirm = false }) { Text("Oui") } },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("Annuler") } },
        )
    }
}

@Composable
private fun TripStat(label: String, value: String, unit: String, modifier: Modifier) {
    val p = LocalPalette.current
    Column(modifier) {
        Text(label, color = p.sub, fontSize = 13.sp)
        Row(verticalAlignment = Alignment.Bottom) {
            Text(value, color = p.text, fontSize = 28.sp, fontWeight = FontWeight.SemiBold)
            if (unit.isNotEmpty()) {
                Spacer(Modifier.width(4.dp))
                Text(unit, color = p.sub, fontSize = 14.sp, modifier = Modifier.padding(bottom = 4.dp))
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Messages
// ---------------------------------------------------------------------------

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MessagesCard(state: AppState, modifier: Modifier) {
    val ctx = LocalContext.current
    val p = LocalPalette.current
    val msgs = NotifStore.messages

    CardShell(modifier) {
        Column(Modifier.fillMaxSize()) {
            CardHeader("Messages", trailing = {
                if (msgs.isNotEmpty()) {
                    Text(
                        "Tout lire",
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 14.sp,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .clickable { msgs.take(5).reversed().forEach { state.speakMessage(it) } }
                            .padding(8.dp),
                    )
                }
            })
            if (!state.media.info.access) {
                Text("Autorise l'accès aux notifications pour voir et écouter tes messages.", color = p.sub, fontSize = 14.sp)
                Spacer(Modifier.height(8.dp))
                Button(onClick = { Apps.openNotificationAccess(ctx) }, shape = RoundedCornerShape(50)) { Text("Autoriser") }
            } else if (msgs.isEmpty()) {
                Text("Aucun nouveau message", color = p.sub, fontSize = 15.sp)
            } else {
                Column(
                    Modifier.weight(1f).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    msgs.take(8).forEach { m -> MessageRow(state, m) }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MessageRow(state: AppState, m: Msg) {
    val p = LocalPalette.current
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(p.chip)
            .combinedClickable(
                onClick = { state.speakMessage(m) },
                onLongClick = {
                    try {
                        m.intent?.send()
                    } catch (e: Exception) {
                        state.launch(m.pkg)
                    }
                },
            )
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppIcon(m.pkg, 30.dp)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(m.title.ifEmpty { m.app }, color = p.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (!state.prefs.hideMsgContent && m.text.isNotEmpty()) {
                Text(m.text, color = p.sub, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        Text(
            "✕",
            color = p.sub,
            fontSize = 16.sp,
            modifier = Modifier.clip(CircleShape).clickable { NotifStore.dismiss(m) }.padding(8.dp),
        )
    }
}

// ---------------------------------------------------------------------------
// Contacts favoris
// ---------------------------------------------------------------------------

@Composable
fun ContactsCard(state: AppState, modifier: Modifier) {
    val ctx = LocalContext.current
    val p = LocalPalette.current
    val contacts = state.prefs.contacts

    CardShell(modifier) {
        Column(Modifier.fillMaxSize()) {
            CardHeader("Contacts favoris", trailing = {
                Text(
                    "Clavier",
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 14.sp,
                    modifier = Modifier.clip(RoundedCornerShape(50)).clickable { state.screen = Screen.Phone }.padding(8.dp),
                )
            })
            if (contacts.isEmpty()) {
                Text("Ajoute tes contacts favoris dans Téléphone ou Réglages.", color = p.sub, fontSize = 14.sp)
            } else {
                Column(
                    Modifier.weight(1f).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    contacts.chunked(2).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            row.forEach { c -> ContactChip(c, Modifier.weight(1f)) { Apps.call(ctx, c.number) } }
                            if (row.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ContactChip(c: Contact, modifier: Modifier, onClick: () -> Unit) {
    val p = LocalPalette.current
    val accent = MaterialTheme.colorScheme.primary
    Row(
        modifier
            .clip(RoundedCornerShape(18.dp))
            .background(p.chip)
            .clickable(onClick = onClick)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(38.dp).clip(CircleShape).background(accent),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                c.name.trim().take(1).uppercase().ifEmpty { "#" },
                color = MaterialTheme.colorScheme.onPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
            )
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(c.name, color = p.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(c.number, color = p.sub, fontSize = 12.sp, maxLines = 1)
        }
    }
}

// ---------------------------------------------------------------------------
// Raccourcis d'applications
// ---------------------------------------------------------------------------

@Composable
fun AppsCard(state: AppState, modifier: Modifier) {
    val p = LocalPalette.current
    val favs = (state.prefs.favorites ?: emptyList()).filter { f -> state.apps.any { it.pkg == f } }
    val counts = state.prefs.launchCounts
    val frequent = state.apps.filter { (counts[it.pkg] ?: 0) > 0 && it.pkg !in favs }
        .sortedByDescending { counts[it.pkg] ?: 0 }
    val shown = (favs + frequent.map { it.pkg }).take(8)

    CardShell(modifier) {
        Column(Modifier.fillMaxSize()) {
            CardHeader("Raccourcis")
            if (shown.isEmpty()) {
                Text("Tes applis favorites et les plus utilisées apparaîtront ici.", color = p.sub, fontSize = 14.sp)
            } else {
                Column(
                    Modifier.weight(1f).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    shown.chunked(4).forEach { row ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                            row.forEach { pkg ->
                                Column(
                                    Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(16.dp))
                                        .clickable { state.launch(pkg) }
                                        .padding(6.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                ) {
                                    AppIcon(pkg, 54.dp)
                                    Text(
                                        state.labelOf(pkg),
                                        color = p.text,
                                        fontSize = 12.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                            repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
            }
        }
    }
}
