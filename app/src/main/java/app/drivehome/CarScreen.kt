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
            if (state.prefs.car3dOnHome || !Car3D.exists(LocalContext.current)) {
                CarVisual(state, Modifier.weight(1f).fillMaxWidth(), interactive = false)
            } else if (state.prefs.carView != 1) {
                SideCarView(d, state.carColor(), Modifier.weight(1f).fillMaxWidth())
            } else {
                CarView(d, state.carColor(), Modifier.weight(1f).fillMaxWidth(), rotatable = false)
            }
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
    var partNames by remember { mutableStateOf<List<String>>(emptyList()) }
    BoxWithConstraints(Modifier.fillMaxSize().padding(16.dp)) {
        val wide = maxWidth > maxHeight
        if (wide) {
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                CarHero(state, Modifier.weight(1.1f).fillMaxHeight()) { partNames = it }
                Column(
                    Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    CarPanels(state, onRequestPermissions)
                    Model3DPanel(state, partNames)
                }
            }
        } else {
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                CarHero(state, Modifier.fillMaxWidth().height(420.dp)) { partNames = it }
                CarPanels(state, onRequestPermissions)
                Model3DPanel(state, partNames)
            }
        }
    }
}

/** Voiture : vrai modèle 3D si importé, sinon le dessin. */
@Composable
fun CarVisual(state: AppState, modifier: Modifier, interactive: Boolean, onPartsListed: (List<String>) -> Unit = {}) {
    val ctx = LocalContext.current
    val pf = state.prefs
    val has3d = remember(pf.car3dVersion) { Car3D.exists(ctx) }
    if (pf.carView == 3) {
        CarPhotosView(modifier, interactive)
    } else if ((pf.carView == 4 || pf.use3d) && has3d) {
        Box(modifier) {
            Car3DView(
                state.car.doors,
                pf,
                pf.car3dVersion,
                Modifier.fillMaxSize(),
                interactive = interactive,
                onPartsListed = onPartsListed,
                onMaterialsListed = { CarModelInfo.materials = it },
                paint = state.carColor(),
                onError = { Apps.toast(ctx, it) },
            )
            // mini-schéma des ouvrants, toujours lisible
            if (state.car.doors.anyOpen()) {
                CarView(
                    state.car.doors,
                    state.carColor(),
                    Modifier.align(Alignment.TopStart).width(70.dp).height(70.dp),
                    tilt = 0f,
                    initialSpin = 0f,
                    rotatable = false,
                )
            }
        }
    } else if (pf.carView == 2 && pf.carPhotoUri.isNotEmpty()) {
        CarPhoto(state, modifier)
    } else if (pf.carView != 1) {
        Box(modifier, contentAlignment = Alignment.Center) {
            SideCarView(state.car.doors, state.carColor(), Modifier.fillMaxWidth())
            if (state.car.doors.anyOpen()) {
                CarView(
                    state.car.doors,
                    state.carColor(),
                    Modifier.align(Alignment.TopStart).width(70.dp).height(70.dp),
                    tilt = 0f,
                    initialSpin = 0f,
                    rotatable = false,
                )
            }
        }
    } else {
        CarView(state.car.doors, state.carColor(), modifier, rotatable = interactive)
    }
}

