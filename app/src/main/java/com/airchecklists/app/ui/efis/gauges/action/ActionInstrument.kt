package com.airchecklists.app.ui.efis.gauges.action

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.airchecklists.app.ui.components.AltCalibrationDialog
import com.airchecklists.app.data.model.InstrumentPersistState
import com.airchecklists.app.ui.efis.gauges.nearestAirfieldElevationFt
import com.airchecklists.app.di.ServiceLocator
import com.airchecklists.app.ui.efis.gauges.GaugeColors
import com.airchecklists.app.ui.efis.gauges.LocalGaugeBezel
import com.airchecklists.app.ui.efis.gauges.compact.CompactStyle
import com.airchecklists.app.ui.efis.gauges.compact.compactText
import com.airchecklists.app.ui.efis.gauges.compact.drawGestureHints
import com.airchecklists.app.ui.efis.gauges.drawGaugeTitlePill
import com.airchecklists.app.ui.efis.gauges.gaugeFace

private val GREEN_DONE  = Color(0xFF32C832)
private val RING_GRAY   = Color(0xFF505050)
private val TOGGLE_ON   = Color(0xFF32C832)
private val TOGGLE_OFF  = Color(0xFF444444)
private val TOGGLE_KNOB = Color(0xFFEEEEEE)

enum class ActionId(val label: String) {
    ENGINE_START("Démarrage"),
    ENGINE_STOP("Arrêt moteur"),
}

/**
 * ANLACT — Action instrument (round gauge).
 * Long-press: pick action. Double-tap: toggle DONE ↔ OFF.
 * Turns green when DONE.
 */
