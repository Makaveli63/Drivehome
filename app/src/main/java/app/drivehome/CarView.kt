package app.drivehome

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

private class DoorAnim(
    val fl: Float,
    val fr: Float,
    val rl: Float,
    val rr: Float,
    val trunk: Float,
    val hood: Float,
)

/**
 * Voiture break dessinée en perspective, vue de 3/4 dessus.
 * Glisser horizontalement fait tourner la voiture sur elle-même.
 */
@Composable
fun CarView(
    doors: Doors,
    body: Color,
    modifier: Modifier = Modifier,
    tilt: Float = 50f,
    initialSpin: Float = -30f,
    rotatable: Boolean = true,
    warn: Color = Color(0xFFFF9F0A),
) {
    fun anim(open: Boolean) = if (open) 1f else 0f
    val spec = tween<Float>(600)
    val fl by animateFloatAsState(anim(doors.fl), spec, label = "fl")
    val fr by animateFloatAsState(anim(doors.fr), spec, label = "fr")
    val rl by animateFloatAsState(anim(doors.rl), spec, label = "rl")
    val rr by animateFloatAsState(anim(doors.rr), spec, label = "rr")
    val tr by animateFloatAsState(anim(doors.trunk), spec, label = "trunk")
    val hd by animateFloatAsState(anim(doors.hood), spec, label = "hood")
    var spin by remember { mutableFloatStateOf(initialSpin) }

    Box(
        modifier.pointerInput(rotatable) {
            if (rotatable) detectDragGestures { _, d -> spin += d.x * 0.35f }
        },
        contentAlignment = Alignment.Center,
    ) {
        // couche externe : inclinaison 3D ; couche interne : rotation sur le plan du sol
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    rotationX = tilt
                    cameraDistance = 14f * density
                },
        ) {
            Canvas(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer { rotationZ = spin },
            ) {
                drawCar(DoorAnim(fl, fr, rl, rr, tr, hd), body, warn)
            }
        }
    }
}

