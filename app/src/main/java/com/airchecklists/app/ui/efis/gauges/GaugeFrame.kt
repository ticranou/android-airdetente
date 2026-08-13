package com.airchecklists.app.ui.efis.gauges

import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.cos
import kotlin.math.sin

/** Shared colors for the analog gauges. */
object GaugeColors {
    val Face = Color(0xFF0A0A0A)
    val Bezel = Color(0xFF1C1C1C)
    val Mark = Color(0xFFFFFFFF)
    val MarkDim = Color(0xFFB0B0B0)
    val Needle = Color(0xFFFFFFFF)
    val Sky = Color(0xFF2B84C8)
    val Ground = Color(0xFF6B4A2B)
    val Accent = Color(0xFFFFC107)
}

/** Standard modifier for a gauge cell: square, small padding. */
fun Modifier.gaugeCell(): Modifier = this.aspectRatio(1f).padding(3.dp)

/** Effective bezel/accent for the current instrument cell: the base colour to use
 *  for the ANL bezel and the NUM title-bar background, plus the global style. */
data class GaugeBezel(
    val color: Color = Color(0xFF1C1C1C),
    val style: com.airchecklists.app.data.model.GaugeBezelStyle = com.airchecklists.app.data.model.GaugeBezelStyle.SOLID,
)

/** Resolve the current global bezel preference (used as the default). */
fun globalGaugeBezel(): GaugeBezel {
    val p = com.airchecklists.app.di.ServiceLocator.preferences.preferences.value
    return GaugeBezel(Color(p.gaugeBezelColor.toInt()), p.gaugeBezelStyle)
}

/** Provided per instrument cell by InstrumentSlot; defaults to the global setting. */
val LocalGaugeBezel = androidx.compose.runtime.compositionLocalOf { GaugeBezel() }

/** Title-bar background colour for a NUM instrument: the accent/bezel colour when
 *  it is a solid colour; for texture styles (carbon/brushed) use a neutral dark. */
@androidx.compose.runtime.Composable
fun numTitleColor(): Color {
    val b = LocalGaugeBezel.current
    return if (b.style == com.airchecklists.app.data.model.GaugeBezelStyle.SOLID) b.color else Color(0xFF1E1E1E)
}

/** Paint a NUM instrument's title bar (0,0 → w×h) honouring the bezel style:
 *  solid colour, carbon weave, or brushed metal — matching the ANL bezel. */
fun DrawScope.drawNumTitleBar(bezel: GaugeBezel, w: Float, h: Float) {
    when (bezel.style) {
        com.airchecklists.app.data.model.GaugeBezelStyle.SOLID ->
            drawRect(bezel.color, topLeft = Offset(0f, 0f), size = androidx.compose.ui.geometry.Size(w, h))

        com.airchecklists.app.data.model.GaugeBezelStyle.BRUSHED -> {
            drawRect(
                brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                    0f to Color(0xFFBFC3C7), 0.55f to Color(0xFF6E7276), 1f to Color(0xFF303336),
                    startY = 0f, endY = h,
                ),
                topLeft = Offset(0f, 0f), size = androidx.compose.ui.geometry.Size(w, h),
            )
            var x = 0f
            while (x < w) { drawLine(Color(0x22FFFFFF), Offset(x, 0f), Offset(x, h), strokeWidth = 1f); x += 5f }
        }

        com.airchecklists.app.data.model.GaugeBezelStyle.CARBON -> {
            drawRect(Color(0xFF141414), topLeft = Offset(0f, 0f), size = androidx.compose.ui.geometry.Size(w, h))
            clipRect(0f, 0f, w, h) {
                val cell = (h * 0.5f).coerceIn(6f, 14f)
                var row = 0
                var yy = 0f
                while (yy < h) {
                    var col = 0
                    var xx = 0f
                    while (xx < w) {
                        val dark = (row + col) % 2 == 0
                        drawRect(if (dark) Color(0xFF1E1E1E) else Color(0xFF2C2C2C),
                            topLeft = Offset(xx, yy), size = androidx.compose.ui.geometry.Size(cell, cell))
                        drawLine(Color(0x18FFFFFF), Offset(xx, yy + cell), Offset(xx + cell, yy), strokeWidth = 1.2f)
                        xx += cell; col++
                    }
                    yy += cell; row++
                }
            }
        }
    }
}

/** Draws the round face + configurable bezel using [bezel]; returns center/radius. */
fun DrawScope.gaugeFace(bezel: GaugeBezel = globalGaugeBezel()): Triple<Float, Float, Float> {
    val cx = size.width / 2f
    val cy = size.height / 2f
    val r = size.minDimension / 2f
    drawBezel(cx, cy, r, bezel)
    drawCircle(GaugeColors.Face, radius = r * 0.92f, center = Offset(cx, cy))
    return Triple(cx, cy, r * 0.92f)
}

