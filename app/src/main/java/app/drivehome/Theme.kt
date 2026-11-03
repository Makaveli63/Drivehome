package app.drivehome

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import java.util.Calendar

data class Accent(val name: String, val color: Color, val on: Color)

val ACCENTS = listOf(
    Accent("Bleu", Color(0xFF0A84FF), Color.White),
    Accent("Vert", Color(0xFF30D158), Color.Black),
    Accent("Orange", Color(0xFFFF9F0A), Color.Black),
    Accent("Rouge", Color(0xFFFF453A), Color.White),
    Accent("Violet", Color(0xFFBF5AF2), Color.White),
    Accent("Rose", Color(0xFFFF375F), Color.White),
    Accent("Turquoise", Color(0xFF40C8E0), Color.Black),
    Accent("Jaune", Color(0xFFFFD60A), Color.Black),
)

data class WallpaperPreset(val name: String, val dark: List<Color>, val light: List<Color>)

val WALLPAPERS = listOf(
    WallpaperPreset(
        "Nuit",
        listOf(Color(0xFF05070D), Color(0xFF16213A)),
        listOf(Color(0xFFF2F5FB), Color(0xFFD9E3F5)),
    ),
    WallpaperPreset(
        "Aurore",
        listOf(Color(0xFF0B1F26), Color(0xFF1F5A6B)),
        listOf(Color(0xFFE8F6F4), Color(0xFFBFE3E8)),
    ),
    WallpaperPreset(
        "Crépuscule",
        listOf(Color(0xFF1A0B2E), Color(0xFF5B2A86)),
        listOf(Color(0xFFF3EAFB), Color(0xFFD9C4F2)),
    ),
    WallpaperPreset(
        "Braise",
        listOf(Color(0xFF1C0A08), Color(0xFF7A2E1B)),
        listOf(Color(0xFFFFF0E8), Color(0xFFF5CDB8)),
    ),
    WallpaperPreset(
        "Forêt",
        listOf(Color(0xFF07140D), Color(0xFF1F5A3A)),
        listOf(Color(0xFFEAF6EE), Color(0xFFC3E3CE)),
    ),
    WallpaperPreset(
        "Graphite",
        listOf(Color(0xFF0D0D0F), Color(0xFF2B2B31)),
        listOf(Color(0xFFF4F4F6), Color(0xFFDADADF)),
    ),
    WallpaperPreset(
        "Noir pur",
        listOf(Color(0xFF000000), Color(0xFF000000)),
        listOf(Color(0xFFFFFFFF), Color(0xFFFFFFFF)),
    ),
)

data class Palette(
    val night: Boolean,
    val card: Color,
    val chip: Color,
    val dock: Color,
    val text: Color,
    val sub: Color,
)

val LocalPalette = compositionLocalOf {
    Palette(
        night = true,
        card = Color(0x66000000),
        chip = Color(0x22FFFFFF),
        dock = Color(0x66000000),
        text = Color.White,
        sub = Color(0xB3FFFFFF),
    )
}

fun isNightAt(millis: Long): Boolean {
    val cal = Calendar.getInstance()
    cal.timeInMillis = millis
    val h = cal.get(Calendar.HOUR_OF_DAY)
    return h >= 19 || h < 7
}

fun AppState.isNight(): Boolean = when (prefs.themeMode) {
    1 -> true
    2 -> false
    else -> autoNight
}

@Composable
fun DriveTheme(state: AppState, content: @Composable () -> Unit) {
    val night = state.isNight()
    val accent = ACCENTS[state.prefs.accentIndex.coerceIn(0, ACCENTS.lastIndex)]

    val palette = if (night) {
        Palette(
            night = true,
            card = Color(0x80000000),
            chip = Color(0x26FFFFFF),
            dock = Color(0x73000000),
            text = Color.White,
            sub = Color(0xB3FFFFFF),
        )
    } else {
        Palette(
            night = false,
            card = Color(0xB3FFFFFF),
            chip = Color(0x14000000),
            dock = Color(0xB3FFFFFF),
            text = Color(0xFF111114),
            sub = Color(0xB3111114),
        )
    }

    val scheme = if (night) {
        darkColorScheme(
            primary = accent.color,
            onPrimary = accent.on,
            background = Color.Black,
            surface = Color(0xFF1C1C1E),
            onSurface = Color.White,
        )
    } else {
        lightColorScheme(
            primary = accent.color,
            onPrimary = accent.on,
            background = Color.White,
            surface = Color(0xFFF2F2F7),
            onSurface = Color(0xFF111114),
        )
    }

    CompositionLocalProvider(LocalPalette provides palette) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}
