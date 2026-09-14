package com.airchecklists.app.ui.efis.gauges.approach

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FlightLand
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.airchecklists.app.data.sensors.EfisState
import com.airchecklists.app.data.repository.AerodromeDirectory
import com.airchecklists.app.di.ServiceLocator
import com.airchecklists.app.ui.efis.gauges.GaugeBezel
import com.airchecklists.app.ui.efis.gauges.GaugeColors
import com.airchecklists.app.ui.efis.gauges.GaugeLobe
import com.airchecklists.app.ui.efis.gauges.GaugeLobeCentre
import com.airchecklists.app.ui.efis.gauges.LocalGaugeBezel
import com.airchecklists.app.ui.efis.gauges.compact.drawGestureHints
import com.airchecklists.app.ui.efis.gauges.compact.compactText
import com.airchecklists.app.ui.efis.gauges.drawGaugeLobes
import com.airchecklists.app.ui.efis.gauges.gaugeFace
import com.airchecklists.app.ui.efis.gauges.gaugeTitle
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import kotlin.math.atan2
import kotlin.math.roundToInt
import kotlin.math.tan

private val TOUCHDOWN_GREEN = Color(0xFF35C759)
private val TOUCHDOWN_ORANGE = Color(0xFFE8843A)
private val TOUCHDOWN_RED = Color(0xFFFF5252)

private const val RUNWAY_LENGTH_M = 1800f

