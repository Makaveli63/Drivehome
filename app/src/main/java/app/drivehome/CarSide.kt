package app.drivehome

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.lerp
import kotlin.math.cos
import kotlin.math.sin

/**
 * Passat Variant (B8) dessinée de profil, côté conducteur (avant à droite).
 * Portes conducteur, coffre et capot s'ouvrent sur le dessin ; les portes
 * côté passager sont signalées par un liseré orange sur la vitre.
 */
@Composable
fun SideCarView(
    doors: Doors,
    body: Color,
    modifier: Modifier = Modifier,
    warn: Color = Color(0xFFFF9F0A),
) {
    val spec = tween<Float>(650)
    val fl by animateFloatAsState(if (doors.fl) 1f else 0f, spec, label = "sfl")
    val rl by animateFloatAsState(if (doors.rl) 1f else 0f, spec, label = "srl")
    val fr by animateFloatAsState(if (doors.fr) 1f else 0f, spec, label = "sfr")
    val rr by animateFloatAsState(if (doors.rr) 1f else 0f, spec, label = "srr")
    val tr by animateFloatAsState(if (doors.trunk) 1f else 0f, spec, label = "str")
    val hd by animateFloatAsState(if (doors.hood) 1f else 0f, spec, label = "shd")

    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxWidth().aspectRatio(2.9f)) {
            // le dessin est fait avant à gauche puis retourné : avant à droite, côté conducteur visible
            scale(-1f, 1f, pivot = center) {
                drawSideCar(fl, rl, fr, rr, tr, hd, body, warn)
            }
        }
    }
}

