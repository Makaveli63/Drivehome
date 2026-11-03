package app.drivehome

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Icône réelle d'une application installée. */
@Composable
fun AppIcon(pkg: String, size: Dp, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val bmp by produceState<ImageBitmap?>(initialValue = IconCache.peek(pkg), pkg) {
        if (value == null) {
            value = withContext(Dispatchers.IO) { IconCache.get(ctx, pkg) }
        }
    }
    val b = bmp
    Box(modifier.size(size).clip(RoundedCornerShape(size * 0.22f))) {
        if (b != null) {
            Image(bitmap = b, contentDescription = null, modifier = Modifier.fillMaxSize())
        }
    }
}

/** Carte arrondie translucide façon CarPlay. */
@Composable
fun CardShell(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val p = LocalPalette.current
    Box(
        modifier
            .clip(RoundedCornerShape(28.dp))
            .background(p.card)
            .padding(20.dp),
        content = content,
    )
}

/** Pastille de choix (remplace les chips pour rester sur des API stables). */
@Composable
fun Pill(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val p = LocalPalette.current
    val primary = MaterialTheme.colorScheme.primary
    val onPrimary = MaterialTheme.colorScheme.onPrimary
    Box(
        modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) primary else p.chip)
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            color = if (selected) onPrimary else p.text,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

/** Disque coloré sélectionnable. */
@Composable
fun Swatch(color: Color, selected: Boolean, onClick: () -> Unit, size: Dp = 44.dp) {
    val p = LocalPalette.current
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .background(if (selected) p.text else Color.Transparent)
            .padding(3.dp)
            .clip(CircleShape)
            .background(color)
            .clickable(onClick = onClick),
    )
}

/** Bouton rond avec une icône dessinée. */
@Composable
fun RoundButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 56.dp,
    filled: Boolean = false,
    content: @Composable BoxScope.() -> Unit,
) {
    val p = LocalPalette.current
    val primary = MaterialTheme.colorScheme.primary
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(if (filled) primary else p.chip)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
        content = content,
    )
}

// ---------------------------------------------------------------------------
// Pictogrammes dessinés à la main (évite la lourde bibliothèque d'icônes étendue)
// ---------------------------------------------------------------------------

@Composable
fun PlayGlyph(color: Color, size: Dp = 26.dp) {
    Canvas(Modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val path = Path().apply {
            moveTo(w * 0.22f, h * 0.10f)
            lineTo(w * 0.92f, h * 0.50f)
            lineTo(w * 0.22f, h * 0.90f)
            close()
        }
        drawPath(path, color)
    }
}

@Composable
fun PauseGlyph(color: Color, size: Dp = 26.dp) {
    Canvas(Modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val r = CornerRadius(w * 0.06f, w * 0.06f)
        drawRoundRect(color, Offset(w * 0.16f, h * 0.10f), Size(w * 0.24f, h * 0.80f), r)
        drawRoundRect(color, Offset(w * 0.60f, h * 0.10f), Size(w * 0.24f, h * 0.80f), r)
    }
}

@Composable
fun NextGlyph(color: Color, size: Dp = 26.dp) {
    Canvas(Modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val path = Path().apply {
            moveTo(w * 0.10f, h * 0.15f)
            lineTo(w * 0.70f, h * 0.50f)
            lineTo(w * 0.10f, h * 0.85f)
            close()
        }
        drawPath(path, color)
        drawRoundRect(
            color,
            Offset(w * 0.76f, h * 0.15f),
            Size(w * 0.14f, h * 0.70f),
            CornerRadius(w * 0.04f, w * 0.04f),
        )
    }
}

@Composable
fun PrevGlyph(color: Color, size: Dp = 26.dp) {
    Canvas(Modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val path = Path().apply {
            moveTo(w * 0.90f, h * 0.15f)
            lineTo(w * 0.30f, h * 0.50f)
            lineTo(w * 0.90f, h * 0.85f)
            close()
        }
        drawPath(path, color)
        drawRoundRect(
            color,
            Offset(w * 0.10f, h * 0.15f),
            Size(w * 0.14f, h * 0.70f),
            CornerRadius(w * 0.04f, w * 0.04f),
        )
    }
}

@Composable
fun AppsGlyph(color: Color, size: Dp = 28.dp) {
    Canvas(Modifier.size(size)) {
        val w = this.size.width
        val step = w / 3f
        val r = w * 0.075f
        for (row in 0..2) {
            for (col in 0..2) {
                drawCircle(color, r, Offset(step * (col + 0.5f), step * (row + 0.5f)))
            }
        }
    }
}

@Composable
fun MicGlyph(color: Color, size: Dp = 28.dp) {
    Canvas(Modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        drawRoundRect(
            color,
            Offset(w * 0.36f, h * 0.06f),
            Size(w * 0.28f, h * 0.52f),
            CornerRadius(w * 0.14f, w * 0.14f),
        )
        drawArc(
            color = color,
            startAngle = 0f,
            sweepAngle = 180f,
            useCenter = false,
            topLeft = Offset(w * 0.22f, h * 0.22f),
            size = Size(w * 0.56f, h * 0.50f),
            style = Stroke(width = w * 0.07f, cap = StrokeCap.Round),
        )
        drawLine(
            color,
            Offset(w * 0.5f, h * 0.72f),
            Offset(w * 0.5f, h * 0.92f),
            strokeWidth = w * 0.07f,
            cap = StrokeCap.Round,
        )
    }
}
