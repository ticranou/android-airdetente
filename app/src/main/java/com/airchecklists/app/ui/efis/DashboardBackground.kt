package com.airchecklists.app.ui.efis

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.airchecklists.app.data.model.DashboardBackgroundStyle
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** Draw the cockpit background texture for [style] into this DrawScope.
 *  All sizes are relative to [size] so the same function works for both
 *  the full-screen cockpit Canvas and the thumbnail previews. */
fun DrawScope.drawDashboardBackground(style: DashboardBackgroundStyle) {
    when (style) {
        DashboardBackgroundStyle.NONE -> Unit

        DashboardBackgroundStyle.CARBON -> drawCarbon(
            bg = Color(0xFF141414),
            dark = Color(0xFF1E1E1E),
            light = Color(0xFF2C2C2C),
            highlight = Color(0x18FFFFFF),
        )

        DashboardBackgroundStyle.BRUSHED_METAL -> {
            drawRect(
                brush = Brush.verticalGradient(
                    0f to Color(0xFFBFC3C7), 0.55f to Color(0xFF6E7276), 1f to Color(0xFF303336),
                    startY = 0f, endY = size.height,
                ),
                size = size,
            )
            var x = 0f
            while (x < size.width) {
                drawLine(Color(0x22FFFFFF), Offset(x, 0f), Offset(x, size.height), strokeWidth = 1f)
                x += 5f
            }
        }

        DashboardBackgroundStyle.HONEYCOMB -> {
            drawRect(Color(0xFF111111), size = size)
            val r = (size.minDimension * 0.04f).coerceIn(8f, 20f)
            val hexW = r * sqrt(3f)
            val hexH = r * 2f
            val path = Path()
            var row = 0
            var yCenter = r
            while (yCenter - r < size.height) {
                val offsetX = if (row % 2 == 0) 0f else hexW / 2f
                var xCenter = offsetX + r
                while (xCenter - hexW < size.width) {
                    path.reset()
                    for (i in 0..5) {
                        val angle = Math.toRadians((60.0 * i - 30.0))
                        val px = xCenter + r * cos(angle).toFloat()
                        val py = yCenter + r * sin(angle).toFloat()
                        if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
                    }
                    path.close()
                    drawPath(path, Color(0xFF1E1E1E), style = Stroke(width = 1.2f))
                    // Subtle highlight on top-left edge
                    val a0 = Math.toRadians(-30.0)
                    val a1 = Math.toRadians(30.0)
                    drawLine(
                        Color(0x14FFFFFF),
                        Offset(xCenter + r * cos(a0).toFloat(), yCenter + r * sin(a0).toFloat()),
                        Offset(xCenter + r * cos(a1).toFloat(), yCenter + r * sin(a1).toFloat()),
                        strokeWidth = 1f,
                    )
                    xCenter += hexW
                }
                yCenter += hexH * 0.75f
                row++
            }
        }

        DashboardBackgroundStyle.RIVETED_METAL -> {
            // Brushed base
            drawRect(
                brush = Brush.verticalGradient(
                    0f to Color(0xFF8A8E92), 0.55f to Color(0xFF525659), 1f to Color(0xFF2A2D30),
                    startY = 0f, endY = size.height,
                ),
                size = size,
            )
            var x = 0f
            while (x < size.width) {
                drawLine(Color(0x18FFFFFF), Offset(x, 0f), Offset(x, size.height), strokeWidth = 1f)
                x += 5f
            }
            // Rivets
            val spacingX = (size.width * 0.1f).coerceIn(28f, 50f)
            val spacingY = (size.height * 0.12f).coerceIn(20f, 36f)
            val rivetR = (size.minDimension * 0.012f).coerceIn(3f, 6f)
            var row = 0
            var ry = spacingY / 2f
            while (ry < size.height) {
                val shift = if (row % 2 == 0) 0f else spacingX / 2f
                var rx = shift + spacingX / 2f
                while (rx < size.width) {
                    // Rivet body
                    drawCircle(Color(0xFF484848), radius = rivetR, center = Offset(rx, ry))
                    // Rivet rim
                    drawCircle(Color(0xFF707070), radius = rivetR, center = Offset(rx, ry), style = Stroke(width = 1f))
                    // Highlight top-left
                    drawCircle(Color(0x44FFFFFF), radius = rivetR * 0.45f, center = Offset(rx - rivetR * 0.25f, ry - rivetR * 0.25f))
                    rx += spacingX
                }
                ry += spacingY
                row++
            }
        }

        DashboardBackgroundStyle.CARBON_RED -> drawCarbon(
            bg = Color(0xFF1A0606),
            dark = Color(0xFF2A0C0C),
            light = Color(0xFF3D1414),
            highlight = Color(0x18FF4444),
        )

        DashboardBackgroundStyle.CARBON_BLUE -> drawCarbon(
            bg = Color(0xFF060A1A),
            dark = Color(0xFF0C152A),
            light = Color(0xFF14203D),
            highlight = Color(0x184488FF),
        )

        DashboardBackgroundStyle.MIDNIGHT -> {
            drawRect(
                brush = Brush.verticalGradient(
                    0f to Color(0xFF0A0E1A),
                    0.5f to Color(0xFF060810),
                    1f to Color(0xFF020204),
                    startY = 0f, endY = size.height,
                ),
                size = size,
            )
        }

        DashboardBackgroundStyle.GRID -> {
            drawRect(Color(0xFF050505), size = size)
            val step = (size.minDimension * 0.06f).coerceIn(14f, 28f)
            val accent = (step * 4f)
            var x = 0f
            while (x <= size.width) {
                val isAccent = (x / step).toInt() % 4 == 0
                drawLine(
                    if (isAccent) Color(0x55006600) else Color(0x33004400),
                    Offset(x, 0f), Offset(x, size.height),
                    strokeWidth = if (isAccent) 1.2f else 0.8f,
                )
                x += step
            }
            var y = 0f
            while (y <= size.height) {
                val isAccent = (y / step).toInt() % 4 == 0
                drawLine(
                    if (isAccent) Color(0x55006600) else Color(0x33004400),
                    Offset(0f, y), Offset(size.width, y),
                    strokeWidth = if (isAccent) 1.2f else 0.8f,
                )
                y += step
            }
        }
    }
}

private fun DrawScope.drawCarbon(bg: Color, dark: Color, light: Color, highlight: Color) {
    drawRect(bg, size = size)
    val cell = (size.minDimension * 0.02f).coerceIn(6f, 14f)
    var trow = 0; var yy = 0f
    while (yy < size.height) {
        var tcol = 0; var xx = 0f
        while (xx < size.width) {
            drawRect(
                if ((trow + tcol) % 2 == 0) dark else light,
                topLeft = Offset(xx, yy), size = Size(cell, cell),
            )
            drawLine(highlight, Offset(xx, yy + cell), Offset(xx + cell, yy), strokeWidth = 1.2f)
            xx += cell; tcol++
        }
        yy += cell; trow++
    }
}
