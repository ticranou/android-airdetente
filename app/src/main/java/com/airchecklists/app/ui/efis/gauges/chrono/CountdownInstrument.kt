package com.airchecklists.app.ui.efis.gauges.chrono

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.airchecklists.app.ui.efis.gauges.compact.CompactStyle
import com.airchecklists.app.ui.efis.gauges.compact.compactText
import com.airchecklists.app.ui.efis.gauges.compact.drawGestureHints
import com.airchecklists.app.ui.efis.gauges.gaugeFace

private enum class Cd { STOPPED, RUNNING, PAUSED }

/** One countdown timer's mutable state (self-contained). */
private class TimerState(private val onChange: (TimerState) -> Unit = {}) {
    var setMs by mutableLongStateOf(0L)   // configured duration (0 until the user sets it)
    var remainingMs by mutableLongStateOf(0L)
    var state by mutableStateOf(Cd.STOPPED)
    var anchor by mutableLongStateOf(0L)
    fun remainingNow(): Long =
        if (state == Cd.RUNNING) (remainingMs - (System.currentTimeMillis() - anchor)).coerceAtLeast(0L)
        else remainingMs

    fun start() {
        if (remainingMs <= 0L) remainingMs = setMs   // reload after finish / first start
        if (remainingMs <= 0L) return                // nothing to count down yet
        anchor = System.currentTimeMillis(); state = Cd.RUNNING
    }
    /** Double-tap: start from stopped, else toggle run/pause. */
    fun startStop() {
        when (state) {
            Cd.STOPPED -> start()
            Cd.RUNNING -> { remainingMs = remainingNow(); state = Cd.PAUSED }
            Cd.PAUSED -> { anchor = System.currentTimeMillis(); state = Cd.RUNNING }
        }
        onChange(this)
    }
    /** Called after the keypad confirms a new duration: start counting immediately. */
    fun applyDuration(ms: Long) {
        setMs = ms; remainingMs = ms
        if (ms > 0L) { anchor = System.currentTimeMillis(); state = Cd.RUNNING } else state = Cd.STOPPED
        onChange(this)
    }
    fun snapshot() = com.airchecklists.app.data.model.CountdownSnapshot(
        setMs = setMs,
        remainingMs = if (state == Cd.RUNNING) remainingNow() else remainingMs,
        running = state == Cd.RUNNING,
        anchorEpochMs = if (state == Cd.RUNNING) anchor else null,
    )
    fun seed(s: com.airchecklists.app.data.model.CountdownSnapshot): TimerState {
        setMs = s.setMs; remainingMs = s.remainingMs
        if (s.running && s.anchorEpochMs != null) { anchor = s.anchorEpochMs; state = Cd.RUNNING } else state = Cd.STOPPED
        return this
    }
}

/**
 * Analog (circular) countdown: round face with TWO independent countdown timers
 * (top + bottom cells), like the chronometer. Tap on a half: long-press = start /
 * reset (or open the duration keypad when idle); double-tap = pause/resume.
 */
@Composable
fun CountdownAnalogInstrument(modifier: Modifier = Modifier) {
    val tm = rememberTextMeasurer()
    val top = remember { com.airchecklists.app.di.ServiceLocator.instrumentState("countdown.anl.top") { TimerState { s -> com.airchecklists.app.di.ServiceLocator.updateInstruments { it.copy(countdownAnlTop = s.snapshot()) } }.seed(com.airchecklists.app.di.ServiceLocator.instrumentPersist.countdownAnlTop) } }
    val bottom = remember { com.airchecklists.app.di.ServiceLocator.instrumentState("countdown.anl.bot") { TimerState { s -> com.airchecklists.app.di.ServiceLocator.updateInstruments { it.copy(countdownAnlBot = s.snapshot()) } }.seed(com.airchecklists.app.di.ServiceLocator.instrumentPersist.countdownAnlBot) } }
    var dialogFor by remember { mutableStateOf<TimerState?>(null) }
    var nowTick by remember { mutableLongStateOf(0L) }

    val anyRunning = top.state == Cd.RUNNING || bottom.state == Cd.RUNNING
    LaunchedEffect(anyRunning) {
        while (top.state == Cd.RUNNING || bottom.state == Cd.RUNNING) {
            nowTick = System.currentTimeMillis()
            if (top.state == Cd.RUNNING && top.remainingNow() <= 0L) { top.remainingMs = 0L; top.state = Cd.STOPPED }
            if (bottom.state == Cd.RUNNING && bottom.remainingNow() <= 0L) { bottom.remainingMs = 0L; bottom.state = Cd.STOPPED }
            kotlinx.coroutines.delay(250)
        }
    }

    Canvas(
        modifier = modifier.fillMaxSize().pointerInput(Unit) {
            fun target(y: Float): TimerState = if (y < size.height / 2f) top else bottom
            detectTapGestures(
                onLongPress = { dialogFor = target(it.y) },       // define duration
                onDoubleTap = { target(it.y).startStop() },
            )
        },
    ) {
        @Suppress("UNUSED_EXPRESSION") nowTick
        drawCountdownRound(
            tm,
            top.setMs, top.remainingNow(), Color(0xFFE8843A),
            bottom.setMs, bottom.remainingNow(), Color(0xFF2E9BE6),
        )
    }

    dialogFor?.let { t ->
        DurationDialog(
            initialMs = t.setMs,
            onDismiss = { dialogFor = null },
            onConfirm = { ms -> t.applyDuration(ms); dialogFor = null },
            onClear = { t.applyDuration(0L); dialogFor = null },
        )
    }
}

