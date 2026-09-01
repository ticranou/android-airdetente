package com.airchecklists.app.ui.efis.gauges.compact

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.airchecklists.app.data.model.SpeedArcs
import kotlin.math.abs
import kotlin.math.roundToInt

/** Shared palette + helpers for the rectangular "compact" EFIS instruments. */
object CompactStyle {
    val Bg = Color(0xFF0A0A0A)
    val Panel = Color(0xFF161616)
    val Mark = Color(0xFFFFFFFF)
    val Dim = Color(0xFFAAAAAA)
    val Sky = Color(0xFF2B84C8)
    val Ground = Color(0xFF6B4A2B)
    // Chart-style ground (aeronautical tan → earth), shared by the horizon instruments
    // and NUMAPP so the ground looks like a sectional map rather than flat brown.
    val GroundMapTop = Color(0xFFBFA377)   // hazy far terrain (chart tan)
    val GroundMapBot = Color(0xFF6E5A34)   // near terrain (darker earth)
    val Accent = Color(0xFFFFC107)
    val Accent2 = Color(0xFFE8843A)  // orange titles (ALTI / VARIO)
    val Climb = Color(0xFF35C759)
    val Descent = Color(0xFFFF5252)
}

fun DrawScope.compactText(
    tm: TextMeasurer,
    text: String,
    x: Float,
    y: Float,
    sizeSp: Float,
    color: Color = CompactStyle.Mark,
    bold: Boolean = false,
    mono: Boolean = false,
    center: Boolean = true,
    anchorRight: Boolean = false,
) {
    val m = tm.measure(
        text,
        TextStyle(
            color = color,
            fontSize = sizeSp.sp,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
            fontFamily = if (mono) FontFamily.Monospace else FontFamily.Default,
        ),
    )
    val tx = when {
        anchorRight -> x - m.size.width      // right edge at x (text extends left)
        center -> x - m.size.width / 2f
        else -> x
    }
    drawText(m, topLeft = Offset(tx, y - m.size.height / 2f))
}

/** Full-cell modifier for compact instruments. */
fun Modifier.compactCell(): Modifier = this.fillMaxSize()

/**
 * Draws gesture-affordance hints anchored at (x,y) (their left edge), laid out
 * horizontally: a dash "–" when [hasLongPress], two dots ".." when [hasDoubleTap].
 * Shared by analog gauges (drawn just outside the face) and numeric instruments
 * (drawn in the title bar).
 */
fun DrawScope.drawGestureHints(x: Float, y: Float, hasLongPress: Boolean, hasDoubleTap: Boolean) {
    val color = Color(0xFFCFCFCF)
    var cx = x + 5f     // small inset from the edge
    if (hasLongPress) {
        // Dash for long-press: longer + thicker for legibility.
        drawLine(color, Offset(cx, y), Offset(cx + 20f, y), strokeWidth = 5f)
        cx += 30f
    }
    if (hasDoubleTap) {
        // Two dots for double-tap.
        drawCircle(color, radius = 3.6f, center = Offset(cx + 4f, y))
        drawCircle(color, radius = 3.6f, center = Offset(cx + 17f, y))
    }
}

/** Fixed header/footer band height in px (density-independent enough at these
 *  panel sizes). Keeping them constant lets the main zone grow on tall panels. */
private const val BAND_PX = 40f

/** Draws the 3-zone panel: grey header (with title), black main, grey footer.
 *  Header and footer have a FIXED height so the main zone is as large as possible.
 *  Returns the main content Rect (between header and footer). */
fun DrawScope.compactPanel(tm: TextMeasurer, title: String): androidx.compose.ui.geometry.Rect {
    val w = size.width
    val h = size.height
    // Fixed bands, but never more than 22%/18% of a very short panel.
    val headerH = minOf(BAND_PX, h * 0.22f)
    val footerH = minOf(BAND_PX, h * 0.18f)
    // Main (black) background.
    drawRect(CompactStyle.Bg, size = androidx.compose.ui.geometry.Size(w, h))
    // Grey header band.
    drawRect(Color(0xFF2A2A2A), topLeft = Offset(0f, 0f), size = androidx.compose.ui.geometry.Size(w, headerH))
    // Grey footer band.
    drawRect(Color(0xFF2A2A2A), topLeft = Offset(0f, h - footerH), size = androidx.compose.ui.geometry.Size(w, footerH))
    // Outer border (square corners).
    drawRect(
        color = Color(0xFF3A3A3A),
        size = androidx.compose.ui.geometry.Size(w, h),
        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2f),
    )
    compactText(tm, title, w / 2f, headerH / 2f, sizeSp = 13f, color = CompactStyle.Dim)
    return androidx.compose.ui.geometry.Rect(0f, headerH, w, h - footerH)
}

