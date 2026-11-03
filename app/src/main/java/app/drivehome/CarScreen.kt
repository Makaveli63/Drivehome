package app.drivehome

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale

fun AppState.carColor(): Color =
    CAR_COLORS[prefs.carColor.coerceIn(0, CAR_COLORS.lastIndex)].color

/** Plaque d'immatriculation façon européenne. */
@Composable
fun LicensePlate(text: String, height: Int = 34) {
    if (text.isBlank()) return
    Row(
        Modifier
            .height(height.dp)
            .clip(RoundedCornerShape(5.dp))
            .background(Color.White)
            .border(2.dp, Color(0xFF1A1A1A), RoundedCornerShape(5.dp)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.fillMaxHeight().width((height * 0.6f).dp).background(Color(0xFF0033A0)),
            contentAlignment = Alignment.BottomCenter,
        ) {
            Text("F", color = Color.White, fontSize = (height * 0.36f).sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 2.dp))
        }
        Text(
            text.uppercase(Locale.ROOT),
            color = Color(0xFF111111),
            fontSize = (height * 0.55f).sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = (height * 0.3f).dp),
        )
        Box(Modifier.fillMaxHeight().width((height * 0.6f).dp).background(Color(0xFF0033A0)))
    }
}

private fun doorSummary(d: Doors): String {
    val n = d.openNames()
    return if (n.isEmpty()) "Tout est fermé ✓" else "Ouvert : " + n.joinToString(", ")
}

// ---------------------------------------------------------------------------
// Tuile d'accueil
// ---------------------------------------------------------------------------

@Composable
fun CarCard(state: AppState, modifier: Modifier) {
    val p = LocalPalette.current
    val d = state.car.doors
    val obd = state.car.obd
    CardShell(modifier.clickable { state.screen = Screen.Car }, tint = if (d.anyOpen()) Color(0x33FF9F0A) else Color.Transparent) {
        Column(Modifier.fillMaxSize()) {
            CardHeader(state.prefs.carName.ifBlank { "Ma voiture" })
            CarView(d, state.carColor(), Modifier.weight(1f).fillMaxWidth(), rotatable = false)
            Text(
                doorSummary(d),
                color = if (d.anyOpen()) Color(0xFFFF9F0A) else p.sub,
                fontSize = 14.sp,
                fontWeight = if (d.anyOpen()) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            val extra = listOfNotNull(
                (obd.fuel ?: state.car.extFuel)?.let { "⛽ $it %" },
                state.car.extRange?.let { "$it km" },
                obd.voltage?.let { String.format(Locale.FRANCE, "🔋 %.1f V", it) },
                obd.coolant?.let { "🌡 $it °C" },
            )
            if (extra.isNotEmpty()) Text(extra.joinToString("   "), color = p.text, fontSize = 13.sp, maxLines = 1)
        }
    }
}

@Composable
fun EngineCard(state: AppState, modifier: Modifier) {
    val p = LocalPalette.current
    val o = state.car.obd
    CardShell(modifier.clickable { state.screen = Screen.Car }) {
        Column(Modifier.fillMaxSize()) {
            CardHeader("Moteur · ${state.fuelLabel()}")
            if (o.status != ObdClient.Status.CONNECTED) {
                Text(
                    if (o.status == ObdClient.Status.CONNECTING) "Connexion au boîtier OBD…"
                    else "Boîtier OBD non connecté. Touche pour ouvrir « Ma voiture ».",
                    color = p.sub,
                    fontSize = 14.sp,
                )
            } else {
                Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Gauge("Régime", o.rpm, 5000, "tr/min", Modifier.weight(1f))
                    Gauge("Vitesse", o.speed, 220, "km/h", Modifier.weight(1f))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        MiniStat("Refroid.", o.coolant?.let { "$it °C" })
                        MiniStat("Batterie", o.voltage?.let { String.format(Locale.FRANCE, "%.1f V", it) })
                        MiniStat("Charge", o.load?.let { "$it %" })
                    }
                }
            }
        }
    }
}

@Composable
private fun MiniStat(label: String, value: String?) {
    val p = LocalPalette.current
    Row {
        Text(label, color = p.sub, fontSize = 12.sp, modifier = Modifier.weight(1f))
        Text(value ?: "—", color = p.text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

/** Jauge circulaire. */
@Composable
fun Gauge(label: String, value: Int?, max: Int, unit: String, modifier: Modifier, warnAbove: Int = Int.MAX_VALUE) {
    val p = LocalPalette.current
    val accent = MaterialTheme.colorScheme.primary
    Box(modifier.aspectRatio(1f, matchHeightConstraintsFirst = false), contentAlignment = Alignment.Center) {
        val frac = ((value ?: 0).toFloat() / max).coerceIn(0f, 1f)
        val col = if ((value ?: 0) > warnAbove) DANGER else accent
        val track = p.chip
        Canvas(Modifier.fillMaxSize().padding(4.dp)) {
            val sw = size.minDimension * 0.09f
            drawArc(track, 135f, 270f, false, style = Stroke(sw, cap = StrokeCap.Round))
            drawArc(col, 135f, 270f * frac, false, style = Stroke(sw, cap = StrokeCap.Round))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value?.toString() ?: "—", color = p.text, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Text(unit, color = p.sub, fontSize = 11.sp)
            Text(label, color = p.sub, fontSize = 11.sp)
        }
    }
}

// ---------------------------------------------------------------------------
// Écran « Ma voiture »
// ---------------------------------------------------------------------------

@Composable
fun CarScreen(state: AppState, onRequestPermissions: (Array<String>) -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize().padding(16.dp)) {
        val wide = maxWidth > maxHeight
        if (wide) {
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                CarHero(state, Modifier.weight(1.1f).fillMaxHeight())
                Column(
                    Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) { CarPanels(state, onRequestPermissions) }
            }
        } else {
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                CarHero(state, Modifier.fillMaxWidth().height(420.dp))
                CarPanels(state, onRequestPermissions)
            }
        }
    }
}

