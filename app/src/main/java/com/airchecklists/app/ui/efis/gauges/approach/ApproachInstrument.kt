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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.airchecklists.app.data.model.AltitudeFormat
import com.airchecklists.app.data.model.AltitudeUnit
import com.airchecklists.app.data.model.EfisSpeedUnit
import com.airchecklists.app.data.model.VacChart
import com.airchecklists.app.data.repository.AerodromeDirectory
import com.airchecklists.app.data.sensors.EfisState
import com.airchecklists.app.di.ServiceLocator
import com.airchecklists.app.ui.efis.gauges.LocalGaugeBezel
import com.airchecklists.app.ui.efis.gauges.compact.CompactStyle
import com.airchecklists.app.ui.efis.gauges.compact.compactText
import com.airchecklists.app.ui.efis.gauges.compact.drawGestureHints
import com.airchecklists.app.ui.efis.gauges.drawNumTitleBar
import kotlin.math.abs
import kotlin.math.roundToInt

private val SKY_TOP = Color(0xFF173F63)
private val SKY_BOT = Color(0xFF4D86B0)
// Ground: aeronautical-chart look (warm earth tones), shared with the horizon instruments.
private val GROUND_TOP = CompactStyle.GroundMapTop   // hazy far terrain (chart tan)
private val GROUND_BOT = CompactStyle.GroundMapBot   // near terrain (darker earth)
private val GROUND_GRID = Color(0x33FFF4D8)  // faint parcel/section grid lines
private val GROUND_ROAD = Color(0x552E2410)  // a couple of darker "roads"
private val GATE_500 = CompactStyle.Climb          // green
private val GATE_1000 = CompactStyle.Accent        // yellow
private val GATE_1500 = CompactStyle.Accent2       // orange
private val RWY = Color(0xFF2A2A2A)
private val RWY_MARK = Color(0xFFEDEDED)

