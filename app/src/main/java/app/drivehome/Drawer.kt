package app.drivehome

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DrawerScreen(state: AppState) {
    val ctx = LocalContext.current
    val p = LocalPalette.current
    var query by remember { mutableStateOf("") }
    var menuFor by remember { mutableStateOf<String?>(null) }

    val scale = state.prefs.iconScale / 100f
    val apps = state.visibleApps.filter { it.label.contains(query, ignoreCase = true) }
    val favorites = state.prefs.favorites ?: emptyList()

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                placeholder = { Text("Rechercher une application (${state.visibleApps.size})") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                shape = RoundedCornerShape(50),
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(10.dp))
            Pill(
                if (state.prefs.drawerSort == 1) "Plus utilisées" else "A → Z",
                false,
                { state.prefs.drawerSort = if (state.prefs.drawerSort == 1) 0 else 1 },
            )
        }
        Spacer(Modifier.height(12.dp))

        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = (112 * scale).dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            items(apps, key = { it.pkg }) { app ->
                Box {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(20.dp))
                            .combinedClickable(
                                onClick = { state.launch(app.pkg) },
                                onLongClick = { menuFor = app.pkg },
                            )
                            .padding(10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        AppIcon(app.pkg, (68 * scale).dp)
                        if (state.prefs.drawerLabels) {
                            Spacer(Modifier.height(6.dp))
                            Text(
                                app.label,
                                color = p.text,
                                fontSize = 14.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }

                    DropdownMenu(
                        expanded = menuFor == app.pkg,
                        onDismissRequest = { menuFor = null },
                    ) {
                        DropdownMenuItem(
                            text = {
                                Text(
                                    if (app.pkg in favorites) "Retirer du dock" else "Ajouter au dock",
                                )
                            },
                            onClick = {
                                state.toggleFavorite(app.pkg)
                                menuFor = null
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Définir comme appli de navigation") },
                            onClick = {
                                state.prefs.navApp = app.pkg
                                menuFor = null
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Définir comme appli de musique") },
                            onClick = {
                                state.prefs.musicApp = app.pkg
                                menuFor = null
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Masquer") },
                            onClick = {
                                state.toggleHidden(app.pkg)
                                menuFor = null
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Infos sur l'application") },
                            onClick = {
                                Apps.openAppInfo(ctx, app.pkg)
                                menuFor = null
                            },
                        )
                    }
                }
            }
        }
    }
}
