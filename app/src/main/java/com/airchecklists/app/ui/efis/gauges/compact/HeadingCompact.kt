package com.airchecklists.app.ui.efis.gauges.compact

import com.airchecklists.app.ui.efis.gauges.drawNumTitleBar
import com.airchecklists.app.ui.efis.gauges.LocalGaugeBezel
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.airchecklists.app.di.ServiceLocator
import com.airchecklists.app.ui.components.HeadingEntryDialog

/** Compact heading: a fixed 20dp title header over the refined EFIS heading tape
 *  (labels + fine ticks, highlighted current-heading cell, magenta target cursor).
 *  Long-press sets/clears the magenta target heading. */
@Composable
fun HeadingCompact(headingDeg: Float, showValue: Boolean, modifier: Modifier = Modifier) {
    val tm = rememberTextMeasurer()
    val targetHeading by ServiceLocator.targetHeading.collectAsStateWithLifecycle()
    var showDialog by remember { mutableStateOf(false) }
    val bezel = LocalGaugeBezel.current

    Canvas(
        modifier = modifier.fillMaxSize().pointerInput(Unit) {
            detectTapGestures(onLongPress = { showDialog = true })
        },
    ) {
        val w = size.width
        val h = size.height
        val headerH = 20.dp.toPx().coerceAtMost(h * 0.4f)
        drawRect(CompactStyle.Bg, size = size)
        drawNumTitleBar(bezel, w, headerH)
        drawRect(Color(0xFF3A3A3A), size = size, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2f))
        compactText(tm, "CONSERVATEUR", w / 2f, headerH / 2f, sizeSp = 12f, color = CompactStyle.Dim)
        drawGestureHints(6f, headerH / 2f, hasLongPress = true, hasDoubleTap = false)

        val hdg = ((headingDeg % 360f) + 360f) % 360f
        val tape = Rect(0f, headerH, w, h)
        efisHeadingTape(tm, tape, hdg, showValue, targetHeading)
    }

    if (showDialog) {
        HeadingEntryDialog(
            initial = targetHeading,
            onDismiss = { showDialog = false },
            onConfirm = { hdg -> ServiceLocator.targetHeading.value = hdg; showDialog = false },
            onClear = { ServiceLocator.targetHeading.value = null; showDialog = false },
        )
    }
}