/**
 * NUMAPP — final-approach aid ("highway in the sky"). A 3D tunnel toward the landing
 * runway drawn on a Canvas over a sky/ground gradient: three nested perspective gates
 * (green 500 / yellow 1000 / orange 1500 ft above the circuit height = field elev +
 * 1000 ft) receding to a single vanishing point where the runway sits. A rear-view
 * aircraft symbol is offset by the lateral (right/left of axis) and vertical
 * (above/below the 3° plane) deviations — green inside tolerance, orange outside. A
 * five-cell readout strip shows IAS, ALT, V/S, AXE (m L/R), PLAN (ft vs 3° plane).
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
        modifier = modifier.pointerInput(Unit) {
            detectTapGestures(
                onDoubleTap = { ServiceLocator.efisProvider.nextDemo() },
                onLongPress = { showDialog = true },
            )
        },
    ) {
        drawApproach(tm, bezel, state, speedUnit, altUnit, target, errors)
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
    speedUnit: EfisSpeedUnit,
    altUnit: AltitudeUnit,
    target: ApproachTarget?,
    errors: ApproachGeometry.ApproachErrors,
) {
    val w = size.width
    val h = size.height
    val headerH = 22.dp.toPx().coerceAtMost(h * 0.20f)
    val readoutH = (h * 0.50f).coerceIn(104f, 140f)
    val bodyTop = headerH
    val sceneBottom = h - readoutH
    val sceneH = sceneBottom - bodyTop

    drawRect(CompactStyle.Bg, size = size)

    val hasTarget = errors.hasTarget
    // Deviations (clamped for the on-screen offset only; readouts show real numbers).
    val lateralM = errors.lateralM.coerceIn(-90.0, 90.0).toFloat()
    val aboveNorm = (errors.aboveFt / 240.0).coerceIn(-2.5, 2.5).toFloat()

    val aimX = w / 2f
    val aimY = bodyTop + sceneH * 0.50f
    val vpX = (aimX - lateralM * 2.1f).coerceIn(w * 0.18f, w * 0.82f)
    val vpY = (aimY + aboveNorm * 26f).coerceIn(bodyTop + sceneH * 0.20f, sceneBottom - sceneH * 0.18f)

    // ---- Background: sky above the horizon (vpY), ground below. ----
    drawRect(
        brush = Brush.verticalGradient(listOf(SKY_TOP, SKY_BOT), startY = bodyTop, endY = vpY),
        topLeft = Offset(0f, bodyTop), size = Size(w, vpY - bodyTop),
    )
    drawRect(
        brush = Brush.verticalGradient(listOf(GROUND_TOP, GROUND_BOT), startY = vpY, endY = sceneBottom),
        topLeft = Offset(0f, vpY), size = Size(w, sceneBottom - vpY),
    )
    drawGroundMap(w, vpX, vpY, sceneBottom)
    drawRect(Color(0x47C8D7E1), topLeft = Offset(0f, vpY - 2f), size = Size(w, 4f))

    if (hasTarget) {
        // Two framed info chips in the sky (top corners of the scene): landing QFU (runway
        // designator, e.g. "QFU 30") on the left, runway length from the VAC chart on the right.
        val qfuChip = if (target?.qfuKnown == true) {
            val tens = ((target.qfuTrueDeg / 10f).roundToInt() % 36).let { if (it <= 0) it + 36 else it }
            "QFU " + tens.toString().padStart(2, '0')
        } else "QFU ?"
        val lenChip = target?.runwayLengthM?.let { "$it m" } ?: "long. ?"
        val lenColor = if (target?.runwayLengthM != null) CompactStyle.Mark else GATE_1500
        drawSkyChip(tm, x = 8f, top = bodyTop + 6f, text = qfuChip, textColor = CompactStyle.Mark, anchorLeft = true)
        drawSkyChip(tm, x = w / 2f, top = bodyTop + 6f, text = target?.icao ?: "—", textColor = CompactStyle.Accent, anchorLeft = true, center = true)
        drawSkyChip(tm, x = w - 8f, top = bodyTop + 6f, text = lenChip, textColor = lenColor, anchorLeft = false)

        // Nearness 0 (≥4 km out) → 1 (at threshold): drives how "open"/big the tunnel and
        // runway are, so the runway visibly grows and rushes up as we close in.
        val nearness = (1.0 - (errors.alongM / 4000.0)).coerceIn(0.0, 1.0).toFloat()
        // Height above the field (AGL), and ground speed in m/s, feed the impact markers.
        val fieldElevFt = (target?.fieldElevFt ?: 0).toFloat()
        val aglFt = (state.gpsAltitudeFt - fieldElevFt).toDouble()
        val gsMs = (state.gpsSpeedKmh / 3.6).toDouble()
        // Clip to the scene rect so the runway sliding down during a low pass / go-around
        // (overflyDrop) can't spill below the readout strip and off the instrument.
        clipRect(0f, bodyTop, w, sceneBottom) {
            drawTunnel(
                tm, w, bodyTop, sceneBottom, sceneH, vpX, vpY, nearness,
                target?.qfuTrueDeg ?: Float.NaN,
                alongM = errors.alongM, aglFt = aglFt, groundSpeedMs = gsMs,
                runwayLengthM = target?.runwayLengthM,
            )
        }
    }

    // ---- Reference cross + aircraft symbol. ----
    val latOk = ApproachGeometry.onAxis(errors.lateralM)
    val planeOk = ApproachGeometry.onPlane(errors.aboveFt)
    if (hasTarget) {
        // Centreline reference (vertical dashed): green when on axis, else white. Starts
        // BELOW the central ICAO chip so it doesn't run through it and hurt legibility.
        val dash = PathEffect.dashPathEffect(floatArrayOf(11f, 8f))
        drawLine(
            color = if (latOk) GATE_500 else CompactStyle.Mark,
            start = Offset(aimX, bodyTop + 44f), end = Offset(aimX, sceneBottom - 4f),
            strokeWidth = 3f, pathEffect = dash,
        )
    }
    // Aircraft: offset OPPOSITE to the vanishing point ("fly the tunnel").
    val acx = aimX + lateralM * 2.1f
    val acy = aimY - aboveNorm * 26f
    // Colour code: GREEN on axis AND on plane; RED when clearly BELOW the plane
    // (dangerous — too low); ORANGE for any other out-of-tolerance state.
    val belowPlane = hasTarget && errors.aboveFt < -ApproachGeometry.VERT_TOL_FT
    val acColor = when {
        !hasTarget -> CompactStyle.Dim
        latOk && planeOk -> GATE_500
        belowPlane -> CompactStyle.Descent   // red = below the 3° plane
        else -> GATE_1500                     // orange = otherwise off-tolerance
    }
    // Trajectory line: from the aircraft symbol toward the runway threshold at the
    // vanishing point, so the pilot sees where the current path leads. Same colour as
    // the aircraft. Only when we have a target (the runway/VP is meaningful).
    if (hasTarget) {
        drawLine(
            color = acColor.copy(alpha = 0.75f),
            start = Offset(acx, acy), end = Offset(vpX, vpY),
            strokeWidth = 2.5f,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 9f)),
        )
    }
    drawAircraft(acx, acy, acColor)
    // Fixed white aim reticle in the centre.
    drawCircle(CompactStyle.Mark, radius = 9f, center = Offset(aimX, aimY), style = Stroke(width = 2f))
    drawLine(CompactStyle.Mark, Offset(aimX - 15f, aimY), Offset(aimX - 4f, aimY), strokeWidth = 2f)
    drawLine(CompactStyle.Mark, Offset(aimX + 4f, aimY), Offset(aimX + 15f, aimY), strokeWidth = 2f)
    drawLine(CompactStyle.Mark, Offset(aimX, aimY - 15f), Offset(aimX, aimY - 4f), strokeWidth = 2f)
    drawLine(CompactStyle.Mark, Offset(aimX, aimY + 4f), Offset(aimX, aimY + 15f), strokeWidth = 2f)

    // ---- Readouts strip. ----
    drawReadouts(tm, w, sceneBottom, readoutH, state, speedUnit, altUnit, errors)

    // ---- Title bar (drawn last, over the scene top). ----
    drawNumTitleBar(bezel, w, headerH)
    drawRect(Color(0xFF3A3A3A), size = size, style = Stroke(width = 2f))
    drawGestureHints(6f, headerH / 2f, hasLongPress = true, hasDoubleTap = true)
    compactText(tm, "Assistant Approche", w / 2f, headerH / 2f, sizeSp = 13f, color = CompactStyle.Dim)
    // Target ICAO / status on the right of the header.
    val sub = when {
        !state.hasPosition -> "—"
        target == null -> "—"
        else -> target.icao
    }
    compactText(tm, sub, w - 8f, headerH / 2f, sizeSp = 11f, color = CompactStyle.Dim, center = false, anchorRight = true)
}

/** Draws a small framed info chip on the sky. [x] is the left edge when [anchorLeft]
 *  (or the centre when [center]), else the right edge; [top] is the top of the chip. */
