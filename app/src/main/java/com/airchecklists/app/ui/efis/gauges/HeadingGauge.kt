package com.airchecklists.app.ui.efis.gauges

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.airchecklists.app.di.ServiceLocator
import com.airchecklists.app.ui.components.HeadingEntryDialog
import com.airchecklists.app.ui.efis.gauges.compact.drawGestureHints

/** Directional gyro: a compass card rotating so the current heading is at top,
 *  with a fixed aircraft silhouette. Long-press sets a magenta heading bug. */
@Composable
fun HeadingGauge(headingDeg: Float, showValue: Boolean, modifier: Modifier = Modifier) {
    val tm = rememberTextMeasurer()
    val bezel = LocalGaugeBezel.current
    val targetHeading by ServiceLocator.targetHeading.collectAsStateWithLifecycle()
    var showDialog by remember { mutableStateOf(false) }

    Canvas(
        modifier = modifier.fillMaxSize().pointerInput(Unit) {
            detectTapGestures(onLongPress = { showDialog = true })
        },
    ) {
        val (cx, cy, r) = gaugeFace(bezel)
        drawGestureHints(cx - r * 0.98f, cy - r * 0.98f, hasLongPress = true, hasDoubleTap = false)

        // Rotate the whole card so `headingDeg` sits under the top index.
        rotate(degrees = -headingDeg, pivot = Offset(cx, cy)) {
            for (deg in 0 until 360 step 5) {
                val major = deg % 10 == 0
                val outer = r * 0.96f
                val inner = r * (if (major) 0.86f else 0.90f)
                val p1 = polar(cx, cy, inner, deg.toFloat())
                val p2 = polar(cx, cy, outer, deg.toFloat())
                drawLine(GaugeColors.Mark, p1, p2, strokeWidth = if (major) 2.5f else 1.5f)
            }
            // Labels every 30°: N, 3, 6, 9, 12 … (tens of degrees) — bigger.
            for (deg in 0 until 360 step 30) {
                val label = when (deg) {
                    0 -> "N"; 90 -> "E"; 180 -> "S"; 270 -> "W"
                    else -> (deg / 10).toString()
                }
                val pos = polar(cx, cy, r * 0.70f, deg.toFloat())
                // Counter-rotate text so it stays upright.
                rotate(degrees = headingDeg, pivot = pos) {
                    gaugeText(tm, label, pos.x, pos.y, sizeSp = if (deg % 90 == 0) 20f else 16f,
                        bold = true)
                }
            }
            // Heading bug on the outer ring (rotates with the card).
            targetHeading?.let { bug ->
                val outer = polar(cx, cy, r * 0.985f, bug.toFloat())
                val bl = polar(cx, cy, r * 0.86f, bug - 5f)
                val br = polar(cx, cy, r * 0.86f, bug + 5f)
                val p = Path().apply { moveTo(outer.x, outer.y); lineTo(bl.x, bl.y); lineTo(br.x, br.y); close() }
                drawPath(p, Color(0xFFB94DD6))
            }
        }

        // Fixed top index triangle — larger.
        val tip = polar(cx, cy, r * 0.99f, 0f)
        val path = Path().apply {
            moveTo(tip.x, tip.y)
            lineTo(cx - 14f, tip.y + 26f)
            lineTo(cx + 14f, tip.y + 26f)
            close()
        }
        drawPath(path, GaugeColors.Accent)

        // Fixed aircraft silhouette (simple plane).
        drawAircraftSilhouette(cx, cy, r * 0.5f)

        // Numeric heading in a dedicated box at the instrument centre.
        if (showValue) {
            val hdg = (((headingDeg.toInt()) % 360) + 360) % 360
            drawRoundedValue(tm, "${hdg.toString().padStart(3, '0')}°", cx, cy, sizeSp = 20f)
        }
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

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawAircraftSilhouette(
    cx: Float, cy: Float, s: Float,
) {
    val c = GaugeColors.Mark
    val w = 5f
    // Fuselage.
    drawLine(c, Offset(cx, cy - s), Offset(cx, cy + s * 0.8f), strokeWidth = w)
    // Wings.
    drawLine(c, Offset(cx - s * 0.9f, cy), Offset(cx + s * 0.9f, cy), strokeWidth = w)
    // Tail.
    drawLine(c, Offset(cx - s * 0.3f, cy + s * 0.7f), Offset(cx + s * 0.3f, cy + s * 0.7f), strokeWidth = w)
}
