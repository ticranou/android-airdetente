package com.airchecklists.app.ui.efis.gauges.compact

import com.airchecklists.app.ui.efis.gauges.drawNumTitleBar
import com.airchecklists.app.ui.efis.gauges.LocalHideTitle
import com.airchecklists.app.ui.efis.gauges.LocalGaugeBezel
import com.airchecklists.app.ui.efis.gauges.LocalHideBorder
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.airchecklists.app.data.model.AltitudeFormat
import com.airchecklists.app.data.model.AltitudeUnit
import com.airchecklists.app.di.ServiceLocator
import com.airchecklists.app.ui.components.AltCalibrationDialog
import com.airchecklists.app.ui.components.AltitudeEntryDialog
import com.airchecklists.app.ui.efis.gauges.nearestAirfieldElevationFt
import kotlin.math.roundToInt

/** Combined Altitude + Vario (single height, half width): fixed 20dp header with
 *  two titles, then two value cells whose BORDER colours reflect climb (green) /
 *  descent (red), with a single centred trend arrow between them. Long-press sets
 *  a magenta target altitude, shown above the ALT cell. */
@Composable
fun AltVarioCompact(
    altitudeFt: Float,
    verticalSpeedFtMin: Float,
    showValue: Boolean,
    altUnit: AltitudeUnit = AltitudeUnit.FEET,
    modifier: Modifier = Modifier,
) {
    val tm = rememberTextMeasurer()
    val bezel = LocalGaugeBezel.current
    val hideCellBorder = LocalHideBorder.current
    val hideTitle = LocalHideTitle.current
    val targetAlt by ServiceLocator.targetAltitude.collectAsStateWithLifecycle()
    val calibAlt by ServiceLocator.altCalibrationFt.collectAsStateWithLifecycle()
    var showDialog by remember { mutableStateOf(false) }
    var showCalibDialog by remember { mutableStateOf(false) }

    Canvas(
        modifier = modifier.fillMaxSize().pointerInput(Unit) {
            detectTapGestures(
                onLongPress = { showDialog = true },
                onDoubleTap = { showCalibDialog = true },
            )
        },
    ) {
        val w = size.width
        val h = size.height
        val headerH = if (hideTitle) 0f else 20.dp.toPx().coerceAtMost(h * 0.4f)
        drawRect(CompactStyle.Bg, size = size)
        if (headerH > 0f) {
            drawNumTitleBar(bezel, w, headerH)
            drawGestureHints(6f, headerH / 2f, hasLongPress = true, hasDoubleTap = true)
            compactText(tm, "ALTI / VARIO", w / 2f, headerH / 2f, sizeSp = 12f, color = CompactStyle.Dim)
        }
        // Instrument border (matches the other compact instruments).
        if (!hideCellBorder) drawRect(Color(0xFF3A3A3A), size = size, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2f))

        val mainTop = headerH
        val mainBottom = h
        val cy = (mainTop + mainBottom) / 2f
        val climbing = verticalSpeedFtMin >= 0f
        val trendColor = if (climbing) CompactStyle.Climb else CompactStyle.Descent

        // Two value cells with colour-coded borders: label (top) / value (centre) /
        // unit (bottom), all inside the box. Pushed toward the edges to leave room
        // for the trend arrow in the centre.
        val cellW = w * 0.34f
        val cellH = (mainBottom - mainTop) * 0.74f
        val altCx = w * 0.23f
        val varCx = w * 0.77f
        val altText = AltitudeFormat.altValue(altitudeFt, altUnit).roundToInt().toString()
        val varText = if (altUnit == AltitudeUnit.METERS)
            "%.1f".format(AltitudeFormat.vsValue(verticalSpeedFtMin, altUnit))
        else verticalSpeedFtMin.roundToInt().toString()
        // Shrink the font when a value is long so it fits; both cells share the size.
        val maxLen = maxOf(altText.length, varText.length)
        val valueSp = when {
            maxLen >= 5 -> 20f
            maxLen == 4 -> 24f
            else -> 28f
        }
        fun cell(cxc: Float, label: String, text: String, unit: String) {
            // Boxed value: dark fill + neutral grey border; label on top, big
            // trend-coloured value centred (green climb / red descent), unit at bottom.
            val left = cxc - cellW / 2f
            val top = cy - cellH / 2f
            val topLeft = androidx.compose.ui.geometry.Offset(left, top)
            val boxSize = androidx.compose.ui.geometry.Size(cellW, cellH)
            drawRect(Color(0xFF111111), topLeft = topLeft, size = boxSize)
            drawRect(Color(0xFF5A5A5A), topLeft = topLeft, size = boxSize,
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2f))
            compactText(tm, label, cxc, top + cellH * 0.17f, sizeSp = 10f, bold = true, color = CompactStyle.Dim)
            if (showValue) compactText(tm, text, cxc, cy + cellH * 0.02f, sizeSp = valueSp, bold = true, color = trendColor)
            compactText(tm, unit, cxc, top + cellH * 0.84f, sizeSp = 9f, bold = true, color = CompactStyle.Dim)
        }
        cell(altCx, "ALTI", altText, AltitudeFormat.altLabel(altUnit).uppercase())
        cell(varCx, "VARIO", varText, AltitudeFormat.vsLabel(altUnit).uppercase())

        // Target altitude (magenta) above the ALT cell.
        targetAlt?.let { t ->
            val tTxt = AltitudeFormat.altValue(t.toFloat(), altUnit).roundToInt().toString()
            compactText(tm, "▲ $tTxt", altCx, cy - cellH / 2 - 7f, sizeSp = 10f, bold = true, color = Color(0xFFD24DEA))
        }

        // Calibration indicator: magenta dot above the ALTI cell.
        if (calibAlt != null) {
            drawCircle(Color(0xFFAA00CC), radius = 5f,
                center = androidx.compose.ui.geometry.Offset(altCx + cellW / 2f - 6f, cy - cellH / 2f - 7f))
        }

        // Single centred trend arrow between the two cells.
        val arrowHalf = cellH * 0.34f
        trendArrow(w * 0.5f, cy, arrowHalf, verticalSpeedFtMin)
    }

    if (showDialog) {
        AltitudeEntryDialog(
            initial = targetAlt,
            onDismiss = { showDialog = false },
            onConfirm = { a -> ServiceLocator.targetAltitude.value = a; showDialog = false },
            onClear = { ServiceLocator.targetAltitude.value = null; showDialog = false },
        )
    }

    if (showCalibDialog) {
        AltCalibrationDialog(
            initial = calibAlt ?: nearestAirfieldElevationFt(altitudeFt),
            onDismiss = { showCalibDialog = false },
            onConfirm = { a -> ServiceLocator.setAltCalibration(a, altitudeFt); showCalibDialog = false },
            onClear = { ServiceLocator.clearAltCalibration(); showCalibDialog = false },
        )
    }
}