private fun DrawScope.drawSkyChip(
    tm: TextMeasurer, x: Float, top: Float, text: String, textColor: Color,
    anchorLeft: Boolean, center: Boolean = false,
) {
    val padH = 11f; val padV = 6f
    val m = tm.measure(text, TextStyle(color = textColor, fontSize = 15f.sp, fontWeight = FontWeight.Bold))
    val boxW = m.size.width + padH * 2f
    val boxH = m.size.height + padV * 2f
    val left = when {
        anchorLeft && center -> x - boxW / 2f
        anchorLeft -> x
        else -> x - boxW
    }
    // Semi-transparent dark panel + subtle border so it reads over the sky gradient.
    drawRect(Color(0xB3101418), topLeft = Offset(left, top), size = Size(boxW, boxH))
    drawRect(Color(0xFF6A7A88), topLeft = Offset(left, top), size = Size(boxW, boxH), style = Stroke(width = 1.5f))
    drawText(m, topLeft = Offset(left + padH, top + padV))
}

/** The runway at the vanishing point + three "touchdown" markers projected on the tunnel
 *  floor: where the aircraft would reach the ground if it held a constant 300 / 500 /
 *  1000 ft/min descent at its current ground speed. The marker landing ON the threshold
 *  is the recommended aim point (green); markers landing progressively further down the
 *  runway are yellow then orange (further = riskier). Markers whose impact falls outside
 *  the visible floor are hidden.
 *  [nearness] 0 (far) → 1 (at threshold) pulls the runway toward the viewer and grows it. */
