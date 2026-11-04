package app.drivehome

import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(state: AppState, onRequestPermissions: (Array<String>) -> Unit) {
    val ctx = LocalContext.current
    val p = LocalPalette.current
    val pf = state.prefs
    val scope = rememberCoroutineScope()
    var picker by remember { mutableStateOf<String?>(null) }

    val imageLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            try {
                ctx.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (e: Exception) {
                // certaines sources ne proposent pas de droit persistant
            }
            pf.wallpaperUri = uri.toString()
        }
    }
    val pickContact = rememberContactPicker { c -> pf.setContacts((pf.contacts + c).distinctBy { it.number }) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("Réglages", color = p.text, fontSize = 30.sp, fontWeight = FontWeight.Bold)

        // ------------------------------------------------------------ Apparence
        Section("Apparence") {
            Label("Thème")
            ScrollRow {
                Pill("Auto (horaires)", pf.themeMode == 0, { pf.themeMode = 0 })
                Pill("Auto (soleil)", pf.themeMode == 3, { pf.themeMode = 3 })
                Pill("Sombre", pf.themeMode == 1, { pf.themeMode = 1 })
                Pill("Clair", pf.themeMode == 2, { pf.themeMode = 2 })
            }
            if (pf.themeMode == 0) {
                Stepper("Mode nuit à partir de", "${pf.nightStart} h", { pf.nightStart = (pf.nightStart + 23) % 24 }, { pf.nightStart = (pf.nightStart + 1) % 24 })
                Stepper("Mode jour à partir de", "${pf.nightEnd} h", { pf.nightEnd = (pf.nightEnd + 23) % 24 }, { pf.nightEnd = (pf.nightEnd + 1) % 24 })
            }
            if (pf.themeMode == 3) {
                Label("Bascule au lever et au coucher du soleil, calculés selon ta position (GPS ou ville météo).")
            }

            Label("Couleur d'accent")
            ScrollRow {
                ACCENTS.forEachIndexed { i, a ->
                    Swatch(a.color, pf.accentIndex == i, { pf.accentIndex = i })
                }
            }

            Label("Fond d'écran")
            ScrollRow {
                WALLPAPERS.forEachIndexed { i, w ->
                    val selected = pf.wallpaperIndex == i && pf.wallpaperUri.isEmpty()
                    val ring = if (selected) MaterialTheme.colorScheme.primary else p.chip
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            Modifier
                                .size(52.dp)
                                .clip(CircleShape)
                                .background(Brush.linearGradient(if (p.night) w.dark else w.light))
                                .border(3.dp, ring, CircleShape)
                                .clickable {
                                    pf.wallpaperIndex = i
                                    pf.wallpaperUri = ""
                                },
                        )
                        Text(w.name, color = p.sub, fontSize = 11.sp)
                    }
                }
            }
            ScrollRow {
                Pill("Choisir une image…", pf.wallpaperUri.isNotEmpty(), {
                    try {
                        imageLauncher.launch(arrayOf("image/*"))
                    } catch (e: Exception) {
                        Apps.toast(ctx, "Aucun sélecteur de fichiers sur cet appareil")
                    }
                })
                if (pf.wallpaperUri.isNotEmpty()) {
                    Pill("Retirer l'image", false, { pf.wallpaperUri = "" })
                }
            }
            if (pf.wallpaperUri.isNotEmpty()) {
                SliderPref("Assombrir l'image", pf.wallpaperDim, 0, 90, "%") { pf.wallpaperDim = it }
            }
            SliderPref("Opacité des cartes", pf.cardOpacity, 10, 100, "%") { pf.cardOpacity = it }
            SliderPref("Taille de l'interface", pf.uiScale, 70, 150, "%") { pf.uiScale = it }
            SliderPref("Taille des icônes", pf.iconScale, 70, 150, "%") { pf.iconScale = it }

            Label("Position du dock (écran large)")
            ScrollRow {
                Pill("Gauche", pf.dockPos == 0, { pf.dockPos = 0 })
                Pill("Droite", pf.dockPos == 1, { pf.dockPos = 1 })
                Pill("Bas", pf.dockPos == 2, { pf.dockPos = 2 })
            }
            SwitchRow("Barre d'état (heure, météo, batterie)", pf.showStatusBar) { pf.showStatusBar = it }
            SwitchRow("Plein écran (masquer les barres Android)", pf.fullscreen) { pf.fullscreen = it }
            SwitchRow("Garder l'écran allumé", pf.keepScreenOn) { pf.keepScreenOn = it }
            SwitchRow("Horloge 24 heures", pf.clock24) { pf.clock24 = it }
            SwitchRow("Afficher les secondes", pf.showSeconds) { pf.showSeconds = it }
        }

        // ---------------------------------------------------------- Ma voiture
        Section("Ma voiture") {
            OutlinedTextField(
                value = pf.carName,
                onValueChange = { pf.carName = it },
                label = { Text("Nom affiché") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = pf.carPlate,
                    onValueChange = { pf.carPlate = it.uppercase().take(12) },
                    label = { Text("Immatriculation") },
                    placeholder = { Text("AB-123-CD") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                LicensePlate(pf.carPlate)
            }
            Label("Motorisation")
            ScrollRow {
                listOf("Diesel", "Essence", "Hybride", "Électrique").forEachIndexed { i, n ->
                    Pill(n, pf.carFuel == i, { pf.carFuel = i })
                }
            }
            Label("Couleur de la voiture")
            ScrollRow {
                CAR_COLORS.forEachIndexed { i, c ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Swatch(c.color, pf.carColor == i, { pf.carColor = i })
                        Text(c.name, color = p.sub, fontSize = 11.sp)
                    }
                }
            }
            SwitchRow("Annoncer à voix haute l'ouverture des portes / du coffre", pf.doorVoice) { pf.doorVoice = it }
            SwitchRow("Connexion automatique au boîtier OBD", pf.obdAuto) { pf.obdAuto = it }
            ChoiceRow("Écran « Ma voiture » (OBD, diagnostic, CANbus)", "Ouvrir") { state.screen = Screen.Car }
        }

        // -------------------------------------------------------------- Accueil
        Section("Tuiles de l'accueil") {
            Label("Astuce : sur l'accueil, touche « ✎ Modifier » pour redimensionner, déplacer, ajouter ou retirer les tuiles directement.")
            Stepper("Colonnes (paysage)", "${pf.homeCols}", { pf.homeCols = (pf.homeCols - 1).coerceAtLeast(2) }, { pf.homeCols = (pf.homeCols + 1).coerceAtMost(6) })
            Stepper("Lignes (paysage)", "${pf.homeRows}", { pf.homeRows = (pf.homeRows - 1).coerceAtLeast(1) }, { pf.homeRows = (pf.homeRows + 1).coerceAtMost(4) })
            val order = pf.cardOrder
            val off = pf.cardsOff
            order.forEachIndexed { i, c ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    val (cw, ch) = pf.cardSize(c)
                    Text(c.label, color = p.text, fontSize = 17.sp, modifier = Modifier.weight(1f))
                    Text(
                        "$cw×$ch",
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 14.sp,
                        modifier = Modifier.clip(RoundedCornerShape(50)).clickable {
                            // cycle 1×1 → 2×1 → 1×2 → 2×2
                            val next = when (cw to ch) { 1 to 1 -> 2 to 1; 2 to 1 -> 1 to 2; 1 to 2 -> 2 to 2; else -> 1 to 1 }
                            pf.setCardSize(c, next.first, next.second)
                        }.padding(8.dp),
                    )
                    IconButton(onClick = {
                        if (i > 0) {
                            val l = order.toMutableList()
                            l.add(i - 1, l.removeAt(i))
                            pf.setCardOrder(l)
                        }
                    }) { Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "Monter", tint = p.text) }
                    IconButton(onClick = {
                        if (i < order.lastIndex) {
                            val l = order.toMutableList()
                            l.add(i + 1, l.removeAt(i))
                            pf.setCardOrder(l)
                        }
                    }) { Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Descendre", tint = p.text) }
                    Switch(checked = c !in off, onCheckedChange = { pf.setCardEnabled(c, it) })
                }
            }
        }

        // --------------------------------------------------------- Conduite
        Section("Conduite & météo") {
            SwitchRow("Carte en direct dans la tuile Navigation (OpenStreetMap)", pf.liveMap) { pf.liveMap = it }
            Label("Pendant un guidage Waze / Google Maps, la tuile affiche la prochaine manœuvre (accès aux notifications requis).")
            Label("Unité de vitesse")
            ScrollRow {
                Pill("km/h", pf.speedUnit == 0, { pf.speedUnit = 0 })
                Pill("mph", pf.speedUnit == 1, { pf.speedUnit = 1 })
            }
            Stepper(
                "Alerte de vitesse",
                if (pf.speedLimit == 0) "Désactivée" else "${pf.speedLimit} ${speedUnitLabel(pf.speedUnit)}",
                { pf.speedLimit = (pf.speedLimit - 5).coerceAtLeast(0) },
                { pf.speedLimit = (pf.speedLimit + 5).coerceAtMost(250) },
            )
            SwitchRow("Bip sonore en cas de dépassement", pf.speedBeep) { pf.speedBeep = it }
            ChoiceRow("Remettre l'ordinateur de bord à zéro", "Réinitialiser") {
                state.drive.resetTrip()
                Apps.toast(ctx, "Trajet remis à zéro")
            }
            SwitchRow("Température en °F", pf.fahrenheit) { pf.fahrenheit = it }

            var city by remember { mutableStateOf(pf.weatherCity) }
            Label("Ville pour la météo (vide = position GPS)")
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = city,
                    onValueChange = { city = it },
                    singleLine = true,
                    placeholder = { Text("ex. Lyon") },
                    modifier = Modifier.weight(1f),
                )
                Pill("Valider", false, {
                    val q = city.trim()
                    if (q.isEmpty()) {
                        pf.weatherCity = ""
                        pf.cityLat = Float.NaN
                        pf.cityLon = Float.NaN
                    } else {
                        scope.launch {
                            val r = state.weather.geocode(q)
                            if (r == null) {
                                Apps.toast(ctx, "Ville introuvable")
                            } else {
                                pf.cityLat = r.second.toFloat()
                                pf.cityLon = r.third.toFloat()
                                pf.weatherCity = r.first
                                city = r.first
                                state.weather.refresh(r.second, r.third, force = true)
                            }
                        }
                    }
                })
            }
        }

        // --------------------------------------------------------- Messages
        Section("Messages & voix") {
            SwitchRow("Lire à voix haute les nouveaux messages", pf.readAloud) { pf.readAloud = it }
            SwitchRow("Masquer le contenu (annoncer seulement l'expéditeur)", pf.hideMsgContent) { pf.hideMsgContent = it }
            SliderPref("Vitesse de la voix", pf.speechRate, 50, 200, "%") { pf.speechRate = it }
            ChoiceRow("Tester la voix", "Écouter") {
                state.voice.rate = pf.speechRate / 100f
                state.voice.speak("Bonjour ! DriveHome lira tes messages pendant que tu conduis.")
            }
        }

        // --------------------------------------------------------- Applications
        Section("Applications") {
            ChoiceRow("Navigation", state.labelOf(state.navPackage())) { picker = "nav" }
            ChoiceRow("Musique", state.labelOf(state.musicPackage())) { picker = "music" }
            ChoiceRow(
                "Applis du dock",
                "${(pf.favorites ?: emptyList()).size} app(s)",
            ) { picker = "dockManager" }
            ChoiceRow("Applications masquées", "${pf.hidden.size}") { picker = "hidden" }
            Label("Tri du tiroir d'applications")
            ScrollRow {
                Pill("Alphabétique", pf.drawerSort == 0, { pf.drawerSort = 0 })
                Pill("Les plus utilisées", pf.drawerSort == 1, { pf.drawerSort = 1 })
            }
            SwitchRow("Noms sous les icônes", pf.drawerLabels) { pf.drawerLabels = it }
            Label("Boutons du dock (appui long sur une appli du dock : déplacer / retirer)")
            SwitchRow("Bouton « + » pour ajouter des applis", pf.dockAdd) { pf.dockAdd = it }
            SwitchRow("Bouton Ma voiture", pf.dockCar) { pf.dockCar = it }
            SwitchRow("Bouton Téléphone", pf.dockPhone) { pf.dockPhone = it }
            SwitchRow("Bouton Assistant vocal", pf.dockMic) { pf.dockMic = it }
            SwitchRow("Bouton Centre de contrôle", pf.dockControl) { pf.dockControl = it }
        }

        // ------------------------------------------------------- Destinations
        Section("Destinations") {
            OutlinedTextField(
                value = pf.homeAddr,
                onValueChange = { pf.homeAddr = it },
                label = { Text("Adresse du domicile") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = pf.workAddr,
                onValueChange = { pf.workAddr = it },
                label = { Text("Adresse du travail") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Label("Lieux favoris")
            pf.places.forEach { pl ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(pl.label, color = p.text, fontSize = 16.sp)
                        Text(pl.address, color = p.sub, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    IconButton(onClick = { pf.setPlaces(pf.places.filter { it != pl }) }) {
                        Icon(Icons.Filled.Delete, contentDescription = "Supprimer", tint = p.sub)
                    }
                }
            }
            var plLabel by remember { mutableStateOf("") }
            var plAddr by remember { mutableStateOf("") }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = plLabel,
                    onValueChange = { plLabel = it },
                    label = { Text("Nom") },
                    singleLine = true,
                    modifier = Modifier.weight(0.6f),
                )
                OutlinedTextField(
                    value = plAddr,
                    onValueChange = { plAddr = it },
                    label = { Text("Adresse") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                Pill("Ajouter", false, {
                    if (plAddr.isNotBlank()) {
                        pf.setPlaces(pf.places + Place(plLabel.ifBlank { plAddr }.trim(), plAddr.trim()))
                        plLabel = ""
                        plAddr = ""
                    }
                })
            }
        }

        // ------------------------------------------------------- Contacts
        Section("Contacts favoris") {
            pf.contacts.forEach { c ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(c.name, color = p.text, fontSize = 16.sp)
                        Text(c.number, color = p.sub, fontSize = 13.sp)
                    }
                    IconButton(onClick = { pf.setContacts(pf.contacts.filter { it != c }) }) {
                        Icon(Icons.Filled.Delete, contentDescription = "Supprimer", tint = p.sub)
                    }
                }
            }
            var cName by remember { mutableStateOf("") }
            var cNum by remember { mutableStateOf("") }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = cName,
                    onValueChange = { cName = it },
                    label = { Text("Nom") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = cNum,
                    onValueChange = { cNum = it },
                    label = { Text("Numéro") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    modifier = Modifier.weight(1f),
                )
                Pill("Ajouter", false, {
                    if (cNum.isNotBlank()) {
                        pf.setContacts(pf.contacts + Contact(cName.ifBlank { cNum }.trim(), cNum.trim()))
                        cName = ""
                        cNum = ""
                    }
                })
            }
            ChoiceRow("Choisir dans le carnet d'adresses", "Ouvrir") { pickContact() }
        }

        // ------------------------------------------------------- Démarrage
        Section("Démarrage & veille") {
            SwitchRow("Reprendre la musique au démarrage", pf.autoResumeMusic) { pf.autoResumeMusic = it }
            SwitchRow("Ouvrir la navigation au démarrage", pf.autoLaunchNav) { pf.autoLaunchNav = it }
            Label("Écran de veille (horloge) après inactivité sur l'accueil")
            ScrollRow {
                listOf(0, 1, 2, 5, 10, 30).forEach { m ->
                    Pill(if (m == 0) "Jamais" else "$m min", pf.screensaverMin == m, { pf.screensaverMin = m })
                }
            }
        }

        // --------------------------------------------------------------- Système
        Section("Autorisations & système") {
            ChoiceRow("Localisation (vitesse, météo, soleil)", if (state.drive.permission) "✓ Autorisée" else "Autoriser") {
                if (state.drive.permission) Apps.openLocationSettings(ctx)
                else onRequestPermissions(MainActivity.LOCATION_PERMISSIONS)
            }
            ChoiceRow("Accès aux notifications (musique, messages)", if (state.media.info.access) "✓ Autorisé" else "Autoriser") {
                Apps.openNotificationAccess(ctx)
            }
            ChoiceRow("Appel direct des favoris", if (Apps.hasCallPermission(ctx)) "✓ Autorisé" else "Autoriser") {
                onRequestPermissions(MainActivity.PHONE_PERMISSIONS)
            }
            ChoiceRow("Réglage de la luminosité", if (Settings.System.canWrite(ctx)) "✓ Autorisé" else "Autoriser") {
                Apps.openWriteSettings(ctx)
            }
            ChoiceRow("Launcher par défaut", "Ouvrir") { Apps.openHomeSettings(ctx) }
            ChoiceRow("Paramètres Android", "Ouvrir") { Apps.openSystemSettings(ctx) }
        }

        Section("À propos") {
            Label("DriveHome ${appVersion(ctx)} — launcher de conduite pour autoradio Android.")
        }

        Spacer(Modifier.height(24.dp))
    }

    // ------------------------------------------------------------- Sélecteurs
    if (picker == "dockManager") DockManagerDialog(state) { picker = null }
    when (picker) {
        "nav" -> AppPickerDialog(
            title = "Application de navigation",
            apps = state.apps,
            selected = listOf(pf.navApp),
            multi = false,
            allowAuto = true,
            onDismiss = { picker = null },
        ) { res ->
            pf.navApp = res.firstOrNull() ?: ""
            picker = null
        }

        "music" -> AppPickerDialog(
            title = "Application de musique",
            apps = state.apps,
            selected = listOf(pf.musicApp),
            multi = false,
            allowAuto = true,
            onDismiss = { picker = null },
        ) { res ->
            pf.musicApp = res.firstOrNull() ?: ""
            picker = null
        }

        "dock" -> AppPickerDialog(
            title = "Applications du dock",
            apps = state.apps,
            selected = pf.favorites ?: emptyList(),
            multi = true,
            max = AppState.MAX_FAVORITES,
            onDismiss = { picker = null },
            onChange = { pf.setFavorites(it) },
        ) { res ->
            pf.setFavorites(res)
            picker = null
        }

        "hidden" -> AppPickerDialog(
            title = "Applications masquées",
            apps = state.apps,
            selected = pf.hidden,
            multi = true,
            onDismiss = { picker = null },
            onChange = { pf.setHidden(it) },
        ) { res ->
            pf.setHidden(res)
            picker = null
        }
    }
}

