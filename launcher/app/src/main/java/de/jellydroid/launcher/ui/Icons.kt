package de.jellydroid.launcher.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun SearchIcon(color: Color, modifier: Modifier = Modifier) = Canvas(modifier.size(16.dp)) {
    val w = size.minDimension
    val stroke = w * .11f
    drawCircle(color, radius = w * .3f, center = Offset(w * .45f, w * .45f), style = Stroke(stroke))
    drawLine(color, Offset(w * .67f, w * .67f), Offset(w * .88f, w * .88f), stroke, StrokeCap.Round)
}

@Composable
fun GearIcon(color: Color, modifier: Modifier = Modifier) = Canvas(modifier.size(16.dp)) {
    val w = size.minDimension
    val c = Offset(w / 2, w / 2)
    val stroke = w * .1f
    drawCircle(color, radius = w * .27f, center = c, style = Stroke(stroke))
    drawCircle(color, radius = w * .1f, center = c, style = Stroke(stroke))
    for (i in 0 until 8) {
        val a = i * Math.PI / 4
        val inner = Offset(c.x + cos(a).toFloat() * w * .32f, c.y + sin(a).toFloat() * w * .32f)
        val outer = Offset(c.x + cos(a).toFloat() * w * .46f, c.y + sin(a).toFloat() * w * .46f)
        drawLine(color, inner, outer, stroke * 1.4f, StrokeCap.Round)
    }
}

@Composable
fun SourceIcon(color: Color, modifier: Modifier = Modifier) = Canvas(modifier.size(16.dp)) {
    val w = size.minDimension
    val stroke = w * .09f
    drawRoundRect(color, Offset(w * .08f, w * .14f), Size(w * .84f, w * .56f), CornerRadius(w * .09f), style = Stroke(stroke))
    drawLine(color, Offset(w * .5f, w * .7f), Offset(w * .5f, w * .86f), stroke, StrokeCap.Round)
    drawLine(color, Offset(w * .32f, w * .88f), Offset(w * .68f, w * .88f), stroke, StrokeCap.Round)
    drawLine(color, Offset(w * .44f, w * .32f), Offset(w * .58f, w * .42f), stroke, StrokeCap.Round)
    drawLine(color, Offset(w * .58f, w * .42f), Offset(w * .44f, w * .52f), stroke, StrokeCap.Round)
}

// ---- Sidebar ----

