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
import androidx.compose.ui.graphics.drawscope.clipPath
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
import com.airchecklists.app.ui.efis.gauges.gaugeText
import com.airchecklists.app.ui.efis.gauges.gaugeTitle
import kotlin.math.abs
import kotlin.math.roundToInt

private val ON = Color(0xFF35C759)   // green: inside tolerance
private val OFF = Color(0xFFE8843A)  // orange: outside tolerance
private val LOW = Color(0xFFFF5252)  // red: below the 3° plane
private val FLAG = Color(0xFFFF5252) // GS/LOC flag (no data)

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
                onDoubleTap = { ServiceLocator.efisProvider.nextDemo() },
                onLongPress = { showDialog = true },
            )
        },
    ) {
        drawIls(tm, bezel, target, errors)
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
) {
    val (cx, cy, r) = gaugeFace(bezel)
    val hasTarget = errors.hasTarget

    // The cross-pointer scale is centred a little ABOVE the face centre, so the lower
    // third of the face is free for the four readout "lobes" (AXE / QFU / ICAO / PLAN).
    val scx = cx
    val scy = cy - r * 0.14f

    // Deviation scale: 1 dot = tolerance, 2 dots = full-scale (2× tolerance). Bars saturate
    // at the 2nd dot and never reach the bezel, so the instrument stays legible off-scale.
    val full = r * 0.44f                       // deflection at full-scale (2× tol)
    val dot1 = full * 0.5f                     // 1st diamond = ±tolerance
    val latM = errors.lateralM.toFloat()
    val aboveFt = errors.aboveFt.toFloat()
    // Localizer: ship RIGHT of axis (+lat) ⇒ axis is LEFT ⇒ bar deflects LEFT (−x).
    val locDefl = (-latM / (2f * ApproachGeometry.LAT_TOL_M.toFloat()) * full)
        .coerceIn(-full, full)
    // Glideslope: ship ABOVE plane (+above) ⇒ plane is BELOW ⇒ bar deflects DOWN (+y).
    val gsDefl = (aboveFt / (2f * ApproachGeometry.VERT_TOL_FT.toFloat()) * full)
        .coerceIn(-full, full)

    val latOk = abs(latM) <= ApproachGeometry.LAT_TOL_M
    val planeOk = abs(aboveFt) <= ApproachGeometry.VERT_TOL_FT
    val barColor = when {
        !hasTarget -> GaugeColors.MarkDim
        aboveFt < -ApproachGeometry.VERT_TOL_FT -> LOW
        latOk && planeOk -> ON
        else -> OFF
    }

    // ---- Tolerance scale + deviation bars. ----
    // Diamond scale markers at ±tol and ±full on both axes.
    val diaR = r * 0.045f
    for (d in listOf(-full, -dot1, dot1, full)) {
        drawDiamond(Offset(scx + d, scy), diaR, GaugeColors.MarkDim)
        drawDiamond(Offset(scx, scy + d), diaR, GaugeColors.MarkDim)
    }

    // Deviation pointers (rounded ILS bars, capped at ±full).
    val barReach = full + r * 0.10f
    val barW = r * 0.035f
    if (hasTarget) {
        val vx = scx + locDefl
        drawLine(barColor, Offset(vx, scy - barReach), Offset(vx, scy + barReach),
            strokeWidth = barW, cap = StrokeCap.Round)
        val hy = scy + gsDefl
        drawLine(barColor, Offset(scx - barReach, hy), Offset(scx + barReach, hy),
            strokeWidth = barW, cap = StrokeCap.Round)
    } else {
        drawLine(GaugeColors.MarkDim, Offset(scx, scy - barReach), Offset(scx, scy + barReach),
            strokeWidth = barW, cap = StrokeCap.Round)
        drawLine(GaugeColors.MarkDim, Offset(scx - barReach, scy), Offset(scx + barReach, scy),
            strokeWidth = barW, cap = StrokeCap.Round)
        drawFlag(tm, "LOC", scx - r * 0.28f, scy - r * 0.28f)
        drawFlag(tm, "GS", scx + r * 0.28f, scy + r * 0.28f)
    }

    // Centre reference (the aircraft): a crisp white ring drawn on top of the bars.
    drawCircle(GaugeColors.Face, r * 0.075f, Offset(scx, scy))
    drawCircle(GaugeColors.Mark, r * 0.075f, Offset(scx, scy), style = Stroke(width = 2.4f))

    // ---- Numeric readouts — 3 bottom lobes per mockup layout. ----
    val axeVal: String
    val planVal: String
    if (hasTarget) {
        val side = if (errors.lateralM > 0) "D" else "G"
        val latN = abs(latM).roundToInt().coerceAtMost(999)
        axeVal = if (abs(latM) < 1f) "0" else "${latN}m $side"
        val sign = if (aboveFt > 0f) "+" else "−"
        val altN = abs(aboveFt).roundToInt().coerceAtMost(999)
        planVal = if (abs(aboveFt) < 1f) "0" else "$sign${altN}ft"
    } else {
        axeVal = "—"
        planVal = "—"
    }
    val axeColor = if (!hasTarget) GaugeColors.MarkDim else if (latOk) ON else OFF
    val planColor = when {
        !hasTarget -> GaugeColors.MarkDim
        aboveFt < -ApproachGeometry.VERT_TOL_FT -> LOW
        planeOk -> ON
        else -> OFF
    }
    val qfuTxt = if (target?.qfuKnown == true) {
        val tens = ((target.qfuTrueDeg / 10f).roundToInt() % 36).let { if (it <= 0) it + 36 else it }
        tens.toString().padStart(2, '0')
    } else "?"

    // Title at the standard position.
    gaugeTitle(tm, "APPROCHE", cx, cy, r)

    // Three bottom lobes: AXE | ICAO+QFU | PLAN
    drawGaugeLobes(
        tm, cx, cy, r,
        left   = GaugeLobe("AXE",  axeVal,  axeColor),
        centre = GaugeLobeCentre(
            primary      = qfuTxt,
            sub          = target?.icao ?: "—",
            primaryColor = GaugeColors.Mark,
            subColor     = if (hasTarget) GaugeColors.Accent else GaugeColors.MarkDim,
        ),
        right  = GaugeLobe("PLAN", planVal, planColor),
    )

    // Gesture hints (double-tap = demo, long-press = lock target).
    drawGestureHints(cx - r * 0.92f, cy - r * 0.92f, hasLongPress = true, hasDoubleTap = true)
}

/** Small filled diamond (ILS scale marker) centred on [c] with half-diagonal [rad]. */
private fun DrawScope.drawDiamond(c: Offset, rad: Float, color: Color) {
    val p = Path().apply {
        moveTo(c.x, c.y - rad); lineTo(c.x + rad, c.y)
        lineTo(c.x, c.y + rad); lineTo(c.x - rad, c.y); close()
    }
    drawPath(p, color, style = Stroke(width = 1.8f))
}

/** A small red "warning flag" box (classic ILS off-flag) with a label. */
private fun DrawScope.drawFlag(tm: TextMeasurer, label: String, x: Float, y: Float) {
    val m = tm.measure(label)
    val w = m.size.width + 10f
    val h = m.size.height + 4f
    drawRect(FLAG, topLeft = Offset(x - w / 2f, y - h / 2f), size = Size(w, h))
    drawText(m, topLeft = Offset(x - m.size.width / 2f, y - m.size.height / 2f))
}
