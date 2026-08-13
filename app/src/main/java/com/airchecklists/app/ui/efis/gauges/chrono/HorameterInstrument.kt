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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.airchecklists.app.ui.efis.gauges.compact.CompactStyle
import com.airchecklists.app.ui.efis.gauges.compact.compactText
import com.airchecklists.app.ui.efis.gauges.compact.drawGestureHints
import com.airchecklists.app.ui.efis.gauges.gaugeFace

/** One hour-meter reading, stored as hundredths of an hour (e.g. 106.22 -> 10622),
 *  or null when not entered yet. */
private class HourReading {
    var hundredths by mutableStateOf<Int?>(null)
    /** "NNN.NN" for display, or a placeholder when empty. */
    fun display(): String = hundredths?.let { "%d.%02d".format(it / 100, it % 100) } ?: "---.--"
}

/**
 * Hour-meter ("Horamètre") instrument: round face with TWO cells, each holding a
 * reading like 106.22 (hundredths of an hour). Tap a cell to type its value on a
 * numeric keypad. Once both are set, the flight duration between them is shown
 * below (e.g. 106.22 → 106.34 = 0.12 h = 72' = 1h12).
 */
@Composable
fun HorameterInstrument(modifier: Modifier = Modifier) {
    val tm = rememberTextMeasurer()
    val top = remember { com.airchecklists.app.di.ServiceLocator.instrumentState("horameter.anl.top") { HourReading().also { it.hundredths = com.airchecklists.app.di.ServiceLocator.instrumentPersist.horameterAnl.a } } }
    val bottom = remember { com.airchecklists.app.di.ServiceLocator.instrumentState("horameter.anl.bot") { HourReading().also { it.hundredths = com.airchecklists.app.di.ServiceLocator.instrumentPersist.horameterAnl.b } } }
    var dialogFor by remember { mutableStateOf<HourReading?>(null) }
    fun persist() {
        com.airchecklists.app.di.ServiceLocator.updateInstruments {
            it.copy(horameterAnl = com.airchecklists.app.data.model.HorameterSnapshot(a = top.hundredths, b = bottom.hundredths))
        }
    }

    Canvas(
        modifier = modifier.fillMaxSize().pointerInput(Unit) {
            detectTapGestures(
                onLongPress = { dialogFor = if (it.y < size.height / 2f) top else bottom },
            )
        },
    ) {
        drawHorameter(tm, top.display(), bottom.display(), durationLabel(top.hundredths, bottom.hundredths))
    }

    dialogFor?.let { reading ->
        HourReadingDialog(
            initial = reading.hundredths,
            onDismiss = { dialogFor = null },
            onConfirm = { value -> reading.hundredths = value; persist(); dialogFor = null },
            onClear = { reading.hundredths = null; persist(); dialogFor = null },
        )
    }
}

/** "72' – 1h12" between two readings, or null while either is missing / invalid.
 *  A reading is HH.DD where each unit of the DD decimals counts a 6-minute step,
 *  so a raw difference of N (in hundredths) is N × 6 minutes:
 *  106.12 → 106.18 = 6 → 36 min ; 106.22 → 106.34 = 12 → 72 min = 1h12. */
private fun durationLabel(top: Int?, bottom: Int?): String? {
    if (top == null || bottom == null) return null
    val delta = bottom - top          // raw difference, in hundredth-units
    if (delta <= 0) return null
    val totalMin = delta * 6          // each unit = 6 minutes
    val h = totalMin / 60
    val m = totalMin % 60
    val hm = if (h > 0) "${h}h%02d".format(m) else "${m}min"
    return "$totalMin' – $hm"
}

private fun DrawScope.drawHorameter(tm: TextMeasurer, topText: String, botText: String, duration: String?) {
    // Round black face + bezel (same style as the chronometer).
    val (cx, cy, r) = gaugeFace()
    compactText(tm, "HORAMETRE", cx, cy - r * 0.78f, sizeSp = 12f, color = CompactStyle.Dim)
    drawGestureHints(cx - r * 0.98f, cy - r * 0.98f, hasLongPress = true, hasDoubleTap = false)

    val cellW = r * 1.25f
    val cellH = r * 0.34f
    fun cell(cyc: Float, text: String) {
        drawRect(Color(0xFF141414), topLeft = Offset(cx - cellW / 2, cyc - cellH / 2), size = Size(cellW, cellH))
        drawRect(Color(0xFF5A5A5A), topLeft = Offset(cx - cellW / 2, cyc - cellH / 2), size = Size(cellW, cellH), style = Stroke(width = 2f))
        compactText(tm, text, cx, cyc, sizeSp = 30f, bold = true, mono = true, color = CompactStyle.Mark)
    }
    cell(cy - r * 0.20f, topText)
    cell(cy + r * 0.28f, botText)

    // Flight duration below the two cells (orange, like the mockup).
    if (duration != null) {
        compactText(tm, duration, cx, cy + r * 0.66f, sizeSp = 15f, bold = true, color = CompactStyle.Accent2)
    }
}

/**
 * 5-digit hour-meter entry (NNN.NN): a numeric keypad feeding a buffer, filled
 * from the right so the last two digits are the hundredths. Mirrors the countdown
 * DurationDialog style.
 */
@Composable
private fun HourReadingDialog(
    initial: Int?,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit,
    onClear: () -> Unit,
) {
    // Up to 5 digits (NNNNN), filled from the right; last two are the decimals.
    var buf by remember { mutableStateOf(initial?.toString()?.takeIf { it.isNotEmpty() } ?: "") }

    val value = buf.toIntOrNull()
    val valid = value != null && value > 0
    val padded = buf.padStart(3, '0')
    val displayInt = padded.dropLast(2).ifEmpty { "0" }
    val displayDec = padded.takeLast(2)

    fun push(d: Char) { if (buf.length < 5) buf += d }
    fun back() { buf = buf.dropLast(1) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Horamètre") },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "$displayInt.$displayDec",
                    style = MaterialTheme.typography.displayMedium,
                    color = if (valid) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                )
                Spacer(Modifier.height(12.dp))
                val rows = listOf(
                    listOf("1", "2", "3"), listOf("4", "5", "6"),
                    listOf("7", "8", "9"), listOf("C", "0", "⌫"),
                )
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
            TextButton(onClick = { value?.let(onConfirm) }, enabled = valid) { Text("OK") }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onClear) { Text("Effacer") }
                TextButton(onClick = onDismiss) { Text("Annuler") }
            }
        },
    )
}
