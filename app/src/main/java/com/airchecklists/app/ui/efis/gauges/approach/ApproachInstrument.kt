package com.airchecklists.app.ui.efis.gauges.approach

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.airchecklists.app.data.model.AltitudeUnit
import com.airchecklists.app.data.model.EfisSpeedUnit
import com.airchecklists.app.data.repository.AerodromeDirectory
import com.airchecklists.app.data.sensors.EfisState
import com.airchecklists.app.di.ServiceLocator
import com.airchecklists.app.ui.efis.gauges.LocalGaugeBezel
import com.airchecklists.app.ui.efis.gauges.LocalHideBorder
import com.airchecklists.app.ui.efis.gauges.LocalHideTitle
import com.airchecklists.app.ui.efis.gauges.compact.CompactStyle
import com.airchecklists.app.ui.efis.gauges.compact.compactText
import com.airchecklists.app.ui.efis.gauges.compact.drawGestureHints
import com.airchecklists.app.ui.efis.gauges.drawNumTitleBar
import kotlin.math.abs
import kotlin.math.roundToInt

// Proximity-arc colours (shared meaning with ANLAPP).
private val ALT_OK   = Color(0xFF32C832)   // green
private val ALT_WARN = Color(0xFFFFC107)   // orange
private val ALT_LOW  = Color(0xFFE53935)   // red
private val RING_DIM = Color(0xFF2E2E2E)   // unlit arc
private val ON       = CompactStyle.Climb      // on-axis green
private val OFF      = CompactStyle.Accent2    // off-axis orange
private val FLAG     = Color(0xFFFF5252)       // LOC flag (no data)

/**
 * NUMAPP — final-approach aid, horizontal strip layout (mirrors the analog ANLAPP):
 *  - three coloured proximity bars on EACH edge (green/orange/red), lighting up as the
 *    AGL height drops below the configurable thresholds (shared with ANLAPP);
 *  - a left zone recalling the target field (ICAO + runway length, circuit altitude + QFU);
 *  - a centre cross-pointer axis (diamond scale + deviated localizer bar), like ANLAPP;
 *  - a right zone with the AXE (lateral metres L/R) and ALTI (GPS altitude) values.
 *
 * Target is the nearest aerodrome (AUTO); long-press locks a chosen terrain + QFU.
 * INDICATIVE ONLY, not certified. See [ApproachGeometry]/[ApproachTarget].
 */
@Composable
fun ApproachInstrument(
    state: EfisState,
    speedUnit: EfisSpeedUnit,
    altUnit: AltitudeUnit,
    modifier: Modifier = Modifier,
) {
    val tm = rememberTextMeasurer()
    val bezel = LocalGaugeBezel.current
    val hideTitle = LocalHideTitle.current
    val hideCellBorder = LocalHideBorder.current
    val prefs by ServiceLocator.preferences.preferences.collectAsStateWithLifecycle()
    val charts by ServiceLocator.vacRepository.charts.collectAsStateWithLifecycle()
    var showDialog by remember { mutableStateOf(false) }
    // Transient manual override (session only): non-null → resolver uses it; null → AUTO.
    var locked by remember { mutableStateOf<ApproachTarget?>(null) }

    // Resolve the AUTO target off the UI thread; recompute only every ~1-2 km (rounded
    // position key) or when charts change. When a manual lock is set, AUTO is bypassed.
    val latKey = (state.latitude * 50).roundToInt()
    val lonKey = (state.longitude * 50).roundToInt()
    val auto by produceState<ApproachTarget?>(
        initialValue = null, charts, state.hasPosition, latKey, lonKey,
    ) {
        value = if (state.hasPosition) {
            ApproachTargetResolver.auto(state.latitude, state.longitude, charts)
        } else {
            null
        }
    }
    val target = locked ?: auto

    val errors = ApproachGeometry.compute(
        state.latitude, state.longitude, state.gpsAltitudeFt.toDouble(),
        state.hasPosition, target,
    )

    Canvas(
        modifier = modifier.fillMaxSize().pointerInput(Unit) {
            detectTapGestures(
                onLongPress = { showDialog = true },
            )
        },
    ) {
        drawApproach(tm, bezel, state, altUnit, target, errors, hideTitle, hideCellBorder,
            greenFt = prefs.approachGreenFt.toFloat(),
            orangeFt = prefs.approachOrangeFt.toFloat(),
            redFt = prefs.approachRedFt.toFloat())
    }

    if (showDialog) {
        // Nearby terrains for the picker (offline directory + user charts).
        val terrains by produceState(initialValue = charts, charts, state.hasPosition, latKey, lonKey) {
            value = AerodromeDirectory.nearbyCharts(
                state.latitude, state.longitude, state.hasPosition, charts, 40,
            )
        }
        ApproachTargetDialog(
            terrains = terrains,
            onDismiss = { showDialog = false },
            onAuto = { locked = null },
            onLock = { locked = it },
        )
    }
}