private fun DrawScope.drawCar(a: DoorAnim, body: Color, warn: Color) {
    val cl = min(size.width, size.height) * 0.84f
    val cw = cl * 0.41f
    val left = center.x - cw / 2
    val top = center.y - cl / 2
    val right = left + cw
    val bottom = top + cl
    fun y(f: Float) = top + cl * f
    fun x(f: Float) = left + cw * f

    val shade = lerp(body, Color.Black, 0.22f)
    val light = lerp(body, Color.White, 0.35f)
    val line = lerp(body, Color.Black, 0.40f)
    val dark = Color(0xFF111317)
    val glassA = Color(0xFF16202B)
    val glassB = Color(0xFF3A4B60)

    // ombre au sol
    drawOval(
        Color.Black.copy(alpha = 0.35f),
        topLeft = Offset(left - cw * 0.16f, top - cl * 0.02f),
        size = Size(cw * 1.32f, cl * 1.06f),
    )

    // roues
    for (wy in listOf(0.17f, 0.79f)) {
        drawRoundRect(dark, Offset(left - cw * 0.035f, y(wy - 0.06f)), Size(cw * 0.14f, cl * 0.12f), CornerRadius(cw * 0.04f))
        drawRoundRect(dark, Offset(right - cw * 0.105f, y(wy - 0.06f)), Size(cw * 0.14f, cl * 0.12f), CornerRadius(cw * 0.04f))
    }

    // hayon ouvert (derrière la voiture)
    if (a.trunk > 0.01f) {
        val ext = cl * 0.13f * a.trunk
        val tl = Offset(x(0.10f), bottom - cl * 0.02f)
        drawRoundRect(warn.copy(alpha = 0.45f), tl - Offset(cw * 0.03f, 0f), Size(cw * 0.86f, ext + cl * 0.03f), CornerRadius(cw * 0.06f))
        drawRoundRect(body, tl, Size(cw * 0.80f, ext), CornerRadius(cw * 0.05f))
        drawRoundRect(Brush.verticalGradient(listOf(glassB, glassA)), tl + Offset(cw * 0.08f, ext * 0.15f), Size(cw * 0.64f, ext * 0.55f), CornerRadius(cw * 0.03f))
        drawRoundRect(line, tl, Size(cw * 0.80f, ext), CornerRadius(cw * 0.05f), style = Stroke(cw * 0.01f))
    }

    // rétroviseurs
    drawOval(shade, Offset(left - cw * 0.10f, y(0.30f)), Size(cw * 0.13f, cl * 0.035f))
    drawOval(shade, Offset(right - cw * 0.03f, y(0.30f)), Size(cw * 0.13f, cl * 0.035f))

    // carrosserie
    val bodyPath = Path().apply {
        addRoundRect(
            RoundRect(
                left, top, right, bottom,
                topLeftCornerRadius = CornerRadius(cw * 0.30f, cl * 0.09f),
                topRightCornerRadius = CornerRadius(cw * 0.30f, cl * 0.09f),
                bottomRightCornerRadius = CornerRadius(cw * 0.15f, cl * 0.035f),
                bottomLeftCornerRadius = CornerRadius(cw * 0.15f, cl * 0.035f),
            ),
        )
    }
    drawPath(bodyPath, Brush.horizontalGradient(listOf(shade, light, body, light, shade), startX = left, endX = right))
    drawPath(bodyPath, line, style = Stroke(cw * 0.012f))

    // phares et feux
    drawRoundRect(Color(0xFFDDE8F5), Offset(x(0.07f), y(0.012f)), Size(cw * 0.24f, cl * 0.022f), CornerRadius(cw * 0.02f))
    drawRoundRect(Color(0xFFDDE8F5), Offset(x(0.69f), y(0.012f)), Size(cw * 0.24f, cl * 0.022f), CornerRadius(cw * 0.02f))
    drawRoundRect(Color(0xFFD0141E), Offset(x(0.05f), y(0.972f)), Size(cw * 0.26f, cl * 0.02f), CornerRadius(cw * 0.02f))
    drawRoundRect(Color(0xFFD0141E), Offset(x(0.69f), y(0.972f)), Size(cw * 0.26f, cl * 0.02f), CornerRadius(cw * 0.02f))

    // capot : nervures, ou compartiment moteur si ouvert
    if (a.hood > 0.01f) {
        drawRoundRect(dark.copy(alpha = a.hood), Offset(x(0.10f), y(0.04f)), Size(cw * 0.80f, cl * 0.22f), CornerRadius(cw * 0.06f))
        val h = cl * 0.23f * (1f - 0.55f * a.hood)
        val hoodTop = y(0.265f) - h
        drawRoundRect(warn.copy(alpha = 0.45f), Offset(x(0.06f), hoodTop - cl * 0.01f), Size(cw * 0.88f, h + cl * 0.02f), CornerRadius(cw * 0.08f))
        drawRoundRect(light, Offset(x(0.08f), hoodTop), Size(cw * 0.84f, h), CornerRadius(cw * 0.07f))
        drawRoundRect(line, Offset(x(0.08f), hoodTop), Size(cw * 0.84f, h), CornerRadius(cw * 0.07f), style = Stroke(cw * 0.01f))
    } else {
        drawLine(line.copy(alpha = 0.5f), Offset(x(0.30f), y(0.04f)), Offset(x(0.27f), y(0.26f)), cw * 0.008f)
        drawLine(line.copy(alpha = 0.5f), Offset(x(0.70f), y(0.04f)), Offset(x(0.73f), y(0.26f)), cw * 0.008f)
    }

    // pare-brise
    val wind = Path().apply {
        moveTo(x(0.20f), y(0.265f))
        lineTo(x(0.80f), y(0.265f))
        lineTo(x(0.89f), y(0.37f))
        lineTo(x(0.11f), y(0.37f))
        close()
    }
    drawPath(wind, Brush.verticalGradient(listOf(glassB, glassA), startY = y(0.265f), endY = y(0.37f)))

    // vitres latérales
    drawRect(glassA, Offset(x(0.05f), y(0.375f)), Size(cw * 0.075f, cl * 0.42f))
    drawRect(glassA, Offset(x(0.875f), y(0.375f)), Size(cw * 0.075f, cl * 0.42f))

    // toit long de break + barres de toit
    drawRoundRect(light, Offset(x(0.13f), y(0.37f)), Size(cw * 0.74f, cl * 0.45f), CornerRadius(cw * 0.05f))
    drawLine(Color(0xFF9AA0A8), Offset(x(0.17f), y(0.39f)), Offset(x(0.17f), y(0.80f)), cw * 0.03f, cap = StrokeCap.Round)
    drawLine(Color(0xFF9AA0A8), Offset(x(0.83f), y(0.39f)), Offset(x(0.83f), y(0.80f)), cw * 0.03f, cap = StrokeCap.Round)

    // lunette arrière (ou coffre béant si ouvert)
    val rear = Path().apply {
        moveTo(x(0.13f), y(0.82f))
        lineTo(x(0.87f), y(0.82f))
        lineTo(x(0.84f), y(0.895f))
        lineTo(x(0.16f), y(0.895f))
        close()
    }
    drawPath(rear, Brush.verticalGradient(listOf(glassA, glassB), startY = y(0.82f), endY = y(0.895f)))
    if (a.trunk > 0.01f) {
        drawRect(dark.copy(alpha = 0.9f * a.trunk), Offset(x(0.12f), y(0.83f)), Size(cw * 0.76f, cl * 0.15f))
    }

    // lignes de portes
    for (fy in listOf(0.36f, 0.565f, 0.775f)) {
        drawLine(line, Offset(left + cw * 0.01f, y(fy)), Offset(x(0.13f), y(fy)), cw * 0.008f)
        drawLine(line, Offset(x(0.87f), y(fy)), Offset(right - cw * 0.01f, y(fy)), cw * 0.008f)
    }

    // portes ouvertes : panneau pivotant sur sa charnière avant
    fun door(open: Float, side: Int, hingeF: Float, lenF: Float) {
        if (open < 0.01f) return
        val ang = Math.toRadians(65.0 * open)
        val hinge = Offset(if (side < 0) left + cw * 0.02f else right - cw * 0.02f, y(hingeF))
        val len = cl * lenF
        val end = Offset(hinge.x + side * (sin(ang) * len).toFloat(), hinge.y + (cos(ang) * len).toFloat())
        // intérieur visible (trou dans la caisse)
        drawRect(
            dark.copy(alpha = 0.85f * open),
            Offset(if (side < 0) left else x(0.87f), y(hingeF)),
            Size(cw * 0.13f, cl * lenF),
        )
        drawLine(warn.copy(alpha = 0.55f), hinge, end, cw * 0.11f, cap = StrokeCap.Round)
        drawLine(line, hinge, end, cw * 0.075f, cap = StrokeCap.Round)
        drawLine(body, hinge, end, cw * 0.055f, cap = StrokeCap.Round)
        // vitre de la porte
        val mid = Offset((hinge.x + end.x) / 2, (hinge.y + end.y) / 2)
        drawLine(glassB, Offset((hinge.x + mid.x) / 2, (hinge.y + mid.y) / 2), end, cw * 0.02f, cap = StrokeCap.Round)
    }
    door(a.fl, -1, 0.36f, 0.20f)
    door(a.fr, 1, 0.36f, 0.20f)
    door(a.rl, -1, 0.565f, 0.20f)
    door(a.rr, 1, 0.565f, 0.20f)
}
