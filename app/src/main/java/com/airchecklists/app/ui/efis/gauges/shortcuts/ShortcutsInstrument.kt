package com.airchecklists.app.ui.efis.gauges.shortcuts

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.Canvas
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.airchecklists.app.data.model.EfisInstrument
import com.airchecklists.app.di.ServiceLocator
import com.airchecklists.app.ui.components.InstrumentPickerDialog
import com.airchecklists.app.ui.efis.gauges.InstrumentSlot
import com.airchecklists.app.ui.efis.gauges.LocalGaugeBezel
import com.airchecklists.app.ui.efis.gauges.compact.CompactStyle
import com.airchecklists.app.ui.efis.gauges.compact.compactText
import com.airchecklists.app.ui.efis.gauges.compact.drawGestureHints
import com.airchecklists.app.ui.efis.gauges.drawNumTitleBar

private const val N = 3

/**
 * CMNSCT — Raccourcis : 3 cellules côte à côte, chacune représentant un instrument
 * choisi par l'utilisateur. Tap sur une cellule → ouvre l'instrument dans une
 * boîte de dialogue plein écran. Long-press → config des 3 raccourcis.
 */
@Composable
fun ShortcutsInstrument(modifier: Modifier = Modifier) {
    val tm = rememberTextMeasurer()
    val bezel = LocalGaugeBezel.current
    val prefs = ServiceLocator.preferences.preferences.collectAsStateWithLifecycle()
    val state by ServiceLocator.efisProvider.state.collectAsStateWithLifecycle()
    val speedUnit = prefs.value.efisSpeedUnit
    val altUnit = prefs.value.altitudeUnit
    val speedArcs = remember { null } // no arcs for shortcuts context

    // Persist slots via InstrumentPersistState.shortcutSlots.
    val slots = remember(prefs.value.instruments.shortcutSlots) {
        val raw = prefs.value.instruments.shortcutSlots
        // Ensure exactly 3 entries.
        List(N) { i -> raw.getOrElse(i) { EfisInstrument.NONE } }
    }

    // Which instrument is open in the full-screen dialog (-1 = none).
    var openIdx by remember { mutableIntStateOf(-1) }
    // Which slot is being configured (picker dialog).
    var configIdx by remember { mutableIntStateOf(-1) }

    Canvas(
        modifier = modifier.fillMaxSize().pointerInput(slots) {
            detectTapGestures(
                onLongPress = { pos ->
                    val idx = (pos.x / (size.width.toFloat() / N)).toInt().coerceIn(0, N - 1)
                    configIdx = idx
                },
                onTap = { pos ->
                    val idx = (pos.x / (size.width.toFloat() / N)).toInt().coerceIn(0, N - 1)
                    if (slots[idx] != EfisInstrument.NONE) openIdx = idx
                },
            )
        },
    ) {
        val w = size.width; val h = size.height
        val headerH = 20.dp.toPx().coerceAtMost(h * 0.4f)
        drawRect(CompactStyle.Bg, size = size)
        drawNumTitleBar(bezel, w, headerH)
        drawRect(Color(0xFF3A3A3A), size = size, style = Stroke(width = 2f))
        compactText(tm, "RACCOURCIS", w / 2f, headerH / 2f, sizeSp = 12f, color = CompactStyle.Dim)
        drawGestureHints(6f, headerH / 2f, hasLongPress = true, hasDoubleTap = false)

        val mainTop = headerH
        val cellH = (h - mainTop) * 0.33f
        val cy = mainTop + (h - mainTop) / 2f
        val slotW = w / N

        // Vertical separators.
        for (i in 1 until N) {
            val x = slotW * i
            drawLine(Color(0xFF3A3A3A), Offset(x, cy - cellH / 2), Offset(x, cy + cellH / 2), strokeWidth = 1.5f)
        }

        // Labels for each slot.
        for (i in 0 until N) {
            val cx = slotW * i + slotW / 2f
            val instr = slots[i]
            if (instr == EfisInstrument.NONE) {
                // Empty slot: show a dim dash.
                compactText(tm, "—", cx, cy, sizeSp = 20f, color = CompactStyle.Dim)
            } else {
                // Show the instrument code (prefix, e.g. "ANLCAP").
                val label = efisShortCode(instr)
                compactText(tm, label, cx, cy - cellH * 0.14f, sizeSp = 14f, bold = true, color = CompactStyle.Mark)
                // Draw a small "open" indicator arrow below the label.
                val arrowY = cy + cellH * 0.22f
                val aw = slotW * 0.18f
                drawLine(CompactStyle.Dim, Offset(cx - aw, arrowY), Offset(cx, arrowY + aw * 0.7f), strokeWidth = 1.5f)
                drawLine(CompactStyle.Dim, Offset(cx + aw, arrowY), Offset(cx, arrowY + aw * 0.7f), strokeWidth = 1.5f)
            }
        }
    }

    // Full-screen instrument dialog.
    if (openIdx in 0 until N) {
        val instr = slots[openIdx]
        Dialog(
            onDismissRequest = { openIdx = -1 },
            properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = true),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.90f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.Black),
                contentAlignment = Alignment.Center,
            ) {
                // Render the instrument in a square aspect (most round gauges expect it).
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(4.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    InstrumentSlot(
                        instrument = instr,
                        state = state,
                        speedUnit = speedUnit,
                        showValues = true,
                        speedArcs = speedArcs,
                        altUnit = altUnit,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }

    // Config dialog: pick instruments for each slot in sequence.
    if (configIdx in 0 until N) {
        InstrumentPickerDialog(
            current = slots[configIdx],
            onDismiss = { configIdx = -1 },
            onSelect = { chosen ->
                val newSlots = slots.toMutableList().also { it[configIdx] = chosen }
                ServiceLocator.updateInstruments { it.copy(shortcutSlots = newSlots) }
                // Advance to next slot, or close.
                configIdx = if (configIdx < N - 1) configIdx + 1 else -1
            },
        )
    }
}

/** Returns the instrument's code prefix (everything before " - " in the label). */
private fun efisShortCode(instr: EfisInstrument): String {
    // The enum name maps directly to the code (e.g. ANLCAP, NUMCHR, CMNSCT…).
    return instr.name
}
