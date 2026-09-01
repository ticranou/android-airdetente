package com.airchecklists.app.ui.efis.gauges.compact

import com.airchecklists.app.ui.efis.gauges.drawNumTitleBar
import com.airchecklists.app.ui.efis.gauges.LocalGaugeBezel
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.roundToInt

/** Compact turn indicator (single height, half width): fixed 20dp header, then a
 *  banked aircraft with "N° D/G" beneath it (left) and the slip-ball pill (right). */
@Composable
fun BallCompact(rollDeg: Float, slip: Float, modifier: Modifier = Modifier) {
    val tm = rememberTextMeasurer()
    val bezel = LocalGaugeBezel.current
    Canvas(modifier = modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val headerH = 20.dp.toPx().coerceAtMost(h * 0.4f)
        drawRect(CompactStyle.Bg, size = size)
        drawNumTitleBar(bezel, w, headerH)
        drawRect(Color(0xFF3A3A3A), size = size, style = Stroke(width = 2f))
        compactText(tm, "INDICATEUR DE VIRAGE", w / 2f, headerH / 2f, sizeSp = 11f, color = CompactStyle.Dim)

        val mainTop = headerH
        val mainH = h - headerH
        val bank = rollDeg.coerceIn(-45f, 45f)

        // --- Left: banked aircraft, with the bank value beneath it ---
        val ax = w * 0.22f
        val ay = mainTop + mainH * 0.42f
        rotate(degrees = bank, pivot = Offset(ax, ay)) {
            val wing = w * 0.13f
            drawLine(CompactStyle.Accent, Offset(ax - wing, ay), Offset(ax + wing, ay), strokeWidth = 6f)
            drawLine(CompactStyle.Accent, Offset(ax, ay), Offset(ax, ay - wing * 0.5f), strokeWidth = 6f)
            drawCircle(CompactStyle.Accent, radius = mainH * 0.09f, center = Offset(ax, ay))
        }
        val side = if (bank >= 0f) "D" else "G"
        compactText(tm, "${abs(bank).roundToInt()}° $side", ax, mainTop + mainH * 0.82f, sizeSp = 18f, bold = true, color = CompactStyle.Accent)

        // --- Right: slip ball pill ---
        val pillCx = w * 0.66f
        val pillW = w * 0.56f
        val pillH = mainH * 0.42f
        val pcy = mainTop + mainH * 0.5f
        drawRoundRect(Color(0xFF2A2A2A), topLeft = Offset(pillCx - pillW / 2f, pcy - pillH / 2f),
            size = Size(pillW, pillH), cornerRadius = CornerRadius(pillH / 2f, pillH / 2f))
        val ballR = pillH * 0.40f
        listOf(-ballR - 6f, ballR + 6f).forEach { dx ->
            drawLine(CompactStyle.Mark, Offset(pillCx + dx, pcy - ballR - 4f), Offset(pillCx + dx, pcy + ballR + 4f), strokeWidth = 3f)
        }
        val travel = pillW * 0.32f
        drawCircle(CompactStyle.Accent, radius = ballR, center = Offset(pillCx + slip.coerceIn(-1f, 1f) * travel, pcy))
    }
}
