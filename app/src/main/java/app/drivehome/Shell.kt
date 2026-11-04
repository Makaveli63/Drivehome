package app.drivehome

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.AudioManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Racine de l'interface : fond d'écran + barre d'état + dock + contenu + calques. */
@Composable
fun Root(state: AppState, onRequestPermissions: (Array<String>) -> Unit) {
    val pf = state.prefs

    // Horloge interne : heure, bascule jour/nuit, surveillance GPS
    LaunchedEffect(Unit) {
        while (true) {
            val t = System.currentTimeMillis()
            state.now = t
            val night = state.computeNight(t)
            if (night != state.autoNight) state.autoNight = night
            state.drive.tick()
            state.car.tick(state.drive.speedKmh)
            delay(1000)
        }
    }

    // Météo : rafraîchie toutes les 30 min dès qu'une position est connue
    LaunchedEffect(pf.fahrenheit, pf.weatherCity, state.drive.location != null) {
        while (true) {
            val ll = state.drive.bestLatLon()
            if (ll != null) state.weather.refresh(ll.first, ll.second)
            delay(if (ll == null) 30_000L else 5 * 60_000L)
        }
    }

    BackHandler(enabled = state.screen != Screen.Home || state.controlCenter || state.editHome) {
        when {
            state.controlCenter -> state.controlCenter = false
            state.editHome -> state.editHome = false
            else -> state.screen = Screen.Home
        }
    }

    val idleMs = pf.screensaverMin * 60_000L
    val saver = state.screensaverForced ||
        (idleMs > 0 && state.screen == Screen.Home && !state.controlCenter && state.now - state.lastTouch > idleMs)

    Box(
        Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        awaitPointerEvent(PointerEventPass.Initial)
                        state.lastTouch = System.currentTimeMillis()
                    }
                }
            },
    ) {
        WallpaperLayer(state)
        Column(Modifier.fillMaxSize()) {
            if (pf.showStatusBar) StatusBar(state)
            Box(Modifier.weight(1f).fillMaxWidth()) { Shell(state, onRequestPermissions) }
        }

        DoorAlertBanner(state)
        AnimatedVisibility(visible = state.controlCenter, enter = fadeIn(), exit = fadeOut()) {
            ControlCenter(state)
        }
        AnimatedVisibility(visible = saver, enter = fadeIn(), exit = fadeOut()) {
            Screensaver(state)
        }
    }
}

@Composable
fun WallpaperLayer(state: AppState) {
    val ctx = LocalContext.current
    val night = state.isNight()
    val uri = state.prefs.wallpaperUri

    val custom by produceState<ImageBitmap?>(initialValue = null, uri) {
        value = if (uri.isEmpty()) null else withContext(Dispatchers.IO) { decodeSampled(ctx, uri) }
    }

    val preset = WALLPAPERS[state.prefs.wallpaperIndex.coerceIn(0, WALLPAPERS.lastIndex)]
    val colors = if (night) preset.dark else preset.light
    val img = custom
    val dim = state.prefs.wallpaperDim.coerceIn(0, 90) / 100f

    Box(Modifier.fillMaxSize().background(Brush.linearGradient(colors))) {
        if (img != null) {
            Image(
                bitmap = img,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            // voile pour garder les cartes lisibles
            Box(
                Modifier
                    .fillMaxSize()
                    .background(if (night) Color.Black.copy(alpha = dim) else Color.White.copy(alpha = dim * 0.6f)),
            )
        }
    }
}

fun decodeSampled(ctx: Context, uriString: String): ImageBitmap? {
    return try {
        val uri = Uri.parse(uriString)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (bounds.outWidth / sample > 2200 || bounds.outHeight / sample > 1400) sample *= 2
        val opts = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.RGB_565
        }
        val bmp = ctx.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, opts)
        }
        bmp?.asImageBitmap()
    } catch (e: Exception) {
        null
    }
}

// ---------------------------------------------------------------------------
// Barre d'état
// ---------------------------------------------------------------------------

