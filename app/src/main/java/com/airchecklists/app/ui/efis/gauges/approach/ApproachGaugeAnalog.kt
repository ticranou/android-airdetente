package com.airchecklists.app.ui.efis.gauges.approach

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.airchecklists.app.data.sensors.EfisState
import com.airchecklists.app.di.ServiceLocator
import com.airchecklists.app.ui.efis.gauges.GaugeBezel
import com.airchecklists.app.ui.efis.gauges.GaugeColors
import com.airchecklists.app.ui.efis.gauges.GaugeLobe
import com.airchecklists.app.ui.efis.gauges.GaugeLobeCentre
import com.airchecklists.app.ui.efis.gauges.LocalGaugeBezel
import com.airchecklists.app.ui.efis.gauges.compact.drawGestureHints
import com.airchecklists.app.ui.efis.gauges.drawGaugeLobes
import com.airchecklists.app.ui.efis.gauges.gaugeFace
import com.airchecklists.app.ui.efis.gauges.gaugeTitle
import kotlin.math.abs
import kotlin.math.roundToInt

private val ON   = Color(0xFF35C759)   // green: on axis
private val OFF  = Color(0xFFE8843A)   // orange: off axis
private val FLAG = Color(0xFFFF5252)   // LOC flag (no data)

// Proximity ring colour (thin, dimmed — ground proximity indicator)
private val RING_DIM = Color(0x44FFFFFF)

private val ALT_OK   = Color(0xFF32C832)
private val ALT_WARN = Color(0xFFFFC107)
private val ALT_LOW  = Color(0xFFE53935)

/**
 * ANLAPP — analog final-approach aid, in the classic ILS "cross-pointer" style.
 *
 * A round instrument face with two full-length deviation bars:
 *  - the VERTICAL bar slides left/right to show the lateral deviation from the extended
 *    runway axis (localizer): bar to the LEFT ⇒ the axis is to your left ⇒ fly left.
 *  - the HORIZONTAL bar slides up/down to show the deviation from the 3° glide plane
 *    (glideslope): bar ABOVE centre ⇒ the plane is above you ⇒ you are LOW.
 *
 * Two dots per side mark the tolerance scale (1st dot = ±15 m / ±60 ft, 2nd = double).
 * Bars are green inside both tolerances, red when below the plane, orange otherwise —
 * mirroring the NUMAPP colour rule. When no target/QFU is available the bars park at
 * centre and red LOC / GS flags appear. Mini numeric readouts (AXE / PLAN / QFU) sit on
 * the lower face. Double-tap cycles the demo; long-press locks a terrain + QFU.
 *
 * INDICATIVE ONLY, not certified. See [ApproachGeometry] / [ApproachTarget].
 */
