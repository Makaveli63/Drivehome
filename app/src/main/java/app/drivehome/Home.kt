package app.drivehome

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HomeContent(state: AppState, wide: Boolean) {
    val pf = state.prefs
    val p = LocalPalette.current
    val gap = 16.dp

    if (!pf.showNav && !pf.showMedia && !pf.showClock) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                "Toutes les cartes sont masquées.\nActive-les dans Réglages.",
                color = p.sub,
                fontSize = 18.sp,
            )
        }
        return
    }

    val sideCards = pf.showMedia || pf.showClock

    if (wide) {
        Row(
            Modifier.fillMaxSize().padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(gap),
        ) {
            if (pf.showNav) {
                NavCard(state, Modifier.weight(1.15f).fillMaxHeight())
            }
            if (sideCards) {
                Column(
                    Modifier.weight(1f).fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(gap),
                ) {
                    if (pf.showMedia) MediaCard(state, Modifier.weight(1.2f).fillMaxWidth())
                    if (pf.showClock) ClockCard(state, Modifier.weight(1f).fillMaxWidth())
                }
            }
        }
    } else {
        Column(
            Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(gap),
        ) {
            if (pf.showNav) NavCard(state, Modifier.weight(1.2f).fillMaxWidth())
            if (pf.showMedia) MediaCard(state, Modifier.weight(1f).fillMaxWidth())
            if (pf.showClock) ClockCard(state, Modifier.weight(0.8f).fillMaxWidth())
        }
    }
}

// ---------------------------------------------------------------------------
// Navigation
// ---------------------------------------------------------------------------

@Composable
fun NavCard(state: AppState, modifier: Modifier) {
    val ctx = LocalContext.current
    val p = LocalPalette.current
    val accent = MaterialTheme.colorScheme.primary
    val navPkg = state.navPackage()
    val pf = state.prefs

    CardShell(modifier) {
        // décor façon carte
        Canvas(Modifier.matchParentSize()) {
            val w = this.size.width
            val h = this.size.height
            val road = accent.copy(alpha = 0.16f)
            drawLine(road, Offset(0f, h * 0.78f), Offset(w, h * 0.30f), strokeWidth = 30f, cap = StrokeCap.Round)
            drawLine(road, Offset(w * 0.28f, 0f), Offset(w * 0.58f, h), strokeWidth = 20f, cap = StrokeCap.Round)
            val curve = Path().apply {
                moveTo(0f, h * 0.22f)
                cubicTo(w * 0.30f, h * 0.05f, w * 0.52f, h * 0.55f, w, h * 0.62f)
            }
            drawPath(curve, road, style = Stroke(width = 14f, cap = StrokeCap.Round))
            drawCircle(accent.copy(alpha = 0.35f), radius = 34f, center = Offset(w * 0.64f, h * 0.40f))
            drawCircle(accent, radius = 14f, center = Offset(w * 0.64f, h * 0.40f))
        }

        Column(
            Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (navPkg != null) {
                    AppIcon(navPkg, 36.dp)
                    Spacer(Modifier.width(10.dp))
                }
                Text(
                    "Navigation",
                    color = p.text,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                DestinationRow(
                    label = "Domicile",
                    address = pf.homeAddr,
                    onClick = {
                        if (pf.homeAddr.isEmpty()) state.screen = Screen.Settings
                        else Apps.startNavigation(ctx, navPkg, pf.homeAddr)
                    },
                )
                DestinationRow(
                    label = "Travail",
                    address = pf.workAddr,
                    onClick = {
                        if (pf.workAddr.isEmpty()) state.screen = Screen.Settings
                        else Apps.startNavigation(ctx, navPkg, pf.workAddr)
                    },
                )
                Button(
                    onClick = {
                        if (navPkg != null) Apps.launch(ctx, navPkg)
                        else Apps.toast(ctx, "Aucune application de navigation trouvée")
                    },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(50),
                ) {
                    Text("Ouvrir la carte", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun DestinationRow(label: String, address: String, onClick: () -> Unit) {
    val p = LocalPalette.current
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(p.chip)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, color = p.text, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            Text(
                if (address.isEmpty()) "Définir dans Réglages" else address,
                color = p.sub,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
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
            !m.access -> Column(
                Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("Musique", color = p.text, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    "Autorise l'accès aux notifications pour afficher et piloter la lecture en cours.",
                    color = p.sub,
                    fontSize = 14.sp,
                )
                Button(
                    onClick = { Apps.openNotificationAccess(ctx) },
                    shape = RoundedCornerShape(50),
                ) { Text("Autoriser") }
            }

            !m.hasSession -> Column(
                Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (musicPkg != null) {
                        AppIcon(musicPkg, 36.dp)
                        Spacer(Modifier.width(10.dp))
                    }
                    Text("Musique", color = p.text, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
                }
                Text("Rien en lecture", color = p.sub, fontSize = 15.sp)
                Button(
                    onClick = {
                        if (musicPkg != null) Apps.launch(ctx, musicPkg)
                        else Apps.toast(ctx, "Aucune application de musique trouvée")
                    },
                    shape = RoundedCornerShape(50),
                ) { Text("Ouvrir la musique") }
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
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(18.dp))
                        .background(p.chip),
                ) {
                    if (art != null) {
                        Image(
                            bitmap = art.asImageBitmap(),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else if (m.pkg != null) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            AppIcon(m.pkg, 56.dp)
                        }
                    }
                }

                Column(
                    Modifier.weight(1f).fillMaxHeight(),
                    verticalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column {
                        Text(
                            m.title ?: "Lecture en cours",
                            color = p.text,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (!m.artist.isNullOrEmpty()) {
                            Text(
                                m.artist,
                                color = p.sub,
                                fontSize = 15.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RoundButton(onClick = { state.media.previous() }) { PrevGlyph(p.text) }
                        RoundButton(onClick = { state.media.playPause() }, filled = true) {
                            val on = MaterialTheme.colorScheme.onPrimary
                            if (m.playing) PauseGlyph(on) else PlayGlyph(on)
                        }
                        RoundButton(onClick = { state.media.next() }) { NextGlyph(p.text) }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Horloge
// ---------------------------------------------------------------------------

@Composable
fun ClockCard(state: AppState, modifier: Modifier) {
    val p = LocalPalette.current
    val pattern = if (state.prefs.clock24) "HH:mm" else "h:mm a"
    val time = SimpleDateFormat(pattern, Locale.getDefault()).format(Date(state.now))
    val date = SimpleDateFormat("EEEE d MMMM", Locale.getDefault())
        .format(Date(state.now))
        .replaceFirstChar { it.uppercase() }

    CardShell(modifier) {
        Column(
            Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(time, color = p.text, fontSize = 56.sp, fontWeight = FontWeight.Light)
            Text(date, color = p.sub, fontSize = 18.sp)
        }
    }
}
