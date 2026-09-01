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
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.airchecklists.app.data.model.EfisSpeedUnit
import com.airchecklists.app.data.model.SpeedArcs

/** Compact airspeed: the same EFIS speed tape (ticks + arc rows + Vpl/stall
 *  cursors + prominent orange centre cell) under a fixed 20dp title header. */
@Composable
fun AirspeedCompact(
    speedKmh: Float,
    unit: EfisSpeedUnit,
    showValue: Boolean,
    arcs: SpeedArcs?,
    modifier: Modifier = Modifier,
) {
    val tm = rememberTextMeasurer()
    val bezel = LocalGaugeBezel.current
    Canvas(modifier = modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val headerH = 20.dp.toPx().coerceAtMost(h * 0.4f)
        drawRect(CompactStyle.Bg, size = size)
        drawNumTitleBar(bezel, w, headerH)
        drawRect(androidx.compose.ui.graphics.Color(0xFF3A3A3A), size = size, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2f))

        val title = if (unit == EfisSpeedUnit.KNOTS) "VITESSE (kt)" else "VITESSE (km/h)"
        compactText(tm, title, w / 2f, headerH / 2f, sizeSp = 12f, color = CompactStyle.Dim)

        val toUnit = if (unit == EfisSpeedUnit.KNOTS) 1f / 1.852f else 1f
        val speed = speedKmh * toUnit
        val unitLabel = if (unit == EfisSpeedUnit.KNOTS) "kt" else "km/h"
        val arcsInUnit = arcs?.scaled(toUnit)

        val tape = Rect(0f, headerH, w, h)
        efisSpeedTape(tm, tape, speed, unitLabel, arcsInUnit, showValue)
    }
}