@Composable
fun StatusBar(state: AppState) {
    val p = LocalPalette.current
    val pf = state.prefs
    val time = SimpleDateFormat(if (pf.clock24) "HH:mm" else "h:mm a", Locale.getDefault()).format(Date(state.now))
    val date = SimpleDateFormat("EEE d MMM", Locale.getDefault()).format(Date(state.now))
    val w = state.weather.current
    val unread = NotifStore.messages.size

    Row(
        Modifier
            .fillMaxWidth()
            .height(40.dp)
            .background(p.dock)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(time, color = p.text, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        Text(date.replaceFirstChar { it.uppercase() }, color = p.sub, fontSize = 14.sp)
        Spacer(Modifier.weight(1f))

        if (state.drive.gpsFix && state.drive.speedKmh > 0f) {
            val shown = displaySpeed(state.drive.speedKmh, pf.speedUnit)
            Text(
                "$shown ${speedUnitLabel(pf.speedUnit)}",
                color = if (state.drive.overLimit) DANGER else p.text,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
        if (unread > 0) {
            Row(
                Modifier
                    .clip(RoundedCornerShape(50))
                    .clickable { state.screen = Screen.Messages }
                    .padding(horizontal = 6.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.Notifications, contentDescription = "Messages", tint = p.text, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("$unread", color = p.text, fontSize = 14.sp)
            }
        }
        if (w != null) {
            val (icon, _) = weatherLabel(w.code, w.isDay)
            Text("$icon ${w.temp.toInt()}°", color = p.text, fontSize = 15.sp)
        }
        if (state.drive.gpsFix) {
            Text("GPS", color = OK_GREEN, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
        if (state.batteryLevel >= 0) {
            BatteryGlyph(state.batteryLevel, state.charging, p.text)
        }
    }
}

@Composable
fun BatteryGlyph(level: Int, charging: Boolean, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("$level%", color = color, fontSize = 13.sp)
        Spacer(Modifier.width(4.dp))
        val fill = when {
            charging -> OK_GREEN
            level <= 15 -> DANGER
            else -> color
        }
        Canvas(Modifier.size(width = 24.dp, height = 12.dp)) {
            val w = size.width
            val h = size.height
            drawRoundRect(color, Offset.Zero, Size(w * 0.9f, h), CornerRadius(3f, 3f), style = Stroke(2f))
            drawRect(color, Offset(w * 0.92f, h * 0.3f), Size(w * 0.08f, h * 0.4f))
            drawRoundRect(fill, Offset(3f, 3f), Size((w * 0.9f - 6f) * level / 100f, h - 6f), CornerRadius(2f, 2f))
        }
    }
}

// ---------------------------------------------------------------------------
// Disposition : dock + contenu
// ---------------------------------------------------------------------------

@Composable
fun Shell(state: AppState, onRequestPermissions: (Array<String>) -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth > maxHeight
        val pos = if (wide) state.prefs.dockPos else 2

        when (pos) {
            0 -> Row(Modifier.fillMaxSize()) {
                Dock(state, vertical = true)
                Box(Modifier.weight(1f).fillMaxHeight()) { ScreenContent(state, wide, onRequestPermissions) }
            }
            1 -> Row(Modifier.fillMaxSize()) {
                Box(Modifier.weight(1f).fillMaxHeight()) { ScreenContent(state, wide, onRequestPermissions) }
                Dock(state, vertical = true)
            }
            else -> Column(Modifier.fillMaxSize()) {
                Box(Modifier.weight(1f).fillMaxWidth()) { ScreenContent(state, wide, onRequestPermissions) }
                Dock(state, vertical = false)
            }
        }
    }
}

@Composable
fun ScreenContent(state: AppState, wide: Boolean, onRequestPermissions: (Array<String>) -> Unit) {
    when (state.screen) {
        Screen.Home -> HomeContent(state, wide, onRequestPermissions)
        Screen.Car -> CarScreen(state, onRequestPermissions)
        Screen.Drawer -> DrawerScreen(state)
        Screen.Phone -> PhoneScreen(state, onRequestPermissions)
        Screen.Messages -> MessagesScreen(state)
        Screen.Settings -> SettingsScreen(state, onRequestPermissions)
    }
}

/** Barre d'accès rapide : accueil, voiture, apps favorites (à volonté), téléphone, tiroir, assistant, contrôle, réglages. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Dock(state: AppState, vertical: Boolean) {
    val ctx = LocalContext.current
    val p = LocalPalette.current
    val pf = state.prefs
    val scale = pf.iconScale / 100f
    val iconSize = (56 * scale).dp
    val favorites = (pf.favorites ?: emptyList())
        .filter { fav -> state.apps.any { it.pkg == fav } }
    val onPrimary = MaterialTheme.colorScheme.onPrimary
    var menuFor by remember { mutableStateOf<String?>(null) }
    var addDialog by remember { mutableStateOf(false) }

    @Composable
    fun ScreenButton(target: Screen, content: @Composable (Color) -> Unit) {
        val sel = state.screen == target
        RoundButton(
            onClick = { state.screen = target },
            size = iconSize,
            filled = sel,
        ) { content(if (sel) onPrimary else p.text) }
    }

    val head: @Composable () -> Unit = {
        ScreenButton(Screen.Home) { Icon(Icons.Filled.Home, contentDescription = "Accueil", tint = it) }
        if (pf.dockCar) {
            ScreenButton(Screen.Car) { CarGlyph(it, state.car.doors.anyOpen()) }
        }
    }

    // zone défilante : applis du dock + bouton « + » + boutons optionnels
    val middle: @Composable () -> Unit = {
        favorites.forEach { pkg ->
            Box {
                Box(
                    Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .combinedClickable(
                            onClick = { state.launch(pkg) },
                            onLongClick = { menuFor = pkg },
                        ),
                ) {
                    AppIcon(pkg, iconSize)
                }
                DropdownMenu(expanded = menuFor == pkg, onDismissRequest = { menuFor = null }) {
                    Text(
                        state.labelOf(pkg),
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                    DropdownMenuItem(
                        text = { Text(if (vertical) "↑ Monter" else "← Vers la gauche") },
                        onClick = { state.moveFavorite(pkg, -1) },
                    )
                    DropdownMenuItem(
                        text = { Text(if (vertical) "↓ Descendre" else "→ Vers la droite") },
                        onClick = { state.moveFavorite(pkg, 1) },
                    )
                    DropdownMenuItem(
                        text = { Text("Retirer du dock") },
                        onClick = {
                            state.toggleFavorite(pkg)
                            menuFor = null
                            Apps.toast(ctx, "${state.labelOf(pkg)} retiré du dock")
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Infos sur l'application") },
                        onClick = {
                            Apps.openAppInfo(ctx, pkg)
                            menuFor = null
                        },
                    )
                }
            }
        }
        if (pf.dockAdd) {
            RoundButton(onClick = { addDialog = true }, size = iconSize) {
                Text("+", color = p.text, fontSize = 30.sp, fontWeight = FontWeight.Light)
            }
        }
        if (pf.dockPhone) {
            ScreenButton(Screen.Phone) { Icon(Icons.Filled.Call, contentDescription = "Téléphone", tint = it) }
        }
        if (pf.dockMic) {
            RoundButton(onClick = { Apps.voiceAssistant(ctx) }, size = iconSize) { MicGlyph(p.text) }
        }
        if (pf.dockControl) {
            RoundButton(onClick = { state.controlCenter = true }, size = iconSize, filled = state.controlCenter) {
                SlidersGlyph(if (state.controlCenter) onPrimary else p.text)
            }
        }
    }

    val tail: @Composable () -> Unit = {
        ScreenButton(Screen.Drawer) { AppsGlyph(it) }
        ScreenButton(Screen.Settings) { Icon(Icons.Filled.Settings, contentDescription = "Réglages", tint = it) }
    }

    if (vertical) {
        Column(
            Modifier
                .fillMaxHeight()
                .width(iconSize + 32.dp)
                .background(p.dock)
                .padding(vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            head()
            Column(
                Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterVertically),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) { middle() }
            tail()
        }
    } else {
        Row(
            Modifier
                .fillMaxWidth()
                .height(iconSize + 24.dp)
                .background(p.dock)
                .padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            head()
            Row(
                Modifier.weight(1f).fillMaxHeight().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) { middle() }
            tail()
        }
    }

    if (addDialog) {
        DockManagerDialog(state) { addDialog = false }
    }
}
}

/** Petite silhouette de voiture (point orange si un ouvrant est ouvert). */
@Composable
fun CarGlyph(color: Color, alert: Boolean) {
    Canvas(Modifier.size(30.dp)) {
        val w = size.width
        val h = size.height
        drawRoundRect(color, Offset(w * 0.06f, h * 0.42f), Size(w * 0.88f, h * 0.30f), CornerRadius(w * 0.08f, w * 0.08f))
        val roof = androidx.compose.ui.graphics.Path().apply {
            moveTo(w * 0.22f, h * 0.44f)
            lineTo(w * 0.32f, h * 0.22f)
            lineTo(w * 0.80f, h * 0.22f)
            lineTo(w * 0.90f, h * 0.44f)
            close()
        }
        drawPath(roof, color)
        drawCircle(color, w * 0.10f, Offset(w * 0.26f, h * 0.76f))
        drawCircle(color, w * 0.10f, Offset(w * 0.74f, h * 0.76f))
        if (alert) drawCircle(Color(0xFFFF9F0A), w * 0.14f, Offset(w * 0.86f, h * 0.14f))
    }
}

// ---------------------------------------------------------------------------
// Centre de contrôle (volume, luminosité, raccourcis système)
// ---------------------------------------------------------------------------

@Composable
fun ControlCenter(state: AppState) {
    val ctx = LocalContext.current
    val p = LocalPalette.current
    val pf = state.prefs
    val audio = remember { ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val maxVol = remember { audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1) }
    var vol by remember { mutableFloatStateOf(audio.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat()) }
    var bright by remember {
        mutableFloatStateOf(
            try {
                Settings.System.getInt(ctx.contentResolver, Settings.System.SCREEN_BRIGHTNESS).toFloat()
            } catch (e: Exception) {
                128f
            },
        )
    }
    var canWrite by remember { mutableStateOf(Settings.System.canWrite(ctx)) }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.55f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { state.controlCenter = false },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .widthIn(max = 720.dp)
                .fillMaxWidth()
                .padding(24.dp)
                .clip(RoundedCornerShape(32.dp))
                .background(if (p.night) Color(0xF01C1C1E) else Color(0xF2F2F2F7))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { }
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("Centre de contrôle", color = p.text, fontSize = 24.sp, fontWeight = FontWeight.Bold)

            SliderRow("Volume", "${(vol / maxVol * 100).toInt()} %") {
                Slider(
                    value = vol,
                    onValueChange = {
                        vol = it
                        try {
                            audio.setStreamVolume(AudioManager.STREAM_MUSIC, it.toInt(), 0)
                        } catch (e: Exception) {
                            // volume fixe sur certains autoradios
                        }
                    },
                    valueRange = 0f..maxVol.toFloat(),
                    steps = (maxVol - 1).coerceIn(0, 50),
                )
            }

            SliderRow("Luminosité", if (canWrite) "${(bright / 255f * 100).toInt()} %" else "Autoriser") {
                Slider(
                    value = bright,
                    onValueChange = {
                        canWrite = Settings.System.canWrite(ctx)
                        if (!canWrite) {
                            Apps.openWriteSettings(ctx)
                        } else {
                            bright = it
                            try {
                                Settings.System.putInt(
                                    ctx.contentResolver,
                                    Settings.System.SCREEN_BRIGHTNESS_MODE,
                                    Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL,
                                )
                                Settings.System.putInt(ctx.contentResolver, Settings.System.SCREEN_BRIGHTNESS, it.toInt())
                            } catch (e: Exception) {
                                // ignoré
                            }
                        }
                    },
                    valueRange = 5f..255f,
                )
            }

            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                ControlTile("Thème", when (pf.themeMode) { 1 -> "Sombre"; 2 -> "Clair"; 3 -> "Soleil"; else -> "Auto" }) {
                    pf.themeMode = (pf.themeMode + 1) % 4
                }
                ControlTile("Wi-Fi", "Réglages") { Apps.openWifi(ctx) }
                ControlTile("Bluetooth", "Réglages") { Apps.openBluetooth(ctx) }
                ControlTile("Égaliseur", "Audio") { Apps.openEqualizer(ctx) }
                ControlTile("Son", "Réglages") { Apps.openSound(ctx) }
                ControlTile("Écran", "Affichage") { Apps.openDisplay(ctx) }
            }
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                ControlTile("Veille", "Horloge") {
                    state.controlCenter = false
                    state.screensaverForced = true
                }
                ControlTile("Plein écran", if (pf.fullscreen) "Activé" else "Désactivé") {
                    pf.fullscreen = !pf.fullscreen
                }
                ControlTile("Écran allumé", if (pf.keepScreenOn) "Toujours" else "Auto") {
                    pf.keepScreenOn = !pf.keepScreenOn
                }
                ControlTile("Lecture vocale", if (pf.readAloud) "Activée" else "Désactivée") {
                    pf.readAloud = !pf.readAloud
                }
                ControlTile("Silence", "Stop voix") { state.voice.stop() }
                ControlTile("Android", "Paramètres") { Apps.openSystemSettings(ctx) }
            }
        }
    }
}

