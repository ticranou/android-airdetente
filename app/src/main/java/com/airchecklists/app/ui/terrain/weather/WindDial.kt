package com.airchecklists.app.ui.terrain.weather

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.cos
import kotlin.math.sin

/**
 * Combined wind dial (compass rose + anemometer arc + oriented runway + wind
 * arrow + variable-wind arc), reproducing metar-taf-card's `renderCombinedDial`.
 *
 * Angles use the aviation convention: 0° = North (up), clockwise.
 */
@Composable
fun WindDial(
    windDir: Int?,
    windSpeedKt: Int?,
    windGustKt: Int?,
    variableFrom: Int?,
    variableTo: Int?,
    runwayHeading: Int?,
    modifier: Modifier = Modifier,
    onSurface: Color = Color(0xFF90A4AE),
    accent: Color = Color(0xFF3B6BC4),
    windColor: Color = Color(0xFF2E7D32),
    gustColor: Color = Color(0xFFFFB300),
    runwayColor: Color = Color(0xFF607D8B),
) {
    val tm = rememberTextMeasurer()
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1f),
    ) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val unit = size.minDimension / 320f   // JS SIZE = 320
        val rOuter = 130f * unit
        val rOuterIn = 110f * unit
        val rInner = 96f * unit
        val maxKt = 45f

        // --- Anemometer ring: ticks over -135°..+135° (270°), gap at bottom ---
        for (v in 0..45) {
            val major = v % 5 == 0
            val a = -135f + 270f * v / maxKt          // 12 o'clock clockwise
            val th = Math.toRadians((a - 90).toDouble())
            val ri = rOuterIn + (if (major) 0f else 4f * unit)
            val ro = rOuter - (if (major) 0f else 4f * unit)
            drawLine(
                color = if (major) onSurface else onSurface.copy(alpha = 0.5f),
                start = Offset(cx + ri * cos(th).toFloat(), cy + ri * sin(th).toFloat()),
                end = Offset(cx + ro * cos(th).toFloat(), cy + ro * sin(th).toFloat()),
                strokeWidth = if (major) 2f * unit else 1f * unit,
            )
            if (major) {
                val lr = rOuter + 12f * unit
                drawCenteredText(tm, v.toString(), cx + lr * cos(th).toFloat(),
                    cy + lr * sin(th).toFloat(), (10f * unit).sp, onSurface)
            }
        }

        // --- Wind speed arrow on the ring (points inward) ---
        fun angleForSpeed(v: Float): Double {
            val clamped = v.coerceIn(0f, maxKt)
            return Math.toRadians((-135f + 270f * clamped / maxKt - 90f).toDouble())
        }
        fun drawSpeedArrow(speed: Int, color: Color, halfW: Float) {
            val th = angleForSpeed(speed.toFloat())
            val tipR = rOuterIn - 2f * unit
            val baseR = rOuter + 8f * unit
            val tx = cx + tipR * cos(th).toFloat(); val ty = cy + tipR * sin(th).toFloat()
            val bxC = cx + baseR * cos(th).toFloat(); val byC = cy + baseR * sin(th).toFloat()
            val px = (-sin(th)).toFloat(); val py = cos(th).toFloat()
            val path = Path().apply {
                moveTo(tx, ty)
                lineTo(bxC + halfW * px, byC + halfW * py)
                lineTo(bxC - halfW * px, byC - halfW * py)
                close()
            }
            drawPath(path, color)
        }
        windGustKt?.takeIf { it > 0 }?.let { drawSpeedArrow(it, gustColor, 7f * unit) }
        windSpeedKt?.takeIf { it > 0 }?.let { drawSpeedArrow(it, windColor, 10f * unit) }

        // --- Inner compass ring ---
        drawCircle(color = onSurface, radius = rInner, center = Offset(cx, cy), style = Stroke(width = 1.5f * unit))
        for (a in 0 until 360 step 10) {
            val major = a % 30 == 0
            val inner = rInner - (if (major) 12f * unit else 6f * unit)
            val rad = Math.toRadians(a.toDouble())
            val x1 = cx + inner * sin(rad).toFloat(); val y1 = cy - inner * cos(rad).toFloat()
            val x2 = cx + rInner * sin(rad).toFloat(); val y2 = cy - rInner * cos(rad).toFloat()
            drawLine(
                color = if (major) onSurface else onSurface.copy(alpha = 0.5f),
                start = Offset(x1, y1), end = Offset(x2, y2),
                strokeWidth = if (major) 2f * unit else 1f * unit,
            )
        }
        val labels = listOf(0 to "N", 90 to "E", 180 to "S", 270 to "O")
        labels.forEach { (deg, t) ->
            val lr = rInner - 22f * unit
            val rad = Math.toRadians(deg.toDouble())
            drawCenteredText(tm, t, cx + lr * sin(rad).toFloat(), cy - lr * cos(rad).toFloat(),
                (13f * unit).sp, onSurface, bold = true)
        }

        // --- Variable wind arc ---
        val varFrom = variableFrom
        val varTo = variableTo
        if (varFrom != null && varTo != null) {
            val path = Path()
            var a: Int = varFrom
            path.moveTo(
                cx + rInner * sin(Math.toRadians(a.toDouble())).toFloat(),
                cy - rInner * cos(Math.toRadians(a.toDouble())).toFloat(),
            )
            val steps: Int = ((varTo - varFrom + 360) % 360)
            repeat(steps + 1) {
                val rad = Math.toRadians(a.toDouble())
                path.lineTo(cx + rInner * sin(rad).toFloat(), cy - rInner * cos(rad).toFloat())
                a = (a + 1) % 360
            }
            drawPath(path, accent, style = Stroke(width = 2f * unit))
        }

        // --- Runway rectangle, rotated to its heading ---
        if (runwayHeading != null) {
            rotate(degrees = runwayHeading.toFloat(), pivot = Offset(cx, cy)) {
                val halfLen = 78f * unit
                val halfW = 8f * unit
                drawRoundRect(
                    color = runwayColor,
                    topLeft = Offset(cx - halfW, cy - halfLen),
                    size = androidx.compose.ui.geometry.Size(halfW * 2, halfLen * 2),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(2f * unit, 2f * unit),
                )
            }
        }

        // --- Wind direction needle (points from where the wind blows toward center) ---
        if (windDir != null && (windSpeedKt ?: 0) > 0) {
            rotate(degrees = windDir.toFloat(), pivot = Offset(cx, cy)) {
                drawLine(
                    color = windColor,
                    start = Offset(cx, cy - rInner + 4f * unit),
                    end = Offset(cx, cy + rInner - 12f * unit),
                    strokeWidth = 3f * unit,
                )
                val ah = 14f * unit
                val aw = 7f * unit
                val tipY = cy + rInner - 4f * unit
                val path = Path().apply {
                    moveTo(cx, tipY)
                    lineTo(cx - aw, tipY - ah)
                    lineTo(cx + aw, tipY - ah)
                    close()
                }
                drawPath(path, windColor)
            }
        }
    }
}

private fun DrawScope.drawCenteredText(
    tm: TextMeasurer,
    text: String,
    x: Float,
    y: Float,
    size: androidx.compose.ui.unit.TextUnit,
    color: Color,
    bold: Boolean = false,
) {
    val style = TextStyle(
        color = color,
        fontSize = size,
        fontWeight = if (bold) androidx.compose.ui.text.font.FontWeight.Bold else androidx.compose.ui.text.font.FontWeight.Normal,
    )
    val measured = tm.measure(text, style)
    drawText(measured, topLeft = Offset(x - measured.size.width / 2f, y - measured.size.height / 2f))
}