private fun DrawScope.drawCountdownRound(
    tm: TextMeasurer,
    topSet: Long, topRem: Long, topColor: Color,
    botSet: Long, botRem: Long, botColor: Color,
) {
    val w = size.width; val h = size.height
    // Round black face + bezel (same style as the analog gauges, e.g. altimeter).
    val (cx, cy, r) = gaugeFace()
    compactText(tm, "REBOURS", cx, cy - r * 0.78f, sizeSp = 12f, color = CompactStyle.Dim)
    drawGestureHints(cx - r * 0.98f, cy - r * 0.98f, hasLongPress = true, hasDoubleTap = true)

    val cellW = r * 1.25f
    val cellH = r * 0.34f
    fun timer(cyc: Float, setMs: Long, remMs: Long, color: Color) {
        val unset = setMs <= 0L
        // Consigne (configured duration) placed ABOVE the remaining value.
        if (!unset) compactText(tm, fmtMin(setMs), cx, cyc - r * 0.24f, sizeSp = 13f, bold = true, color = color)
        // Value (remaining)
        val txt = if (!unset && remMs <= 0L) Color(0xFFFF4136) else CompactStyle.Mark
        compactText(tm, if (unset) "--:--" else fmt(remMs), cx, cyc, sizeSp = 26f, bold = true, mono = true, color = txt)
        // Thin progress bar below
        val frac = if (setMs > 0) (1f - remMs.toFloat() / setMs).coerceIn(0f, 1f) else 0f
        val barY = cyc + r * 0.19f
        val barW = r * 1.20f
        drawRect(Color(0xFF3A3A3A), topLeft = Offset(cx - barW / 2, barY - 5f), size = Size(barW, 10f))
        drawRect(color, topLeft = Offset(cx - barW / 2, barY - 5f), size = Size(barW * frac, 10f))
    }
    timer(cy - r * 0.26f, topSet, topRem, topColor)
    timer(cy + r * 0.44f, botSet, botRem, botColor)
}

private fun fmt(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return "%02d:%02d".format(s / 60, s % 60)   // MM:SS (no hours for a countdown)
}
private fun fmtMin(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return "%02d:%02d".format(s / 60, s % 60)
}

/**
 * Big-digit duration entry: a numeric keypad feeding an MMSS buffer shown as
 * MM:SS. Digits shift in from the right (type 0-8-0-0 → 08:00) so minutes flow
 * automatically into seconds — easy to use in flight.
 */
@Composable
internal fun DurationDialog(initialMs: Long, onDismiss: () -> Unit, onConfirm: (Long) -> Unit, onClear: (() -> Unit)? = null) {
    // Start empty; the user types up to 4 digits (MMSS), filling from the right.
    var buf by remember { mutableStateOf("") }

    val padded = buf.padStart(4, '0')
    val mm = padded.substring(0, 2).toInt()
    val ss = padded.substring(2, 4).toInt()
    val valid = ss <= 59 && (mm + ss) > 0

    fun push(d: Char) { if (buf.length < 4) buf += d }
    fun back() { buf = buf.dropLast(1) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Durée") },
        text = {
            Column(horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
                // Big MM:SS display.
                Text(
                    "%02d:%02d".format(mm, ss),
                    style = MaterialTheme.typography.displayMedium,
                    color = if (valid) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                )
                Spacer(Modifier.height(12.dp))
                // 3x4 numeric keypad.
                val rows = listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"), listOf("C", "0", "⌫"))
                rows.forEach { row ->
                    Row {
                        row.forEach { key ->
                            TextButton(
                                onClick = {
                                    when (key) {
                                        "C" -> buf = ""
                                        "⌫" -> back()
                                        else -> push(key[0])
                                    }
                                },
                                modifier = Modifier.width(72.dp).height(56.dp),
                            ) {
                                Text(key, style = MaterialTheme.typography.headlineSmall)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm((mm * 60L + ss) * 1000L) }, enabled = valid) { Text("OK") }
        },
        dismissButton = {
            Row {
                if (onClear != null) {
                    TextButton(onClick = onClear) { Text("Effacer") }
                    Spacer(Modifier.width(4.dp))
                }
                TextButton(onClick = onDismiss) { Text("Annuler") }
            }
        },
    )
}
