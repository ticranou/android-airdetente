package com.airchecklists.app.ui.efis.gauges.compact

import com.airchecklists.app.ui.efis.gauges.drawNumTitleBar
import com.airchecklists.app.ui.efis.gauges.LocalGaugeBezel
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.airchecklists.app.data.model.EfisSpeedUnit
import com.airchecklists.app.data.model.SpeedArcs
import com.airchecklists.app.data.sensors.EfisState
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Simplified HORIZON (double height, half width): artificial horizon (sky/ground +
 * pitch ladder + aircraft symbol) with a top roll arc + cursor showing bank angle,
 * plus altitude (bottom-left) and vario (bottom-right) overlaid on the horizon,
 * each with a title and unit. No heading/speed/ball (that's the full EFIS block).
 */
@Composable
fun AttitudeCompact(
    state: EfisState,
    unit: EfisSpeedUnit,
    showValue: Boolean,
    arcs: SpeedArcs?,
    altUnit: com.airchecklists.app.data.model.AltitudeUnit = com.airchecklists.app.data.model.AltitudeUnit.FEET,
    modifier: Modifier = Modifier,
) {
    val tm = rememberTextMeasurer()
    val bezel = LocalGaugeBezel.current
    Canvas(modifier = modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val headerH = 20.dp.toPx().coerceAtMost(h * 0.2f)
        // Panel background + header + border.
        drawRect(CompactStyle.Bg, size = size)
        drawNumTitleBar(bezel, w, headerH)
        drawRect(Color(0xFF3A3A3A), size = size, style = Stroke(width = 2f))
        compactText(tm, "HORIZON", w / 2f, headerH / 2f, sizeSp = 12f, color = CompactStyle.Dim)

        val horizon = Rect(2f, headerH, w - 2f, h - 2f)
        drawHorizon(tm, horizon, state.pitchDeg, state.rollDeg)
        drawRollArc(horizon, state.rollDeg)

        // Altitude (bottom-left) and vario (bottom-right), overlaid on the horizon.
        if (showValue) {
            val trendColor = if (state.verticalSpeedFtMin >= 0f) CompactStyle.Climb else CompactStyle.Descent
            val meters = altUnit == com.airchecklists.app.data.model.AltitudeUnit.METERS
            val altText = com.airchecklists.app.data.model.AltitudeFormat.altValue(state.gpsAltitudeFt, altUnit).roundToInt().toString()
            val varText = if (meters) "%.1f".format(com.airchecklists.app.data.model.AltitudeFormat.vsValue(state.verticalSpeedFtMin, altUnit))
                else state.verticalSpeedFtMin.roundToInt().toString()
            cornerValue(tm, horizon, left = true, title = "ALTI", value = altText,
                unit = com.airchecklists.app.data.model.AltitudeFormat.altLabel(altUnit).uppercase(), valueColor = CompactStyle.Mark)
            cornerValue(tm, horizon, left = false, title = "VARIO", value = varText,
                unit = com.airchecklists.app.data.model.AltitudeFormat.vsLabel(altUnit).uppercase(), valueColor = trendColor)
        }
    }
}

private fun DrawScope.drawHorizon(tm: TextMeasurer, r: Rect, pitch: Float, roll: Float) {
    val cx = r.center.x
    val cy = r.center.y
    val pxPerDeg = r.height / 45f
    clipRect(r.left, r.top, r.right, r.bottom) {
        rotate(degrees = -roll, pivot = Offset(cx, cy)) {
            translate(top = pitch * pxPerDeg) {
                val big = maxOf(r.width, r.height) * 3f
                drawRect(
                    brush = Brush.verticalGradient(
                        0f to Color(0xFF8FD0FF), 1f to Color(0xFF0E4C7A),
                        startY = cy - big, endY = cy,
                    ),
                    topLeft = Offset(cx - big, cy - big), size = Size(big * 2, big),
                )
                drawRect(
                    brush = Brush.verticalGradient(
                        0f to Color(0xFF9A6A38), 1f to Color(0xFF3A2612),
                        startY = cy, endY = cy + big,
                    ),
                    topLeft = Offset(cx - big, cy), size = Size(big * 2, big),
                )
                drawLine(Color.White, Offset(cx - big, cy), Offset(cx + big, cy), strokeWidth = 2.5f)
                pitchLadder(tm, cx, cy, pxPerDeg, r.width)
            }
        }
    }
    drawAircraftSymbol(cx, cy, r.width * 0.22f)
}

/** Top roll arc with tick marks and a moving cursor at the current bank angle. */
private fun DrawScope.drawRollArc(r: Rect, roll: Float) {
    val cx = r.center.x
    val radius = minOf(r.width, r.height) * 0.42f
    // Anchor the arc's top a few pixels BELOW the title bar (r.top) so it never
    // overlaps it, whatever the panel height. Arc top point is at cy - radius.
    val topMargin = 6f
    val cy = r.top + topMargin + radius
    // Tick marks at -60..60 in 30° steps, plus 10/20 minor.
    val ticks = listOf(-60 to 0.09f, -30 to 0.09f, -20 to 0.05f, -10 to 0.05f,
        0 to 0.11f, 10 to 0.05f, 20 to 0.05f, 30 to 0.09f, 60 to 0.09f)
    for ((deg, lenFrac) in ticks) {
        // Screen angle: 0° bank at top (−90° in math), positive bank to the right.
        val a = Math.toRadians((-90 + deg).toDouble())
        val ux = cos(a).toFloat(); val uy = sin(a).toFloat()
        val outer = Offset(cx + ux * radius, cy + uy * radius)
        val inner = Offset(cx + ux * (radius - radius * lenFrac), cy + uy * (radius - radius * lenFrac))
        drawLine(Color.White, inner, outer, strokeWidth = 2f)
    }
    // Cursor: a triangle pointing outward at the current bank angle.
    val a = Math.toRadians((-90 + roll.coerceIn(-60f, 60f)).toDouble())
    val ux = cos(a).toFloat(); val uy = sin(a).toFloat()
    // Perpendicular for the triangle base.
    val px = -uy; val py = ux
    val tip = Offset(cx + ux * (radius - radius * 0.13f), cy + uy * (radius - radius * 0.13f))
    val bl = Offset(cx + ux * radius + px * 8f, cy + uy * radius + py * 8f)
    val br = Offset(cx + ux * radius - px * 8f, cy + uy * radius - py * 8f)
    val p = Path().apply { moveTo(tip.x, tip.y); lineTo(bl.x, bl.y); lineTo(br.x, br.y); close() }
    drawPath(p, CompactStyle.Accent)
}

/** Pitch ladder with intermediate 5/15 marks (minor short, 10 medium, 20 long). */
private fun DrawScope.pitchLadder(tm: TextMeasurer, cx: Float, cy: Float, pxPerDeg: Float, width: Float) {
    data class Rung(val deg: Int, val halfFrac: Float, val labeled: Boolean)
    val rungs = listOf(
        Rung(-20, 0.13f, true), Rung(-15, 0.06f, false), Rung(-10, 0.09f, true), Rung(-5, 0.06f, false),
        Rung(5, 0.06f, false), Rung(10, 0.09f, true), Rung(15, 0.06f, false), Rung(20, 0.13f, true),
    )
    for (rung in rungs) {
        val y = cy - rung.deg * pxPerDeg
        val half = width * rung.halfFrac
        drawLine(Color.White, Offset(cx - half, y), Offset(cx + half, y), strokeWidth = 2f)
        if (rung.labeled) {
            val label = abs(rung.deg).toString()
            compactText(tm, label, cx - half - 20f, y, sizeSp = 11f)
            compactText(tm, label, cx + half + 20f, y, sizeSp = 11f)
        }
    }
}

/** Turn-indicator style aircraft: wing bar with a center gap + short vertical fin. */
private fun DrawScope.drawAircraftSymbol(cx: Float, cy: Float, wing: Float) {
    val gap = wing * 0.18f
    val stroke = 6f
    drawLine(CompactStyle.Accent, Offset(cx - wing, cy), Offset(cx - gap, cy), strokeWidth = stroke)
    drawLine(CompactStyle.Accent, Offset(cx + gap, cy), Offset(cx + wing, cy), strokeWidth = stroke)
    drawLine(CompactStyle.Accent, Offset(cx, cy), Offset(cx, cy - wing * 0.5f), strokeWidth = stroke)
    drawCircle(CompactStyle.Accent, radius = wing * 0.16f, center = Offset(cx, cy))
}

/** A value overlaid in a bottom corner of the horizon: title (small), big value, unit. */
private fun DrawScope.cornerValue(
    tm: TextMeasurer,
    r: Rect,
    left: Boolean,
    title: String,
    value: String,
    unit: String,
    valueColor: Color,
) {
    val boxW = r.width * 0.30f
    val boxH = r.height * 0.26f
    val bx = if (left) r.left + 4f else r.right - boxW - 4f
    val by = r.bottom - boxH - 4f
    // Semi-opaque backdrop so text reads over sky/ground.
    drawRect(Color(0xB0000000), topLeft = Offset(bx, by), size = Size(boxW, boxH))
    val cx = bx + boxW / 2f
    compactText(tm, title, cx, by + boxH * 0.20f, sizeSp = 9f, color = CompactStyle.Dim)
    compactText(tm, value, cx, by + boxH * 0.52f, sizeSp = 24f, bold = true, color = valueColor)
    compactText(tm, unit, cx, by + boxH * 0.84f, sizeSp = 9f, color = CompactStyle.Dim)
}
