package com.airchecklists.app.ui.efis.gauges

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.rememberTextMeasurer
import com.airchecklists.app.R

/**
 * Placeholder shown in a dashboard cell when the selected instrument needs a
 * sensor the device lacks (e.g. attitude/heading/ball without a gyroscope or
 * magnetometer). Keeps the cell's bezel so the layout stays coherent, and
 * explains WHY the instrument is unavailable rather than showing frozen data.
 *
 * [round] selects the analog look (round bezel) vs. the compact/numeric look
 * (title bar + flat panel).
 */
@Composable
fun UnavailableInstrument(round: Boolean, modifier: Modifier = Modifier) {
    val tm = rememberTextMeasurer()
    val bezel = LocalGaugeBezel.current
    val line1 = stringResource(R.string.instrument_unavailable)
    val line2 = stringResource(R.string.instrument_unavailable_gyro)
    Canvas(modifier = modifier.fillMaxSize()) {
        val cx = size.width / 2f
        if (round) {
            val (ccx, ccy, r) = gaugeFace(bezel)
            gaugeText(tm, line1, ccx, ccy - r * 0.10f, sizeSp = 15f, bold = true, color = Color(0xFFCDCDCD))
            gaugeText(tm, line2, ccx, ccy + r * 0.16f, sizeSp = 11f, color = GaugeColors.MarkDim)
        } else {
            val w = size.width
            val h = size.height
            val headerH = (h * 0.18f).coerceIn(18f, 40f)
            drawRect(GaugeColors.Face, size = size)
            drawNumTitleBar(bezel, w, headerH)
            val cy = headerH + (h - headerH) / 2f
            gaugeText(tm, line1, cx, cy - 10f, sizeSp = 15f, bold = true, color = Color(0xFFCDCDCD))
            gaugeText(tm, line2, cx, cy + 14f, sizeSp = 11f, color = GaugeColors.MarkDim)
        }
    }
}
