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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.airchecklists.app.data.model.EfisSpeedUnit
import com.airchecklists.app.data.model.SpeedArcs
import com.airchecklists.app.data.sensors.EfisState
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Full EFIS panel (3 rows tall) matching the reference mockup:
 *  - Top grey band: "EFIS" title + horizontal heading tape (yellow centre cell).
 *  - Black main: ALTI/ball/VARIO titles row, then vertical ALT scale | horizon | VARIO scale.
 *  - Bottom grey band: horizontal speed tape with coloured arcs + magenta Vpl cursor.
 */
@Composable
fun EfisCompact(
    state: EfisState,
    unit: EfisSpeedUnit,
    showValue: Boolean,
    arcs: SpeedArcs?,
    altUnit: com.airchecklists.app.data.model.AltitudeUnit = com.airchecklists.app.data.model.AltitudeUnit.FEET,
    modifier: Modifier = Modifier,
) {
    val tm = rememberTextMeasurer()
    val bezel = LocalGaugeBezel.current
    val targetHeading by com.airchecklists.app.di.ServiceLocator.targetHeading.collectAsStateWithLifecycle()
    val targetAltitude by com.airchecklists.app.di.ServiceLocator.targetAltitude.collectAsStateWithLifecycle()
    var showHeadingDialog by remember { mutableStateOf(false) }
    var showAltitudeDialog by remember { mutableStateOf(false) }
    Canvas(
        modifier = modifier.fillMaxSize().pointerInput(Unit) {
            detectTapGestures(
                onLongPress = { pos ->
                    when {
                        // Top heading-tape band → set the heading bug.
                        pos.y <= size.height * 0.13f -> showHeadingDialog = true
                        // Left ALT column (below the header) → set the target altitude.
                        pos.x <= size.width * 0.23f -> showAltitudeDialog = true
                    }
                },
            )
        },
    ) {
        val w = size.width
        val h = size.height
        val headerH = h * 0.13f
        val footerH = h * 0.14f

        // Backgrounds.
        drawRect(CompactStyle.Bg, size = size)
        // Accent/texture only on the thin EFIS title strip — NOT on the heading tape
        // band nor the speed tape band (those keep their own rendering).
        drawNumTitleBar(bezel, w, headerH * 0.28f)
        drawRect(Color(0xFF3A3A3A), size = size, style = Stroke(width = 2f))

        val toUnit = if (unit == EfisSpeedUnit.KNOTS) 1f / 1.852f else 1f
        val speed = state.gpsSpeedKmh * toUnit
        val speedUnitLabel = if (unit == EfisSpeedUnit.KNOTS) "kt" else "km/h"
        val arcsInUnit = arcs?.scaled(toUnit)
        val heading = (state.headingDeg.roundToInt() % 360 + 360) % 360

        // --- Header: EFIS title + heading tape ---
        compactText(tm, "EFIS", w / 2f, headerH * 0.14f, sizeSp = 12f, color = CompactStyle.Dim)
        val headTape = Rect(0f, headerH * 0.28f, w, headerH)
        efisHeadingTape(tm, headTape, heading.toFloat(), showValue, targetHeading)

        // --- Footer: speed tape + arcs ---
        val footTape = Rect(0f, h - footerH, w, h)
        efisSpeedTape(tm, footTape, speed, speedUnitLabel, arcsInUnit, showValue)

        // --- Main area (leave a gap above the footer speed tape) ---
        val main = Rect(6f, headerH + 4f, w - 6f, h - footerH - h * 0.03f)
        val colW = main.width * 0.23f
        val titleRowH = main.height * 0.22f
        val altiTitleRect = Rect(main.left, main.top, main.left + colW, main.top + titleRowH)
        val varioTitleRect = Rect(main.right - colW, main.top, main.right, main.top + titleRowH)
        val ballRect = Rect(altiTitleRect.right, main.top, varioTitleRect.left, main.top + titleRowH)

        // Titles (two clearly separated lines) — grey label + grey unit.
        compactText(tm, "ALTI", altiTitleRect.center.x, altiTitleRect.center.y - 22f, sizeSp = 16f, bold = true, color = CompactStyle.Dim)
        compactText(tm, "(${com.airchecklists.app.data.model.AltitudeFormat.altLabel(altUnit)})", altiTitleRect.center.x, altiTitleRect.center.y + 22f, sizeSp = 12f, bold = true, color = CompactStyle.Dim)
        compactText(tm, "VARIO", varioTitleRect.center.x, varioTitleRect.center.y - 22f, sizeSp = 16f, bold = true, color = CompactStyle.Dim)
        compactText(tm, "(${com.airchecklists.app.data.model.AltitudeFormat.vsLabel(altUnit)})", varioTitleRect.center.x, varioTitleRect.center.y + 22f, sizeSp = 12f, bold = true, color = CompactStyle.Dim)

        // Slip ball (centre of titles row); its pill matches the horizon width.
        val horizonLeft = main.left + colW + 12f
        val horizonRight = main.right - colW - 12f
        slipBall(tm, ballRect, state.slip, state.rollDeg, horizonLeft, horizonRight)

        // Bottom row: vertical scales + horizon.
        val scalesTop = main.top + titleRowH
        val altScale = Rect(main.left, scalesTop, main.left + colW, main.bottom)
        val varioScale = Rect(main.right - colW, scalesTop, main.right, main.bottom)
        val horizon = Rect(horizonLeft, scalesTop, horizonRight, main.bottom)

        drawHorizonBox(tm, horizon, state.pitchDeg, state.rollDeg)

        val trendColor = if (state.verticalSpeedFtMin >= 0f) CompactStyle.Climb else CompactStyle.Descent
        val meters = altUnit == com.airchecklists.app.data.model.AltitudeUnit.METERS
        val altVal = com.airchecklists.app.data.model.AltitudeFormat.altValue(state.gpsAltitudeFt, altUnit)
        val varVal = com.airchecklists.app.data.model.AltitudeFormat.vsValue(state.verticalSpeedFtMin, altUnit)
        val altTarget = targetAltitude?.let { com.airchecklists.app.data.model.AltitudeFormat.altValue(it.toFloat(), altUnit).roundToInt() }
        verticalScale(tm, altScale, altVal, labelStep = if (meters) 50 else 100,
            valueColor = trendColor, showValue = showValue, tickOnRight = true, targetValue = altTarget)
        verticalScale(tm, varioScale, varVal, labelStep = if (meters) 1 else 100,
            valueColor = trendColor, showValue = showValue, tickOnRight = false)
        // Gesture hints in the EFIS title bar (long-press: heading + altitude bands).
        drawGestureHints(6f, headerH * 0.5f, hasLongPress = true, hasDoubleTap = false)
    }

    if (showAltitudeDialog) {
        com.airchecklists.app.ui.components.AltitudeEntryDialog(
            initial = targetAltitude,
            onDismiss = { showAltitudeDialog = false },
            onConfirm = { a -> com.airchecklists.app.di.ServiceLocator.targetAltitude.value = a; showAltitudeDialog = false },
            onClear = { com.airchecklists.app.di.ServiceLocator.targetAltitude.value = null; showAltitudeDialog = false },
        )
    }

    if (showHeadingDialog) {
        com.airchecklists.app.ui.components.HeadingEntryDialog(
            initial = targetHeading,
            onDismiss = { showHeadingDialog = false },
            onConfirm = { hdg -> com.airchecklists.app.di.ServiceLocator.targetHeading.value = hdg; showHeadingDialog = false },
            onClear = { com.airchecklists.app.di.ServiceLocator.targetHeading.value = null; showHeadingDialog = false },
        )
    }
}