private fun DrawScope.drawApproach(
    tm: TextMeasurer,
    bezel: com.airchecklists.app.ui.efis.gauges.GaugeBezel,
    state: EfisState,
    altUnit: AltitudeUnit,
    target: ApproachTarget?,
    errors: ApproachGeometry.ApproachErrors,
    hideTitle: Boolean,
    hideCellBorder: Boolean,
    greenFt: Float,
    orangeFt: Float,
    redFt: Float,
) {
    val w = size.width
    val h = size.height
    drawRect(CompactStyle.Bg, size = size)

    val headerH = if (hideTitle) 0f else 22.dp.toPx().coerceAtMost(h * 0.28f)
    val bodyTop = headerH
    val bodyH = h - headerH
    val bodyCy = bodyTop + bodyH / 2f

    val hasTarget = errors.hasTarget
    val fieldElevFt = target?.fieldElevFt
    val aglFt = if (fieldElevFt != null) state.gpsAltitudeFt - fieldElevFt else null

    // ── Proximity arcs (three vertical bars) on both edges, mirrored. ──
    val arcZoneW = w * 0.055f
    val outerColor = if (aglFt != null && hasTarget && aglFt < greenFt)  ALT_OK   else RING_DIM
    val midColor   = if (aglFt != null && hasTarget && aglFt < orangeFt) ALT_WARN else RING_DIM
    val innerColor = if (aglFt != null && hasTarget && aglFt < redFt)    ALT_LOW  else RING_DIM
    drawEdgeArcs(0f, arcZoneW, bodyTop, bodyH, outerColor, midColor, innerColor, mirror = false)
    drawEdgeArcs(w - arcZoneW, arcZoneW, bodyTop, bodyH, outerColor, midColor, innerColor, mirror = true)

    // Content band between the two arc zones.
    val left = arcZoneW
    val right = w - arcZoneW
    val contentW = right - left
    val terrainW = contentW * 0.38f
    val valuesW = contentW * 0.34f
    val axisLeft = left + terrainW
    val axisRight = right - valuesW
    val axisCx = (axisLeft + axisRight) / 2f

    // ── Left: TERRAIN + CIRCUIT recall. ──
    val circuitAltFt: Int? = fieldElevFt?.let { it + 1000 }
    drawTerrainZone(tm, left, terrainW, bodyTop, bodyH, target, circuitAltFt)

    // ── Centre: cross-pointer axis (diamond scale + deviated localizer bar). ──
    drawAxis(tm, axisLeft, axisRight, axisCx, bodyCy, bodyH, errors, hasTarget)

    // ── Right: AXE + ALTI values. ──
    drawValuesZone(tm, axisRight, valuesW, bodyTop, bodyH, state, errors, altUnit, circuitAltFt, hasTarget)

    if (!hideCellBorder) drawRect(Color(0xFF3A3A3A), size = size, style = Stroke(width = 2f))

    // ── Title bar (over the top). ──
    if (headerH > 0f) {
        drawNumTitleBar(bezel, w, headerH)
        drawGestureHints(6f, headerH / 2f, hasLongPress = true, hasDoubleTap = false)
        compactText(tm, "APPROCHE", w / 2f, headerH / 2f, sizeSp = 13f, color = CompactStyle.Dim)
    }
}

/** Three stacked vertical bars (green/orange/red) filling the given edge zone. When
 *  [mirror] the order is reversed so the colours point inward symmetrically. */