/** Hilfsfunktion: Pfad im 24er-Raster (wie die Symbole der Vorschau), Strich in Grid-Einheiten. */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.line24(color: Color, sw: Float, vararg pts: Float) {
    val k = size.minDimension / 24f
    val p = androidx.compose.ui.graphics.Path()
    p.moveTo(pts[0] * k, pts[1] * k)
    var i = 2
    while (i < pts.size) { p.lineTo(pts[i] * k, pts[i + 1] * k); i += 2 }
    drawPath(p, color, style = Stroke(sw * k, cap = StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round))
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.rect24(color: Color, sw: Float, x: Float, y: Float, w: Float, h: Float, r: Float) {
    val k = size.minDimension / 24f
    drawRoundRect(color, Offset(x * k, y * k), Size(w * k, h * k), CornerRadius(r * k), style = Stroke(sw * k))
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.circle24(color: Color, sw: Float, cx: Float, cy: Float, r: Float, fill: Boolean = false) {
    val k = size.minDimension / 24f
    if (fill) drawCircle(color, r * k, Offset(cx * k, cy * k)) else drawCircle(color, r * k, Offset(cx * k, cy * k), style = Stroke(sw * k))
}

@Composable
fun SidebarIcon(color: Color, modifier: Modifier = Modifier) = Canvas(modifier.size(16.dp)) {
    rect24(color, 1.9f, 3f, 4f, 18f, 16f, 3f)
    line24(color, 1.9f, 14.5f, 4f, 14.5f, 20f)
}

@Composable
fun HouseIcon(color: Color, modifier: Modifier = Modifier) = Canvas(modifier.size(16.dp)) {
    line24(color, 1.9f, 3f, 11f, 12f, 3.5f, 21f, 11f)
    line24(color, 1.9f, 5.5f, 9.5f, 5.5f, 20f, 18.5f, 20f, 18.5f, 9.5f)
    line24(color, 1.9f, 10f, 20f, 10f, 14f, 14f, 14f, 14f, 20f)
}

@Composable
fun WifiIcon(color: Color, modifier: Modifier = Modifier, level: Int = 4) = Canvas(modifier.size(16.dp)) {
    val k = size.minDimension / 24f
    val c = Offset(12f * k, 19f * k)
    listOf(4.5f, 9f, 13.5f).forEachIndexed { i, r ->
        val a = if (level >= i + 2) color else color.copy(alpha = color.alpha * .3f)
        drawArc(a, 225f, 90f, false, Offset(c.x - r * k, c.y - r * k), Size(2 * r * k, 2 * r * k), style = Stroke(1.9f * k, cap = StrokeCap.Round))
    }
    circle24(color, 0f, 12f, 19f, 1.3f, fill = true)
}

@Composable
fun AirPlayIcon(color: Color, modifier: Modifier = Modifier) = Canvas(modifier.size(16.dp)) {
    line24(color, 1.8f, 6f, 17f, 4f, 17f, 3f, 16f, 3f, 5f, 4f, 4f, 20f, 4f, 21f, 5f, 21f, 16f, 20f, 17f, 18f, 17f)
    line24(color, 1.8f, 12f, 14f, 17f, 20f, 7f, 20f, 12f, 14f)
}

@Composable
fun BackIcon(color: Color, modifier: Modifier = Modifier) = Canvas(modifier.size(16.dp)) {
    line24(color, 2.2f, 14.5f, 6f, 8.5f, 12f, 14.5f, 18f)
}

@Composable
fun ChevronDownIcon(color: Color, modifier: Modifier = Modifier) = Canvas(modifier.size(16.dp)) {
    line24(color, 2.4f, 6.5f, 9.5f, 12f, 15f, 17.5f, 9.5f)
}

@Composable
fun BulbIcon(color: Color, modifier: Modifier = Modifier) = Canvas(modifier.size(16.dp)) {
    line24(color, 1.8f, 9f, 18f, 15f, 18f)
    line24(color, 1.8f, 10f, 21.5f, 14f, 21.5f)
    circle24(color, 1.8f, 12f, 9f, 6.2f)
    line24(color, 1.8f, 9.3f, 14.5f, 9.3f, 18f)
    line24(color, 1.8f, 14.7f, 14.5f, 14.7f, 18f)
}

@Composable
fun PlugIcon(color: Color, modifier: Modifier = Modifier) = Canvas(modifier.size(16.dp)) {
    line24(color, 1.8f, 9f, 2f, 9f, 8f)
    line24(color, 1.8f, 15f, 2f, 15f, 8f)
    line24(color, 1.8f, 6f, 8f, 18f, 8f, 18f, 11f, 16.5f, 15f, 12f, 17f, 7.5f, 15f, 6f, 11f, 6f, 8f)
    line24(color, 1.8f, 12f, 17f, 12f, 22f)
}

@Composable
fun SceneIcon(color: Color, modifier: Modifier = Modifier) = Canvas(modifier.size(16.dp)) {
    // Stern/Funkeln für Szenen
    line24(color, 1.8f, 12f, 3f, 13.8f, 10.2f, 21f, 12f, 13.8f, 13.8f, 12f, 21f, 10.2f, 13.8f, 3f, 12f, 10.2f, 10.2f, 12f, 3f)
}

@Composable
fun ThermoIcon(color: Color, modifier: Modifier = Modifier) = Canvas(modifier.size(16.dp)) {
    line24(color, 1.8f, 10f, 14.5f, 10f, 5f, 11f, 3.2f, 12f, 3f, 13f, 3.2f, 14f, 5f, 14f, 14.5f)
    circle24(color, 1.8f, 12f, 17.5f, 3.6f)
    line24(color, 1.8f, 12f, 9f, 12f, 15f)
}

@Composable
fun BellIcon(color: Color, modifier: Modifier = Modifier) = Canvas(modifier.size(16.dp)) {
    line24(color, 2f, 6f, 16f, 6f, 10f, 7.5f, 6f, 12f, 4f, 16.5f, 6f, 18f, 10f, 18f, 16f, 20f, 18f, 4f, 18f, 6f, 16f)
    line24(color, 2f, 10f, 21f, 14f, 21f)
}

@Composable
fun SlidersIcon(color: Color, modifier: Modifier = Modifier) = Canvas(modifier.size(16.dp)) {
    line24(color, 1.8f, 4f, 6f, 7f, 6f); line24(color, 1.8f, 11f, 6f, 20f, 6f); circle24(color, 1.8f, 9f, 6f, 2f)
    line24(color, 1.8f, 4f, 12f, 13f, 12f); line24(color, 1.8f, 17f, 12f, 20f, 12f); circle24(color, 1.8f, 15f, 12f, 2f)
    line24(color, 1.8f, 4f, 18f, 5f, 18f); line24(color, 1.8f, 9f, 18f, 20f, 18f); circle24(color, 1.8f, 7f, 18f, 2f)
}

@Composable
fun TvIcon(color: Color, modifier: Modifier = Modifier) = Canvas(modifier.size(16.dp)) {
    rect24(color, 1.8f, 2f, 4f, 20f, 13f, 2f)
    line24(color, 1.8f, 8f, 21f, 16f, 21f)
    line24(color, 1.8f, 12f, 17f, 12f, 21f)
}

@Composable
fun CheckIcon(color: Color, modifier: Modifier = Modifier) = Canvas(modifier.size(16.dp)) {
    line24(color, 2.4f, 5f, 12.5f, 10f, 17.5f, 19f, 7f)
}

@Composable
fun CloseIcon(color: Color, modifier: Modifier = Modifier) = Canvas(modifier.size(16.dp)) {
    line24(color, 2.4f, 6.5f, 6.5f, 17.5f, 17.5f)
    line24(color, 2.4f, 17.5f, 6.5f, 6.5f, 17.5f)
}
