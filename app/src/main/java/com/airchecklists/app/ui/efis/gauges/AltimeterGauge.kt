package com.airchecklists.app.ui.efis.gauges

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.airchecklists.app.data.model.AltitudeFormat
import com.airchecklists.app.data.model.AltitudeUnit
import com.airchecklists.app.di.ServiceLocator
import com.airchecklists.app.ui.components.AltitudeEntryDialog
import com.airchecklists.app.ui.efis.gauges.compact.drawGestureHints

/** Classic 3-pointer altimeter (0–9 = ×1000 ft). Long thin needle = 1000 ft,
 *  short fat needle = 100 ft. Long-press sets a magenta target-altitude bug.
 *  The dial stays graduated in feet (classic altimeter); only the numeric
 *  readout follows [altUnit]. */
@Composable
fun AltimeterGauge(
    altitudeFt: Float,
    showValue: Boolean,
    altUnit: AltitudeUnit = AltitudeUnit.FEET,
    modifier: Modifier = Modifier,
) {
    val tm = rememberTextMeasurer()
    val bezel = LocalGaugeBezel.current
    val targetAlt by ServiceLocator.targetAltitude.collectAsStateWithLifecycle()
    var showDialog by remember { mutableStateOf(false) }

    Canvas(
        modifier = modifier.fillMaxSize().pointerInput(Unit) {
            detectTapGestures(onLongPress = { showDialog = true })
        },
    ) {
        val (cx, cy, r) = gaugeFace(bezel)

        // Ticks every 20 ft-equivalent (i.e. 50 divisions around); labels 0..9.
        for (i in 0 until 50) {
            val deg = i * 360f / 50f
            val major = i % 5 == 0
            val outer = r * 0.96f
            val inner = r * (if (major) 0.84f else 0.90f)
            drawLine(GaugeColors.Mark, polar(cx, cy, inner, deg), polar(cx, cy, outer, deg),
                strokeWidth = if (major) 2.5f else 1.5f)
        }
        for (n in 0..9) {
            val deg = n * 36f
            val pos = polar(cx, cy, r * 0.70f, deg)
            gaugeText(tm, n.toString(), pos.x, pos.y, sizeSp = 15f, bold = true)
        }
        gaugeText(tm, "ALT", cx, cy - r * 0.32f, sizeSp = 11f, color = GaugeColors.MarkDim)
        gaugeText(tm, AltitudeFormat.altLabel(altUnit), cx, cy + r * 0.34f, sizeSp = 10f, color = GaugeColors.MarkDim)

        // Drive the dial in the DISPLAY unit so the analog reading matches the
        // numeric value (labels 0–9 mean ×1000 and ×100 of the selected unit).
        val alt = AltitudeFormat.altValue(altitudeFt, altUnit).coerceAtLeast(0f)
        // Long needle: one full turn = 10 000 (unit).
        val thousandsAngle = (alt % 10000f) / 10000f * 360f
        // Short needle: one full turn = 1000 (unit).
        val hundredsAngle = (alt % 1000f) / 1000f * 360f

        // Magenta target-altitude bug on the outer ring (target in the display unit).
        targetAlt?.let { t ->
            val tUnit = AltitudeFormat.altValue(t.toFloat(), altUnit)
            val ang = (tUnit % 1000f) / 1000f * 360f
            val outer = polar(cx, cy, r * 0.985f, ang)
            val bl = polar(cx, cy, r * 0.86f, ang - 5f)
            val br = polar(cx, cy, r * 0.86f, ang + 5f)
            val p = Path().apply { moveTo(outer.x, outer.y); lineTo(bl.x, bl.y); lineTo(br.x, br.y); close() }
            drawPath(p, Color(0xFFD24DEA))
        }

        // Thin long needle = 1000s.
        drawNeedle(cx, cy, length = r * 0.62f, angleDeg = thousandsAngle, width = 4f, tailLength = r * 0.12f)
        // Fat short needle = 100s.
        drawNeedle(cx, cy, length = r * 0.82f, angleDeg = hundredsAngle, width = 7f, tailLength = r * 0.14f)

        drawCircle(GaugeColors.Mark, radius = r * 0.05f, center = Offset(cx, cy))

        // Numeric value in a dedicated box at the instrument centre.
        if (showValue) {
            val txt = "${alt.toInt()}"
            drawRoundedValue(tm, txt, cx, cy, sizeSp = 20f)
        }

        // Gesture hint (long-press) just outside the face, top-left.
        drawGestureHints(cx - r * 0.98f, cy - r * 0.98f, hasLongPress = true, hasDoubleTap = false)
    }

    if (showDialog) {
        AltitudeEntryDialog(
            initial = targetAlt,
            onDismiss = { showDialog = false },
            onConfirm = { a -> ServiceLocator.targetAltitude.value = a; showDialog = false },
            onClear = { ServiceLocator.targetAltitude.value = null; showDialog = false },
        )
    }
}