private fun DrawScope.drawEdgeArcs(
    x: Float, zoneW: Float, top: Float, height: Float,
    green: Color, orange: Color, red: Color, mirror: Boolean,
) {
    val colors = if (mirror) listOf(red, orange, green) else listOf(green, orange, red)
    val gap = zoneW * 0.14f
    val barW = (zoneW - 4f * gap) / 3f
    val barTop = top + height * 0.12f
    val barH = height * 0.76f
    colors.forEachIndexed { i, c ->
        val bx = x + gap + i * (barW + gap)
        drawRoundRect(
            color = c,
            topLeft = Offset(bx, barTop),
            size = Size(barW, barH),
            cornerRadius = CornerRadius(barW * 0.4f),
        )
    }
}

/** Left zone: two columns — TERRAIN (ICAO + runway length) and CIRCUIT (altitude + QFU). */
private fun DrawScope.drawTerrainZone(
    tm: TextMeasurer, x: Float, zoneW: Float, top: Float, height: Float,
    target: ApproachTarget?, circuitAltFt: Int?,
) {
    val hasTarget = target != null
    val colW = zoneW / 2f
    val cxTerrain = x + colW * 0.5f
    val cxCircuit = x + colW * 1.5f

    val icaoTxt = target?.icao ?: "—"
    val lenTxt = target?.runwayLengthM?.let { "${it}m" } ?: ""
    val circTxt = circuitAltFt?.let { "${it}ft" } ?: "—"
    val qfuTxt = if (target?.qfuKnown == true) {
        val tens = ((target.qfuTrueDeg / 10f).roundToInt() % 36).let { if (it <= 0) it + 36 else it }
        "QFU " + tens.toString().padStart(2, '0')
    } else "QFU ?"

    // TERRAIN column.
    compactText(tm, "TERRAIN", cxTerrain, top + height * 0.22f, sizeSp = 10f, color = CompactStyle.Dim)
    compactText(tm, icaoTxt, cxTerrain, top + height * 0.52f, sizeSp = 19f, bold = true,
        color = if (hasTarget) CompactStyle.Accent else CompactStyle.Dim)
    if (lenTxt.isNotEmpty()) {
        compactText(tm, lenTxt, cxTerrain, top + height * 0.78f, sizeSp = 11f, color = CompactStyle.Dim)
    }

    // CIRCUIT column.
    compactText(tm, "CIRCUIT", cxCircuit, top + height * 0.22f, sizeSp = 10f, color = CompactStyle.Dim)
    compactText(tm, circTxt, cxCircuit, top + height * 0.52f, sizeSp = 19f, bold = true,
        color = if (circuitAltFt != null) CompactStyle.Accent else CompactStyle.Dim)
    compactText(tm, qfuTxt, cxCircuit, top + height * 0.78f, sizeSp = 11f, color = CompactStyle.Dim)
}

/** Centre cross-pointer: a horizontal diamond scale with a vertical localizer bar
 *  deflected left/right by the lateral error, bounded by dotted side separators. */
private fun DrawScope.drawAxis(
    tm: TextMeasurer, axisLeft: Float, axisRight: Float, cx: Float, cy: Float,
    bodyH: Float, errors: ApproachGeometry.ApproachErrors, hasTarget: Boolean,
) {
    val full = (axisRight - axisLeft) * 0.34f
    val dot1 = full * 0.5f
    val latM = errors.lateralM.toFloat()
    // Ship RIGHT of axis (+lat) ⇒ bar deflects LEFT (−x).
    val locDefl = (-latM / (2f * ApproachGeometry.LAT_TOL_M.toFloat()) * full).coerceIn(-full, full)
    val latOk = ApproachGeometry.onAxis(errors.lateralM)
    val barColor = if (!hasTarget) CompactStyle.Dim else if (latOk) ON else OFF

    // Dotted side separators (like the mockup).
    val dash = PathEffect.dashPathEffect(floatArrayOf(4f, 7f))
    val sepTop = cy - bodyH * 0.34f
    val sepBot = cy + bodyH * 0.34f
    drawLine(Color(0xFF555555), Offset(axisLeft, sepTop), Offset(axisLeft, sepBot), strokeWidth = 2f, pathEffect = dash)
    drawLine(Color(0xFF555555), Offset(axisRight, sepTop), Offset(axisRight, sepBot), strokeWidth = 2f, pathEffect = dash)

    // Diamond scale markers.
    val diaR = bodyH * 0.11f
    for (d in listOf(-full, -dot1, dot1, full)) {
        drawDiamond(Offset(cx + d, cy), diaR, Color(0xFF8A8A8A))
    }

    // Localizer bar (vertical).
    val barReach = bodyH * 0.36f
    val barW = bodyH * 0.05f
    if (hasTarget) {
        val vx = cx + locDefl
        drawLine(barColor, Offset(vx, cy - barReach), Offset(vx, cy + barReach), strokeWidth = barW, cap = StrokeCap.Round)
    } else {
        drawLine(CompactStyle.Dim, Offset(cx, cy - barReach), Offset(cx, cy + barReach), strokeWidth = barW, cap = StrokeCap.Round)
        drawFlag(tm, "LOC", cx, cy - bodyH * 0.22f)
    }

    // Centre reference ring.
    drawCircle(CompactStyle.Bg, diaR * 0.95f, Offset(cx, cy))
    drawCircle(CompactStyle.Mark, diaR * 0.95f, Offset(cx, cy), style = Stroke(width = 2.4f))
}

