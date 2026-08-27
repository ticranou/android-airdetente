package com.airchecklists.app.ui.efis.gauges

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.text.rememberTextMeasurer

/** Round artificial horizon: blue sky / brown ground rotating with roll and
 *  shifting with pitch, fixed aircraft symbol + roll index. */
@Composable
fun AttitudeGauge(pitchDeg: Float, rollDeg: Float, modifier: Modifier = Modifier) {
    val tm = rememberTextMeasurer()
    val bezel = LocalGaugeBezel.current
    Canvas(modifier = modifier.fillMaxSize()) {
        val (cx, cy, r) = gaugeFace(bezel)
        val pxPerDeg = r / 30f

        val circle = Path().apply { addOval(androidx.compose.ui.geometry.Rect(cx - r, cy - r, cx + r, cy + r)) }
        clipPath(circle) {
            rotate(degrees = -rollDeg, pivot = Offset(cx, cy)) {
                translate(top = pitchDeg * pxPerDeg) {
                    val big = r * 3f
                    drawRect(
                        brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                            0f to androidx.compose.ui.graphics.Color(0xFF8FD0FF),
                            1f to androidx.compose.ui.graphics.Color(0xFF0E4C7A),
                            startY = cy - big, endY = cy,
                        ),
                        topLeft = Offset(cx - big, cy - big), size = Size(big * 2, big),
                    )
                    drawRect(
                        brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                            0f to com.airchecklists.app.ui.efis.gauges.compact.CompactStyle.GroundMapTop,
                            1f to com.airchecklists.app.ui.efis.gauges.compact.CompactStyle.GroundMapBot,
                            startY = cy, endY = cy + big,
                        ),
                        topLeft = Offset(cx - big, cy), size = Size(big * 2, big),
                    )
                    drawLine(GaugeColors.Mark, Offset(cx - big, cy), Offset(cx + big, cy), strokeWidth = 3f)
                    // Pitch ladder ±20 with intermediate 5/15 marks — widths differ:
                    // minor (5,15) short, 10 medium, 20 long.
                    for (d in intArrayOf(-20, -15, -10, -5, 5, 10, 15, 20)) {
                        val y = cy - d * pxPerDeg
                        val halfFrac = when (kotlin.math.abs(d)) {
                            20 -> 0.30f
                            10 -> 0.22f
                            else -> 0.13f  // 5 and 15
                        }
                        val half = r * halfFrac
                        drawLine(GaugeColors.Mark, Offset(cx - half, y), Offset(cx + half, y), strokeWidth = 2.5f)
                        if (d % 10 == 0) {
                            val label = kotlin.math.abs(d).toString()
                            gaugeText(tm, label, cx - half - 34f, y, sizeSp = 15f, bold = true)
                            gaugeText(tm, label, cx + half + 34f, y, sizeSp = 15f, bold = true)
                        }
                    }
                }
            }
            // Roll index ticks (fixed) at top.
            for (b in intArrayOf(-60, -30, -20, -10, 0, 10, 20, 30, 60)) {
                val deg = b.toFloat()
                val outer = r * 0.98f
                val inner = r * (if (b % 30 == 0 || b == 0) 0.86f else 0.91f)
                drawLine(GaugeColors.Mark, polar(cx, cy, inner, deg), polar(cx, cy, outer, deg), strokeWidth = 2f)
            }
            // Roll pointer (moves with roll) — larger triangle.
            rotate(degrees = rollDeg, pivot = Offset(cx, cy)) {
                val tip = polar(cx, cy, r * 0.84f, 0f)
                val p = Path().apply {
                    moveTo(tip.x, tip.y)
                    lineTo(tip.x - 16f, tip.y - 26f)
                    lineTo(tip.x + 16f, tip.y - 26f)
                    close()
                }
                drawPath(p, GaugeColors.Accent)
            }
        }

        // Fixed aircraft symbol — same graphic as the slip indicator (ANLSLP) /
        // NUMHRZ: a wing bar, a vertical fin, and a centre dot.
        val wing = r * 0.62f
        val stroke = 6f
        drawLine(GaugeColors.Accent, Offset(cx - wing, cy), Offset(cx + wing, cy), strokeWidth = stroke)
        drawLine(GaugeColors.Accent, Offset(cx, cy - r * 0.18f), Offset(cx, cy + r * 0.10f), strokeWidth = stroke)
        drawCircle(GaugeColors.Accent, radius = r * 0.09f, center = Offset(cx, cy))
    }
}
