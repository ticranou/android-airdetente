package com.airchecklists.app.ui.efis.gauges

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.rememberTextMeasurer

/** Turn coordinator: an aircraft silhouette that banks with roll, plus an
 *  inclinometer ball at the bottom driven by lateral slip. */
@Composable
fun BallGauge(rollDeg: Float, slip: Float, modifier: Modifier = Modifier) {
    val tm = rememberTextMeasurer()
    val bezel = LocalGaugeBezel.current
    Canvas(modifier = modifier.fillMaxSize()) {
        val (cx, cy, r) = gaugeFace(bezel)

        gaugeText(tm, "L", cx - r * 0.6f, cy - r * 0.05f, sizeSp = 14f, bold = true)
        gaugeText(tm, "R", cx + r * 0.6f, cy - r * 0.05f, sizeSp = 14f, bold = true)
        gaugeText(tm, "2 MIN", cx, cy + r * 0.30f, sizeSp = 9f, color = GaugeColors.MarkDim)

        // Banking aircraft silhouette (clamped bank for readability).
        val bank = rollDeg.coerceIn(-30f, 30f)
        rotate(degrees = bank, pivot = Offset(cx, cy - r * 0.15f)) {
            val ay = cy - r * 0.15f
            val wing = r * 0.62f
            drawLine(GaugeColors.Mark, Offset(cx - wing, ay), Offset(cx + wing, ay), strokeWidth = 6f)
            drawLine(GaugeColors.Mark, Offset(cx, ay - r * 0.18f), Offset(cx, ay + r * 0.10f), strokeWidth = 6f)
            drawCircle(GaugeColors.Mark, radius = r * 0.09f, center = Offset(cx, ay))
        }

        // Inclinometer tube + ball near the bottom — wider zone & bigger ball.
        val tubeY = cy + r * 0.55f
        val travel = r * 0.62f
        val ballR = r * 0.13f
        // Tube outline for a clearer zone.
        drawRoundRect(
            color = Color(0xFF262626),
            topLeft = Offset(cx - travel - ballR - 4f, tubeY - ballR - 4f),
            size = androidx.compose.ui.geometry.Size((travel + ballR + 4f) * 2f, (ballR + 4f) * 2f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(ballR + 4f, ballR + 4f),
        )
        // Reference marks.
        listOf(-ballR - 4f, ballR + 4f).forEach { dx ->
            drawLine(GaugeColors.Mark, Offset(cx + dx, tubeY - ballR - 6f), Offset(cx + dx, tubeY + ballR + 6f), strokeWidth = 3f)
        }
        val ballX = cx + slip.coerceIn(-1f, 1f) * travel
        drawCircle(GaugeColors.Mark, radius = ballR, center = Offset(ballX, tubeY))
    }
}
