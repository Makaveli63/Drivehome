package app.drivehome

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/** Racine de l'interface : fond d'écran + dock + contenu. */
@Composable
fun Root(state: AppState) {
    LaunchedEffect(Unit) {
        while (true) {
            val t = System.currentTimeMillis()
            state.now = t
            val night = isNightAt(t)
            if (night != state.autoNight) state.autoNight = night
            delay(1000)
        }
    }

    BackHandler(enabled = state.screen != Screen.Home) {
        state.screen = Screen.Home
    }

    Box(Modifier.fillMaxSize()) {
        WallpaperLayer(state)
        Shell(state)
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
                    .background(if (night) Color(0x66000000) else Color(0x33FFFFFF)),
            )
        }
    }
}

private fun decodeSampled(ctx: android.content.Context, uriString: String): ImageBitmap? {
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

@Composable
fun Shell(state: AppState) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth > maxHeight
        val pos = if (wide) state.prefs.dockPos else 2

        when (pos) {
            0 -> Row(Modifier.fillMaxSize()) {
                Dock(state, vertical = true)
                Box(Modifier.weight(1f).fillMaxHeight()) { ScreenContent(state, wide) }
            }
            1 -> Row(Modifier.fillMaxSize()) {
                Box(Modifier.weight(1f).fillMaxHeight()) { ScreenContent(state, wide) }
                Dock(state, vertical = true)
            }
            else -> Column(Modifier.fillMaxSize()) {
                Box(Modifier.weight(1f).fillMaxWidth()) { ScreenContent(state, wide) }
                Dock(state, vertical = false)
            }
        }
    }
}

@Composable
fun ScreenContent(state: AppState, wide: Boolean) {
    when (state.screen) {
        Screen.Home -> HomeContent(state, wide)
        Screen.Drawer -> DrawerScreen(state)
        Screen.Settings -> SettingsScreen(state)
    }
}

/** Barre d'accès rapide : accueil, favoris, toutes les apps, assistant, réglages. */
@Composable
fun Dock(state: AppState, vertical: Boolean) {
    val ctx = LocalContext.current
    val p = LocalPalette.current
    val scale = state.prefs.iconScale / 100f
    val iconSize = (60 * scale).dp
    val favorites = (state.prefs.favorites ?: emptyList())
        .filter { fav -> state.apps.any { it.pkg == fav } }

    val items: @Composable () -> Unit = {
        RoundButton(
            onClick = { state.screen = Screen.Home },
            size = iconSize,
            filled = state.screen == Screen.Home,
        ) {
            Icon(
                Icons.Filled.Home,
                contentDescription = "Accueil",
                tint = if (state.screen == Screen.Home) MaterialTheme.colorScheme.onPrimary else p.text,
            )
        }

        favorites.forEach { pkg ->
            Box(
                Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .clickable { Apps.launch(ctx, pkg) },
            ) {
                AppIcon(pkg, iconSize)
            }
        }

        RoundButton(
            onClick = { state.screen = Screen.Drawer },
            size = iconSize,
            filled = state.screen == Screen.Drawer,
        ) {
            AppsGlyph(
                if (state.screen == Screen.Drawer) MaterialTheme.colorScheme.onPrimary else p.text,
            )
        }

        RoundButton(onClick = { Apps.voiceAssistant(ctx) }, size = iconSize) {
            MicGlyph(p.text)
        }

        RoundButton(
            onClick = { state.screen = Screen.Settings },
            size = iconSize,
            filled = state.screen == Screen.Settings,
        ) {
            Icon(
                Icons.Filled.Settings,
                contentDescription = "Réglages",
                tint = if (state.screen == Screen.Settings) MaterialTheme.colorScheme.onPrimary else p.text,
            )
        }
    }

    if (vertical) {
        Column(
            Modifier
                .fillMaxHeight()
                .width(iconSize + 36.dp)
                .background(p.dock)
                .verticalScroll(rememberScrollState())
                .padding(vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) { items() }
    } else {
        Row(
            Modifier
                .fillMaxWidth()
                .height(iconSize + 28.dp)
                .background(p.dock)
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(18.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) { items() }
    }
}