/** Vertical moving-tape scale (ALT/VARIO). Highlighted centre value cell (green/red). */
private fun DrawScope.verticalScale(
    tm: TextMeasurer,
    r: Rect,
    current: Float,
    labelStep: Int,
    valueColor: Color,
    showValue: Boolean,
    tickOnRight: Boolean,
    targetValue: Int? = null,
) {
    val cy = r.center.y
    // ~9 labels across the column, like the mockup (e.g. 1300..2100).
    val visibleLabels = 9
    val pxPerUnit = r.height / (labelStep * visibleLabels.toFloat())
    // Rail near the horizon-facing edge; ticks point outward; labels centred in the outer zone.
    val tickX = if (tickOnRight) r.right - r.width * 0.06f else r.left + r.width * 0.06f
    val tickLen = r.width * 0.16f
    val labelCx = if (tickOnRight) r.left + r.width * 0.36f else r.right - r.width * 0.36f
    // Value-cell half-height in value units (labels behind it are hidden).
    val cellHalfUnits = (r.height * 0.11f) / pxPerUnit
    val base = (current / labelStep).let { kotlin.math.round(it) }.toInt() * labelStep
    // Short grey rail spanning only the extreme visible ticks.
    val railTopY = cy - (labelStep * (visibleLabels / 2)) * pxPerUnit
    val railBotY = cy + (labelStep * (visibleLabels / 2)) * pxPerUnit
    drawLine(Color(0xFF8A8A8A), Offset(tickX, railTopY.coerceAtLeast(r.top)), Offset(tickX, railBotY.coerceAtMost(r.bottom)), strokeWidth = 3f)
    for (k in -(visibleLabels / 2)..(visibleLabels / 2)) {
        val v = base + k * labelStep
        val y = cy - (v - current) * pxPerUnit
        if (y < r.top + 4f || y > r.bottom - 4f) continue
        // Skip labels/ticks that fall behind the centre value cell.
        if (showValue && abs(v - current) < cellHalfUnits) continue
        drawLine(Color(0xFF8A8A8A), Offset(tickX, y), Offset(tickX + (if (tickOnRight) -tickLen else tickLen), y), strokeWidth = 3f)
        compactText(tm, v.toString(), labelCx, y, sizeSp = 16f, color = CompactStyle.Mark, center = true)
    }
    // Magenta target cursor (e.g. target altitude): drawn AFTER the value cell (below)
    // so it stays visible when the target ≈ current value.
    if (showValue) {
        val cellW = r.width * 0.98f
        val cellH = r.height * 0.17f
        // Anchor the cell to the OUTER edge (like the mockup: ALT flush-left, VARIO flush-right).
        val cellLeft = if (tickOnRight) r.left - r.width * 0.04f else r.right - cellW + r.width * 0.04f
        val cellCx = cellLeft + cellW / 2f
        drawRoundRect(Color(0xFF141414), topLeft = Offset(cellLeft, cy - cellH / 2), size = Size(cellW, cellH),
            cornerRadius = CornerRadius(4f, 4f))
        drawRoundRect(Color(0xFF5A5A5A), topLeft = Offset(cellLeft, cy - cellH / 2), size = Size(cellW, cellH),
            cornerRadius = CornerRadius(4f, 4f), style = Stroke(width = 1.5f))
        compactText(tm, current.roundToInt().toString(), cellCx, cy, sizeSp = 28f, bold = true, color = valueColor)
    }
    // Target cursor last → always on top of the value cell.
    targetValue?.let { t ->
        val y = (cy - (t - current) * pxPerUnit).coerceIn(r.top + 4f, r.bottom - 4f)
        val magenta = Color(0xFFD24DEA)
        val dir = if (tickOnRight) -1f else 1f
        val p = androidx.compose.ui.graphics.Path().apply {
            moveTo(tickX, y)
            lineTo(tickX + dir * tickLen, y - tickLen * 0.6f)
            lineTo(tickX + dir * tickLen, y + tickLen * 0.6f)
            close()
        }
        drawPath(p, magenta)
    }
}

