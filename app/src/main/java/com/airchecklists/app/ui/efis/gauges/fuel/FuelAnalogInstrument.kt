package com.airchecklists.app.ui.efis.gauges.fuel

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlin.math.cos
import kotlin.math.sin
import com.airchecklists.app.di.ServiceLocator
import com.airchecklists.app.ui.efis.gauges.GaugeBezel
import com.airchecklists.app.ui.efis.gauges.GaugeColors
import com.airchecklists.app.ui.efis.gauges.GaugeLobe
import com.airchecklists.app.ui.efis.gauges.GaugeLobeCentre
import com.airchecklists.app.ui.efis.gauges.LocalGaugeBezel
import com.airchecklists.app.ui.efis.gauges.compact.compactText
import com.airchecklists.app.ui.efis.gauges.compact.drawGestureHints
import com.airchecklists.app.ui.efis.gauges.drawGaugeLobes
import com.airchecklists.app.ui.efis.gauges.gaugeFace
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

private val FUEL_GREEN  = Color(0xFF35C759)
private val FUEL_ORANGE = Color(0xFFE8843A)
private val FUEL_RED    = Color(0xFFFF5252)

/** ANLCRB — Carburant, jauge ronde. */
@Composable
fun FuelAnalogInstrument(modifier: Modifier = Modifier) {
    val tm = rememberTextMeasurer()
    val bezel = LocalGaugeBezel.current
    var showConfig by remember { mutableStateOf(false) }
    var showAlert  by remember { mutableStateOf(false) }

    val aircraft       = ServiceLocator.currentAircraft()
    val capacityL      = aircraft?.fuelCapacityL?.toFloat()?.takeIf { it > 0f } ?: 91f
    val consumptionLh  = aircraft?.fuelConsumptionLh?.toFloat()?.takeIf { it > 0f } ?: 22f
    val reserveMin     = aircraft?.fuelReserveMin ?: 30
    val reserveL       = consumptionLh * reserveMin / 60f

    var fuelL by remember {
        mutableStateOf(ServiceLocator.instrumentPersist.fuelStartL ?: capacityL)
    }

    LaunchedEffect(Unit) {
        while (true) {
            val start = ServiceLocator.instrumentPersist.fuelTrackingStartMs
            val base  = ServiceLocator.instrumentPersist.fuelStartL ?: capacityL
            fuelL = if (start != null) {
                val elapsedH = (System.currentTimeMillis() - start) / 3_600_000f
                (base - consumptionLh * elapsedH).coerceAtLeast(0f)
            } else base
            delay(1_000)
        }
    }

    val trackingStart = ServiceLocator.instrumentPersist.fuelTrackingStartMs
    val alertShown    = ServiceLocator.instrumentPersist.fuelAlertShown

    if (fuelL <= reserveL && trackingStart != null && !alertShown) {
        ServiceLocator.updateInstruments { it.copy(fuelAlertShown = true) }
        showAlert = true
    }

    val state by ServiceLocator.efisProvider.state.collectAsStateWithLifecycle()
    val speedKmh = state.gpsSpeedKmh

    Canvas(
        modifier = modifier.pointerInput(Unit) {
            detectTapGestures(onLongPress = { showConfig = true })
        },
    ) {
        drawFuelGauge(tm, bezel, fuelL, capacityL, reserveL, consumptionLh, trackingStart, speedKmh)
    }

    if (showAlert) {
        AlertDialog(
            onDismissRequest = { showAlert = false },
            title = { Text("Réserve de carburant") },
            text  = { Text("Vous êtes entré en réserve de sécurité.\nCarburant restant : ${fuelL.roundToInt()} L") },
            confirmButton = { TextButton(onClick = { showAlert = false }) { Text("OK") } },
        )
    }

    if (showConfig) {
        FuelConfigDialog(
            currentL  = fuelL,
            capacityL = capacityL,
            onDismiss = { showConfig = false },
            onConfirm = { startL, startTracking ->
                ServiceLocator.updateInstruments {
                    it.copy(
                        fuelStartL          = startL,
                        fuelTrackingStartMs = if (startTracking) System.currentTimeMillis() else null,
                        fuelAlertShown      = false,
                    )
                }
                fuelL = startL
                showConfig = false
            },
        )
    }
}

