package com.airchecklists.app.ui.efis.gauges.fuel

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.airchecklists.app.di.ServiceLocator
import com.airchecklists.app.ui.efis.gauges.GaugeColors
import com.airchecklists.app.ui.efis.gauges.LocalGaugeBezel
import com.airchecklists.app.ui.efis.gauges.compact.CompactStyle
import com.airchecklists.app.ui.efis.gauges.compact.compactText
import com.airchecklists.app.ui.efis.gauges.drawNumTitleBar
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

private val FUEL_GREEN  = Color(0xFF1E8B1E)
private val FUEL_ORANGE = Color(0xFFCC7744)
private val FUEL_RED    = Color(0xFFCC2222)

/** NUMCRB — Carburant, compact. */
@Composable
fun FuelDigital(modifier: Modifier = Modifier) {
    val tm = rememberTextMeasurer()
    val bezel = LocalGaugeBezel.current
    var showConfig by remember { mutableStateOf(false) }

    val aircraft       = ServiceLocator.currentAircraft()
    val capacityL      = aircraft?.fuelCapacityL?.toFloat()?.takeIf { it > 0f } ?: 91f
    val consumptionLh  = aircraft?.fuelConsumptionLh?.toFloat()?.takeIf { it > 0f } ?: 22f
    val reserveL       = consumptionLh * (aircraft?.fuelReserveMin ?: 30) / 60f

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

    val efisState by ServiceLocator.efisProvider.state.collectAsStateWithLifecycle()
    val speedKmh = efisState.gpsSpeedKmh

    Canvas(
        modifier = modifier.fillMaxSize().pointerInput(Unit) {
            detectTapGestures(onLongPress = { showConfig = true })
        },
    ) {
        val w = size.width
        val h = size.height
        val pad = w * 0.02f

        // ── Header ──
        val headerH = h * 0.20f
        drawNumTitleBar(bezel, w, headerH)
        compactText(tm, "CARBURANT", w / 2f, headerH / 2f, sizeSp = 11f, color = GaugeColors.Mark)

        val pct        = (fuelL / capacityL).coerceIn(0f, 1f)
        val reservePct = (reserveL / capacityL).coerceIn(0f, 1f)
        val fuelColor  = when {
            fuelL <= reserveL -> FUEL_RED
            pct < 0.5f        -> FUEL_ORANGE
            else              -> FUEL_GREEN
        }

        // ── Barre pleine largeur avec dégradé ──
        val barTop  = headerH + h * 0.04f
        val barH    = h * 0.22f
        val barLeft = pad
        val barRight = w - pad

        // Fond gris
        drawRect(Color(0xFF222222), topLeft = Offset(barLeft, barTop), size = Size(barRight - barLeft, barH))

        // 3 zones colorées fixes : rouge (0→réserve), orange (réserve→50%), vert (50%→100%)
        val barW = barRight - barLeft
        val redEnd    = barLeft + barW * reservePct
        val orangeEnd = barLeft + barW * 0.5f
        drawRect(FUEL_RED,    topLeft = Offset(barLeft,    barTop), size = Size(redEnd    - barLeft,    barH))
        drawRect(FUEL_ORANGE, topLeft = Offset(redEnd,     barTop), size = Size(orangeEnd - redEnd,     barH))
        drawRect(FUEL_GREEN,  topLeft = Offset(orangeEnd,  barTop), size = Size(barRight  - orangeEnd,  barH))

        // Masque sombre sur la partie vide (au-delà du niveau actuel)
        val filledWidth = barW * pct
        if (pct < 1f) {
            drawRect(
                Color(0xCC111111),
                topLeft = Offset(barLeft + filledWidth, barTop),
                size = Size(barRight - barLeft - filledWidth, barH),
            )
        }

        // Labels E / F
        val labelY = barTop + barH / 2f
        compactText(tm, "E", barLeft - pad * 0.5f, labelY, sizeSp = 11f, color = GaugeColors.MarkDim)
        compactText(tm, "F", barRight + pad * 0.5f, labelY, sizeSp = 11f, color = GaugeColors.MarkDim)

        // Curseur triangulaire sous la barre, pointant vers le haut
        val cursorX = barLeft + filledWidth
        val cursorH = barH * 0.6f
        val cursorW = barH * 0.45f
        val cursorPath = Path().apply {
            moveTo(cursorX, barTop + barH)              // pointe touchant le bas de la barre
            lineTo(cursorX - cursorW / 2f, barTop + barH + cursorH)  // base gauche
            lineTo(cursorX + cursorW / 2f, barTop + barH + cursorH)  // base droite
            close()
        }
        drawPath(cursorPath, Color.White)

        // ── Ligne de valeurs ──
        val valY   = barTop + barH + (h - barTop - barH) * 0.78f
        val labY   = valY - h * 0.16f

        val div1 = w * 0.38f
        val div2 = w * 0.65f
        val divColor = Color(0xFF444444)
        val divTop = barTop + barH + h * 0.03f
        val divBot = h - h * 0.04f
        drawLine(divColor, Offset(div1, divTop), Offset(div1, divBot), strokeWidth = 1f)
        drawLine(divColor, Offset(div2, divTop), Offset(div2, divBot), strokeWidth = 1f)

        val cx1 = div1 / 2f
        val cx2 = (div1 + div2) / 2f
        val cx3 = (div2 + w) / 2f

        val autonomyH   = if (consumptionLh > 0f) fuelL / consumptionLh else 0f
        val totalMin    = (autonomyH * 60f).roundToInt()
        val autonomyStr = "%dh%02d".format(totalMin / 60, totalMin % 60)
        val rangeKm     = if (speedKmh > 30f) (autonomyH * speedKmh).roundToInt() else 0
        val rangeStr    = if (rangeKm > 0) "${rangeKm}kms" else "—"

        compactText(tm, "Carburant Restant", cx1, labY, sizeSp = 10f, color = CompactStyle.Dim)
        compactText(tm, "${fuelL.roundToInt()} L", cx1, valY, sizeSp = 20f, color = fuelColor, bold = true)

        compactText(tm, "Autonomie", cx2, labY, sizeSp = 10f, color = CompactStyle.Dim)
        compactText(tm, autonomyStr, cx2, valY, sizeSp = 20f, color = fuelColor, bold = true)

        compactText(tm, "Range", cx3, labY, sizeSp = 10f, color = CompactStyle.Dim)
        compactText(tm, rangeStr, cx3, valY, sizeSp = 20f, color = fuelColor, bold = true)
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