private fun DrawScope.drawHorizonBox(tm: TextMeasurer, r: Rect, pitch: Float, roll: Float) {
    val cx = r.center.x
    val cy = r.center.y
    val pxPerDeg = r.height / 45f
    clipRect(r.left, r.top, r.right, r.bottom) {
        rotate(degrees = -roll, pivot = Offset(cx, cy)) {
            translate(top = pitch * pxPerDeg) {
                val big = maxOf(r.width, r.height) * 3f
                drawRect(
                    brush = Brush.verticalGradient(
                        0f to Color(0xFF8FD0FF), 1f to Color(0xFF0E4C7A),
                        startY = cy - big, endY = cy,
                    ),
                    topLeft = Offset(cx - big, cy - big), size = Size(big * 2, big),
                )
                drawRect(
                    brush = Brush.verticalGradient(
                        0f to CompactStyle.GroundMapTop, 1f to CompactStyle.GroundMapBot,
                        startY = cy, endY = cy + big,
                    ),
                    topLeft = Offset(cx - big, cy), size = Size(big * 2, big),
                )
                drawLine(Color.White, Offset(cx - big, cy), Offset(cx + big, cy), strokeWidth = 2.5f)
                pitchLadderBox(tm, cx, cy, pxPerDeg, r.width)
            }
        }
    }
    // Aircraft symbol — turn-indicator style.
    val wing = r.width * 0.22f
    val gap = wing * 0.18f
    val stroke = 6f
    drawLine(CompactStyle.Accent, Offset(cx - wing, cy), Offset(cx - gap, cy), strokeWidth = stroke)
    drawLine(CompactStyle.Accent, Offset(cx + gap, cy), Offset(cx + wing, cy), strokeWidth = stroke)
    drawLine(CompactStyle.Accent, Offset(cx, cy), Offset(cx, cy - wing * 0.5f), strokeWidth = stroke)
    drawCircle(CompactStyle.Accent, radius = wing * 0.14f, center = Offset(cx, cy))
}