@Composable
fun ActionInstrument(cellIdx: Int, modifier: Modifier = Modifier) {
    val tm = rememberTextMeasurer()
    val bezel = LocalGaugeBezel.current
    val prefs by ServiceLocator.preferences.preferences.collectAsStateWithLifecycle()
    val persist = prefs.instruments

    val actionId = persist.actionSlots[cellIdx]?.let { id ->
        ActionId.entries.firstOrNull { it.name == id }
    }
    val isDone = persist.actionDone[cellIdx] == true

    var showPicker by remember { mutableStateOf(false) }
    var showCalibDialog by remember { mutableStateOf(false) }
    var showFlightSummaryDialog by remember { mutableStateOf(false) }
    var showFuelDialog by remember { mutableStateOf(false) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current

    Canvas(
        modifier = modifier.pointerInput(actionId, isDone) {
            detectTapGestures(
                onLongPress = { showPicker = true },
                onDoubleTap = {
                    if (actionId != null) {
                        val newDone = !isDone
                        ServiceLocator.updateInstruments { s ->
                            val toggled = s.copy(actionDone = s.actionDone + (cellIdx to newDone))
                            if (newDone) executeAction(actionId, toggled)
                            else cancelAction(actionId, toggled)
                        }
                        if (newDone && actionId == ActionId.ENGINE_START) {
                            showCalibDialog = true
                            showFuelDialog = true
                        }
                        if (newDone && actionId == ActionId.ENGINE_STOP) {
                            showFlightSummaryDialog = true
                        }
                    }
                },
            )
        },
    ) {
        val (cx, cy, r) = gaugeFace(bezel)

        val ringColor = if (isDone) GREEN_DONE else RING_GRAY
        drawCircle(ringColor, radius = r * 0.92f, center = Offset(cx, cy), style = Stroke(width = r * 0.05f))
        drawGestureHints(cx - r * 0.82f, cy - r * 0.65f, hasLongPress = true, hasDoubleTap = true)

        if (actionId == null) {
            compactText(tm, "---", cx, cy, sizeSp = 18f, color = GaugeColors.MarkDim)
            compactText(tm, "appui long", cx, cy + r * 0.35f, sizeSp = 10f, color = CompactStyle.Dim)
        } else {
            val labelColor = if (isDone) GREEN_DONE else GaugeColors.Mark

            // Action name (wrap on 2 lines max)
            val maxCharsPerLine = 12
            val words = actionId.label.split(" ")
            val lines = mutableListOf<String>()
            var cur = ""
            for (word in words) {
                val test = if (cur.isEmpty()) word else "$cur $word"
                if (test.length <= maxCharsPerLine) cur = test
                else { if (cur.isNotEmpty()) lines.add(cur); cur = word; if (lines.size == 1) { cur = "$cur…"; break } }
            }
            if (cur.isNotEmpty()) lines.add(cur)
            val lineH = r * 0.26f
            val startY = cy - r * 0.22f - (lines.size - 1) * lineH / 2f
            lines.forEachIndexed { li, line ->
                compactText(tm, line, cx, startY + li * lineH, sizeSp = 18f, bold = true, color = labelColor)
            }

            // Toggle pill
            val pillW = r * 0.9f
            val pillH = r * 0.35f
            val pillX = cx - pillW / 2f
            val pillY = cy + r * 0.22f
            drawRoundRect(
                color = if (isDone) TOGGLE_ON else TOGGLE_OFF,
                topLeft = Offset(pillX, pillY),
                size = Size(pillW, pillH),
                cornerRadius = CornerRadius(pillH / 2),
            )
            val knobR = pillH * 0.38f
            val knobX = if (isDone) pillX + pillW - knobR - pillH * 0.12f
                        else pillX + knobR + pillH * 0.12f
            drawCircle(TOGGLE_KNOB, radius = knobR, center = Offset(knobX, pillY + pillH / 2f))

            val stateLabel = if (isDone) "DONE" else "OFF"
            compactText(
                tm, stateLabel, cx, pillY + pillH + r * 0.18f, sizeSp = 13f,
                color = if (isDone) Color.White else Color(0xFF909090),
            )
        }
        drawGaugeTitlePill(tm, "ACTION", cx, cy, r, bezel.hideTitle)
    }

    if (showPicker) {
        ActionPickerDialog(
            hasSelection = actionId != null,
            onDismiss = { showPicker = false },
            onSelect = { chosen ->
                ServiceLocator.updateInstruments { s ->
                    s.copy(
                        actionSlots = s.actionSlots + (cellIdx to chosen.name),
                        actionDone = s.actionDone + (cellIdx to false),
                    )
                }
                showPicker = false
            },
            onClear = {
                ServiceLocator.updateInstruments { s ->
                    s.copy(
                        actionSlots = s.actionSlots - cellIdx,
                        actionDone = s.actionDone - cellIdx,
                    )
                }
                showPicker = false
            },
        )
    }

    if (showCalibDialog) {
        AltCalibrationDialog(
            initial = ServiceLocator.altCalibrationFt.value ?: nearestAirfieldElevationFt(),            onDismiss = { showCalibDialog = false },
            onConfirm = { a -> ServiceLocator.setAltCalibration(a, ServiceLocator.efisProvider.state.value.gpsAltitudeFt); showCalibDialog = false },
            onClear = { ServiceLocator.clearAltCalibration(); showCalibDialog = false },
        )
    }

    if (showFlightSummaryDialog) {
        val summary = remember { com.airchecklists.app.data.local.buildFlightSummary() }
        androidx.compose.ui.window.Dialog(
            onDismissRequest = { showFlightSummaryDialog = false },
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
        ) {
            androidx.compose.foundation.layout.Column(
                modifier = androidx.compose.ui.Modifier
                    .fillMaxSize()
                    .background(androidx.compose.ui.graphics.Color(0xFF0A0A0A))
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                com.airchecklists.app.ui.efis.gauges.flight.FlightSummaryCard(
                    summary = summary,
                    modifier = androidx.compose.ui.Modifier.weight(1f),
                )
                androidx.compose.foundation.layout.Row(
                    modifier = androidx.compose.ui.Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = { showFlightSummaryDialog = false }) {
                        Text("Fermer")
                    }
                    Button(onClick = {
                        com.airchecklists.app.ui.efis.gauges.flight.exportFlightSessionZip(scope) { name ->
                            android.widget.Toast.makeText(
                                context,
                                if (name != null) "Dossier exporté : Téléchargements/$name"
                                else "Échec de l'export du dossier.",
                                android.widget.Toast.LENGTH_LONG,
                            ).show()
                        }
                        showFlightSummaryDialog = false
                    }) {
                        Text("Enregistrer le dossier (ZIP)")
                    }
                }
            }
        }
    }

    if (showFuelDialog) {
        val aircraft  = ServiceLocator.currentAircraft()
        val capacityL = aircraft?.fuelCapacityL?.toFloat()?.takeIf { it > 0f } ?: 91f
        val currentL  = ServiceLocator.instrumentPersist.fuelStartL ?: capacityL
        com.airchecklists.app.ui.efis.gauges.fuel.FuelConfigDialog(
            currentL  = currentL,
            capacityL = capacityL,
            onDismiss = { showFuelDialog = false },
            onConfirm = { startL, startTracking ->
                ServiceLocator.updateInstruments {
                    it.copy(
                        fuelStartL          = startL,
                        fuelTrackingStartMs = if (startTracking) System.currentTimeMillis() else null,
                        fuelAlertShown      = false,
                    )
                }
                showFuelDialog = false
            },
        )
    }
}

private fun executeAction(action: ActionId, s: InstrumentPersistState): InstrumentPersistState = when (action) {
    ActionId.ENGINE_START -> {
        ServiceLocator.flightRecorder.start()
        ServiceLocator.efisProvider.calibrate()
        s.copy(
            engineStartMs = System.currentTimeMillis(),
            engineStopMs = null,
        )
    }
    ActionId.ENGINE_STOP -> {
        ServiceLocator.flightRecorder.stop()
        s.copy(engineStopMs = System.currentTimeMillis(), fuelTrackingStartMs = null)
    }
}

private fun cancelAction(action: ActionId, s: InstrumentPersistState): InstrumentPersistState = when (action) {
    ActionId.ENGINE_START -> {
        ServiceLocator.flightRecorder.stop()
        s.copy(engineStartMs = null, fuelTrackingStartMs = null)
    }
    ActionId.ENGINE_STOP -> s.copy(engineStopMs = null)
}

@Composable
private fun ActionPickerDialog(
    hasSelection: Boolean,
    onDismiss: () -> Unit,
    onSelect: (ActionId) -> Unit,
    onClear: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Choisir une action") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ActionId.entries.forEach { action ->
                    Button(
                        onClick = { onSelect(action) },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(action.label) }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (hasSelection) {
                    TextButton(onClick = onClear) { Text("Effacer") }
                }
                TextButton(onClick = onDismiss) { Text("Fermer") }
            }
        },
    )
}
