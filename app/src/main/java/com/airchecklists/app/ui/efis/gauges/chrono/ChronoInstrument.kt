package com.airchecklists.app.ui.efis.gauges.chrono

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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.rememberTextMeasurer
import com.airchecklists.app.ui.efis.gauges.compact.CompactStyle
import com.airchecklists.app.ui.efis.gauges.compact.compactText
import com.airchecklists.app.ui.efis.gauges.compact.drawGestureHints
import com.airchecklists.app.ui.efis.gauges.gaugeFace

private enum class RunState { STOPPED, RUNNING, PAUSED }

/** One independent stopwatch (count-up) with its own lifecycle. */
private class StopwatchState(private val onChange: (StopwatchState) -> Unit = {}) {
    var run by mutableStateOf(RunState.STOPPED)
    var accumMs by mutableLongStateOf(0L)
    var anchor by mutableLongStateOf(0L)
    var now by mutableLongStateOf(0L)   // ticked while running

    fun elapsed(): Long = accumMs + if (run == RunState.RUNNING) (now - anchor) else 0L
    fun start() { anchor = System.currentTimeMillis(); now = anchor; run = RunState.RUNNING }
    fun reset() { run = RunState.STOPPED; accumMs = 0L; onChange(this) }
    /** Double-tap semantics: start from stopped, otherwise toggle run/pause. */
    fun startStop() {
        when (run) {
            RunState.STOPPED -> start()
            RunState.RUNNING -> { accumMs += System.currentTimeMillis() - anchor; run = RunState.PAUSED }
            RunState.PAUSED -> { anchor = System.currentTimeMillis(); now = anchor; run = RunState.RUNNING }
        }
        onChange(this)
    }
    fun snapshot() = com.airchecklists.app.data.model.ChronoSnapshot(
        running = run == RunState.RUNNING, accumMs = accumMs,
        anchorEpochMs = if (run == RunState.RUNNING) anchor else null,
    )
    fun seed(s: com.airchecklists.app.data.model.ChronoSnapshot): StopwatchState {
        accumMs = s.accumMs
        if (s.running && s.anchorEpochMs != null) { anchor = s.anchorEpochMs; now = System.currentTimeMillis(); run = RunState.RUNNING } else run = RunState.STOPPED
        return this
    }
}

/**
 * Chronometer instrument: round face with TWO independent count-up chronometers
 * (top + bottom), each with its own lifecycle. Tap on a counter's half:
 *  long-press = start / reset; double-tap = pause/resume.
 */
@Composable
fun ChronoInstrument(modifier: Modifier = Modifier) {
    val tm = rememberTextMeasurer()
    val top = remember { com.airchecklists.app.di.ServiceLocator.instrumentState("chrono.anl.top") { StopwatchState { s -> com.airchecklists.app.di.ServiceLocator.updateInstruments { it.copy(chronoAnlTop = s.snapshot()) } }.seed(com.airchecklists.app.di.ServiceLocator.instrumentPersist.chronoAnlTop) } }
    val bottom = remember { com.airchecklists.app.di.ServiceLocator.instrumentState("chrono.anl.bot") { StopwatchState { s -> com.airchecklists.app.di.ServiceLocator.updateInstruments { it.copy(chronoAnlBot = s.snapshot()) } }.seed(com.airchecklists.app.di.ServiceLocator.instrumentPersist.chronoAnlBot) } }

    // Single ticker driving both while either runs.
    val anyRunning = top.run == RunState.RUNNING || bottom.run == RunState.RUNNING
    LaunchedEffect(anyRunning) {
        while (top.run == RunState.RUNNING || bottom.run == RunState.RUNNING) {
            val t = System.currentTimeMillis()
            if (top.run == RunState.RUNNING) top.now = t
            if (bottom.run == RunState.RUNNING) bottom.now = t
            kotlinx.coroutines.delay(250)
        }
    }

    Canvas(
        modifier = modifier.fillMaxSize().pointerInput(Unit) {
            fun target(y: Float): StopwatchState = if (y < size.height / 2f) top else bottom
            detectTapGestures(
                onLongPress = { target(it.y).reset() },
                onDoubleTap = { target(it.y).startStop() },
            )
        },
    ) {
        drawChrono(tm, top.elapsed(), top.run != RunState.STOPPED, bottom.elapsed(), bottom.run != RunState.STOPPED)
    }
}

private fun DrawScope.drawChrono(
    tm: TextMeasurer, topMs: Long, topActive: Boolean, botMs: Long, botActive: Boolean,
) {
    val w = size.width; val h = size.height
    // Round black face + bezel (same style as the analog gauges, e.g. altimeter).
    val (cx, cy, r) = gaugeFace()

    compactText(tm, "CHRONO", cx, cy - r * 0.78f, sizeSp = 12f, color = CompactStyle.Dim)
    // Gesture hints (long-press reset, double-tap start/stop) just outside the face.
    drawGestureHints(cx - r * 0.98f, cy - r * 0.98f, hasLongPress = true, hasDoubleTap = true)

    val cellW = r * 1.25f
    val cellH = r * 0.36f
    fun cell(cyc: Float, text: String, active: Boolean) {
        drawRect(Color(0xFF141414), topLeft = Offset(cx - cellW / 2, cyc - cellH / 2), size = Size(cellW, cellH))
        drawRect(Color(0xFF5A5A5A), topLeft = Offset(cx - cellW / 2, cyc - cellH / 2), size = Size(cellW, cellH), style = Stroke(width = 2f))
        compactText(tm, text, cx, cyc, sizeSp = 30f, bold = true, mono = true, color = CompactStyle.Mark)
    }
    cell(cy - r * 0.22f, fmt(topMs), topActive)
    cell(cy + r * 0.30f, fmt(botMs), botActive)
}

/** Format millis as H:MM:SS (or MM:SS under an hour). */
private fun fmt(ms: Long): String {
    val totalSec = (ms / 1000).coerceAtLeast(0)
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
}
