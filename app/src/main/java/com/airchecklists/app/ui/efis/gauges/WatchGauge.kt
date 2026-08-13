package com.airchecklists.app.ui.efis.gauges

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.rememberTextMeasurer
import java.util.Calendar

/** Analog watch: round face with hour/minute/second hands showing local time.
 *  When [showValue] is on, the time is also shown numerically at the centre. */
@Composable
fun WatchGauge(showValue: Boolean, modifier: Modifier = Modifier) {
    val tm = rememberTextMeasurer()
    val bezel = LocalGaugeBezel.current
    var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            nowMs = System.currentTimeMillis()
            kotlinx.coroutines.delay(500)
        }
    }

    Canvas(modifier = modifier.fillMaxSize()) {
        val (cx, cy, r) = gaugeFace(bezel)
        gaugeText(tm, "MONTRE", cx, cy - r * 0.32f, sizeSp = 11f, color = GaugeColors.MarkDim)

        // Hour ticks (12) + minute ticks (60).
        for (i in 0 until 60) {
            val deg = i * 6f
            val major = i % 5 == 0
            val outer = r * 0.96f
            val inner = r * (if (major) 0.86f else 0.91f)
            drawLine(GaugeColors.Mark, polar(cx, cy, inner, deg), polar(cx, cy, outer, deg),
                strokeWidth = if (major) 2.5f else 1.2f)
        }
        // Hour numerals 12/3/6/9.
        for (h in intArrayOf(12, 3, 6, 9)) {
            val deg = (h % 12) * 30f
            val pos = polar(cx, cy, r * 0.72f, deg)
            gaugeText(tm, h.toString(), pos.x, pos.y, sizeSp = 15f, bold = true)
        }

        val cal = Calendar.getInstance().apply { timeInMillis = nowMs }
        val h24 = cal.get(Calendar.HOUR_OF_DAY)
        val hh = cal.get(Calendar.HOUR)
        val mm = cal.get(Calendar.MINUTE)
        val ss = cal.get(Calendar.SECOND)
        // 0° = up (12 o'clock), clockwise. polar() uses 0°=up already for these gauges.
        val hourAngle = (hh + mm / 60f) * 30f
        val minAngle = mm * 6f
        val secAngle = ss * 6f

        drawNeedle(cx, cy, length = r * 0.50f, angleDeg = hourAngle, width = 6f, tailLength = r * 0.10f)
        drawNeedle(cx, cy, length = r * 0.74f, angleDeg = minAngle, width = 4f, tailLength = r * 0.12f)
        drawNeedle(cx, cy, length = r * 0.80f, angleDeg = secAngle, width = 2f, tailLength = r * 0.16f)
        drawCircle(GaugeColors.Accent, radius = r * 0.045f, center = Offset(cx, cy))

        // Numeric time in a dedicated box at the centre, like the altimeter.
        if (showValue) {
            drawRoundedValue(tm, "%02d:%02d".format(h24, mm), cx, cy + r * 0.30f, sizeSp = 18f)
        }
    }
}