/** Colour for a displayed speed based on the band it falls in
 *  (white below greenMin, green in green band, yellow Vno..Vne, red at/above Vne). */
fun speedColor(speedInUnit: Float, arcsInUnit: SpeedArcs?): Color {
    if (arcsInUnit == null) return CompactStyle.Accent
    val vne = arcsInUnit.vne
    val vno = arcsInUnit.vno
    val greenMin = arcsInUnit.greenMin
    return when {
        vne != null && speedInUnit >= vne -> Color(0xFFFF4136)
        vno != null && speedInUnit >= vno -> CompactStyle.Accent // yellow
        greenMin != null && speedInUnit >= greenMin -> CompactStyle.Climb // green
        else -> CompactStyle.Mark                                 // white
    }
}

/** Vertical "house" trend arrow: green up (climb), red down (descent). A wide
 *  triangular head over a rectangular body, centered at (cx,cy). */
fun DrawScope.trendArrow(cx: Float, cy: Float, half: Float, verticalSpeed: Float) {
    val up = verticalSpeed >= 0f
    val color = if (up) CompactStyle.Climb else CompactStyle.Descent
    val headHalfW = half * 0.55f
    val bodyHalfW = half * 0.24f
    val tipY = if (up) cy - half else cy + half
    val headBaseY = if (up) cy - half * 0.1f else cy + half * 0.1f
    val bodyEndY = if (up) cy + half else cy - half
    // Body (rectangle).
    val bodyTop = minOf(headBaseY, bodyEndY)
    val bodyBottom = maxOf(headBaseY, bodyEndY)
    drawRect(color, topLeft = Offset(cx - bodyHalfW, bodyTop), size = androidx.compose.ui.geometry.Size(bodyHalfW * 2, bodyBottom - bodyTop))
    // Head (triangle).
    val head = androidx.compose.ui.graphics.Path().apply {
        moveTo(cx, tipY)
        lineTo(cx - headHalfW, headBaseY)
        lineTo(cx + headHalfW, headBaseY)
        close()
    }
    drawPath(head, color)
}

/**
 * Renders a horizontal scrolling tape: fine grey ticks at every [stepDeg], white
 * labels, and a highlighted center cell containing [centerValue] in big yellow.
 * [current] is the value under the center; [labelStep] labels every N units.
 */
fun DrawScope.scrollingTape(
    tm: TextMeasurer,
    content: androidx.compose.ui.geometry.Rect,
    current: Float,
    pxPerUnit: Float,
    labelStep: Int,
    centerValue: String,
    showCenter: Boolean,
    wrap360: Boolean,
    tickTopFrac: Float = 0.62f,
    labelFrac: Float = 0.42f,
) {
    val cx = content.center.x
    val tickTop = content.top + content.height * tickTopFrac
    val labelY = content.top + content.height * labelFrac

    // Highlighted center cell.
    val cellW = labelStep * pxPerUnit * 1.6f
    val cellRect = androidx.compose.ui.geometry.Rect(
        cx - cellW / 2f, content.top + 2f, cx + cellW / 2f, content.bottom - 2f,
    )
    drawRect(Color(0xFF2C2C2C), topLeft = cellRect.topLeft, size = cellRect.size)
    drawRect(Color(0xFF6A6A6A), topLeft = cellRect.topLeft, size = cellRect.size,
        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.5f))

    val base = (current / labelStep).let { kotlin.math.round(it) }.toInt() * labelStep
    // Left/right margin around the center cell where nothing is drawn.
    val margin = cellW / 2f + pxPerUnit * labelStep * 0.35f
    for (k in -8..8) {
        val v = base + k * labelStep
        if (!wrap360 && v < 0) continue
        val disp = if (wrap360) ((v % 360) + 360) % 360 else v
        val x = cx + (v - current) * pxPerUnit
        if (x < content.left + 4f || x > content.right - 4f) continue
        // Skip anything within the margin band around the center cell.
        if (kotlin.math.abs(x - cx) < margin) continue
        drawLine(
            Color(0xFFCFCFCF),
            Offset(x, tickTop), Offset(x, tickTop + content.height * 0.16f),
            strokeWidth = 2f,
        )
        compactText(tm, disp.toString(), x, labelY, sizeSp = 15f, color = CompactStyle.Mark)
    }
    if (showCenter) {
        compactText(tm, centerValue, cx, labelY, sizeSp = 32f, bold = true, color = CompactStyle.Accent)
        drawLine(CompactStyle.Accent, Offset(cx, tickTop), Offset(cx, tickTop + content.height * 0.16f), strokeWidth = 3f)
    }
}