private fun DrawScope.drawSideCar(
    fl: Float, rl: Float, fr: Float, rr: Float, tr: Float, hd: Float,
    body: Color, warn: Color,
) {
    val w = size.width
    val h = size.height
    // repère normalisé : x = 0 (avant) → 1 (arrière), y = 0 (toit) → 1 (sol)
    fun X(x: Float) = w * (0.01f + x * 0.98f)
    fun Y(y: Float) = h * (0.16f + y * 0.76f)
    fun P(x: Float, y: Float) = Offset(X(x), Y(y))
    fun poly(pts: List<Pair<Float, Float>>, close: Boolean = true) = Path().apply {
        pts.forEachIndexed { i, (x, y) -> if (i == 0) moveTo(X(x), Y(y)) else lineTo(X(x), Y(y)) }
        if (close) close()
    }
    fun Path.q(cx: Float, cy: Float, x: Float, y: Float) = quadraticBezierTo(X(cx), Y(cy), X(x), Y(y))

    val shade = lerp(body, Color.Black, 0.30f)
    val light = lerp(body, Color.White, 0.85f)
    val line = lerp(body, Color.Black, 0.45f)
    val glass = Color(0xFF121820)
    val glassHi = Color(0xFF465568)
    val chrome = Color(0xFFD4D8DE)
    val dark = Color(0xFF0D0F12)
    val warnA = warn.copy(alpha = 0.6f)
    val r = 0.25f * 0.76f * h
    val wheelY = Y(0.75f)
    val fx = 0.173f
    val rx = 0.78f
    val px = h * 0.0045f

    // ombre au sol
    drawOval(Color.Black.copy(alpha = 0.5f), topLeft = Offset(w * 0.03f, Y(1f) - h * 0.035f), size = Size(w * 0.94f, h * 0.07f))

    // carrosserie
    val bodyPath = Path().apply {
        moveTo(X(0.008f), Y(0.83f))
        lineTo(X(0f), Y(0.62f))
        q(0f, 0.50f, 0.03f, 0.44f)
        q(0.12f, 0.37f, 0.285f, 0.29f)
        q(0.36f, 0.10f, 0.45f, 0.03f)
        q(0.62f, -0.015f, 0.85f, 0f)
        q(0.92f, 0.02f, 0.94f, 0.10f)
        q(0.975f, 0.26f, 0.99f, 0.36f)
        q(1f, 0.55f, 0.995f, 0.80f)
        lineTo(X(rx + 0.095f), Y(0.84f))
        arcTo(androidx.compose.ui.geometry.Rect(Offset(X(rx), wheelY), r * 1.12f), 14.3f, -208.6f, false)
        lineTo(X(fx + 0.095f), Y(0.85f))
        arcTo(androidx.compose.ui.geometry.Rect(Offset(X(fx), wheelY), r * 1.12f), 14.3f, -208.6f, false)
        close()
    }
    drawPath(bodyPath, Brush.verticalGradient(listOf(light, body, shade), startY = Y(0f), endY = Y(0.85f)))
    drawPath(bodyPath, line, style = Stroke(px * 0.9f))

    // bas de caisse noir + jonc chromé
    drawLine(Color(0xFF2A2D33), P(0.27f, 0.86f), P(0.69f, 0.85f), h * 0.022f)
    drawLine(chrome, P(0.28f, 0.84f), P(0.68f, 0.835f), h * 0.007f, cap = StrokeCap.Round)
    // ligne d'épaule (reflet) et nervures
    drawLine(Color.White.copy(alpha = 0.95f), P(0.04f, 0.46f), P(0.975f, 0.37f), h * 0.011f, cap = StrokeCap.Round)
    drawLine(line.copy(alpha = 0.45f), P(0.05f, 0.49f), P(0.97f, 0.40f), px * 0.7f)
    drawLine(line.copy(alpha = 0.25f), P(0.26f, 0.66f), P(0.70f, 0.64f), px * 0.6f)

    // vitrage + entourage chromé
    val windows = Path().apply {
        moveTo(X(0.30f), Y(0.30f))
        q(0.37f, 0.12f, 0.465f, 0.055f)
        q(0.66f, 0.035f, 0.875f, 0.06f)
        q(0.915f, 0.08f, 0.935f, 0.19f)
        lineTo(X(0.925f), Y(0.225f))
        lineTo(X(0.31f), Y(0.315f))
        close()
    }
    drawPath(windows, Brush.verticalGradient(listOf(glassHi, glass), startY = Y(0.04f), endY = Y(0.31f)))
    drawPath(windows, chrome, style = Stroke(h * 0.011f))
    drawLine(dark, P(0.545f, 0.04f), P(0.548f, 0.30f), w * 0.012f)
    drawLine(dark, P(0.71f, 0.045f), P(0.725f, 0.26f), w * 0.009f)
    drawLine(Color.White.copy(alpha = 0.10f), P(0.47f, 0.08f), P(0.53f, 0.28f), w * 0.03f)
    // vitres arrière surteintées
    val privacy = Path().apply {
        moveTo(X(0.552f), Y(0.045f))
        q(0.75f, 0.045f, 0.875f, 0.06f)
        q(0.915f, 0.08f, 0.935f, 0.19f)
        lineTo(X(0.925f), Y(0.225f))
        lineTo(X(0.552f), Y(0.30f))
        close()
    }
    drawPath(privacy, Color.Black.copy(alpha = 0.45f))
    // antenne « aileron de requin »
    drawPath(poly(listOf(0.79f to -0.002f, 0.815f to -0.055f, 0.84f to 0.0f)), Color(0xFF1C1F24))
    // attelage
    drawLine(Color(0xFF1C1F24), P(0.99f, 0.86f), P(1.012f, 0.87f), h * 0.018f, cap = StrokeCap.Round)
    drawCircle(Color(0xFF2A2D33), h * 0.016f, P(1.015f, 0.85f))
    // côté passager : liseré orange sous la vitre correspondante
    if (fr > 0.01f) drawLine(warn.copy(alpha = fr), P(0.33f, 0.335f), P(0.54f, 0.305f), h * 0.02f, cap = StrokeCap.Round)
    if (rr > 0.01f) drawLine(warn.copy(alpha = rr), P(0.555f, 0.30f), P(0.72f, 0.275f), h * 0.02f, cap = StrokeCap.Round)

    // rétroviseur
    val mirror = Path().apply {
        moveTo(X(0.305f), Y(0.31f))
        q(0.31f, 0.20f, 0.36f, 0.205f)
        lineTo(X(0.37f), Y(0.30f))
        close()
    }
    drawPath(mirror, Color(0xFFE9EBEE))
    drawPath(mirror, line, style = Stroke(px * 0.7f))

    // portes côté conducteur : contour, poignée, ouverture pivotante
    val frontDoor = listOf(0.29f to 0.31f, 0.30f to 0.30f, 0.37f to 0.12f, 0.465f to 0.055f, 0.545f to 0.04f, 0.548f to 0.84f, 0.27f to 0.85f, 0.268f to 0.62f)
    val rearDoor = listOf(0.548f to 0.04f, 0.71f to 0.045f, 0.725f to 0.26f, 0.73f to 0.48f, 0.70f to 0.62f, 0.69f to 0.84f, 0.548f to 0.84f)
    fun door(open: Float, pts: List<Pair<Float, Float>>, hinge: Float, handleX: Float, handleY: Float) {
        if (open < 0.01f) {
            drawPath(poly(pts, close = false), line, style = Stroke(px * 0.75f))
            drawRoundRect(Color(0xFFCFD2D6), P(handleX, handleY), Size(w * 0.034f, h * 0.02f), CornerRadius(h * 0.01f))
            return
        }
        drawPath(poly(pts), dark.copy(alpha = 0.92f * open))
        drawRoundRect(Color(0xFF2A2E35).copy(alpha = open), P(pts[0].first + 0.06f, 0.28f), Size(w * 0.09f, h * 0.34f), CornerRadius(h * 0.03f))
        val k = 1f - 0.5f * open
        val swung = pts.map { (x, y) -> (hinge + (x - hinge) * k - 0.02f * open) to y }
        val panel = poly(swung)
        drawPath(panel, warnA, style = Stroke(h * 0.028f))
        drawPath(panel, Brush.horizontalGradient(listOf(shade, light), startX = X(hinge - 0.03f), endX = X(swung[4].first)))
        drawPath(panel, line, style = Stroke(px * 0.75f))
    }
    door(fl, frontDoor, 0.27f, 0.47f, 0.37f)
    door(rl, rearDoor, 0.548f, 0.665f, 0.355f)

    // capot entrouvert
    if (hd > 0.01f) {
        val hood = poly(listOf(0.285f to 0.29f, 0.12f to 0.37f, 0.03f to 0.44f, 0.05f to 0.46f, 0.285f to 0.32f))
        drawPath(poly(listOf(0.285f to 0.29f, 0.03f to 0.44f, 0.285f to 0.44f)), dark.copy(alpha = hd))
        rotate(degrees = 18f * hd, pivot = P(0.285f, 0.30f)) {
            drawPath(hood, warnA, style = Stroke(h * 0.028f))
            drawPath(hood, light)
            drawPath(hood, line, style = Stroke(px * 0.75f))
        }
    }

    // hayon ouvert : pivote vers le haut autour de la charnière de toit
    if (tr > 0.01f) {
        drawPath(poly(listOf(0.86f to 0.03f, 0.93f to 0.10f, 0.975f to 0.30f, 0.985f to 0.50f, 0.88f to 0.50f)), dark.copy(alpha = tr))
        val gate = poly(listOf(0.85f to 0f, 0.92f to 0.02f, 0.94f to 0.10f, 0.975f to 0.26f, 0.99f to 0.36f, 0.995f to 0.52f, 0.965f to 0.52f, 0.93f to 0.22f, 0.86f to 0.04f))
        rotate(degrees = -40f * tr, pivot = P(0.85f, 0f)) {
            drawPath(gate, warnA, style = Stroke(h * 0.028f))
            drawPath(gate, Color(0xFFF6F7F9))
            drawPath(gate, line, style = Stroke(px * 0.75f))
        }
    }

    // optiques B8 : phare effilé relié à la calandre chromée, feux arrière en deux parties
    val headlight = poly(listOf(0.008f to 0.47f, 0.02f to 0.43f, 0.13f to 0.385f, 0.14f to 0.40f, 0.06f to 0.46f))
    drawPath(headlight, Brush.horizontalGradient(listOf(Color(0xFF9AA6B4), Color(0xFFEAF3FF)), startX = X(0f), endX = X(0.14f)))
    drawPath(headlight, Color(0xFF555B64), style = Stroke(px * 0.6f))
    drawLine(Color.White, P(0.03f, 0.445f), P(0.12f, 0.395f), h * 0.008f, cap = StrokeCap.Round) // feux de jour LED
    drawLine(chrome, P(0.0f, 0.50f), P(0.02f, 0.49f), h * 0.012f, cap = StrokeCap.Round) // calandre
    drawLine(chrome, P(0.0f, 0.55f), P(0.015f, 0.545f), h * 0.008f, cap = StrokeCap.Round)
    // antibrouillard
    drawRoundRect(Color(0xFF2A2D33), P(0.008f, 0.70f), Size(w * 0.03f, h * 0.035f), CornerRadius(h * 0.01f))
    // feu arrière : partie aile + partie hayon
    val tailBody = poly(listOf(0.95f to 0.31f, 0.993f to 0.33f, 0.996f to 0.40f, 0.955f to 0.375f))
    drawPath(tailBody, Color(0xFFB80F18))
    drawLine(Color(0xFFFF5A5A), P(0.958f, 0.335f), P(0.99f, 0.35f), h * 0.01f, cap = StrokeCap.Round)
    drawLine(Color(0xFF8E0B12), P(0.93f, 0.31f), P(0.95f, 0.315f), h * 0.03f, cap = StrokeCap.Round)
    // poignée de hayon / emplacement de plaque
    drawLine(chrome, P(0.965f, 0.45f), P(0.99f, 0.46f), h * 0.008f, cap = StrokeCap.Round)
    // barres de toit
    drawLine(Color(0xFFBFC4CB), P(0.50f, -0.005f), P(0.86f, 0.005f), h * 0.018f, cap = StrokeCap.Round)

    // roues : pneus + jantes alu 10 branches (5 doubles branches)
    fun wheel(cx: Float) {
        val c = Offset(X(cx), wheelY)
        drawCircle(Color(0xFF16181C), r, c)
        drawCircle(Color(0xFF8E939A), r * 0.73f, c)
        drawCircle(Color(0xFF2A2D33), r * 0.68f, c)
        for (k in 0 until 5) {
            for (d in listOf(-10.0, 10.0)) {
                val a = Math.toRadians(k * 72.0 - 90.0 + d)
                val e = Offset(c.x + (cos(a) * r * 0.69f).toFloat(), c.y + (sin(a) * r * 0.69f).toFloat())
                val m = Offset(c.x + (cos(a - Math.toRadians(d * 0.8)) * r * 0.22f).toFloat(), c.y + (sin(a - Math.toRadians(d * 0.8)) * r * 0.22f).toFloat())
                drawLine(Color(0xFFD9DCE0), m, e, r * 0.10f, cap = StrokeCap.Round)
                drawLine(Color.White.copy(alpha = 0.6f), m, e, r * 0.035f, cap = StrokeCap.Round)
            }
        }
        drawCircle(Brush.radialGradient(listOf(Color(0xFFE9ECEF), Color(0xFF9EA3AA)), c, r * 0.25f), r * 0.24f, c)
        drawCircle(Color(0xFF3A3E45), r * 0.11f, c)
        drawCircle(Color(0xFFBFC3C9), r * 0.73f, c, style = Stroke(r * 0.05f))
    }
    wheel(fx)
    wheel(rx)
}
