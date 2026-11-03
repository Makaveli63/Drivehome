package app.drivehome

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun SettingsScreen(state: AppState) {
    val ctx = LocalContext.current
    val p = LocalPalette.current
    val pf = state.prefs
    var picker by remember { mutableStateOf<String?>(null) }

    val imageLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            try {
                ctx.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            } catch (e: Exception) {
                // certaines sources ne proposent pas de droit persistant
            }
            pf.setWallpaperUri(uri.toString())
        }
    }

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
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Pill("Auto (nuit 19h–7h)", pf.themeMode == 0, { pf.setThemeMode(0) })
                Pill("Sombre", pf.themeMode == 1, { pf.setThemeMode(1) })
                Pill("Clair", pf.themeMode == 2, { pf.setThemeMode(2) })
            }

            Label("Couleur d'accent")
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ACCENTS.forEachIndexed { i, a ->
                    Swatch(a.color, pf.accentIndex == i, { pf.setAccentIndex(i) })
                }
            }

            Label("Fond d'écran")
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                WALLPAPERS.forEachIndexed { i, w ->
                    val selected = pf.wallpaperIndex == i && pf.wallpaperUri.isEmpty()
                    val ring = if (selected) MaterialTheme.colorScheme.primary else p.chip
                    Box(
                        Modifier
                            .size(52.dp)
                            .clip(CircleShape)
                            .background(Brush.linearGradient(w.dark))
                            .border(3.dp, ring, CircleShape)
                            .clickable {
                                pf.setWallpaperIndex(i)
                                pf.setWallpaperUri("")
                            },
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Pill("Choisir une image…", pf.wallpaperUri.isNotEmpty(), {
                    try {
                        imageLauncher.launch(arrayOf("image/*"))
                    } catch (e: Exception) {
                        Apps.toast(ctx, "Aucun sélecteur de fichiers sur cet appareil")
                    }
                })
                if (pf.wallpaperUri.isNotEmpty()) {
                    Pill("Retirer l'image", false, { pf.setWallpaperUri("") })
                }
            }

            Label("Taille des icônes : ${pf.iconScale} %")
            Slider(
                value = pf.iconScale.toFloat(),
                onValueChange = { pf.setIconScale(it.toInt()) },
                valueRange = 80f..140f,
            )

            Label("Position du dock (écran large)")
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Pill("Gauche", pf.dockPos == 0, { pf.setDockPos(0) })
                Pill("Droite", pf.dockPos == 1, { pf.setDockPos(1) })
                Pill("Bas", pf.dockPos == 2, { pf.setDockPos(2) })
            }
        }

        // -------------------------------------------------------------- Accueil
        Section("Accueil") {
            SwitchRow("Carte Navigation", pf.showNav) { pf.setShowNav(it) }
            SwitchRow("Carte Musique", pf.showMedia) { pf.setShowMedia(it) }
            SwitchRow("Carte Horloge", pf.showClock) { pf.setShowClock(it) }
            SwitchRow("Horloge 24 heures", pf.clock24) { pf.setClock24(it) }
        }

        // --------------------------------------------------------- Applications
        Section("Applications") {
            ChoiceRow("Navigation", state.labelOf(state.navPackage())) { picker = "nav" }
            ChoiceRow("Musique", state.labelOf(state.musicPackage())) { picker = "music" }
            ChoiceRow(
                "Dock (max ${AppState.MAX_FAVORITES})",
                "${(pf.favorites ?: emptyList()).size} app(s)",
            ) { picker = "dock" }
            ChoiceRow("Applications masquées", "${pf.hidden.size}") { picker = "hidden" }
        }

        // ------------------------------------------------------- Destinations
        Section("Destinations rapides") {
            OutlinedTextField(
                value = pf.homeAddr,
                onValueChange = { pf.setHomeAddr(it) },
                label = { Text("Adresse du domicile") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = pf.workAddr,
                onValueChange = { pf.setWorkAddr(it) },
                label = { Text("Adresse du travail") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        // --------------------------------------------------------------- Système
        Section("Système") {
            ChoiceRow("Launcher par défaut", "Ouvrir") { Apps.openHomeSettings(ctx) }
            ChoiceRow("Accès aux notifications (musique)", "Ouvrir") {
                Apps.openNotificationAccess(ctx)
            }
            ChoiceRow("Paramètres Android", "Ouvrir") { Apps.openSystemSettings(ctx) }
        }

        Spacer(Modifier.height(24.dp))
    }

    // ------------------------------------------------------------- Sélecteurs
    when (picker) {
        "nav" -> AppPickerDialog(
            title = "Application de navigation",
            apps = state.apps,
            selected = listOf(pf.navApp),
            multi = false,
            allowAuto = true,
            onDismiss = { picker = null },
        ) { res ->
            pf.setNavApp(res.firstOrNull() ?: "")
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
            pf.setMusicApp(res.firstOrNull() ?: "")
            picker = null
        }

        "dock" -> AppPickerDialog(
            title = "Applications du dock (max ${AppState.MAX_FAVORITES})",
            apps = state.apps,
            selected = pf.favorites ?: emptyList(),
            multi = true,
            max = AppState.MAX_FAVORITES,
            onDismiss = { picker = null },
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
        ) { res ->
            pf.setHidden(res)
            picker = null
        }
    }
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
        Text(
            title,
            color = MaterialTheme.colorScheme.primary,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
        )
        content()
    }
}

@Composable
private fun Label(text: String) {
    Text(text, color = LocalPalette.current.sub, fontSize = 14.sp)
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    val p = LocalPalette.current
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = p.text, fontSize = 17.sp, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
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
        Text(
            value,
            color = p.sub,
            fontSize = 15.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun AppPickerDialog(
    title: String,
    apps: List<AppItem>,
    selected: List<String>,
    multi: Boolean,
    max: Int = Int.MAX_VALUE,
    allowAuto: Boolean = false,
    onDismiss: () -> Unit,
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
                        } else {
                            onResult(listOf(app.pkg))
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (multi) {
                TextButton(onClick = { onResult(sel) }) { Text("OK") }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Annuler") }
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
        Text(
            label,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (multi) {
            Checkbox(checked = checked, onCheckedChange = { onClick() })
        } else if (checked) {
            Text("✓", color = MaterialTheme.colorScheme.primary, fontSize = 18.sp)
        }
    }
}
