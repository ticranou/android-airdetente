package com.airchecklists.app.ui.efis.gauges

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.rememberTextMeasurer
import com.airchecklists.app.data.model.EfisSpeedUnit
import com.airchecklists.app.data.model.SpeedArcs

/** Airspeed gauge (from GPS speed). Optional colored arcs from a reference
 *  aircraft's characteristic speeds. Scale: km/h 0–250 or knots 0–140. */
@Composable
fun AirspeedGauge(
    speedKmh: Float,
    unit: EfisSpeedUnit,
    showValue: Boolean,
    arcs: SpeedArcs?,
    modifier: Modifier = Modifier,
) {
    val tm = rememberTextMeasurer()
    val bezel = LocalGaugeBezel.current
    Canvas(modifier = modifier.fillMaxSize()) {
        val (cx, cy, r) = gaugeFace(bezel)

        val toUnit = if (unit == EfisSpeedUnit.KNOTS) 1f / 1.852f else 1f
        val value = speedKmh * toUnit
        val maxVal = if (unit == EfisSpeedUnit.KNOTS) 140f else 250f
        val step = if (unit == EfisSpeedUnit.KNOTS) 10 else 20
        val unitLabel = if (unit == EfisSpeedUnit.KNOTS) "kt" else "km/h"

        val startDeg = -135f
        val sweep = 270f
        fun angleFor(v: Float): Float = startDeg + (v.coerceIn(0f, maxVal) / maxVal) * sweep

        // Colored arcs (values are km/h in the aircraft; convert to display unit).
        if (arcs != null) {
            fun conv(v: Int?): Float? = v?.let { it * toUnit }
            // Draw each arc on its own concentric ring so overlapping ranges stay
            // visible (real ASIs put the white flap arc on an inner ring, the
            // green/yellow ranges on an outer ring).
            fun sweepArc(fromV: Float?, toV: Float?, color: Color, radiusFactor: Float) {
                if (fromV == null || toV == null || toV <= fromV) return
                val rr = r * radiusFactor
                val rect = Rect(cx - rr, cy - rr, cx + rr, cy + rr)
                val a0 = angleFor(fromV)
                val a1 = angleFor(toV)
                // Canvas arc: 0° = 3 o'clock; our aviation deg is up=0 clockwise → sub 90.
                drawArc(
                    color = color,
                    startAngle = a0 - 90f,
                    sweepAngle = a1 - a0,
                    useCenter = false,
                    topLeft = rect.topLeft,
                    size = rect.size,
                    style = Stroke(width = r * 0.08f),
                )
            }
            val greenMin = conv(arcs.greenMin); val greenMax = conv(arcs.greenMax)
            val whiteMin = conv(arcs.whiteMin); val whiteMax = conv(arcs.whiteMax)
            val vno = conv(arcs.vno); val vne = conv(arcs.vne)
            sweepArc(whiteMin, whiteMax, Color.White, radiusFactor = 0.68f)   // white arc (flap range), inner ring
            sweepArc(greenMin, greenMax, Color(0xFF2E7D32), radiusFactor = 0.80f)  // green arc (normal), outer ring
            sweepArc(vno, vne, Color(0xFFFFC107), radiusFactor = 0.80f)       // yellow arc (caution), outer ring
            vne?.let {
                val a = angleFor(it)
                drawLine(Color(0xFFD32F2F), polar(cx, cy, r * 0.70f, a), polar(cx, cy, r * 0.88f, a), strokeWidth = r * 0.06f)
            }
            // Vs0/Vs1 red cursors + Vpl magenta cursor.
            listOfNotNull(conv(arcs.vs0), conv(arcs.vs1)).forEach {
                val a = angleFor(it)
                drawLine(Color(0xFFD32F2F), polar(cx, cy, r * 0.68f, a), polar(cx, cy, r * 0.80f, a), strokeWidth = r * 0.04f)
            }
            conv(arcs.vpl)?.let {
                val a = angleFor(it)
                drawLine(Color(0xFFB94DD6), polar(cx, cy, r * 0.68f, a), polar(cx, cy, r * 0.88f, a), strokeWidth = r * 0.05f)
            }
        }

        var v = 0
        while (v <= maxVal.toInt()) {
            val deg = angleFor(v.toFloat())
            val major = v % (step * 2) == 0
            val outer = r * 0.88f
            val inner = r * (if (major) 0.74f else 0.80f)
            drawLine(GaugeColors.Mark, polar(cx, cy, inner, deg), polar(cx, cy, outer, deg),
                strokeWidth = if (major) 2.5f else 1.5f)
            if (major) {
                val pos = polar(cx, cy, r * 0.52f, deg)
                gaugeText(tm, v.toString(), pos.x, pos.y, sizeSp = 12f, bold = true)
            }
            v += step
        }

        gaugeText(tm, unitLabel, cx, cy + r * 0.52f, sizeSp = 11f, color = GaugeColors.MarkDim)

        drawNeedle(cx, cy, length = r * 0.78f, angleDeg = angleFor(value), width = 6f, tailLength = r * 0.10f)
        drawCircle(GaugeColors.Mark, radius = r * 0.05f, center = Offset(cx, cy))

        if (showValue) {
            drawRoundedValue(tm, value.toInt().toString(), cx, cy, sizeSp = 20f)
        }
        drawGaugeTitlePill(tm, "VITESSE", cx, cy, r, bezel.hideTitle)
    }
}