private fun DrawScope.drawTunnel(
    tm: TextMeasurer, w: Float, bodyTop: Float, sceneBottom: Float, sceneH: Float,
    vpX: Float, vpY: Float, nearness: Float, qfuTrueDeg: Float,
    alongM: Double, aglFt: Double, groundSpeedMs: Double, runwayLengthM: Int?,
) {
    val nl = w * 0.11f; val nr = w * 0.89f
    // Lift the near floor edge off the bottom readout strip so the runway threshold isn't
    // glued to it (leave a clear gap).
    val nb = sceneBottom - sceneH * 0.10f
    fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t

    // ---- Runway: a flat strip lying on the tunnel floor, receding to the vanishing
    // point. Far end = narrow, near the horizon (vpX,vpY); near end = wide, toward the
    // bottom of the floor. Both ends are computed by interpolating the floor centreline
    // toward the VP, so the trapezoid shares the tunnel's perspective (coherent vanishing
    // point). It grows/comes closer with nearness. ----
    val floorNearX = (nl + nr) / 2f          // centre of the near floor edge
    val floorNearY = nb
    fun towardVp(x: Float, y: Float, t: Float) = Offset(lerp(x, vpX, t), lerp(y, vpY, t))
    // The runway threshold sits partway up the floor; far end further toward the VP. As
    // nearness grows, the near end slides down (closer) and the strip lengthens.
    val tThr = (0.10f - 0.10f * nearness).coerceAtLeast(0f)    // threshold t: 0.10 (far) → 0.0 (at us)
    val tFar = 0.78f - 0.30f * nearness                        // far runway end
    // Overfly: the runway slides DOWN and off the bottom of the scene so it "passes under"
    // the aircraft. Two cases:
    //  - Still approaching (alongM > 0): only slide when we're BOTH close (within ~300 m)
    //    AND low (near the ground) — a genuine low pass. A high, distant approach keeps the
    //    runway up at the vanishing point (lowFactor: 1 below ~400 ft AGL → 0 above ~800 ft).
    //  - Past the threshold (alongM ≤ 0): the runway is physically behind/under us, so it
    //    slides fully under regardless of altitude — this is what happens on a go-around
    //    once we overfly the threshold and climb away.
    val overfly = if (alongM <= 0.0) {
        (1.0 + (-alongM) / 300.0).coerceIn(0.0, 1.0)   // 0 at threshold → 1 within 300 m past it
    } else {
        val nearOverfly = ((300.0 - alongM) / 900.0).coerceIn(0.0, 1.0)
        val lowFactor = ((800.0 - aglFt) / 400.0).coerceIn(0.0, 1.0)
        nearOverfly * lowFactor
    }.toFloat()
    val overflyDrop = overfly * (sceneBottom - (bodyTop + sceneH * 0.10f)) * 1.15f
    fun onFloor(t: Float) = Offset(towardVp(floorNearX, floorNearY, t).x, towardVp(floorNearX, floorNearY, t).y + overflyDrop)
    val thr = onFloor(tThr)
    val far = onFloor(tFar)
    // Half-width in px scales with perspective depth (wider near, thinner far).
    val baseHalf = (nr - nl) * 0.16f
    val thrHalf = baseHalf * (1f - tThr)
    val farHalf = baseHalf * (1f - tFar)
    val rw = Path().apply {
        moveTo(far.x - farHalf, far.y); lineTo(far.x + farHalf, far.y)
        lineTo(thr.x + thrHalf, thr.y); lineTo(thr.x - thrHalf, thr.y); close()
    }
    drawPath(rw, RWY)
    // Dashed centreline down the strip.
    drawLine(RWY_MARK, far, thr, strokeWidth = 1.5f + 1.5f * nearness,
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f + 8f * nearness, 6f + 6f * nearness)))
    // Threshold bars across the near end (piano keys).
    val bars = 5
    for (k in 0 until bars) {
        val fx = (k + 0.5f) / bars                 // 0..1 across the threshold end
        val bx = thr.x - thrHalf + fx * (2f * thrHalf)
        val bw = (2f * thrHalf / bars) * 0.55f
        drawLine(RWY_MARK, Offset(bx, thr.y - 1f), Offset(bx, thr.y + 3f + 3f * nearness), strokeWidth = bw.coerceIn(1.5f, 8f))
    }
    // QFU label at the threshold (two digits, e.g. "30" for 304°). Grows with nearness.
    if (!qfuTrueDeg.isNaN()) {
        val qfuTens = ((qfuTrueDeg / 10f).roundToInt() % 36).let { if (it <= 0) it + 36 else it }
        val qfuText = qfuTens.toString().padStart(2, '0')
        compactText(tm, qfuText, thr.x, thr.y - 12f - 10f * nearness,
            sizeSp = 12f + 10f * nearness, bold = true, color = RWY_MARK)
    }
    // Runway length caption from the VAC chart (or "long. ?" when unknown) — feeds the
    // touchdown feasibility below.
    val lenText = runwayLengthM?.let { "$it m" } ?: "long. ?"
    compactText(tm, lenText, thr.x, thr.y + 6f + 6f * nearness,
        sizeSp = 9f + 4f * nearness, color = if (runwayLengthM != null) RWY_MARK else GATE_1500, center = true)

    // ---- Touchdown markers for 300 / 500 / 1000 ft/min. ----
    // Distance travelled over the ground before reaching field level at each rate:
    //   time_to_ground(min) = aglFt / rate ; ground distance = groundSpeed(m/s)*60*time.
    // Impact's along-track distance FROM the threshold = alongM - groundDist (negative =
    // past the threshold, down the runway). We map along-track onto the floor: at the ship
    // (alongDist = alongM) the point sits at the near floor edge (tThr's near side); at the
    // threshold (alongDist = 0) it sits at the runway threshold; beyond → toward the far end.
    if (aglFt > 5.0 && groundSpeedMs > 1.0 && alongM > 1.0) {
        data class Rate(val ftMin: Int, val label: String)
        // Map an along-track distance-from-threshold to a floor point + half-width.
        //   alongDist = alongM  → at the ship  → near floor edge (t = 0)
        //   alongDist = 0       → at threshold → t = tThr
        //   alongDist < 0       → past threshold, down the runway → toward tFar
        // Linear in along-distance on each side (perspective foreshortening handled by
        // towardVp's interpolation being denser toward the VP anyway).
        val runwayLenM = (runwayLengthM ?: 1500).toDouble()   // usable length from the VAC chart
        fun floorAt(alongDist: Double): Pair<Offset, Float>? {
            val t: Float = when {
                alongDist > alongM -> return null                       // behind the ship → off-screen
                alongDist >= 0.0 -> {
                    // Ship (t=0) → threshold (t=tThr), linear in the fraction covered.
                    val frac = 1.0 - alongDist / alongM                 // 0 at ship, 1 at threshold
                    (tThr * frac).toFloat()
                }
                else -> {
                    // Past threshold: 0..runwayLenM maps tThr..tFar.
                    val frac = (-alongDist / runwayLenM).coerceIn(0.0, 1.0)
                    (tThr + (tFar - tThr) * frac).toFloat()
                }
            }
            if (-alongDist > runwayLenM) return null                    // impact beyond runway end → hide
            val tc = t.coerceIn(0f, 0.999f)
            val p = onFloor(tc)
            val half = baseHalf * (1f - tc)
            return p to half
        }
        // Compute impacts, sort by distance down the runway to assign green/yellow/orange.
        val rates = listOf(Rate(300, "300 ft/min"), Rate(500, "500 ft/min"), Rate(1000, "1000 ft/min"))
        data class Impact(val rate: Rate, val alongDist: Double, val pos: Offset, val half: Float)
        val impacts = rates.mapNotNull { r ->
            val timeMin = aglFt / r.ftMin
            val groundDist = groundSpeedMs * 60.0 * timeMin
            val alongDist = alongM - groundDist          // distance from threshold (+ before, − past)
            floorAt(alongDist)?.let { (p, half) -> Impact(r, alongDist, p, half) }
        }
        // Recommended aim = impact closest to the threshold (|alongDist| smallest) → green;
        // then by increasing distance down the runway → yellow, orange.
        val ordered = impacts.sortedBy { abs(it.alongDist) }
        val palette = listOf(GATE_500, GATE_1000, GATE_1500)   // green, yellow, orange
        ordered.forEachIndexed { idx, imp ->
            val color = palette.getOrElse(idx) { GATE_1500 }
            // Bracket lying on the floor (open upward), OVERHANGING the runway on both sides
            // so it reads as a gate straddling the strip. Bigger than the runway half-width.
            val hw = (imp.half * 1.7f).coerceAtLeast(16f)
            val hh = (18f + 40f * (1f - (imp.pos.y - vpY) / (sceneBottom - vpY))).coerceIn(16f, 54f)
            val bx = imp.pos.x; val by = imp.pos.y
            val bracket = Path().apply {
                moveTo(bx - hw, by - hh)      // top-left up-post
                lineTo(bx - hw, by)           // down to floor
                lineTo(bx + hw, by)           // across the floor
                lineTo(bx + hw, by - hh)      // up the right post
            }
            drawPath(bracket, color = color, style = Stroke(width = 4f))
            // Floor bar (the "touchdown line").
            drawLine(color, Offset(bx - hw, by), Offset(bx + hw, by), strokeWidth = 4f)
            // Rate label OUTSIDE the bracket, to the side (left if room, else right), vertically
            // centred on the post — not above it.
            val labelY = by - hh / 2f
            val leftX = bx - hw - 6f
            if (leftX > 40f) {
                compactText(tm, imp.rate.label, leftX, labelY, sizeSp = 11f, color = color, center = false, bold = true, anchorRight = true)
            } else {
                compactText(tm, imp.rate.label, bx + hw + 6f, labelY, sizeSp = 11f, color = color, center = false, bold = true)
            }
        }
    }
}