/**
 * EFIS-style horizontal heading tape inside [r]: labels every 10° with fine white
 * ticks under them, a highlighted centre cell holding the current heading in big
 * orange, and a magenta triangle cursor under the tape for the target heading.
 * Shared by the full EFIS panel and the compact heading instrument.
 */
fun DrawScope.efisHeadingTape(tm: TextMeasurer, r: Rect, heading: Float, showValue: Boolean, targetHeading: Int? = null) {
    val cx = r.center.x
    val pxPerDeg = r.width / 130f
    val labelStep = 10
    val cellW = r.width * 0.20f
    // Reserve the SAME bottom band as the speed tape's two arc rows so the graduation
    // part lines up 1:1 with NUMSPD. Heading has no arcs → the band stays empty (the
    // magenta target cursor sits in it).
    val arcRowH = r.height * 0.13f
    val gradBottom = r.bottom - 2f * arcRowH
    val gradH = gradBottom - r.top
    val labelY = r.top + gradH * 0.34f
    val tickTop = r.top + gradH * 0.56f
    val tickBottom = gradBottom
    // Value cell: from the title-bar bottom (r.top) down to the bottom of the graduations.
    val cell = Rect(cx - cellW / 2f, r.top + 2f, cx + cellW / 2f, gradBottom)
    val base = (heading / labelStep).let { kotlin.math.round(it) }.toInt() * labelStep
    val margin = cellW / 2f + 6f

    // Thin baseline at the bottom of the graduations (the "top of the arc band"),
    // mirroring where NUMSPD's arcs begin, so the two tapes line up.
    drawLine(Color(0xFF555555), Offset(r.left + 4f, gradBottom), Offset(r.right - 4f, gradBottom), strokeWidth = 1f)

    for (k in -9..9) {
        val v = base + k * labelStep
        val disp = ((v % 360) + 360) % 360
        val x = cx + (v - heading) * pxPerDeg
        if (x < r.left + 4f || x > r.right - 4f) continue
        if (abs(x - cx) < margin) continue
        drawLine(Color(0xFFEDEDED), Offset(x, tickTop), Offset(x, tickBottom), strokeWidth = 1.5f)
        compactText(tm, disp.toString(), x, labelY, sizeSp = 12f, color = CompactStyle.Dim)
    }

    if (showValue) {
        // Prominent current-heading cell: subtle fill, big orange (no yellow border).
        drawRect(Color(0xFF1E1E1E), topLeft = cell.topLeft, size = cell.size)
        val txt = "${heading.roundToInt()}"
        val sizeSp = if (heading.roundToInt() >= 100) 26f else 30f
        compactText(tm, txt, cx, cell.center.y - 1f, sizeSp = sizeSp, bold = true, color = CompactStyle.Accent)
        // Small white index tick centred just under the cell.
        drawLine(Color.White, Offset(cx, cell.bottom), Offset(cx, cell.bottom + gradH * 0.06f), strokeWidth = 2f)
    }

    // Magenta triangle cursor for the target heading, sitting in the bottom band at the
    // target's horizontal position (pointing up towards the tape).
    if (targetHeading != null) {
        val delta = ((targetHeading - heading + 540f) % 360f) - 180f
        val x = cx + delta * pxPerDeg
        if (x in (r.left + 2f)..(r.right - 2f)) {
            val magenta = Color(0xFFD24DEA)
            val baseY = r.bottom - 1f
            val h = 2f * arcRowH * 0.8f
            val hw = arcRowH * 0.8f
            val p = Path().apply {
                moveTo(x, baseY - h)          // tip up
                lineTo(x - hw, baseY)
                lineTo(x + hw, baseY)
                close()
            }
            drawPath(p, magenta)
        }
    }
}

/**
 * EFIS-style horizontal speed tape inside [r]. The upper part mirrors the heading
 * tape (labels + fine ticks + orange centre cell), shifted up by the height of the
 * TWO arc rows that sit at the bottom (green Vne row + white flap row). Stall
 * (Vs0/Vs1, red) and best-glide (Vpl, magenta) cursors span the full arc-rows
 * height. [arcs] must already be in the display unit.
 */
