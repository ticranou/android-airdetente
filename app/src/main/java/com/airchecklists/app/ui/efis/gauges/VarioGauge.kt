package com.airchecklists.app.ui.efis.gauges

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.rememberTextMeasurer
import com.airchecklists.app.data.model.AltitudeFormat
import com.airchecklists.app.data.model.AltitudeUnit
import kotlin.math.abs

/** Vertical speed indicator. In feet: ±2000 ft/min (labels ×1000). In metres:
 *  ±10 m/s. Zero at 9 o'clock, climb on top, descent on bottom (classic VSI).
 *  Both the graduation and the numeric readout follow [altUnit] so they agree. */
@Composable
fun VarioGauge(
    verticalSpeedFtMin: Float,
    showValue: Boolean,
    altUnit: AltitudeUnit = AltitudeUnit.FEET,
    modifier: Modifier = Modifier,
) {
    val meters = altUnit == AltitudeUnit.METERS
    val tm = rememberTextMeasurer()
    val bezel = LocalGaugeBezel.current
    Canvas(modifier = modifier.fillMaxSize()) {
        val (cx, cy, r) = gaugeFace(bezel)

        // Scale in the display unit: feet → ±2 (×1000 ft/min); metres → ±10 m/s.
        val maxScale = if (meters) 10f else 2f
        val tickStep = if (meters) 1f else 0.25f    // minor tick spacing
        val labelEvery = if (meters) 2f else 1f     // label every N ticks-worth
        val zeroDeg = 270f
        val sweepPerUnit = 150f / maxScale          // 150° each way

        fun angleFor(units: Float): Float =
            zeroDeg + units.coerceIn(-maxScale, maxScale) * sweepPerUnit

        var v = -maxScale
        while (v <= maxScale + 0.001f) {
            val deg = angleFor(v)
            val isLabel = (abs(v) % labelEvery) < 0.01f
            val isHalf = (abs(v) % (labelEvery / 2f)) < 0.01f
            val outer = r * 0.96f
            val inner = r * when {
                isLabel -> 0.84f
                isHalf -> 0.88f
                else -> 0.91f
            }
            drawLine(GaugeColors.Mark, polar(cx, cy, inner, deg), polar(cx, cy, outer, deg),
                strokeWidth = if (isLabel) 2.5f else 1.2f)
            if (isLabel) {
                val pos = polar(cx, cy, r * 0.70f, deg)
                gaugeText(tm, abs(v).toInt().toString(), pos.x, pos.y, sizeSp = 14f, bold = true)
            }
            v += tickStep
        }
        gaugeText(tm, "V/S", cx, cy - r * 0.30f, sizeSp = 11f, color = GaugeColors.MarkDim)
        gaugeText(tm, if (meters) "m/s" else "1000 ft/min", cx, cy + r * 0.32f, sizeSp = 8f, color = GaugeColors.MarkDim)
        gaugeText(tm, "UP", cx + r * 0.30f, cy - r * 0.12f, sizeSp = 8f, color = GaugeColors.MarkDim)
        gaugeText(tm, "DN", cx + r * 0.30f, cy + r * 0.12f, sizeSp = 8f, color = GaugeColors.MarkDim)

        // Needle position in the display unit (feet uses ×1000 ft/min scale).
        val needleUnits = if (meters) AltitudeFormat.vsValue(verticalSpeedFtMin, altUnit) else verticalSpeedFtMin / 1000f
        val angle = angleFor(needleUnits)
        drawNeedle(cx, cy, length = r * 0.80f, angleDeg = angle, width = 6f, tailLength = r * 0.10f)
        drawCircle(GaugeColors.Mark, radius = r * 0.05f, center = androidx.compose.ui.geometry.Offset(cx, cy))

        if (showValue) {
            val txt = if (meters) "%.1f".format(AltitudeFormat.vsValue(verticalSpeedFtMin, altUnit))
                else verticalSpeedFtMin.toInt().toString()
            drawRoundedValue(tm, txt, cx, cy, sizeSp = 20f)
        }
    }
}