@Composable
private fun CarHero(state: AppState, modifier: Modifier) {
    val p = LocalPalette.current
    val pf = state.prefs
    val d = state.car.doors
    CardShell(modifier) {
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(pf.carName.ifBlank { "Ma voiture" }, color = p.text, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    Text(
                        "${state.fuelLabel()} · ${CAR_COLORS[pf.carColor.coerceIn(0, CAR_COLORS.lastIndex)].name}",
                        color = p.sub,
                        fontSize = 14.sp,
                    )
                }
                LicensePlate(pf.carPlate)
            }
            CarView(d, state.carColor(), Modifier.weight(1f).fillMaxWidth())
            Text("Glisse pour faire tourner la voiture", color = p.sub, fontSize = 12.sp)
            Spacer(Modifier.height(6.dp))
            Text(
                doorSummary(d),
                color = if (d.anyOpen()) Color(0xFFFF9F0A) else OK_GREEN,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
            )
            Text("Source : ${state.car.doorsSource}", color = p.sub, fontSize = 12.sp)
            Spacer(Modifier.height(8.dp))
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                fun set(n: Doors) = state.car.updateDoors(n, "mode test")
                Pill("AV G", d.fl, { set(d.copy(fl = !d.fl)) })
                Pill("AV D", d.fr, { set(d.copy(fr = !d.fr)) })
                Pill("AR G", d.rl, { set(d.copy(rl = !d.rl)) })
                Pill("AR D", d.rr, { set(d.copy(rr = !d.rr)) })
                Pill("Coffre", d.trunk, { set(d.copy(trunk = !d.trunk)) })
                Pill("Capot", d.hood, { set(d.copy(hood = !d.hood)) })
                Pill("Tout fermer", false, { set(Doors()) })
            }
        }
    }
}