fun DrawScope.efisSpeedTape(tm: TextMeasurer, r: Rect, speed: Float, unitLabel: String, arcs: SpeedArcs?, showValue: Boolean) {
    val cx = r.center.x
    val pxPerUnit = r.width / 130f
    val labelStep = 10
    fun xForSpeed(v: Float): Float = cx + (v - speed) * pxPerUnit
    fun xClamp(v: Float): Float = xForSpeed(v).coerceIn(r.left + 4f, r.right - 4f)
    val cellW = r.width * 0.20f
    val margin = cellW / 2f + 6f

    // Two arc rows pinned to the bottom; the graduation part uses the space above.
    val arcRowH = r.height * 0.13f
    val row2Y = r.bottom - arcRowH / 2f
    val row1Y = row2Y - arcRowH
    val gradBottom = row1Y - arcRowH / 2f          // = TOP edge of the upper arc row
    val gradH = gradBottom - r.top
    // Labels near the top; ticks run DOWN to gradBottom so their base touches the arcs.
    val labelY = r.top + gradH * 0.34f
    val tickTop = r.top + gradH * 0.56f
    val tickBottom = gradBottom
    // Value cell: from the title-bar bottom down to the bottom of the graduations.
    val cell = Rect(cx - cellW / 2f, r.top + 2f, cx + cellW / 2f, gradBottom)

    val base = (speed / labelStep).let { kotlin.math.round(it) }.toInt() * labelStep
    for (k in -9..9) {
        val v = base + k * labelStep
        if (v < 0) continue
        val x = cx + (v - speed) * pxPerUnit
        if (x < r.left + 4f || x > r.right - 4f) continue
        if (abs(x - cx) < margin) continue
        drawLine(Color(0xFFEDEDED), Offset(x, tickTop), Offset(x, tickBottom), strokeWidth = 1.5f)
        compactText(tm, v.toString(), x, labelY, sizeSp = 12f, color = CompactStyle.Dim)
    }

    fun rowBg(cy: Float) = drawRect(Color(0xFF1E1E1E),
        topLeft = Offset(r.left + 4f, cy - arcRowH / 2), size = Size(r.width - 8f, arcRowH))
    fun band(cy: Float, fromV: Int?, toV: Int?, color: Color) {
        if (fromV == null || toV == null || toV <= fromV) return
        val x0 = xClamp(fromV.toFloat()); val x1 = xClamp(toV.toFloat())
        if (x1 <= x0) return
        drawRect(color, topLeft = Offset(x0, cy - arcRowH / 2), size = Size(x1 - x0, arcRowH))
    }
    // Cursor spanning BOTH arc rows: tip on top of row 1, base at bottom of row 2.
    fun cursor(v: Int?, color: Color) {
        if (v == null) return
        val x = xForSpeed(v.toFloat())
        if (x !in r.left..r.right) return
        val top = row1Y - arcRowH / 2f
        val bot = row2Y + arcRowH / 2f
        val halfW = arcRowH * 0.7f
        val p = Path().apply {
            moveTo(x, top)
            lineTo(x - halfW, bot)
            lineTo(x + halfW, bot)
            close()
        }
        drawPath(p, color)
    }

    // Row 1 — green normal range + red Vne block.
    rowBg(row1Y)
    if (arcs != null) {
        band(row1Y, arcs.greenMin, arcs.greenMax, CompactStyle.Climb)
        arcs.vne?.let {
            val x = xForSpeed(it.toFloat())
            if (x in (r.left + 4f)..(r.right - 4f)) {
                drawRect(Color(0xFFD32F2F), topLeft = Offset(x - 6f, row1Y - arcRowH / 2), size = Size(12f, arcRowH))
            }
        }
    }
    // Row 2 — white flap range.
    rowBg(row2Y)
    if (arcs != null) {
        band(row2Y, arcs.whiteMin, arcs.whiteMax, CompactStyle.Mark)
    }
    // Stall + best-glide cursors, full 2-row height.
    if (arcs != null) {
        cursor(arcs.vs1, Color(0xFFFF4136))
        cursor(arcs.vs0, Color(0xFFFF4136))
        cursor(arcs.vpl, Color(0xFFB94DD6))
    }

    if (showValue) {
        drawRect(Color(0xFF1E1E1E), topLeft = cell.topLeft, size = cell.size)
        val txt = speed.roundToInt().toString()
        val sizeSp = if (speed.roundToInt() >= 100) 26f else 30f
        compactText(tm, txt, cx, cell.top + cell.height * 0.40f, sizeSp = sizeSp, bold = true, color = CompactStyle.Accent)
        compactText(tm, unitLabel, cx, cell.top + cell.height * 0.78f, sizeSp = 9f, color = CompactStyle.Dim)
        // Small white index tick under the cell, like the heading tape.
        drawLine(Color.White, Offset(cx, cell.bottom), Offset(cx, cell.bottom + gradH * 0.10f), strokeWidth = 2f)
    }
}