/** Draws the outer bezel ring per [bezel] (solid colour / carbon / brushed). */
private fun DrawScope.drawBezel(cx: Float, cy: Float, r: Float, bezel: GaugeBezel) {
    val center = Offset(cx, cy)
    when (bezel.style) {
        com.airchecklists.app.data.model.GaugeBezelStyle.SOLID ->
            drawCircle(bezel.color, radius = r, center = center)

        com.airchecklists.app.data.model.GaugeBezelStyle.BRUSHED -> {
            drawCircle(
                brush = androidx.compose.ui.graphics.Brush.radialGradient(
                    0f to Color(0xFFBFC3C7), 0.7f to Color(0xFF6E7276), 1f to Color(0xFF303336),
                    center = center, radius = r,
                ),
                radius = r, center = center,
            )
            for (a in 0 until 360 step 6) {
                val p1 = polar(cx, cy, r * 0.93f, a.toFloat())
                val p2 = polar(cx, cy, r * 0.995f, a.toFloat())
                drawLine(Color(0x22FFFFFF), p1, p2, strokeWidth = 1f)
            }
        }

        com.airchecklists.app.data.model.GaugeBezelStyle.CARBON -> {
            drawCircle(Color(0xFF141414), radius = r, center = center)
            clipPath(androidx.compose.ui.graphics.Path().apply { addOval(androidx.compose.ui.geometry.Rect(cx - r, cy - r, cx + r, cy + r)) }) {
                val cell = (r * 0.14f).coerceAtLeast(6f)
                var row = 0
                var yy = cy - r
                while (yy < cy + r) {
                    var col = 0
                    var xx = cx - r
                    while (xx < cx + r) {
                        val dark = (row + col) % 2 == 0
                        val base = if (dark) Color(0xFF1E1E1E) else Color(0xFF2C2C2C)
                        drawRect(base, topLeft = Offset(xx, yy), size = androidx.compose.ui.geometry.Size(cell, cell))
                        drawLine(Color(0x18FFFFFF), Offset(xx, yy + cell), Offset(xx + cell, yy), strokeWidth = 1.4f)
                        xx += cell; col++
                    }
                    yy += cell; row++
                }
            }
        }
    }
}

/** A needle from center outward at `angleDeg` (0° = up, clockwise). */
fun DrawScope.drawNeedle(
    cx: Float,
    cy: Float,
    length: Float,
    angleDeg: Float,
    color: Color = GaugeColors.Needle,
    width: Float = 6f,
    tailLength: Float = 0f,
) {
    rotate(degrees = angleDeg, pivot = Offset(cx, cy)) {
        drawLine(color, Offset(cx, cy + tailLength), Offset(cx, cy - length), strokeWidth = width, cap = androidx.compose.ui.graphics.StrokeCap.Round)
    }
}

/** Text centered at (x,y). */
fun DrawScope.gaugeText(
    tm: TextMeasurer,
    text: String,
    x: Float,
    y: Float,
    sizeSp: Float,
    color: Color = GaugeColors.Mark,
    bold: Boolean = false,
) {
    val m = tm.measure(
        text,
        TextStyle(color = color, fontSize = sizeSp.sp, fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal),
    )
    drawText(m, topLeft = Offset(x - m.size.width / 2f, y - m.size.height / 2f))
}

/** Point on a circle for angle in aviation degrees (0° = up, clockwise). */
fun polar(cx: Float, cy: Float, r: Float, deg: Float): Offset {
    val rad = Math.toRadians((deg - 90).toDouble())
    return Offset(cx + r * cos(rad).toFloat(), cy + r * sin(rad).toFloat())
}

/** Draws a value in a dedicated dark rounded box (grey border) centered at
 *  (x,y) — legible over needles/ladders. */
fun DrawScope.drawRoundedValue(
    tm: TextMeasurer,
    text: String,
    x: Float,
    y: Float,
    sizeSp: Float,
    color: Color = GaugeColors.Accent,
) {
    val m = tm.measure(text, TextStyle(color = color, fontSize = sizeSp.sp, fontWeight = FontWeight.Bold))
    val padX = 12f
    val padY = 7f
    val w = m.size.width + padX * 2
    val h = m.size.height + padY * 2
    val topLeft = Offset(x - w / 2f, y - h / 2f)
    val size = androidx.compose.ui.geometry.Size(w, h)
    val radius = androidx.compose.ui.geometry.CornerRadius(5f, 5f)
    drawRoundRect(color = Color(0xF2101010), topLeft = topLeft, size = size, cornerRadius = radius)
    drawRoundRect(
        color = Color(0xFF5A5A5A), topLeft = topLeft, size = size, cornerRadius = radius,
        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.5f),
    )
    drawText(m, topLeft = Offset(x - m.size.width / 2f, y - m.size.height / 2f))
}

