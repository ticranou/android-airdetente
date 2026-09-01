package com.airchecklists.app.ui.efis.gauges.shortcuts

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.airchecklists.app.data.model.EfisInstrument
import com.airchecklists.app.di.ServiceLocator
import com.airchecklists.app.ui.components.InstrumentPickerDialog
import com.airchecklists.app.ui.components.efisInstrumentLabel
import com.airchecklists.app.ui.efis.gauges.InstrumentSlot
import com.airchecklists.app.ui.efis.gauges.LocalGaugeBezel
import com.airchecklists.app.ui.efis.gauges.compact.CompactStyle
import com.airchecklists.app.ui.efis.gauges.compact.compactText
import com.airchecklists.app.ui.efis.gauges.compact.drawGestureHints
import com.airchecklists.app.ui.efis.gauges.drawNumTitleBar

private const val N = 3

@Composable
fun ShortcutsInstrument(modifier: Modifier = Modifier) {
    val tm = rememberTextMeasurer()
    val bezel = LocalGaugeBezel.current
    val prefs = ServiceLocator.preferences.preferences.collectAsStateWithLifecycle()
    val state by ServiceLocator.efisProvider.state.collectAsStateWithLifecycle()
    val speedUnit = prefs.value.efisSpeedUnit
    val altUnit = prefs.value.altitudeUnit
    val speedArcs = remember { null }

    val slots = remember(prefs.value.instruments.shortcutSlots) {
        val raw = prefs.value.instruments.shortcutSlots
        List(N) { i -> raw.getOrElse(i) { EfisInstrument.NONE } }
    }

    // Resolve human-readable labels in Composable scope (can't call stringResource in DrawScope).
    // Take only the part after " - " (e.g. "ANLCAP - Conservateur" → "Conservateur").
    val labels = slots.map { instr ->
        if (instr == EfisInstrument.NONE) "-----"
        else efisInstrumentLabel(instr).substringAfter(" - ", missingDelimiterValue = instr.name)
    }

    var openIdx by remember { mutableIntStateOf(-1) }
    var configIdx by remember { mutableIntStateOf(-1) }

    Canvas(
        modifier = modifier.fillMaxWidth().height(52.dp).pointerInput(slots) {
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
        val w = size.width
        val h = size.height
        val headerH = 20.dp.toPx().coerceAtMost(h * 0.4f)

        drawRect(CompactStyle.Bg, size = size)
        drawNumTitleBar(bezel, w, headerH)
        drawRect(Color(0xFF3A3A3A), size = size, style = Stroke(width = 2f))
        compactText(tm, "RACCOURCIS", w / 2f, headerH / 2f, sizeSp = 12f, color = CompactStyle.Dim)
        drawGestureHints(6f, headerH / 2f, hasLongPress = true, hasDoubleTap = false)

        val slotW = w / N
        val bodyCy = headerH + (h - headerH) / 2f

        // Vertical separators spanning the full body area.
        for (i in 1 until N) {
            val x = slotW * i
            drawLine(Color(0xFF3A3A3A), Offset(x, headerH), Offset(x, h), strokeWidth = 1.5f)
        }

        // Label for each slot, centered in the body.
        for (i in 0 until N) {
            val cx = slotW * i + slotW / 2f
            val label = labels[i]
            val isEmpty = slots[i] == EfisInstrument.NONE
            compactText(
                tm, label, cx, bodyCy,
                sizeSp = if (isEmpty) 16f else 15f,
                bold = !isEmpty,
                color = if (isEmpty) CompactStyle.Dim else CompactStyle.Mark,
            )
        }
    }

    // Full-screen instrument dialog.
    if (openIdx in 0 until N) {
        val instr = slots[openIdx]
        Dialog(
            onDismissRequest = { openIdx = -1 },
            properties = DialogProperties(
                usePlatformDefaultWidth = false,
                dismissOnClickOutside = true,
                decorFitsSystemWindows = false,
            ),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black),
                contentAlignment = Alignment.Center,
            ) {
                InstrumentSlot(
                    instrument = instr,
                    state = state,
                    speedUnit = speedUnit,
                    showValues = true,
                    speedArcs = speedArcs,
                    altUnit = altUnit,
                    modifier = Modifier.fillMaxSize(),
                )
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
                configIdx = if (configIdx < N - 1) configIdx + 1 else -1
            },
        )
    }
}