private fun DrawScope.pitchLadderBox(tm: TextMeasurer, cx: Float, cy: Float, pxPerDeg: Float, width: Float) {
    data class Rung(val deg: Int, val halfFrac: Float, val labeled: Boolean)
    val rungs = listOf(
        Rung(-20, 0.16f, true), Rung(-15, 0.07f, false), Rung(-10, 0.11f, true), Rung(-5, 0.07f, false),
        Rung(5, 0.07f, false), Rung(10, 0.11f, true), Rung(15, 0.07f, false), Rung(20, 0.16f, true),
    )
    for (rung in rungs) {
        val y = cy - rung.deg * pxPerDeg
        val half = width * rung.halfFrac
        drawLine(Color.White, Offset(cx - half, y), Offset(cx + half, y), strokeWidth = 2f)
        if (rung.labeled) {
            val label = abs(rung.deg).toString()
            compactText(tm, label, cx - half - 16f, y, sizeSp = 13f)
            compactText(tm, label, cx + half + 16f, y, sizeSp = 13f)
        }
    }
}

/** Slip ball pill + "N° D/G" underneath. The pill spans [pillLeft,pillRight]
 *  (matched to the horizon width); only the ball inside moves with slip. */
private fun DrawScope.slipBall(tm: TextMeasurer, r: Rect, slip: Float, roll: Float, pillLeft: Float, pillRight: Float) {
    val pillW = pillRight - pillLeft
    val pillH = r.height * 0.24f
    val pcx = (pillLeft + pillRight) / 2f
    val pcy = r.top + r.height * 0.30f
    drawRoundRect(Color(0xFF2A2A2A), topLeft = Offset(pillLeft, pcy - pillH / 2), size = Size(pillW, pillH),
        cornerRadius = CornerRadius(pillH / 2, pillH / 2))
    val ballR = pillH * 0.40f
    listOf(-ballR - 5f, ballR + 5f).forEach { dx ->
        drawLine(CompactStyle.Mark, Offset(pcx + dx, pcy - ballR - 3f), Offset(pcx + dx, pcy + ballR + 3f), strokeWidth = 2.5f)
    }
    // Ball travel scaled to the wider pill.
    drawCircle(CompactStyle.Accent, radius = ballR, center = Offset(pcx + slip.coerceIn(-1f, 1f) * (pillW * 0.38f), pcy))
    // Bank angle just under the ball (closer to it, further from the horizon).
    val side = if (roll >= 0f) "D" else "G"
    compactText(tm, "${abs(roll).roundToInt()}° $side", pcx, r.top + r.height * 0.62f, sizeSp = 22f, bold = true, color = CompactStyle.Accent)
}