/** ANLTCH — Toucher de piste. INDICATIVE ONLY, not certified. */
@Composable
fun TouchdownAnalogInstrument(
    state: EfisState,
    modifier: Modifier = Modifier,
) {
    val tm = rememberTextMeasurer()
    val bezel = LocalGaugeBezel.current
    val charts by ServiceLocator.vacRepository.charts.collectAsStateWithLifecycle()
    var showDialog by remember { mutableStateOf(false) }
    var locked by remember { mutableStateOf<ApproachTarget?>(null) }
    val iconPainter = rememberVectorPainter(Icons.Outlined.FlightLand)

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

    Canvas(
        modifier = modifier.pointerInput(Unit) {
            detectTapGestures(onLongPress = { showDialog = true })
        },
    ) {
        drawTouchdown(tm, bezel, target, state, iconPainter)
    }

    if (showDialog) {
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

private fun DrawScope.drawTouchdown(
    tm: TextMeasurer,
    bezel: GaugeBezel,
    target: ApproachTarget?,
    state: EfisState,
    iconPainter: Painter,
) {
    val (cx, cy, r) = gaugeFace(bezel)
    val hasTarget = target != null && target.qfuKnown

    val runwayLengthM = target?.runwayLengthM?.toFloat() ?: RUNWAY_LENGTH_M
    val fieldElevFt = target?.fieldElevFt ?: 0
    val aglFt = state.gpsAltitudeFt - fieldElevFt

    gaugeTitle(tm, "TOUCHER", cx, cy, r)

    // ── Piste ──
    val runwayY    = cy + r * 0.10f
    val runwayH    = r * 0.075f
    val runwayLeft  = cx - r * 0.70f
    val runwayRight = cx + r * 0.70f
    drawRect(
        color = Color(0xFF8B6F47),
        topLeft = androidx.compose.ui.geometry.Offset(runwayLeft, runwayY),
        size = androidx.compose.ui.geometry.Size(runwayRight - runwayLeft, runwayH),
    )
    // Seuil blanc
    drawLine(
        color = Color.White,
        start = androidx.compose.ui.geometry.Offset(runwayLeft, runwayY),
        end   = androidx.compose.ui.geometry.Offset(runwayLeft, runwayY + runwayH),
        strokeWidth = r * 0.025f,
    )

    // ── Calcul du point de toucher ──
    var touchdownColor = GaugeColors.MarkDim
    var touchdownX: Float? = null

    if (hasTarget && state.hasPosition) {
        val distToThresholdM = ApproachGeometry.haversineM(
            state.latitude, state.longitude, target.lat, target.lon,
        ).toFloat()

        val verticalSpeedMs   = state.verticalSpeedFtMin / 196.85f
        val horizontalSpeedMs = state.gpsSpeedKmh / 3.6f

        if (horizontalSpeedMs > 0.5f && verticalSpeedMs < -0.1f) {
            val glideSlopeRad = atan2(verticalSpeedMs, horizontalSpeedMs)
            val aglM = aglFt * 0.3048f
            val touchdownDistFromShipM = aglM / -tan(glideSlopeRad)
            val touchdownDistFromThresholdM = distToThresholdM - touchdownDistFromShipM

            touchdownColor = when {
                touchdownDistFromThresholdM < 0f           -> TOUCHDOWN_RED
                touchdownDistFromThresholdM > runwayLengthM -> TOUCHDOWN_ORANGE
                else                                        -> TOUCHDOWN_GREEN
            }

            val ratio = (touchdownDistFromThresholdM / runwayLengthM).coerceIn(-0.15f, 1.15f)
            touchdownX = runwayLeft + (runwayRight - runwayLeft) * ratio
        }
    }

    // ── Icône FlightLand (fixe, centrée dans la moitié haute, couleur selon toucher) ──
    val planeColor = if (!hasTarget || !state.hasPosition) GaugeColors.MarkDim else touchdownColor
    val iconSize = r * 0.56f
    val iconLeft = cx - iconSize / 2f
    val iconTop  = cy - r * 0.60f
    translate(left = iconLeft, top = iconTop) {
        with(iconPainter) {
            draw(
                size = androidx.compose.ui.geometry.Size(iconSize, iconSize),
                colorFilter = ColorFilter.tint(planeColor),
            )
        }
    }

    // ── Triangle ▼ sur la piste au point projeté ──
    touchdownX?.let { tx ->
        val ts = r * 0.075f
        val triangle = Path().apply {
            moveTo(tx, runwayY - ts * 0.3f)
            lineTo(tx - ts * 0.65f, runwayY - ts * 1.2f)
            lineTo(tx + ts * 0.65f, runwayY - ts * 1.2f)
            close()
        }
        drawPath(triangle, touchdownColor)
    }

    // ── Lobes ──
    val distTxt = if (hasTarget && state.hasPosition) {
        val d = ApproachGeometry.haversineM(
            state.latitude, state.longitude, target.lat, target.lon,
        ).roundToInt()
        "${d}m"
    } else "—"
    val distColor = if (hasTarget && state.hasPosition) GaugeColors.Accent else GaugeColors.MarkDim

    val icaoTxt = target?.icao ?: "—"
    val dimTxt  = if (target?.runwayLengthM != null) "${target.runwayLengthM}x60" else "—"

    val aglTxt   = if (hasTarget) "${aglFt.roundToInt()}ft" else "—"
    val aglColor = when {
        !hasTarget     -> GaugeColors.MarkDim
        aglFt < 2000f  -> GaugeColors.Accent
        else           -> GaugeColors.MarkDim
    }

    drawGaugeLobes(
        tm, cx, cy, r,
        left   = GaugeLobe("DIST", distTxt, distColor),
        centre = GaugeLobeCentre(
            primary      = icaoTxt,
            sub          = dimTxt,
            primaryColor = if (hasTarget) GaugeColors.Accent else GaugeColors.MarkDim,
            subColor     = GaugeColors.MarkDim,
        ),
        right  = GaugeLobe("HAUTEUR", aglTxt, aglColor),
    )

    drawGestureHints(cx - r * 0.92f, cy - r * 0.92f, hasLongPress = true, hasDoubleTap = false)

    // Watermark — instrument en cours de développement
    rotate(degrees = -30f, pivot = Offset(cx, cy)) {
        compactText(tm, "EN DÉVELOPPEMENT", cx, cy, sizeSp = 14f, bold = true, color = Color(0x55FFFFFF))
    }
}