@Composable
private fun CarHero(state: AppState, modifier: Modifier, onPartsListed: (List<String>) -> Unit) {
    val ctxHero = LocalContext.current
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
            CarVisual(state, Modifier.weight(1f).fillMaxWidth(), interactive = true, onPartsListed = onPartsListed)
            val photoPicker = androidx.activity.compose.rememberLauncherForActivityResult(
                androidx.activity.result.contract.ActivityResultContracts.OpenDocument(),
            ) { uri ->
                if (uri != null) {
                    try {
                        ctxHero.contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    } catch (e: Exception) {
                        // droit persistant indisponible
                    }
                    pf.carPhotoUri = uri.toString()
                    pf.carView = 2
                }
            }
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Pill("Mes photos", pf.carView == 3, { pf.carView = 3 })
                if (remember(pf.car3dVersion) { Car3D.exists(ctxHero) }) {
                    Pill("Modèle 3D", pf.carView == 4, { pf.carView = 4 })
                }
                Pill("Profil", pf.carView == 0, { pf.carView = 0 })
                Pill("Vue 3/4 dessus", pf.carView == 1, { pf.carView = 1 })
                Pill(if (pf.carPhotoUri.isEmpty()) "Ma photo…" else "Ma photo", pf.carView == 2, {
                    if (pf.carPhotoUri.isEmpty() || pf.carView == 2) {
                        try {
                            photoPicker.launch(arrayOf("image/*"))
                        } catch (e: Exception) {
                            Apps.toast(ctxHero, "Aucun sélecteur de fichiers")
                        }
                    } else {
                        pf.carView = 2
                    }
                })
            }
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
            if (pf.carView != 3) Row(
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
            if (state.prefs.carView == 3) {
                androidx.compose.foundation.Image(
                    painter = androidx.compose.ui.res.painterResource(CAR_SHOTS[0].first),
                    contentDescription = null,
                    modifier = Modifier.width(120.dp).height(70.dp),
                )
            } else {
                CarView(d, state.carColor(), Modifier.width(90.dp).height(90.dp), rotatable = false, initialSpin = 0f, tilt = 35f)
            }
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

// ---------------------------------------------------------------------------
// Modèle 3D : import + association des portes
// ---------------------------------------------------------------------------

/** Matières du modèle chargé (pour le choix de la carrosserie). */
object CarModelInfo {
    var materials by mutableStateOf<List<String>>(emptyList())
}

@Composable
private fun Model3DPanel(state: AppState, partNames: List<String>) {
    val ctx = LocalContext.current
    val p = LocalPalette.current
    val pf = state.prefs
    val has3d = remember(pf.car3dVersion) { Car3D.exists(ctx) }
    var pickFor by remember { mutableStateOf<CarPart?>(null) }
    var pickPaint by remember { mutableStateOf(false) }
    val launcher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            val err = Car3D.import(ctx, uri)
            if (err != null) {
                Apps.toast(ctx, err)
            } else {
                pf.use3d = true
                pf.carView = 4
                pf.car3dVersion = System.currentTimeMillis()
                Apps.toast(ctx, "Modèle 3D importé")
            }
        }
    }

    CardShell(Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            CardHeader("Modèle 3D de la voiture")
            Text(
                if (has3d) "Modèle importé ✓ — glisse pour tourner, pince pour zoomer."
                else "Importe un vrai modèle 3D de ta voiture au format .glb (Sketchfab, CGTrader, RenderHub…). " +
                    "Copie-le sur une clé USB ou dans Téléchargements, puis touche « Importer ».",
                color = p.sub,
                fontSize = 13.sp,
            )
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Pill(if (has3d) "Remplacer le modèle" else "Importer un .glb", !has3d, {
                    try {
                        launcher.launch(arrayOf("*/*"))
                    } catch (e: Exception) {
                        Apps.toast(ctx, "Aucun sélecteur de fichiers")
                    }
                })
                if (has3d) {
                    Pill(if (pf.use3d) "Afficher : 3D" else "Afficher : dessin", pf.use3d, { pf.use3d = !pf.use3d })
                    Pill(if (pf.car3dOnHome) "3D sur l'accueil : oui" else "3D sur l'accueil : non", false, { pf.car3dOnHome = !pf.car3dOnHome })
                    Pill("Supprimer", false, {
                        Car3D.delete(ctx)
                        pf.car3dVersion = System.currentTimeMillis()
                    })
                }
            }
            if (has3d && pf.use3d) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Repeindre la carrosserie", color = p.text, fontSize = 15.sp, modifier = Modifier.weight(1f))
                    androidx.compose.material3.Switch(checked = pf.repaint, onCheckedChange = {
                        pf.repaint = it
                        pf.car3dVersion = System.currentTimeMillis() // recharge les couleurs d'origine
                    })
                }
                if (pf.repaint) {
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        CAR_COLORS.forEachIndexed { i, c ->
                            Swatch(c.color, pf.carColor == i, { pf.carColor = i })
                        }
                    }
                    val auto = Car3D.guessPaint(CarModelInfo.materials)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Matière de la carrosserie", color = p.text, fontSize = 15.sp, modifier = Modifier.weight(1f))
                        Text(
                            pf.paintMaterial.ifEmpty { auto?.let { "Auto : $it" } ?: "À choisir" },
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 13.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.width(200.dp).clip(RoundedCornerShape(50)).clickable { pickPaint = true }.padding(8.dp),
                        )
                    }
                    Text(
                        "Si la couleur ne change pas, essaie une autre matière : certains modèles peignent la carrosserie dans une texture.",
                        color = p.sub,
                        fontSize = 12.sp,
                    )
                }
                Text(
                    "Pièces mobiles : choisis quelle partie du modèle correspond à chaque porte " +
                        "(seulement si le modèle a des portes séparées). Utilise les boutons de test pour vérifier.",
                    color = p.sub,
                    fontSize = 13.sp,
                )
                CarPart.entries.forEach { part ->
                    val chosen = pf.partNode(part)
                    val auto = Car3D.guess(part, partNames)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(part.label, color = p.text, fontSize = 15.sp, modifier = Modifier.weight(1f))
                        Text(
                            chosen.ifEmpty { auto?.let { "Auto : $it" } ?: "Aucune" },
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 13.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.width(170.dp).clip(RoundedCornerShape(50)).clickable { pickFor = part }.padding(8.dp),
                        )
                        Text(
                            if (pf.partInvert(part)) "⇄ inversé" else "⇄",
                            color = p.sub,
                            fontSize = 13.sp,
                            modifier = Modifier.clip(RoundedCornerShape(50)).clickable { pf.setPartInvert(part, !pf.partInvert(part)) }.padding(8.dp),
                        )
                    }
                }
            }
        }
    }

    if (pickPaint) {
        AlertDialog(
            onDismissRequest = { pickPaint = false },
            title = { Text("Matière de la carrosserie") },
            text = {
                LazyColumn(Modifier.heightIn(max = 380.dp)) {
                    item {
                        Text(
                            "Détection automatique",
                            modifier = Modifier.fillMaxWidth().clickable {
                                pf.paintMaterial = ""
                                pf.car3dVersion = System.currentTimeMillis()
                                pickPaint = false
                            }.padding(vertical = 10.dp),
                        )
                    }
                    items(CarModelInfo.materials) { n ->
                        Text(
                            n,
                            modifier = Modifier.fillMaxWidth().clickable {
                                pf.paintMaterial = n
                                pf.car3dVersion = System.currentTimeMillis()
                                pickPaint = false
                            }.padding(vertical = 10.dp),
                        )
                    }
                }
            },
            confirmButton = { TextButton(onClick = { pickPaint = false }) { Text("Fermer") } },
        )
    }

    val target = pickFor
    if (target != null) {
        AlertDialog(
            onDismissRequest = { pickFor = null },
            title = { Text(target.label) },
            text = {
                LazyColumn(Modifier.heightIn(max = 380.dp)) {
                    item {
                        Text(
                            "Détection automatique",
                            modifier = Modifier.fillMaxWidth().clickable {
                                pf.setPartNode(target, "")
                                pickFor = null
                            }.padding(vertical = 10.dp),
                        )
                    }
                    items(partNames) { n ->
                        Text(
                            n,
                            modifier = Modifier.fillMaxWidth().clickable {
                                pf.setPartNode(target, n)
                                pickFor = null
                            }.padding(vertical = 10.dp),
                        )
                    }
                }
            },
            confirmButton = { TextButton(onClick = { pickFor = null }) { Text("Fermer") } },
        )
    }
}