private fun DrawScope.drawFuelGauge(
    tm: TextMeasurer,
    bezel: GaugeBezel,
    fuelL: Float,
    capacityL: Float,
    reserveL: Float,
    consumptionLh: Float,
    trackingStart: Long?,
    speedKmh: Float,
) {
    val (cx, cy, r) = gaugeFace(bezel)
    val pct = (fuelL / capacityL).coerceIn(0f, 1f)
    val reservePct = (reserveL / capacityL).coerceIn(0f, 1f)

    val fuelColor = when {
        fuelL <= reserveL -> FUEL_RED
        pct < 0.5f        -> FUEL_ORANGE
        else              -> FUEL_GREEN
    }

    // ── Titre ──
    compactText(tm, "CARBURANT", cx, cy - r * 0.78f, sizeSp = 12f, color = GaugeColors.MarkDim)

    // ── Arc de niveau (style altimètre) : 220° d'arc, de -110° à +110° depuis le bas ──
    // Angles en degrés Compose : 0°=droite, sens horaire.
    // On veut l'arc de 7h à 5h (ouverture en bas), soit startAngle=150°, sweep=240°.
    val arcStart  = 150f
    val arcSweep  = 240f
    val arcRadius = r * 0.60f
    val strokeW   = r * 0.10f

    // Arc de fond (gris) — légèrement plus épais que les arcs colorés
    drawArc(
        color      = Color(0xFF2A2A2A),
        startAngle = arcStart,
        sweepAngle = arcSweep,
        useCenter  = false,
        topLeft    = Offset(cx - arcRadius, cy - arcRadius),
        size       = Size(arcRadius * 2, arcRadius * 2),
        style      = Stroke(width = strokeW * 1.1f),
    )
    // Arc réserve (rouge) : du début jusqu'à reservePct
    if (reservePct > 0f) {
        drawArc(
            color      = FUEL_RED,
            startAngle = arcStart,
            sweepAngle = arcSweep * reservePct,
            useCenter  = false,
            topLeft    = Offset(cx - arcRadius, cy - arcRadius),
            size       = Size(arcRadius * 2, arcRadius * 2),
            style      = Stroke(width = strokeW),
        )
    }
    // Arc orange (de reservePct à 50%)
    if (pct > reservePct) {
        val orangeEnd = 0.5f
        val start2 = arcStart + arcSweep * reservePct
        val sweep2 = arcSweep * (minOf(pct, orangeEnd) - reservePct).coerceAtLeast(0f)
        if (sweep2 > 0f) drawArc(
            color      = FUEL_ORANGE,
            startAngle = start2,
            sweepAngle = sweep2,
            useCenter  = false,
            topLeft    = Offset(cx - arcRadius, cy - arcRadius),
            size       = Size(arcRadius * 2, arcRadius * 2),
            style      = Stroke(width = strokeW),
        )
    }
    // Arc vert (de 50% à pct)
    if (pct > 0.5f) {
        val start3 = arcStart + arcSweep * 0.5f
        val sweep3 = arcSweep * (pct - 0.5f)
        drawArc(
            color      = FUEL_GREEN,
            startAngle = start3,
            sweepAngle = sweep3,
            useCenter  = false,
            topLeft    = Offset(cx - arcRadius, cy - arcRadius),
            size       = Size(arcRadius * 2, arcRadius * 2),
            style      = Stroke(width = strokeW),
        )
    }

    // ── Aiguille ──
    val needleAngleDeg = arcStart + arcSweep * pct
    val needleAngleRad = Math.toRadians(needleAngleDeg.toDouble())
    val nx = cx + (arcRadius * cos(needleAngleRad)).toFloat()
    val ny = cy + (arcRadius * sin(needleAngleRad)).toFloat()
    drawLine(fuelColor, Offset(cx, cy), Offset(nx, ny), strokeWidth = r * 0.025f)
    drawCircle(fuelColor, radius = r * 0.04f, center = Offset(cx, cy))

    // ── Labels E / F ──
    val eAngleRad = Math.toRadians(arcStart.toDouble())
    val fAngleRad = Math.toRadians((arcStart + arcSweep).toDouble())
    val labelR = arcRadius + strokeW * 1.8f
    compactText(tm, "E",
        (cx + labelR * cos(eAngleRad)).toFloat(),
        (cy + labelR * sin(eAngleRad)).toFloat(),
        sizeSp = 10f, color = GaugeColors.MarkDim)
    compactText(tm, "F",
        (cx + labelR * cos(fAngleRad)).toFloat(),
        (cy + labelR * sin(fAngleRad)).toFloat(),
        sizeSp = 10f, color = GaugeColors.MarkDim)

    // ── Valeur centrale dans un rectangle arrondi ──
    val valueText = "${fuelL.roundToInt()}L"
    val valY = cy - r * 0.22f
    val boxW = r * 0.46f; val boxH = r * 0.30f
    val corner = androidx.compose.ui.geometry.CornerRadius(boxH * 0.35f)
    drawRoundRect(
        color    = Color(0xFF1A1A1A),
        topLeft  = Offset(cx - boxW / 2f, valY - boxH * 0.60f),
        size     = Size(boxW, boxH),
        cornerRadius = corner,
    )
    drawRoundRect(
        color    = Color(0xFF444444),
        topLeft  = Offset(cx - boxW / 2f, valY - boxH * 0.60f),
        size     = Size(boxW, boxH),
        cornerRadius = corner,
        style    = Stroke(width = 1.5f),
    )
    compactText(tm, valueText, cx, valY, sizeSp = 22f, bold = true, color = fuelColor)
    compactText(tm, "Volume", cx, cy + r * 0.12f, sizeSp = 10f, color = GaugeColors.MarkDim)
    compactText(tm, "Restant", cx, cy + r * 0.24f, sizeSp = 10f, color = GaugeColors.MarkDim)

    // ── Lobes ──
    val autonomyH   = if (consumptionLh > 0f) fuelL / consumptionLh else 0f
    val autonomyMin = (autonomyH * 60f).roundToInt()
    val autonomyStr = "%dh%02d".format(autonomyMin / 60, autonomyMin % 60)
    val rangeKm     = if (speedKmh > 30f) (autonomyH * speedKmh).roundToInt() else 0
    val rangeStr    = if (rangeKm > 0) "${rangeKm}km" else "—"
    val centreLabel = if (trackingStart != null) "ON" else "ARRÊT"
    val centreColor = if (trackingStart != null) FUEL_GREEN else GaugeColors.MarkDim

    drawGaugeLobes(
        tm, cx, cy, r,
        left   = GaugeLobe("AUTON.", autonomyStr, fuelColor),
        centre = GaugeLobeCentre(centreLabel, "", centreColor, GaugeColors.MarkDim),
        right  = GaugeLobe("RANGE", rangeStr, fuelColor),
    )

    drawGestureHints(cx - r * 0.92f, cy - r * 0.92f, hasLongPress = true, hasDoubleTap = false)
}
