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

        DashboardBackgroundStyle.RIVETED_METAL -> {
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
            val spacingX = (size.width * 0.1f).coerceIn(28f, 50f)
            val spacingY = (size.height * 0.12f).coerceIn(20f, 36f)
            val rivetR = (size.minDimension * 0.012f).coerceIn(3f, 6f)
            var row = 0
            var ry = spacingY / 2f
            while (ry < size.height) {
                val shift = if (row % 2 == 0) 0f else spacingX / 2f
                var rx = shift + spacingX / 2f
                while (rx < size.width) {
                    drawCircle(Color(0xFF484848), radius = rivetR, center = Offset(rx, ry))
                    drawCircle(Color(0xFF707070), radius = rivetR, center = Offset(rx, ry), style = Stroke(width = 1f))
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

        DashboardBackgroundStyle.WOVEN_CARBON -> {
            drawRect(Color(0xFF0F0F0F), size = size)
            val cell = (size.minDimension * 0.022f).coerceIn(7f, 15f)
            val diag = cell * 2f
            var d = -size.height
            while (d < size.width + size.height) {
                val bandIdx = ((d / diag) + 1000).toInt()
                val col = if (bandIdx % 2 == 0) Color(0xFF1A1A1A) else Color(0xFF272727)
                drawLine(col, Offset(d, 0f), Offset(d - size.height, size.height), strokeWidth = cell)
                drawLine(Color(0x10FFFFFF), Offset(d, 0f), Offset(d - size.height, size.height), strokeWidth = 0.8f)
                d += diag
            }
            d = -size.height
            while (d < size.width + size.height) {
                val bandIdx = ((d / diag) + 1000).toInt()
                if (bandIdx % 2 == 0) {
                    drawLine(Color(0x18FFFFFF), Offset(size.width - d, 0f), Offset(size.width - d + size.height, size.height), strokeWidth = 0.6f)
                }
                d += diag
            }
        }

        DashboardBackgroundStyle.TOPOGRAPHIC -> {
            drawRect(Color(0xFF08100C), size = size)
            val cx = size.width / 2f
            val cy = size.height / 2f
            val maxR = size.maxDimension * 0.9f
            val spacing = (size.minDimension * 0.08f).coerceIn(14f, 28f)
            var r = spacing
            var lineIdx = 0
            while (r < maxR) {
                val isThick = lineIdx % 5 == 0
                drawCircle(
                    color = if (isThick) Color(0xFF1A3A22) else Color(0xFF102A18),
                    radius = r,
                    center = Offset(cx, cy),
                    style = Stroke(width = if (isThick) 1.5f else 0.8f),
                )
                r += spacing; lineIdx++
            }
            val cx2 = size.width * 0.72f
            val cy2 = size.height * 0.28f
            r = spacing * 1.3f; lineIdx = 0
            while (r < maxR * 0.6f) {
                val isThick = lineIdx % 4 == 0
                drawCircle(
                    color = if (isThick) Color(0xFF163020) else Color(0xFF0C2014),
                    radius = r,
                    center = Offset(cx2, cy2),
                    style = Stroke(width = if (isThick) 1.2f else 0.7f),
                )
                r += spacing; lineIdx++
            }
        }

        DashboardBackgroundStyle.TITANIUM -> {
            drawRect(
                brush = Brush.linearGradient(
                    0f to Color(0xFF6E7A8A), 0.3f to Color(0xFF8A9AAA),
                    0.5f to Color(0xFFB0BEC5), 0.7f to Color(0xFF78909C),
                    1f to Color(0xFF4A5568),
                    start = Offset(0f, 0f), end = Offset(size.width, size.height),
                ),
                size = size,
            )
            for (i in 0..8) {
                val y = size.height * i / 8f
                drawLine(Color(0x14FFFFFF), Offset(0f, y), Offset(size.width, y - size.height * 0.1f), strokeWidth = 2f)
            }
            var x = 0f
            while (x < size.width) {
                drawLine(Color(0x10000000), Offset(x, 0f), Offset(x, size.height), strokeWidth = 1f)
                x += 3f
            }
        }

        DashboardBackgroundStyle.ANODIZED_DARK -> {
            drawRect(Color(0xFF0A0A0E), size = size)
            var y = 0f
            while (y < size.height) {
                val bright = (y * 7).toInt() % 17
                drawLine(
                    Color(if (bright < 3) 0x14FFFFFF else 0x06FFFFFF),
                    Offset(0f, y), Offset(size.width, y), strokeWidth = 1f,
                )
                y += 1.5f
            }
            val sheenH = size.height * 0.18f
            drawRect(
                brush = Brush.linearGradient(
                    0f to Color(0x00FF0044), 0.2f to Color(0x08FF4488),
                    0.4f to Color(0x0644AAFF), 0.6f to Color(0x0800FFAA),
                    0.8f to Color(0x08AAFF44), 1f to Color(0x00FFFF00),
                    start = Offset(0f, 0f), end = Offset(size.width, 0f),
                ),
                topLeft = Offset(0f, 0f),
                size = androidx.compose.ui.geometry.Size(size.width, sheenH),
            )
        }

        DashboardBackgroundStyle.DIAMOND_PLATE -> {
            drawRect(
                brush = Brush.verticalGradient(
                    0f to Color(0xFF9AA4AE), 0.5f to Color(0xFF6E7A84), 1f to Color(0xFF4A5258),
                    startY = 0f, endY = size.height,
                ),
                size = size,
            )
            val cell = (size.minDimension * 0.055f).coerceIn(10f, 20f)
            val path = Path()
            var row = 0
            var yy = 0f
            while (yy < size.height) {
                val shift = if (row % 2 == 0) 0f else cell
                var xx = shift - cell
                while (xx < size.width + cell) {
                    val cx2 = xx + cell; val cy2 = yy + cell / 2f
                    path.reset()
                    path.moveTo(xx + cell / 2f, yy)
                    path.lineTo(cx2, cy2)
                    path.lineTo(xx + cell / 2f, yy + cell)
                    path.lineTo(xx, cy2)
                    path.close()
                    drawPath(path, Color(0xFF7A8892))
                    drawLine(Color(0x44FFFFFF), Offset(xx + cell / 2f, yy), Offset(cx2, cy2), strokeWidth = 1f)
                    drawLine(Color(0x22000000), Offset(cx2, cy2), Offset(xx + cell / 2f, yy + cell), strokeWidth = 1f)
                    xx += cell * 2f
                }
                yy += cell; row++
            }
        }

        DashboardBackgroundStyle.STARFIELD -> {
            drawRect(Color(0xFF010106), size = size)
            var seed = 42L
            fun nextFloat(): Float { seed = seed * 1664525L + 1013904223L; return ((seed ushr 16) and 0xFFFF).toFloat() / 65535f }
            repeat(120) {
                val x = nextFloat() * size.width
                val y = nextFloat() * size.height
                val r = nextFloat() * 1.8f + 0.3f
                val bright = nextFloat()
                val col = when {
                    bright > 0.95f -> Color(0xFFFFFFCC)
                    bright > 0.85f -> Color(0xFFCCDDFF)
                    bright > 0.6f  -> Color(0xAAFFFFFF)
                    else           -> Color(0x55FFFFFF)
                }
                drawCircle(col, radius = r, center = Offset(x, y))
            }
            drawRect(
                brush = Brush.linearGradient(
                    0f to Color(0x00FFFFFF), 0.5f to Color(0x06AABBFF), 1f to Color(0x00FFFFFF),
                    start = Offset(0f, size.height * 0.3f), end = Offset(size.width, size.height * 0.7f),
                ),
                size = size,
            )
        }

        DashboardBackgroundStyle.DEEP_OCEAN -> {
            drawRect(
                brush = Brush.verticalGradient(
                    0f to Color(0xFF020C14), 0.4f to Color(0xFF031828),
                    0.7f to Color(0xFF041020), 1f to Color(0xFF020810),
                    startY = 0f, endY = size.height,
                ),
                size = size,
            )
            var y = 0f
            while (y < size.height) {
                val wave = Math.sin(y * 0.08 + 1.2) * 3.0
                drawLine(
                    Color(0x0A0088AA),
                    Offset(0f, y + wave.toFloat()), Offset(size.width, y - wave.toFloat()),
                    strokeWidth = 1.5f,
                )
                y += 8f
            }
            drawRect(
                brush = Brush.radialGradient(
                    0f to Color(0x0800AACC), 1f to Color(0x00001133),
                    center = Offset(size.width * 0.3f, size.height * 0.25f),
                    radius = size.minDimension * 0.4f,
                ),
                size = size,
            )
            drawRect(
                brush = Brush.radialGradient(
                    0f to Color(0x060088AA), 1f to Color(0x00001133),
                    center = Offset(size.width * 0.75f, size.height * 0.6f),
                    radius = size.minDimension * 0.3f,
                ),
                size = size,
            )
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