@Composable
fun ApproachGaugeAnalog(
    state: EfisState,
    modifier: Modifier = Modifier,
) {
    val tm = rememberTextMeasurer()
    val bezel = LocalGaugeBezel.current
    val charts by ServiceLocator.vacRepository.charts.collectAsStateWithLifecycle()
    var showDialog by remember { mutableStateOf(false) }
    var locked by remember { mutableStateOf<ApproachTarget?>(null) }

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
        modifier = modifier.pointerInput(Unit) {
            detectTapGestures(
                onLongPress = { showDialog = true },
            )
        },
    ) {
        drawIls(tm, bezel, target, errors, state.gpsAltitudeFt)
    }

    if (showDialog) {
        val terrains by produceState(initialValue = charts, charts, state.hasPosition, latKey, lonKey) {
            value = com.airchecklists.app.data.repository.AerodromeDirectory.nearbyCharts(
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

private fun DrawScope.drawIls(
    tm: TextMeasurer,
    bezel: GaugeBezel,
    target: ApproachTarget?,
    errors: ApproachGeometry.ApproachErrors,
    altitudeFt: Float,
) {
    val (cx, cy, r) = gaugeFace(bezel)
    val hasTarget = errors.hasTarget

    // Cross-pointer scale at face centre.
    val scx = cx
    val scy = cy

    val full  = r * 0.44f
    val dot1  = full * 0.5f
    val latM  = errors.lateralM.toFloat()
    // Localizer: ship RIGHT of axis (+lat) => bar deflects LEFT (-x)
    val locDefl = (-latM / (2f * ApproachGeometry.LAT_TOL_M.toFloat()) * full).coerceIn(-full, full)

    val latOk    = abs(latM) <= ApproachGeometry.LAT_TOL_M
    val barColor = if (!hasTarget) GaugeColors.MarkDim else if (latOk) ON else OFF

    // ── Proximity rings at the edge — cumulative as altitude drops ──
    // AGL < 900 ft → outer (green) lights up
    // AGL < 500 ft → middle (orange) lights up
    // AGL < 300 ft → inner (red) lights up
    val fieldElevFt = target?.fieldElevFt
    val aglFt = if (fieldElevFt != null) altitudeFt - fieldElevFt else null
    val ringStroke = Stroke(width = r * 0.020f)
    val outerColor = if (aglFt != null && hasTarget && aglFt < 900f) ALT_OK   else RING_DIM
    val midColor   = if (aglFt != null && hasTarget && aglFt < 500f) ALT_WARN else RING_DIM
    val innerColor = if (aglFt != null && hasTarget && aglFt < 300f) ALT_LOW  else RING_DIM
    drawCircle(outerColor, radius = r * 0.940f, center = Offset(cx, cy), style = ringStroke)
    drawCircle(midColor,   radius = r * 0.880f, center = Offset(cx, cy), style = ringStroke)
    drawCircle(innerColor, radius = r * 0.820f, center = Offset(cx, cy), style = ringStroke)

    // ── Diamond scale markers (horizontal axis only) ──
    val diaR = r * 0.045f
    for (d in listOf(-full, -dot1, dot1, full)) {
        drawDiamond(Offset(scx + d, scy), diaR, GaugeColors.MarkDim)
    }

    // ── Localizer bar (vertical only) ──
    val barReach = full + r * 0.30f
    val barW     = r * 0.035f
    if (hasTarget) {
        val vx = scx + locDefl
        drawLine(barColor, Offset(vx, scy - barReach), Offset(vx, scy + barReach),
            strokeWidth = barW, cap = StrokeCap.Round)
    } else {
        drawLine(GaugeColors.MarkDim, Offset(scx, scy - barReach), Offset(scx, scy + barReach),
            strokeWidth = barW, cap = StrokeCap.Round)
        drawFlag(tm, "LOC", scx, scy - r * 0.28f)
    }

    // ── Centre reference ring ──
    drawCircle(GaugeColors.Face, r * 0.075f, Offset(scx, scy))
    drawCircle(GaugeColors.Mark, r * 0.075f, Offset(scx, scy), style = Stroke(width = 2.4f))

    // ── Numeric readouts ──
    val axeVal: String
    if (hasTarget) {
        val side = if (errors.lateralM > 0) "D" else "G"
        val latN = abs(latM).roundToInt().coerceAtMost(999)
        axeVal = if (abs(latM) < 1f) "0" else "${latN}m $side"
    } else {
        axeVal = "—"
    }
    val axeColor  = if (!hasTarget) GaugeColors.MarkDim else if (latOk) ON else OFF
    val altiTxt   = "${altitudeFt.roundToInt()} ft"
    val circuitAltFt: Int? = target?.fieldElevFt?.let { it + 1000 }
    val altiColor = when {
        circuitAltFt == null || !hasTarget   -> GaugeColors.MarkDim
        altitudeFt < circuitAltFt - 50       -> ALT_LOW
        abs(altitudeFt - circuitAltFt) <= 50 -> ALT_OK
        else                                 -> ALT_WARN
    }
    val circTxt   = circuitAltFt?.let { "$it ft" } ?: "—"
    val icaoTxt   = target?.icao ?: "—"

    gaugeTitle(tm, "APPROCHE", cx, cy, r)

    drawGaugeLobes(
        tm, cx, cy, r,
        left   = GaugeLobe("AXE", axeVal, axeColor),
        centre = GaugeLobeCentre(
            primary      = icaoTxt,
            sub          = altiTxt,
            primaryColor = if (hasTarget) GaugeColors.Accent else GaugeColors.MarkDim,
            subColor     = altiColor,
        ),
        right  = GaugeLobe("CIRC", circTxt, GaugeColors.MarkDim),
    )

    drawGestureHints(cx - r * 0.92f, cy - r * 0.92f, hasLongPress = true, hasDoubleTap = false)
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