/**
 * A stylised "map" ground: a perspective grid (parcels/section lines) converging to the
 * vanishing point, plus a couple of darker roads. Purely procedural — evokes a
 * sectional chart without a live map. Clipped to the ground band [vpY, sceneBottom].
 */
private fun DrawScope.drawGroundMap(w: Float, vpX: Float, vpY: Float, sceneBottom: Float) {
    val h = sceneBottom - vpY
    if (h <= 4f) return
    clipRect(0f, vpY, w, sceneBottom) {
        // Longitudinal lines fanning from the VP down to evenly spaced points on the
        // bottom edge → converging "field boundaries".
        val cols = 12
        for (i in 0..cols) {
            val bx = -w * 0.5f + (w * 2f) * (i.toFloat() / cols)
            drawLine(GROUND_GRID, Offset(vpX, vpY), Offset(bx, sceneBottom), strokeWidth = 1.2f)
        }
        // Two darker "roads" for variety.
        drawLine(GROUND_ROAD, Offset(vpX, vpY), Offset(w * 0.18f, sceneBottom), strokeWidth = 3f)
        drawLine(GROUND_ROAD, Offset(vpX, vpY), Offset(w * 0.86f, sceneBottom), strokeWidth = 3f)
        // Transverse lines compressed toward the horizon (perspective foreshortening).
        val rows = 7
        for (j in 1..rows) {
            val t = j.toFloat() / rows
            val y = vpY + h * (t * t)          // quadratic → denser near the horizon
            drawLine(GROUND_GRID, Offset(0f, y), Offset(w, y), strokeWidth = 1.2f)
        }
    }
}

