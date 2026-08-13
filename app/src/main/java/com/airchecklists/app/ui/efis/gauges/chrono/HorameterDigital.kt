package com.airchecklists.app.ui.efis.gauges.chrono

import com.airchecklists.app.ui.efis.gauges.drawNumTitleBar
import com.airchecklists.app.ui.efis.gauges.LocalGaugeBezel
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.rememberTextMeasurer
import com.airchecklists.app.ui.efis.gauges.compact.CompactStyle
import com.airchecklists.app.ui.efis.gauges.compact.compactText
import com.airchecklists.app.ui.efis.gauges.compact.drawGestureHints

/** One hour-meter reading, stored as hundredths of an hour (106.22 -> 10622). */
private class HourReadingD {
    var hundredths by mutableStateOf<Int?>(null)
    fun display(): String = hundredths?.let { "%d.%02d".format(it / 100, it % 100) } ?: "---.--"
}

/**
 * Digital hour-meter (single height, full width): fixed 20dp "HORAMETRE" header,
 * then two reading cells (Départ / Arrivée) with the computed flight duration shown
 * in the middle. Tap a cell to type its value. A raw difference of N (hundredths)
 * counts N × 6 minutes (e.g. 106.22 → 106.34 = 12 → 72' → 1h12).
 */
@Composable
fun HorameterDigital(modifier: Modifier = Modifier) {
    val tm = rememberTextMeasurer()
    val bezel = LocalGaugeBezel.current
    val start = remember { com.airchecklists.app.di.ServiceLocator.instrumentState("horameter.num.start") { HourReadingD().also { it.hundredths = com.airchecklists.app.di.ServiceLocator.instrumentPersist.horameterNum.a } } }
    val end = remember { com.airchecklists.app.di.ServiceLocator.instrumentState("horameter.num.end") { HourReadingD().also { it.hundredths = com.airchecklists.app.di.ServiceLocator.instrumentPersist.horameterNum.b } } }
    var dialogFor by remember { mutableStateOf<HourReadingD?>(null) }

    Canvas(
        modifier = modifier.fillMaxSize().pointerInput(Unit) {
            detectTapGestures(onLongPress = { dialogFor = if (it.x < size.width / 2f) start else end })
        },
    ) {
        val w = size.width; val h = size.height
        val headerH = 20.dp.toPx().coerceAtMost(h * 0.4f)
        drawRect(CompactStyle.Bg, size = size)
        drawNumTitleBar(bezel, w, headerH)
        drawRect(Color(0xFF3A3A3A), size = size, style = Stroke(width = 2f))
        compactText(tm, "HORAMETRE", w / 2f, headerH / 2f, sizeSp = 12f, color = CompactStyle.Dim)
        drawGestureHints(6f, headerH / 2f, hasLongPress = true, hasDoubleTap = false)

        val mainTop = headerH
        val mainH = h - mainTop
        // Cells sit a bit lower, leaving room for a label ABOVE each one.
        val cellW = w * 0.32f
        val cellH = mainH * 0.52f
        val cy = mainTop + mainH * 0.58f
        fun cell(cx: Float, label: String, value: String) {
            // Label centred above the cell.
            compactText(tm, label, cx, cy - cellH / 2 - mainH * 0.14f, sizeSp = 11f, color = CompactStyle.Dim)
            drawRoundRect(Color(0xFF1C1C1C), topLeft = Offset(cx - cellW / 2, cy - cellH / 2),
                size = Size(cellW, cellH), cornerRadius = CornerRadius(6f, 6f))
            drawRoundRect(Color(0xFF5A5A5A), topLeft = Offset(cx - cellW / 2, cy - cellH / 2),
                size = Size(cellW, cellH), cornerRadius = CornerRadius(6f, 6f), style = Stroke(width = 1.5f))
            compactText(tm, value, cx, cy, sizeSp = 24f, bold = true, color = CompactStyle.Mark)
        }
        cell(w * 0.22f, "Départ", start.display())
        cell(w * 0.78f, "Arrivée", end.display())

        // Duration result in the middle (orange): two well-spaced, larger lines.
        val dur = durationD(start.hundredths, end.hundredths)
        if (dur != null) {
            compactText(tm, dur.first, w / 2f, cy - 26f, sizeSp = 18f, bold = true, color = CompactStyle.Accent2)
            compactText(tm, dur.second, w / 2f, cy + 26f, sizeSp = 18f, bold = true, color = CompactStyle.Accent2)
        } else {
            compactText(tm, "—", w / 2f, cy, sizeSp = 16f, color = CompactStyle.Dim)
        }
    }

    fun persist() {
        com.airchecklists.app.di.ServiceLocator.updateInstruments {
            it.copy(horameterNum = com.airchecklists.app.data.model.HorameterSnapshot(a = start.hundredths, b = end.hundredths))
        }
    }

    dialogFor?.let { reading ->
        HourReadingDialogD(
            initial = reading.hundredths,
            onDismiss = { dialogFor = null },
            onConfirm = { v -> reading.hundredths = v; persist(); dialogFor = null },
            onClear = { reading.hundredths = null; persist(); dialogFor = null },
        )
    }
}

/** ("72m","1h12m") or null. Raw difference in hundredths × 6 minutes. */
private fun durationD(start: Int?, end: Int?): Pair<String, String>? {
    if (start == null || end == null) return null
    val delta = end - start
    if (delta <= 0) return null
    val totalMin = delta * 6
    val h = totalMin / 60
    val m = totalMin % 60
    val hm = if (h > 0) "${h}h%02dm".format(m) else "${m}m"
    return "${totalMin}m" to hm
}

@Composable
private fun HourReadingDialogD(
    initial: Int?,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit,
    onClear: () -> Unit,
) {
    var buf by remember { mutableStateOf(initial?.toString() ?: "") }
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
                                onClick = { when (key) { "C" -> buf = ""; "⌫" -> back(); else -> push(key[0]) } },
                                modifier = Modifier.width(72.dp).height(56.dp),
                            ) { Text(key, style = MaterialTheme.typography.headlineSmall) }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { value?.let(onConfirm) }, enabled = valid) { Text("OK") } },
        dismissButton = {
            Row {
                TextButton(onClick = onClear) { Text("Effacer") }
                TextButton(onClick = onDismiss) { Text("Annuler") }
            }
        },
    )
}
