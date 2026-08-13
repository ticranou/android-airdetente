package com.airchecklists.app.ui.terrain.weather

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.airchecklists.app.data.model.CloudLayer

/**
 * Vertical cloud-layers chart (altitude grid + cloud icons + cover/base badges),
 * reproducing metar-taf-card's `renderClouds`. Ceiling layer is highlighted.
 */
@Composable
fun CloudChart(
    clouds: List<CloudLayer>,
    ceilingFt: Int?,
    modifier: Modifier = Modifier,
    grid: Color = Color(0xFF90A4AE),
    cloudColor: Color = Color(0xFFB0BEC5),
    badgeLayer: Color = Color(0xFF546E7A),
    badgeCeil: Color = Color(0xFFC62828),
    onBadge: Color = Color.White,
) {
    val tm = rememberTextMeasurer()
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(220.dp),
    ) {
        val w = size.width
        val h = size.height
        val topFt = 12000f
        fun yOf(ft: Float): Float = h - 20f - (ft / topFt) * (h - 30f)

        // Altitude grid lines + labels.
        listOf(0, 2000, 4000, 6000, 8000, 10000).forEach { v ->
            val y = yOf(v.toFloat())
            drawLine(grid.copy(alpha = 0.3f), Offset(0f, y), Offset(w, y), strokeWidth = 1f)
            val m = tm.measure("$v ft", TextStyle(color = grid, fontSize = 10.sp))
            drawText(m, topLeft = Offset(6f, y - m.size.height - 2f))
        }

        // Cloud layers.
        clouds.filter { it.baseFt != null }.forEachIndexed { i, layer ->
            val base = layer.baseFt!!.toFloat()
            val y = yOf(base)
            val isCeil = ceilingFt != null && layer.baseFt == ceilingFt
            val count = when (layer.cover) {
                "FEW" -> 2; "SCT" -> 4; "BKN" -> 6; else -> 8
            }
            for (n in 0 until count) {
                val x = 60f + (n + 0.5f) * ((w - 200f) / count) + (if (i % 2 == 0) 0f else 12f)
                drawCloudIcon(x, y, cloudColor)
            }
            // Badge (cover + base) on the right.
            val badgeW = 92f
            val badgeH = 22f
            val bx = w - badgeW - 8f
            drawRoundRect(
                color = if (isCeil) badgeCeil else badgeLayer,
                topLeft = Offset(bx, y - badgeH / 2),
                size = Size(badgeW, badgeH),
                cornerRadius = CornerRadius(4f, 4f),
            )
            val label = "${layer.cover} ${layer.baseFt}"
            val m = tm.measure(label, TextStyle(color = onBadge, fontSize = 11.sp,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold))
            drawText(m, topLeft = Offset(bx + (badgeW - m.size.width) / 2, y - m.size.height / 2f))
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawCloudIcon(cx: Float, cy: Float, color: Color) {
    // Three overlapping ellipses, like the SVG cloud glyph.
    drawOval(color, topLeft = Offset(cx - 22f, cy - 7f), size = Size(28f, 18f))
    drawOval(color, topLeft = Offset(cx - 6f, cy - 10f), size = Size(22f, 14f))
    drawOval(color, topLeft = Offset(cx + 6f, cy - 5f), size = Size(20f, 14f))
}
