package app.drivehome

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Accueil façon CarPlay : grande carte à gauche, guidage + lecture en cours à droite.
 */
@Composable
fun CarPlayDashboard(state: AppState) {
    val gap = 12.dp
    Row(Modifier.fillMaxSize().padding(gap), horizontalArrangement = Arrangement.spacedBy(gap)) {
        MapPanel(state, Modifier.weight(1.12f).fillMaxHeight())
        Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(gap)) {
            GuidancePanel(state, Modifier.weight(1f).fillMaxWidth())
            NowPlayingPanel(state, Modifier.weight(1f).fillMaxWidth())
        }
    }
}

/** Panneau carte : carte en direct, bulle de vitesse, bandeau d'arrivée. */
@Composable
private fun MapPanel(state: AppState, modifier: Modifier) {
    val p = LocalPalette.current
    val pf = state.prefs
    val nav = NotifStore.nav
    val navPkg = nav?.pkg ?: state.navPackage()
    val ctx = LocalContext.current
    val view = androidx.compose.ui.platform.LocalView.current
    var bounds by remember { mutableStateOf(android.graphics.Rect()) }
    Box(
        modifier
            .clip(RoundedCornerShape(26.dp))
            .background(p.card)
            .onGloballyPositioned { c ->
                val loc = IntArray(2)
                view.getLocationOnScreen(loc)
                val pos = c.positionInWindow()
                val l = loc[0] + pos.x.toInt()
                val t = loc[1] + pos.y.toInt()
                bounds = android.graphics.Rect(l, t, l + c.size.width, t + c.size.height)
            }
            .clickable {
                val pkg = navPkg ?: return@clickable
                if (pf.navInTile) Apps.launchInBounds(ctx, pkg, bounds) else state.launch(pkg)
            },
    ) {
        LiveMap(state, Modifier.fillMaxSize(), zoom = 17.0)

        // bulle de vitesse (avec panneau de limite si une alerte est réglée)
        Row(Modifier.align(Alignment.TopEnd).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            if (pf.speedLimit > 0) {
                Box(
                    Modifier.size(46.dp).clip(CircleShape).background(Color.White).border(5.dp, DANGER, CircleShape),
                    contentAlignment = Alignment.Center,
                ) { Text("${pf.speedLimit}", color = Color.Black, fontSize = 16.sp, fontWeight = FontWeight.Bold) }
                Spacer(Modifier.width(4.dp))
            }
            Box(
                Modifier
                    .size(70.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF1C1C1E))
                    .border(3.dp, if (state.drive.overLimit) DANGER else Color(0xFF3A3A3C), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        if (state.drive.gpsFix) "${displaySpeed(state.drive.speedKmh, pf.speedUnit)}" else "--",
                        color = if (state.drive.overLimit) DANGER else Color.White,
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(speedUnitLabel(pf.speedUnit), color = Color(0xFFB0B0B8), fontSize = 10.sp)
                }
            }
        }

        // bandeau bas : arrivée pendant un guidage, sinon invitation
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(10.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(if (p.night) Color(0xE61C1C1E) else Color(0xEBFFFFFF))
                .padding(horizontal = 16.dp, vertical = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                when {
                    nav != null && nav.sub.isNotEmpty() -> nav.sub
                    state.route.summary() != null -> state.route.summary() ?: ""
                    nav != null -> "Guidage en cours · ${state.labelOf(nav.pkg)}"
                    else -> "Toucher la carte pour ouvrir ${state.labelOf(navPkg)}"
                },
                color = if (p.night) Color.White else Color(0xFF111114),
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Panneau guidage : manœuvre en cours, ou destinations rapides. */
@Composable
private fun GuidancePanel(state: AppState, modifier: Modifier) {
    val ctx = LocalContext.current
    val p = LocalPalette.current
    val pf = state.prefs
    val nav = NotifStore.nav
    val navPkg = state.navPackage()
    var search by remember { mutableStateOf(false) }

    Box(
        modifier
            .clip(RoundedCornerShape(26.dp))
            .background(p.card)
            .clickable { (nav?.pkg ?: navPkg)?.let { state.launch(it) } }
            .padding(20.dp),
    ) {
        if (nav != null) {
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val ic = nav.icon
                    if (ic != null) {
                        Image(
                            ic.asImageBitmap(),
                            contentDescription = null,
                            modifier = Modifier.size(56.dp),
                            colorFilter = if (p.night) null else androidx.compose.ui.graphics.ColorFilter.tint(Color(0xFF111114)),
                        )
                    } else {
                        Text("⬆", color = p.text, fontSize = 40.sp)
                    }
                    Spacer(Modifier.width(14.dp))
                    Text(nav.title, color = p.text, fontSize = 34.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (nav.text.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    Text(nav.text, color = p.text, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
        } else {
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Où allons-nous ?", color = p.text, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    QuickDest("🏠", "Domicile", Modifier.weight(1f)) {
                        if (pf.homeAddr.isEmpty()) state.screen = Screen.Settings else state.navigateTo(pf.homeAddr)
                    }
                    QuickDest("💼", "Travail", Modifier.weight(1f)) {
                        if (pf.workAddr.isEmpty()) state.screen = Screen.Settings else state.navigateTo(pf.workAddr)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    QuickDest("🔍", "Rechercher", Modifier.weight(1f)) { search = true }
                    QuickDest("⛽", if (pf.carFuel == 0) "Gazole" else "Essence", Modifier.weight(1f)) {
                        Apps.searchNearby(ctx, navPkg, state.fuelStationQuery())
                    }
                }
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
            dismissButton = { TextButton(onClick = { search = false }) { Text("Annuler") } },
        )
    }
}

@Composable
private fun QuickDest(icon: String, label: String, modifier: Modifier, onClick: () -> Unit) {
    val p = LocalPalette.current
    Row(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .background(p.chip)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(icon, fontSize = 20.sp)
        Spacer(Modifier.width(8.dp))
        Text(label, color = p.text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Panneau lecture en cours, façon CarPlay. */
@Composable
private fun NowPlayingPanel(state: AppState, modifier: Modifier) {
    val ctx = LocalContext.current
    val p = LocalPalette.current
    val m = state.media.info
    val musicPkg = state.musicPackage()

    Column(
        modifier
            .clip(RoundedCornerShape(26.dp))
            .background(p.card)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(14.dp))
                .clickable {
                    val pkg = m.pkg ?: musicPkg
                    if (pkg != null) state.launch(pkg) else Apps.toast(ctx, "Aucune application de musique trouvée")
                },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.fillMaxHeight(0.85f).aspectRatio(1f, matchHeightConstraintsFirst = true)
                    .clip(RoundedCornerShape(10.dp)).background(p.chip),
                contentAlignment = Alignment.Center,
            ) {
                val art = m.art
                if (art != null) {
                    Image(art.asImageBitmap(), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                } else {
                    (m.pkg ?: musicPkg)?.let { AppIcon(it, 40.dp) }
                }
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    if (m.hasSession) (m.title ?: "Lecture en cours") else "Rien en lecture",
                    color = p.text,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                val sub = if (m.hasSession) listOfNotNull(m.artist, m.album).filter { it.isNotEmpty() }.joinToString(" · ")
                else state.labelOf(musicPkg)
                if (sub.isNotEmpty()) Text(sub, color = p.sub, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }

        // progression
        @Suppress("UNUSED_VARIABLE") val tick = state.now
        val frac = if (m.hasSession && m.duration > 0) (m.currentPosition().toFloat() / m.duration).coerceIn(0f, 1f) else 0f
        Box(Modifier.fillMaxWidth().padding(vertical = 10.dp).height(4.dp).clip(RoundedCornerShape(2.dp)).background(p.chip)) {
            Box(Modifier.fillMaxWidth(frac).height(4.dp).background(p.text))
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(56.dp).clip(CircleShape).clickable { state.media.previous() }, contentAlignment = Alignment.Center) {
                PrevGlyph(p.text, 26.dp)
            }
            Box(
                Modifier.size(64.dp).clip(CircleShape).clickable {
                    if (m.hasSession) state.media.playPause() else state.media.resume()
                },
                contentAlignment = Alignment.Center,
            ) {
                if (m.playing) PauseGlyph(p.text, 32.dp) else PlayGlyph(p.text, 32.dp)
            }
            Box(Modifier.size(56.dp).clip(CircleShape).clickable { state.media.next() }, contentAlignment = Alignment.Center) {
                NextGlyph(p.text, 26.dp)
            }
        }
    }
}