/** Photo de la voiture de l'utilisateur, avec le schéma des ouvrants par-dessus. */
@Composable
fun CarPhoto(state: AppState, modifier: Modifier) {
    val ctx = LocalContext.current
    val uri = state.prefs.carPhotoUri
    val img by androidx.compose.runtime.produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, uri) {
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { decodeSampled(ctx, uri) }
    }
    val d = state.car.doors
    Box(
        modifier
            .clip(RoundedCornerShape(20.dp))
            .then(if (d.anyOpen()) Modifier.border(3.dp, Color(0xFFFF9F0A), RoundedCornerShape(20.dp)) else Modifier),
    ) {
        val b = img
        if (b != null) {
            androidx.compose.foundation.Image(
                bitmap = b,
                contentDescription = null,
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        if (d.anyOpen()) {
            Box(
                Modifier
                    .align(Alignment.BottomStart)
                    .padding(10.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xCC1C1C1E)),
            ) {
                CarView(d, state.carColor(), Modifier.width(90.dp).height(90.dp), tilt = 0f, initialSpin = 0f, rotatable = false)
            }
        }
    }
}

/** Photos détourées de la voiture du propriétaire (Passat Variant B8 blanche). */
val CAR_SHOTS = listOf(
    R.drawable.car_avant34d to "3/4 avant",
    R.drawable.car_face to "Avant",
    R.drawable.car_arriere34 to "3/4 arrière",
    R.drawable.car_arriere to "Arrière",
)

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun CarPhotosView(modifier: Modifier, interactive: Boolean) {
    val p = LocalPalette.current
    @Composable
    fun Shot(res: Int, m: Modifier) {
        Box(m, contentAlignment = Alignment.Center) {
            // ombre au sol
            Canvas(Modifier.fillMaxSize()) {
                drawOval(
                    Color.Black.copy(alpha = 0.45f),
                    topLeft = androidx.compose.ui.geometry.Offset(size.width * 0.1f, size.height * 0.80f),
                    size = androidx.compose.ui.geometry.Size(size.width * 0.8f, size.height * 0.10f),
                )
            }
            androidx.compose.foundation.Image(
                painter = androidx.compose.ui.res.painterResource(res),
                contentDescription = null,
                contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                modifier = Modifier.fillMaxSize().padding(6.dp),
            )
        }
    }
    if (!interactive) {
        Shot(CAR_SHOTS[0].first, modifier)
        return
    }
    val pager = androidx.compose.foundation.pager.rememberPagerState(pageCount = { CAR_SHOTS.size })
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        androidx.compose.foundation.pager.HorizontalPager(state = pager, modifier = Modifier.weight(1f).fillMaxWidth()) { i ->
            Shot(CAR_SHOTS[i].first, Modifier.fillMaxSize())
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            CAR_SHOTS.forEachIndexed { i, (_, label) ->
                Text(
                    label,
                    color = if (i == pager.currentPage) p.text else p.sub,
                    fontSize = 12.sp,
                    fontWeight = if (i == pager.currentPage) FontWeight.Bold else FontWeight.Normal,
                )
                if (i < CAR_SHOTS.lastIndex) Text("·", color = p.sub, fontSize = 12.sp)
            }
        }
    }
}