/** Right zone: AXE (lateral metres L/R) and ALTI (GPS altitude) values. */
private fun DrawScope.drawValuesZone(
    tm: TextMeasurer, x: Float, zoneW: Float, top: Float, height: Float,
    state: EfisState, errors: ApproachGeometry.ApproachErrors, altUnit: AltitudeUnit,
    circuitAltFt: Int?, hasTarget: Boolean,
) {
    val colW = zoneW / 2f
    val cxAxe = x + colW * 0.5f
    val cxAlti = x + colW * 1.5f

    // AXE — value (metres) on the middle line, unit "m G/D" below.
    val lat = errors.lateralM
    val axeNum: String
    val axeUnit: String
    if (hasTarget) {
        if (abs(lat) < 1.0) { axeNum = "0"; axeUnit = "m" }
        else { axeNum = "${abs(lat).roundToInt().coerceAtMost(999)}"; axeUnit = "m " + (if (lat > 0) "D" else "G") }
    } else { axeNum = "—"; axeUnit = "" }
    val axeColor = if (!hasTarget) CompactStyle.Dim else if (ApproachGeometry.onAxis(lat)) ON else OFF
    compactText(tm, "AXE", cxAxe, top + height * 0.22f, sizeSp = 10f, color = CompactStyle.Dim)
    compactText(tm, axeNum, cxAxe, top + height * 0.52f, sizeSp = 20f, bold = true, color = axeColor)
    if (axeUnit.isNotEmpty()) {
        compactText(tm, axeUnit, cxAxe, top + height * 0.80f, sizeSp = 11f, color = CompactStyle.Dim)
    }

    // ALTI — value (ft) on the middle line, unit "ft" below.
    val altFt = state.gpsAltitudeFt
    val altNum = if (state.hasPosition) "${altFt.roundToInt()}" else "—"
    val altColor = when {
        circuitAltFt == null || !state.hasPosition -> CompactStyle.Dim
        altFt < circuitAltFt - 50       -> ALT_LOW
        abs(altFt - circuitAltFt) <= 50 -> ALT_OK
        else                            -> ALT_WARN
    }
    compactText(tm, "ALTI", cxAlti, top + height * 0.22f, sizeSp = 10f, color = CompactStyle.Dim)
    compactText(tm, altNum, cxAlti, top + height * 0.52f, sizeSp = 20f, bold = true, color = altColor)
    if (state.hasPosition) {
        compactText(tm, "ft", cxAlti, top + height * 0.80f, sizeSp = 11f, color = CompactStyle.Dim)
    }
}

/** Small filled diamond (ILS scale marker) centred on [c] with half-diagonal [rad]. */
private fun DrawScope.drawDiamond(c: Offset, rad: Float, color: Color) {
    val p = Path().apply {
        moveTo(c.x, c.y - rad); lineTo(c.x + rad, c.y)
        lineTo(c.x, c.y + rad); lineTo(c.x - rad, c.y); close()
    }
    drawPath(p, color, style = Stroke(width = 1.8f))
}

/** A small red "warning flag" box with a label, centred on (x, y). */
private fun DrawScope.drawFlag(tm: TextMeasurer, label: String, x: Float, y: Float) {
    val m = tm.measure(label)
    val w = m.size.width + 10f
    val h = m.size.height + 4f
    drawRoundRect(FLAG,
        topLeft = Offset(x - w / 2f, y - h / 2f),
        size = Size(w, h),
        cornerRadius = CornerRadius(4f),
    )
    drawText(m, topLeft = Offset(x - m.size.width / 2f, y - m.size.height / 2f))
}