@Composable
private fun SliderRow(label: String, value: String, slider: @Composable () -> Unit) {
    val p = LocalPalette.current
    Column {
        Row(Modifier.fillMaxWidth()) {
            Text(label, color = p.text, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Text(value, color = p.sub, fontSize = 15.sp)
        }
        slider()
    }
}

@Composable
private fun ControlTile(title: String, value: String, onClick: () -> Unit) {
    val p = LocalPalette.current
    Column(
        Modifier
            .width(130.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(p.chip)
            .clickable(onClick = onClick)
            .padding(14.dp),
    ) {
        Text(title, color = p.text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(value, color = MaterialTheme.colorScheme.primary, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

// ---------------------------------------------------------------------------
// Écran de veille : grande horloge, vitesse, morceau en cours
// ---------------------------------------------------------------------------

@Composable
fun Screensaver(state: AppState) {
    val pf = state.prefs
    val time = SimpleDateFormat(
        if (pf.clock24) (if (pf.showSeconds) "HH:mm:ss" else "HH:mm") else "h:mm a",
        Locale.getDefault(),
    ).format(Date(state.now))
    val date = SimpleDateFormat("EEEE d MMMM", Locale.getDefault()).format(Date(state.now))
        .replaceFirstChar { it.uppercase() }
    val m = state.media.info
    val w = state.weather.current
    val dim = Color(0xFFB0B0B8)

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) {
                state.screensaverForced = false
                state.lastTouch = System.currentTimeMillis()
            },
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(time, color = Color.White, fontSize = 120.sp, fontWeight = FontWeight.Thin)
            Text(date, color = dim, fontSize = 26.sp)
            Spacer(Modifier.height(24.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(32.dp), verticalAlignment = Alignment.CenterVertically) {
                if (state.drive.gpsFix) {
                    Text(
                        "${displaySpeed(state.drive.speedKmh, pf.speedUnit)} ${speedUnitLabel(pf.speedUnit)}",
                        color = if (state.drive.overLimit) DANGER else Color.White,
                        fontSize = 34.sp,
                    )
                }
                if (w != null) {
                    Text("${weatherLabel(w.code, w.isDay).first} ${w.temp.toInt()}°", color = Color.White, fontSize = 34.sp)
                }
            }
            if (m.hasSession && !m.title.isNullOrEmpty()) {
                Spacer(Modifier.height(20.dp))
                Text(
                    (if (m.playing) "♪ " else "❚❚ ") + m.title + (m.artist?.let { " — $it" } ?: ""),
                    color = dim,
                    fontSize = 20.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 40.dp),
                )
            }
        }
    }
}

/** Taille utilitaire pour les icônes de cartes. */
val CardIconSize: Dp = 34.dp

/** Gestion du dock : ordre (↑ ↓), retrait (✕) et ajout d'applis. */
@Composable
fun DockManagerDialog(state: AppState, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val pf = state.prefs
    var picking by remember { mutableStateOf(false) }
    val favs = (pf.favorites ?: emptyList()).filter { f -> state.apps.any { it.pkg == f } }

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Applis du dock") },
        text = {
            Column {
                if (favs.isEmpty()) {
                    Text("Aucune appli dans le dock. Touche « + Ajouter des applis ».")
                }
                androidx.compose.foundation.lazy.LazyColumn(Modifier.heightIn(max = 380.dp)) {
                    items(favs.size, key = { favs[it] }) { i ->
                        val pkg = favs[i]
                        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            AppIcon(pkg, 40.dp)
                            Spacer(Modifier.width(12.dp))
                            Text(state.labelOf(pkg), modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            androidx.compose.material3.IconButton(onClick = { state.moveFavorite(pkg, -1) }, enabled = i > 0) {
                                Text("▲", fontSize = 18.sp)
                            }
                            androidx.compose.material3.IconButton(onClick = { state.moveFavorite(pkg, 1) }, enabled = i < favs.lastIndex) {
                                Text("▼", fontSize = 18.sp)
                            }
                            androidx.compose.material3.IconButton(onClick = {
                                state.toggleFavorite(pkg)
                                Apps.toast(ctx, "${state.labelOf(pkg)} retiré du dock")
                            }) { Text("✕", fontSize = 18.sp, color = DANGER) }
                        }
                    }
                }
            }
        },
        confirmButton = { androidx.compose.material3.TextButton(onClick = onDismiss) { Text("Terminé") } },
        dismissButton = { androidx.compose.material3.TextButton(onClick = { picking = true }) { Text("+ Ajouter des applis") } },
    )

    if (picking) {
        AppPickerDialog(
            title = "Ajouter au dock",
            apps = state.apps,
            selected = pf.favorites ?: emptyList(),
            multi = true,
            max = AppState.MAX_FAVORITES,
            onDismiss = { picking = false },
            onChange = { pf.setFavorites(it) },
        ) { res ->
            pf.setFavorites(res)
            picking = false
        }
    }
}
