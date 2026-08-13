package com.airchecklists.app.ui.efis.gauges.chrono

import com.airchecklists.app.ui.efis.gauges.drawNumTitleBar
import com.airchecklists.app.ui.efis.gauges.LocalGaugeBezel
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
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
import com.airchecklists.app.ui.efis.gauges.compact.CompactStyle
import com.airchecklists.app.ui.efis.gauges.compact.compactText
import com.airchecklists.app.ui.efis.gauges.compact.drawGestureHints

private enum class Cd1 { STOPPED, RUNNING, PAUSED }

/** One count-up chronometer's state (self-contained). */
private class Chrono1 {
    var run by mutableStateOf(Cd1.STOPPED)
    var accumMs by mutableLongStateOf(0L)
    var anchor by mutableLongStateOf(0L)
    var now by mutableLongStateOf(0L)
    fun elapsed(): Long = accumMs + if (run == Cd1.RUNNING) (now - anchor) else 0L
    fun reset() { run = Cd1.STOPPED; accumMs = 0L; persist() }
    /** Double-tap: start from stopped, else toggle run/pause. */
    fun startStop() {
        when (run) {
            Cd1.STOPPED -> { anchor = System.currentTimeMillis(); now = anchor; run = Cd1.RUNNING }
            Cd1.RUNNING -> { accumMs += System.currentTimeMillis() - anchor; run = Cd1.PAUSED }
            Cd1.PAUSED -> { anchor = System.currentTimeMillis(); now = anchor; run = Cd1.RUNNING }
        }
        persist()
    }
    private fun persist() {
        com.airchecklists.app.di.ServiceLocator.updateInstruments {
            it.copy(chronoNum = com.airchecklists.app.data.model.ChronoSnapshot(
                running = run == Cd1.RUNNING, accumMs = accumMs,
                anchorEpochMs = if (run == Cd1.RUNNING) anchor else null,
            ))
        }
    }
    fun seed(s: com.airchecklists.app.data.model.ChronoSnapshot): Chrono1 {
        accumMs = s.accumMs
        if (s.running && s.anchorEpochMs != null) {
            anchor = s.anchorEpochMs; now = System.currentTimeMillis(); run = Cd1.RUNNING
        } else { run = Cd1.STOPPED }
        return this
    }
}

/**
 * Digital chronometer (single height, half width): fixed 20dp "CHRONO" header,
 * then one dark time cell. Double-tap = start/stop; long-press = reset.
 */
@Composable
fun ChronoDigital(modifier: Modifier = Modifier) {
    val tm = rememberTextMeasurer()
    val bezel = LocalGaugeBezel.current
    val chrono = remember { com.airchecklists.app.di.ServiceLocator.instrumentState("chrono.num") { Chrono1().seed(com.airchecklists.app.di.ServiceLocator.instrumentPersist.chronoNum) } }
    var nowTick by remember { mutableLongStateOf(0L) }

    LaunchedEffect(chrono.run) {
        while (chrono.run == Cd1.RUNNING) {
            chrono.now = System.currentTimeMillis(); nowTick = chrono.now
            kotlinx.coroutines.delay(250)
        }
    }

    Canvas(
        modifier = modifier.fillMaxSize().pointerInput(Unit) {
            detectTapGestures(
                onLongPress = { chrono.reset() },
                onDoubleTap = { chrono.startStop() },
            )
        },
    ) {
        @Suppress("UNUSED_EXPRESSION") nowTick
        val w = size.width; val h = size.height
        val headerH = 20.dp.toPx().coerceAtMost(h * 0.4f)
        drawRect(CompactStyle.Bg, size = size)
        drawNumTitleBar(bezel, w, headerH)
        drawRect(Color(0xFF3A3A3A), size = size, style = Stroke(width = 2f))
        compactText(tm, "CHRONO", w / 2f, headerH / 2f, sizeSp = 12f, color = CompactStyle.Dim)
        drawGestureHints(6f, headerH / 2f, hasLongPress = true, hasDoubleTap = true)

        val cy = headerH + (h - headerH) / 2f
        val cellW = w * 0.82f
        val cellH = (h - headerH) * 0.62f
        drawRoundRect(Color(0xFF1C1C1C), topLeft = Offset(w / 2f - cellW / 2, cy - cellH / 2),
            size = Size(cellW, cellH), cornerRadius = CornerRadius(6f, 6f))
        drawRoundRect(Color(0xFF5A5A5A), topLeft = Offset(w / 2f - cellW / 2, cy - cellH / 2),
            size = Size(cellW, cellH), cornerRadius = CornerRadius(6f, 6f), style = Stroke(width = 1.5f))
        compactText(tm, fmtHms(chrono.elapsed()), w / 2f, cy, sizeSp = 26f, bold = true, mono = true, color = CompactStyle.Mark)
    }
}

/** H:MM:SS (or MM:SS under an hour). */
private fun fmtHms(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    val hh = total / 3600
    val mm = (total % 3600) / 60
    val ss = total % 60
    return if (hh > 0) "%d:%02d:%02d".format(hh, mm, ss) else "%02d:%02d".format(mm, ss)
}