private fun appVersion(ctx: android.content.Context): String = try {
    ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: ""
} catch (e: Exception) {
    ""
}

// ---------------------------------------------------------------------------
// Éléments de mise en page
// ---------------------------------------------------------------------------

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    val p = LocalPalette.current
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(p.card)
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(title, color = MaterialTheme.colorScheme.primary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        content()
    }
}

@Composable
private fun ScrollRow(content: @Composable () -> Unit) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) { content() }
}

@Composable
private fun Label(text: String) {
    Text(text, color = LocalPalette.current.sub, fontSize = 14.sp)
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    val p = LocalPalette.current
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = p.text, fontSize = 17.sp, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun SliderPref(label: String, value: Int, min: Int, max: Int, unit: String, onChange: (Int) -> Unit) {
    Column {
        Label("$label : $value $unit")
        Slider(
            value = value.toFloat(),
            onValueChange = { onChange(it.toInt()) },
            valueRange = min.toFloat()..max.toFloat(),
        )
    }
}

@Composable
private fun Stepper(label: String, value: String, onMinus: () -> Unit, onPlus: () -> Unit) {
    val p = LocalPalette.current
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = p.text, fontSize = 17.sp, modifier = Modifier.weight(1f))
        RoundButton(onClick = onMinus, size = 42.dp) { Text("−", color = p.text, fontSize = 22.sp) }
        Text(
            value,
            color = p.text,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.width(120.dp).padding(horizontal = 8.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        RoundButton(onClick = onPlus, size = 42.dp) { Text("+", color = p.text, fontSize = 22.sp) }
    }
}

@Composable
private fun ChoiceRow(label: String, value: String, onClick: () -> Unit) {
    val p = LocalPalette.current
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = p.text, fontSize = 17.sp, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(10.dp))
        Text(value, color = MaterialTheme.colorScheme.primary, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun AppPickerDialog(
    title: String,
    apps: List<AppItem>,
    selected: List<String>,
    multi: Boolean,
    max: Int = Int.MAX_VALUE,
    allowAuto: Boolean = false,
    onDismiss: () -> Unit,
    /** Appelé à chaque coche/décoche : les changements sont enregistrés tout de suite. */
    onChange: ((List<String>) -> Unit)? = null,
    onResult: (List<String>) -> Unit,
) {
    var sel by remember { mutableStateOf(selected) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            LazyColumn(Modifier.heightIn(max = 360.dp)) {
                if (allowAuto) {
                    item {
                        PickerRow(
                            label = "Automatique",
                            pkg = null,
                            checked = sel.firstOrNull().isNullOrEmpty(),
                            multi = false,
                        ) { onResult(emptyList()) }
                    }
                }
                items(apps, key = { it.pkg }) { app ->
                    PickerRow(
                        label = app.label,
                        pkg = app.pkg,
                        checked = app.pkg in sel,
                        multi = multi,
                    ) {
                        if (multi) {
                            sel = if (app.pkg in sel) sel - app.pkg else (sel + app.pkg).takeLast(max)
                            onChange?.invoke(sel)
                        } else {
                            onResult(listOf(app.pkg))
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (multi) {
                TextButton(onClick = { onResult(sel) }) { Text(if (onChange != null) "Terminé" else "OK") }
            }
        },
        dismissButton = {
            if (onChange == null) TextButton(onClick = onDismiss) { Text("Annuler") }
        },
    )
}

@Composable
private fun PickerRow(
    label: String,
    pkg: String?,
    checked: Boolean,
    multi: Boolean,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (pkg != null) {
            AppIcon(pkg, 36.dp)
        } else {
            Spacer(Modifier.size(36.dp))
        }
        Spacer(Modifier.width(12.dp))
        Text(label, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (multi) {
            Checkbox(checked = checked, onCheckedChange = { onClick() })
        } else if (checked) {
            Text("✓", color = MaterialTheme.colorScheme.primary, fontSize = 18.sp)
        }
    }
}