@Composable
private fun CarPanels(state: AppState, onRequestPermissions: (Array<String>) -> Unit) {
    val ctx = LocalContext.current
    val p = LocalPalette.current
    val pf = state.prefs
    val o = state.car.obd
    var pickDevice by remember { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }
    val connected = o.status == ObdClient.Status.CONNECTED

    // ---- Tableau de bord
    CardShell(Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            CardHeader("Tableau de bord")
            Row(Modifier.fillMaxWidth().height(150.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Gauge("Régime", o.rpm, 5000, "tr/min", Modifier.weight(1f).fillMaxHeight(), warnAbove = 4200)
                val spd = o.speed ?: if (state.drive.gpsFix) state.drive.speedKmh.toInt() else null
                Gauge("Vitesse", spd, 220, "km/h", Modifier.weight(1f).fillMaxHeight())
            }
            val outT = o.ambient?.toDouble() ?: state.car.extOutTemp ?: state.weather.current?.temp
            val tiles = listOf(
                "Liquide refroid." to o.coolant?.let { "$it °C" },
                "Huile" to o.oil?.let { "$it °C" },
                "Batterie" to o.voltage?.let { String.format(Locale.FRANCE, "%.1f V", it) },
                "Carburant" to (o.fuel ?: state.car.extFuel)?.let { "$it %" },
                "Autonomie" to state.car.extRange?.let { "$it km" },
                "Temp. extérieure" to outT?.let { "${it.toInt()} °C" },
                "Charge moteur" to o.load?.let { "$it %" },
                "Accélérateur" to o.throttle?.let { "$it %" },
                "Air admission" to o.intake?.let { "$it °C" },
            )
            tiles.chunked(3).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { (label, value) ->
                        Column(
                            Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).background(p.chip).padding(10.dp),
                        ) {
                            Text(label, color = p.sub, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(value ?: "—", color = p.text, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
            if (!connected) {
                Text(
                    "Les valeurs moteur s'affichent avec un boîtier OBD2 Bluetooth (ELM327, ~15 €) branché sous le volant.",
                    color = p.sub,
                    fontSize = 13.sp,
                )
            }
        }
    }

    // ---- OBD
    CardShell(Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            CardHeader("Boîtier OBD2 Bluetooth")
            Text(
                when (o.status) {
                    ObdClient.Status.CONNECTED -> "✓ " + o.message
                    ObdClient.Status.CONNECTING -> o.message
                    ObdClient.Status.ERROR -> o.message
                    ObdClient.Status.OFF -> if (pf.obdName.isNotEmpty()) "Boîtier : ${pf.obdName}" else "Aucun boîtier choisi"
                },
                color = if (connected) OK_GREEN else p.sub,
                fontSize = 14.sp,
            )
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Pill("Choisir le boîtier", false, {
                    if (!o.hasPermission()) onRequestPermissions(MainActivity.BT_PERMISSIONS) else pickDevice = true
                })
                if (pf.obdAddress.isNotEmpty()) {
                    if (o.status == ObdClient.Status.CONNECTED || o.status == ObdClient.Status.CONNECTING) {
                        Pill("Déconnecter", false, { o.disconnect() })
                    } else {
                        Pill("Connecter", true, {
                            if (!o.hasPermission()) onRequestPermissions(MainActivity.BT_PERMISSIONS) else o.connect(pf.obdAddress)
                        })
                    }
                }
            }
        }
    }

    // ---- Diagnostic
    CardShell(Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            CardHeader("Diagnostic (codes défaut)")
            val codes = o.dtcs
            Text(
                when {
                    !connected -> "Connecte le boîtier OBD pour lire les codes défaut."
                    codes == null -> "Touche « Lire » pour interroger le calculateur moteur."
                    codes.isEmpty() -> "Aucun code défaut ✓"
                    else -> codes.joinToString("  ")
                },
                color = if (codes != null && codes.isNotEmpty()) DANGER else p.sub,
                fontSize = 15.sp,
            )
            if (connected) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Pill("Lire", false, { o.readDtcs() })
                    Pill("Effacer", false, { confirmClear = true })
                }
            }
        }
    }

    // ---- CANbus de l'autoradio
    CardShell(Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            CardHeader("Portes & coffre (CANbus)")
            Text(
                "Le boîtier CANbus de l'autoradio transmet les portes à son appli constructeur. " +
                    "DriveHome reçoit ces infos dès qu'une appli les relaie (appli CAN, Tasker, MacroDroid…) " +
                    "avec la diffusion :",
                color = p.sub,
                fontSize = 13.sp,
            )
            Text(
                "${CarManager.ACTION_STATUS}\n  door_fl, door_fr, door_rl, door_rr, trunk, hood = true/false\n  fuel, range_km, temp_out = nombres",
                color = p.text,
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(p.chip).padding(10.dp),
            )
            Text("Les boutons sous la voiture permettent de tester l'affichage.", color = p.sub, fontSize = 13.sp)
        }
    }

    if (pickDevice) {
        val devices = remember { o.bondedDevices() }
        AlertDialog(
            onDismissRequest = { pickDevice = false },
            title = { Text("Boîtier OBD appairé") },
            text = {
                if (devices.isEmpty()) {
                    Text("Aucun appareil appairé. Appaire d'abord ton boîtier (souvent « OBDII », code 1234) dans les réglages Bluetooth.")
                } else {
                    LazyColumn(Modifier.heightIn(max = 360.dp)) {
                        items(devices) { (name, addr) ->
                            Column(
                                Modifier.fillMaxWidth().clickable {
                                    pf.obdAddress = addr
                                    pf.obdName = name
                                    pickDevice = false
                                    o.connect(addr)
                                }.padding(vertical = 10.dp),
                            ) {
                                Text(name, fontSize = 16.sp)
                                Text(addr, fontSize = 12.sp, color = p.sub)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { Apps.openBluetooth(ctx) }) { Text("Réglages Bluetooth") }
            },
            dismissButton = { TextButton(onClick = { pickDevice = false }) { Text("Fermer") } },
        )
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Effacer les codes défaut ?") },
            text = { Text("Le voyant moteur s'éteindra, mais revient si le problème persiste. À faire moteur coupé, contact mis.") },
            confirmButton = { TextButton(onClick = { o.clearDtcs(); confirmClear = false }) { Text("Effacer") } },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Annuler") } },
        )
    }
}

/** Bandeau d'alerte (ouverture d'une porte, du coffre…), affiché par-dessus tout. */
@Composable
fun DoorAlertBanner(state: AppState) {
    val a = state.car.alert ?: return
    if (state.now - state.car.alertAt > 7000) return
    val d = state.car.doors
    Box(Modifier.fillMaxWidth().padding(top = 50.dp), contentAlignment = Alignment.TopCenter) {
        Row(
            Modifier
                .padding(horizontal = 24.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(Color(0xF01C1C1E))
                .clickable {
                    state.car.dismissAlert()
                    state.screen = Screen.Car
                }
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CarView(d, state.carColor(), Modifier.width(90.dp).height(90.dp), rotatable = false, initialSpin = 0f, tilt = 35f)
            Spacer(Modifier.width(12.dp))
            Column {
                Text(state.prefs.carName.ifBlank { "Ma voiture" }, color = Color(0xFFB0B0B8), fontSize = 13.sp)
                Text(
                    a,
                    color = if (d.anyOpen()) Color(0xFFFF9F0A) else OK_GREEN,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}
