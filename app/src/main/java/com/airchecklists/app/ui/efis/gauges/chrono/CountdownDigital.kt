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

private enum class Cd2 { STOPPED, RUNNING, PAUSED }

/** One count-down timer's state (self-contained). */
private class Countdown1 {
    var setMs by mutableLongStateOf(0L)
    var remainingMs by mutableLongStateOf(0L)
    var run by mutableStateOf(Cd2.STOPPED)
    var anchor by mutableLongStateOf(0L)
    fun remainingNow(): Long =
        if (run == Cd2.RUNNING) (remainingMs - (System.currentTimeMillis() - anchor)).coerceAtLeast(0L) else remainingMs
    fun startStop() {
        when (run) {
            Cd2.STOPPED -> {
                if (remainingMs <= 0L) remainingMs = setMs
                if (remainingMs <= 0L) return
                anchor = System.currentTimeMillis(); run = Cd2.RUNNING
            }
            Cd2.RUNNING -> { remainingMs = remainingNow(); run = Cd2.PAUSED }
            Cd2.PAUSED -> { anchor = System.currentTimeMillis(); run = Cd2.RUNNING }
        }
        persist()
    }
    /** Apply a new duration and start counting down immediately. */
    fun applyDuration(ms: Long) {
        setMs = ms; remainingMs = ms
        if (ms > 0L) { anchor = System.currentTimeMillis(); run = Cd2.RUNNING } else run = Cd2.STOPPED
        persist()
    }
    private fun persist() {
        com.airchecklists.app.di.ServiceLocator.updateInstruments {
            it.copy(countdownNum = com.airchecklists.app.data.model.CountdownSnapshot(
                setMs = setMs,
                remainingMs = if (run == Cd2.RUNNING) remainingNow() else remainingMs,
                running = run == Cd2.RUNNING,
                anchorEpochMs = if (run == Cd2.RUNNING) anchor else null,
            ))
        }
    }
    fun seed(s: com.airchecklists.app.data.model.CountdownSnapshot): Countdown1 {
        setMs = s.setMs; remainingMs = s.remainingMs
        if (s.running && s.anchorEpochMs != null) { anchor = s.anchorEpochMs; run = Cd2.RUNNING } else run = Cd2.STOPPED
        return this
    }
}

/**
 * Digital count-down (single height, half width): fixed 20dp "REBOURS" header with
 * the configured duration shown at the right of the title bar, a dark remaining-time
 * cell, and a progress bar beneath. Long-press opens the duration keypad (starts on
 * OK); once set, long-press = start / reset, double-tap = pause/resume.
 */
@Composable
fun CountdownDigital(modifier: Modifier = Modifier) {
    val tm = rememberTextMeasurer()
    val bezel = LocalGaugeBezel.current
    val cd = remember { com.airchecklists.app.di.ServiceLocator.instrumentState("countdown.num") { Countdown1().seed(com.airchecklists.app.di.ServiceLocator.instrumentPersist.countdownNum) } }
    var showDialog by remember { mutableStateOf(false) }
    var nowTick by remember { mutableLongStateOf(0L) }

    LaunchedEffect(cd.run) {
        while (cd.run == Cd2.RUNNING) {
            if (cd.remainingNow() <= 0L) { cd.remainingMs = 0L; cd.run = Cd2.STOPPED }
            nowTick = System.currentTimeMillis()
            kotlinx.coroutines.delay(250)
        }
    }

    Canvas(
        modifier = modifier.fillMaxSize().pointerInput(Unit) {
            detectTapGestures(
                onLongPress = { showDialog = true },
                onDoubleTap = { cd.startStop() },
            )
        },
    ) {
        @Suppress("UNUSED_EXPRESSION") nowTick
        val w = size.width; val h = size.height
        val headerH = 20.dp.toPx().coerceAtMost(h * 0.4f)
        drawRect(CompactStyle.Bg, size = size)
        drawNumTitleBar(bezel, w, headerH)
        drawRect(Color(0xFF3A3A3A), size = size, style = Stroke(width = 2f))
        // Title + configured duration (consigne) on the right of the title bar.
        compactText(tm, "REBOURS", w * 0.40f, headerH / 2f, sizeSp = 12f, color = CompactStyle.Dim)
        compactText(tm, if (cd.setMs <= 0L) "--:--" else fmtMs(cd.setMs), w * 0.86f, headerH / 2f, sizeSp = 12f, bold = true, color = Color(0xFF2E9BE6))
        drawGestureHints(6f, headerH / 2f, hasLongPress = true, hasDoubleTap = true)

        val rem = cd.remainingNow()
        val mainTop = headerH
        val mainH = h - headerH
        // Same cell height AND same top gap as the chrono (cell top at mainTop + 0.19*mainH).
        val cellW = w * 0.82f
        val cellH = mainH * 0.62f
        val cy = mainTop + mainH * 0.50f
        drawRoundRect(Color(0xFF1C1C1C), topLeft = Offset(w / 2f - cellW / 2, cy - cellH / 2),
            size = Size(cellW, cellH), cornerRadius = CornerRadius(6f, 6f))
        drawRoundRect(Color(0xFF5A5A5A), topLeft = Offset(w / 2f - cellW / 2, cy - cellH / 2),
            size = Size(cellW, cellH), cornerRadius = CornerRadius(6f, 6f), style = Stroke(width = 1.5f))
        val unset = cd.setMs <= 0L
        val col = if (!unset && rem <= 0L) Color(0xFFFF4136) else CompactStyle.Mark
        compactText(tm, if (unset) "--:--" else fmtMs(rem), w / 2f, cy, sizeSp = 26f, bold = true, mono = true, color = col)

        // Progress bar directly under the cell, slightly thicker for visibility.
        val frac = if (cd.setMs > 0) (1f - rem.toFloat() / cd.setMs).coerceIn(0f, 1f) else 0f
        val barLeft = w / 2f - cellW / 2
        val barH = 9f
        val barY = cy + cellH / 2 + 8f
        drawRect(Color(0xFF3A3A3A), topLeft = Offset(barLeft, barY), size = Size(cellW, barH))
        drawRect(Color(0xFF2E9BE6), topLeft = Offset(barLeft, barY), size = Size(cellW * frac, barH))
    }

    if (showDialog) {
        DurationDialog(
            initialMs = cd.setMs,
            onDismiss = { showDialog = false },
            onConfirm = { ms -> cd.applyDuration(ms); showDialog = false },
            onClear = { cd.applyDuration(0L); showDialog = false },
        )
    }
}

/** MM:SS (no hours for a countdown). */
private fun fmtMs(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return "%02d:%02d".format(s / 60, s % 60)
}

/**
 * Arm the shared numeric count-down (NUMCWN) with [ms] and start it immediately.
 * Uses the same shared instance the instrument renders, so an on-screen NUMCWN
 * updates live; the value is also persisted for a NUMCWN composed later.
 * Callable from anywhere (e.g. the map's Goto Direct).
 */
fun armNumCountdown(ms: Long) {
    val cd = com.airchecklists.app.di.ServiceLocator.instrumentState("countdown.num") {
        Countdown1().seed(com.airchecklists.app.di.ServiceLocator.instrumentPersist.countdownNum)
    }
    cd.applyDuration(ms)
}
