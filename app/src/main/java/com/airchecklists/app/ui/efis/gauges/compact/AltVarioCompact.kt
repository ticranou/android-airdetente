package com.airchecklists.app.ui.efis.gauges.compact

import com.airchecklists.app.ui.efis.gauges.drawNumTitleBar
import com.airchecklists.app.ui.efis.gauges.LocalGaugeBezel
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.airchecklists.app.data.model.AltitudeFormat
import com.airchecklists.app.data.model.AltitudeUnit
import com.airchecklists.app.di.ServiceLocator
import com.airchecklists.app.ui.components.AltitudeEntryDialog
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
    val targetAlt by ServiceLocator.targetAltitude.collectAsStateWithLifecycle()
    var showDialog by remember { mutableStateOf(false) }

    Canvas(
        modifier = modifier.fillMaxSize().pointerInput(Unit) {
            detectTapGestures(onLongPress = { showDialog = true })
        },
    ) {
        val w = size.width
        val h = size.height
        val headerH = 20.dp.toPx().coerceAtMost(h * 0.4f)
        drawRect(CompactStyle.Bg, size = size)
        drawNumTitleBar(bezel, w, headerH)
        drawRect(Color(0xFF3A3A3A), size = size, style = Stroke(width = 2f))
        compactText(tm, "ALTITUDE (${AltitudeFormat.altLabel(altUnit)})", w * 0.27f, headerH / 2f, sizeSp = 11f, color = CompactStyle.Dim)
        compactText(tm, "VARIO (${AltitudeFormat.vsLabel(altUnit)})", w * 0.73f, headerH / 2f, sizeSp = 11f, color = CompactStyle.Dim)
        // Gesture hint (long-press) in the title bar, top-left.
        drawGestureHints(6f, headerH / 2f, hasLongPress = true, hasDoubleTap = false)

        val mainTop = headerH
        val mainBottom = h
        val cy = (mainTop + mainBottom) / 2f
        val climbing = verticalSpeedFtMin >= 0f
        val trendColor = if (climbing) CompactStyle.Climb else CompactStyle.Descent

        // Two value cells with colour-coded borders (text stays white).
        val cellW = w * 0.34f
        val cellH = (mainBottom - mainTop) * 0.62f
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
        fun cell(cxc: Float, text: String) {
            drawRoundRect(Color(0xFF1C1C1C), topLeft = Offset(cxc - cellW / 2, cy - cellH / 2),
                size = Size(cellW, cellH), cornerRadius = CornerRadius(6f, 6f))
            drawRoundRect(trendColor, topLeft = Offset(cxc - cellW / 2, cy - cellH / 2),
                size = Size(cellW, cellH), cornerRadius = CornerRadius(6f, 6f), style = Stroke(width = 2f))
            if (showValue) compactText(tm, text, cxc, cy, sizeSp = valueSp, bold = true, color = CompactStyle.Mark)
        }
        cell(w * 0.27f, altText)
        cell(w * 0.73f, varText)

        // Target altitude (magenta) above the ALT cell.
        targetAlt?.let { t ->
            val tTxt = AltitudeFormat.altValue(t.toFloat(), altUnit).roundToInt().toString()
            compactText(tm, "▲ $tTxt", w * 0.27f, cy - cellH / 2 - mainTop * 0.20f - 6f, sizeSp = 11f, bold = true, color = Color(0xFFD24DEA))
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
}