/** Rear-view aircraft silhouette centred at (x,y). */
private fun DrawScope.drawAircraft(x: Float, y: Float, color: Color) {
    val s = 4.05f
    fun px(dx: Float) = x + dx * s
    fun py(dy: Float) = y + dy * s
    // Wing.
    val wing = Path().apply {
        moveTo(px(-30f), py(0f)); lineTo(px(30f), py(0f))
        lineTo(px(26f), py(4.5f)); lineTo(px(-26f), py(4.5f)); close()
    }
    drawPath(wing, color)
    // Fuselage.
    drawOval(color, topLeft = Offset(px(-5f), py(-7.5f)), size = Size(10f * s, 20f * s))
    // Tail fin.
    val fin = Path().apply {
        moveTo(px(0f), py(-6f)); lineTo(px(-3.5f), py(-17f)); lineTo(px(3.5f), py(-17f)); close()
    }
    drawPath(fin, color)
    drawRect(color, topLeft = Offset(px(-10f), py(-15f)), size = Size(20f * s, 3f * s))
}

private fun DrawScope.drawReadouts(
    tm: TextMeasurer, w: Float, top: Float, height: Float,
    state: EfisState, speedUnit: EfisSpeedUnit, altUnit: AltitudeUnit,
    errors: ApproachGeometry.ApproachErrors,
) {
    drawRect(Color(0x99000000), topLeft = Offset(0f, top), size = Size(w, height))
    val em = "—"
    val hasPos = state.hasPosition
    val hasTarget = errors.hasTarget

    val iasV: String
    val iasU: String
    if (speedUnit == EfisSpeedUnit.KNOTS) {
        iasV = if (hasPos) (state.gpsSpeedKmh * (1f / 1.852f)).roundToInt().toString() else em
        iasU = "kn"
    } else {
        iasV = if (hasPos) state.gpsSpeedKmh.roundToInt().toString() else em
        iasU = "km/h"
    }
    val altV = if (hasPos) AltitudeFormat.altValue(state.gpsAltitudeFt, altUnit).roundToInt().toString() else em
    val altU = AltitudeFormat.altLabel(altUnit)
    val vsRaw = AltitudeFormat.vsValue(state.verticalSpeedFtMin, altUnit)
    val vsV = if (hasPos) (if (vsRaw >= 0) "+" else "") + vsRaw.roundToInt() else em
    val vsU = AltitudeFormat.vsLabel(altUnit)
    val vsColor = if (state.verticalSpeedFtMin >= 0f) CompactStyle.Climb else CompactStyle.Descent

    val lat = errors.lateralM
    val axeV = if (hasTarget) (if (lat > 0) "▸" else "◂") + abs(lat).roundToInt() else em
    val axeU = if (hasTarget) "m " + (if (lat > 0) "D" else "G") else ""
    val axeColor = if (hasTarget && ApproachGeometry.onAxis(lat)) CompactStyle.Climb else CompactStyle.Accent2

    val above = errors.aboveFt
    val planV = if (hasTarget) (if (above > 0) "▴+" else "▾") + abs(above).roundToInt() else em
    val planU = if (hasTarget) "ft / 3°" else ""
    // Green on plane; RED below plane (too low); orange when above plane out of tolerance.
    val planColor = when {
        !hasTarget -> CompactStyle.Dim
        ApproachGeometry.onPlane(above) -> CompactStyle.Climb
        above < -ApproachGeometry.VERT_TOL_FT -> CompactStyle.Descent
        else -> CompactStyle.Accent2
    }

    data class Cell(val l: String, val v: String, val u: String, val c: Color)
    val cells = listOf(
        Cell("IAS", iasV, iasU, CompactStyle.Mark),
        Cell("ALT", altV, altU, CompactStyle.Mark),
        Cell("V/S", vsV, vsU, if (hasPos) vsColor else CompactStyle.Dim),
        Cell("AXE", axeV, axeU, if (hasTarget) axeColor else CompactStyle.Dim),
        Cell("PLAN", planV, planU, if (hasTarget) planColor else CompactStyle.Dim),
    )
    val cw = w / cells.size
    cells.forEachIndexed { i, c ->
        val cx = cw * (i + 0.5f)
        compactText(tm, c.l, cx, top + height * 0.18f, sizeSp = 11f, color = CompactStyle.Dim)
        compactText(tm, c.v, cx, top + height * 0.50f, sizeSp = 25f, bold = true, color = c.c)
        if (c.u.isNotEmpty()) compactText(tm, c.u, cx, top + height * 0.82f, sizeSp = 12f, color = CompactStyle.Dim)
        if (i > 0) {
            drawLine(Color(0x1FFFFFFF), Offset(cw * i, top + 4f), Offset(cw * i, top + height - 4f), strokeWidth = 1f)
        }
    }
}
