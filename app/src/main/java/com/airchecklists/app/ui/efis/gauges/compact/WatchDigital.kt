package com.airchecklists.app.ui.efis.gauges.compact

import com.airchecklists.app.ui.efis.gauges.drawNumTitleBar
import com.airchecklists.app.ui.efis.gauges.LocalGaugeBezel
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import java.util.Calendar

/** Digital watch (single height, half width): fixed 20dp "MONTRE" header, then one
 *  dark cell showing the local time HH:MM:SS. Display only (no gestures). */
@Composable
fun WatchDigital(modifier: Modifier = Modifier) {
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
        val w = size.width; val h = size.height
        val headerH = 20.dp.toPx().coerceAtMost(h * 0.4f)
        drawRect(CompactStyle.Bg, size = size)
        drawNumTitleBar(bezel, w, headerH)
        drawRect(Color(0xFF3A3A3A), size = size, style = Stroke(width = 2f))
        compactText(tm, "MONTRE", w / 2f, headerH / 2f, sizeSp = 12f, color = CompactStyle.Dim)

        val cal = Calendar.getInstance().apply { timeInMillis = nowMs }
        val txt = "%02d:%02d:%02d".format(
            cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), cal.get(Calendar.SECOND),
        )
        val cy = headerH + (h - headerH) / 2f
        compactText(tm, txt, w / 2f, cy, sizeSp = 26f, bold = true, mono = true, color = CompactStyle.Mark)
    }
}
