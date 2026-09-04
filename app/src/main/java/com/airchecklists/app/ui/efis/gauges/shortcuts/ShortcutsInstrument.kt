package com.airchecklists.app.ui.efis.gauges.shortcuts

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.airchecklists.app.data.model.EfisInstrument
import com.airchecklists.app.data.model.ShortcutTarget
import com.airchecklists.app.di.ServiceLocator
import com.airchecklists.app.ui.components.InstrumentPickerDialog
import com.airchecklists.app.ui.components.efisInstrumentLabel
import com.airchecklists.app.ui.efis.DashboardGrid
import com.airchecklists.app.ui.efis.gauges.LocalGaugeBezel
import com.airchecklists.app.ui.efis.gauges.compact.CompactStyle
import com.airchecklists.app.ui.efis.gauges.compact.compactText
import com.airchecklists.app.ui.efis.gauges.compact.drawGestureHints
import com.airchecklists.app.ui.efis.gauges.drawNumTitleBar

private const val N = 3

private enum class ConfigStep { CHOOSE_TYPE, PICK_INSTRUMENT, PICK_DASHBOARD }

@Composable
fun ShortcutsInstrument(cellIdx: Int, modifier: Modifier = Modifier) {
    val tm = rememberTextMeasurer()
    val bezel = LocalGaugeBezel.current
    val prefs = ServiceLocator.preferences.preferences.collectAsStateWithLifecycle()
    val state by ServiceLocator.efisProvider.state.collectAsStateWithLifecycle()
    val speedUnit = prefs.value.efisSpeedUnit
    val altUnit = prefs.value.altitudeUnit
    val allDashboards = prefs.value.effectiveDashboards
    val speedArcs = remember(ServiceLocator.currentAircraft()) {
        ServiceLocator.currentAircraft()
            ?.let { com.airchecklists.app.data.model.SpeedArcs.from(it).takeIf { a -> a.hasAny } }
    }

    val targets = remember(cellIdx, prefs.value.instruments.shortcutTargetsByCell) {
        val raw = prefs.value.instruments.shortcutTargetsByCell[cellIdx]
            ?: prefs.value.instruments.shortcutTargets // migration: fallback to global list
        List(N) { i -> raw.getOrElse(i) { ShortcutTarget.Instrument(EfisInstrument.NONE) } }
    }

    // Label for each slot.
    val labels = targets.map { target ->
        when (target) {
            is ShortcutTarget.Instrument ->
                if (target.instrument == EfisInstrument.NONE) "-----"
                else efisInstrumentLabel(target.instrument)
                        .substringAfter(" - ", missingDelimiterValue = target.instrument.name)
                        .replace(Regex("""\s*\([^)]*\)$"""), "")
            is ShortcutTarget.Dashboard ->
                allDashboards.firstOrNull { it.id == target.dashboardId }?.name ?: "-----"
            else -> "-----"
        }
    }

    // Which slot is open for viewing (-1 = none).
    var openIdx by remember { mutableIntStateOf(-1) }
    // Which slot is being configured (-1 = none) + which step of the config flow.
    var configIdx by remember { mutableIntStateOf(-1) }
    var configStep by remember { mutableStateOf(ConfigStep.CHOOSE_TYPE) }

    Canvas(
        modifier = modifier.fillMaxWidth().height(52.dp).pointerInput(targets) {
            detectTapGestures(
                onLongPress = { pos ->
                    val idx = (pos.x / (size.width.toFloat() / N)).toInt().coerceIn(0, N - 1)
                    configIdx = idx
                    configStep = ConfigStep.CHOOSE_TYPE
                },
                onTap = { pos ->
                    val idx = (pos.x / (size.width.toFloat() / N)).toInt().coerceIn(0, N - 1)
                    val t = targets[idx]
                    val isEmpty = t is ShortcutTarget.Instrument && t.instrument == EfisInstrument.NONE
                    if (!isEmpty) openIdx = idx
                },
            )
        },
    ) {
        val w = size.width
        val h = size.height
        val headerH = 20.dp.toPx().coerceAtMost(h * 0.4f)

        drawRect(CompactStyle.Bg, size = size)
        drawNumTitleBar(bezel, w, headerH)
        drawRect(Color(0xFF3A3A3A), size = size, style = Stroke(width = 2f))
        compactText(tm, "RACCOURCIS", w / 2f, headerH / 2f, sizeSp = 12f, color = CompactStyle.Dim)
        drawGestureHints(6f, headerH / 2f, hasLongPress = true, hasDoubleTap = false)

        val slotW = w / N
        val bodyCy = headerH + (h - headerH) / 2f

        for (i in 1 until N) {
            val x = slotW * i
            drawLine(Color(0xFF3A3A3A), Offset(x, headerH), Offset(x, h), strokeWidth = 1.5f)
        }

        for (i in 0 until N) {
            val cx = slotW * i + slotW / 2f
            val label = labels[i]
            val isEmpty = label == "-----"
            val isDashboard = targets[i] is ShortcutTarget.Dashboard && !isEmpty
            compactText(
                tm, label, cx, bodyCy,
                sizeSp = if (isEmpty) 16f else 14f,
                bold = !isEmpty,
                color = when {
                    isEmpty -> CompactStyle.Dim
                    isDashboard -> Color(0xFF4FC3F7) // bleu clair pour les tableaux de bord
                    else -> CompactStyle.Mark
                },
            )
        }
    }

    // ── Open dialog ──────────────────────────────────────────────────────────

    if (openIdx in 0 until N) {
        val target = targets[openIdx]
        when (target) {
            is ShortcutTarget.Instrument -> {
                InstrumentFullScreenDialog(
                    instrument = target.instrument,
                    state = state,
                    speedUnit = speedUnit,
                    altUnit = altUnit,
                    speedArcs = speedArcs,
                    onDismiss = { openIdx = -1 },
                )
            }
            is ShortcutTarget.Dashboard -> {
                val dash = allDashboards.firstOrNull { it.id == target.dashboardId }
                if (dash != null) {
                    DashboardFullScreenDialog(
                        dashboard = dash,
                        state = state,
                        speedUnit = speedUnit,
                        altUnit = altUnit,
                        speedArcs = speedArcs,
                        prefs = prefs.value,
                        onDismiss = { openIdx = -1 },
                    )
                }
            }
            else -> openIdx = -1
        }
    }

    // ── Config flow ──────────────────────────────────────────────────────────

    if (configIdx in 0 until N) {
        when (configStep) {
            ConfigStep.CHOOSE_TYPE -> {
                AlertDialog(
                    onDismissRequest = { configIdx = -1 },
                    title = { Text("Slot ${configIdx + 1} — Choisir le type") },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = { configStep = ConfigStep.PICK_INSTRUMENT },
                                modifier = Modifier.fillMaxWidth(),
                            ) { Text("Instruments") }
                            Button(
                                onClick = { configStep = ConfigStep.PICK_DASHBOARD },
                                modifier = Modifier.fillMaxWidth(),
                            ) { Text("Tableaux de bord") }
                        }
                    },
                    confirmButton = {},
                    dismissButton = {
                        TextButton(onClick = { configIdx = -1 }) { Text("Annuler") }
                    },
                )
            }
            ConfigStep.PICK_INSTRUMENT -> {
                val currentInstr = (targets[configIdx] as? ShortcutTarget.Instrument)?.instrument
                    ?: EfisInstrument.NONE
                InstrumentPickerDialog(
                    current = currentInstr,
                    onDismiss = { configIdx = -1 },
                    onSelect = { chosen ->
                        save(cellIdx, targets, configIdx, ShortcutTarget.Instrument(chosen))
                        configIdx = -1
                    },
                )
            }
            ConfigStep.PICK_DASHBOARD -> {
                DashboardPickerDialog(
                    dashboards = allDashboards,
                    onDismiss = { configIdx = -1 },
                    onSelect = { dash ->
                        save(cellIdx, targets, configIdx, ShortcutTarget.Dashboard(dash.id))
                        configIdx = -1
                    },
                )
            }
        }
    }
}

private fun save(cellIdx: Int, targets: List<ShortcutTarget>, slotIdx: Int, value: ShortcutTarget) {
    val newTargets = targets.toMutableList().also { it[slotIdx] = value }
    ServiceLocator.updateInstruments {
        it.copy(shortcutTargetsByCell = it.shortcutTargetsByCell + (cellIdx to newTargets))
    }
}

// ── Sub-dialogs ───────────────────────────────────────────────────────────────

private fun applyImmersiveDialog(dialogView: android.view.View) {
    val dialogWindow = (dialogView.parent as? DialogWindowProvider)?.window ?: return
    WindowCompat.setDecorFitsSystemWindows(dialogWindow, false)
    dialogWindow.setLayout(
        android.view.ViewGroup.LayoutParams.MATCH_PARENT,
        android.view.ViewGroup.LayoutParams.MATCH_PARENT,
    )
    dialogWindow.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.BLACK))
    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
        dialogWindow.attributes = dialogWindow.attributes.also {
            it.layoutInDisplayCutoutMode =
                android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
    }
    WindowInsetsControllerCompat(dialogWindow, dialogView).apply {
        systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        hide(WindowInsetsCompat.Type.systemBars())
    }
}

@Composable
private fun InstrumentFullScreenDialog(
    instrument: EfisInstrument,
    state: com.airchecklists.app.data.sensors.EfisState,
    speedUnit: com.airchecklists.app.data.model.EfisSpeedUnit,
    altUnit: com.airchecklists.app.data.model.AltitudeUnit,
    speedArcs: com.airchecklists.app.data.model.SpeedArcs?,
    onDismiss: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnClickOutside = true,
            decorFitsSystemWindows = false,
        ),
    ) {
        val dialogView = LocalView.current
        SideEffect { applyImmersiveDialog(dialogView) }
        Column(
            modifier = Modifier.fillMaxSize().background(Color.Black).padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                com.airchecklists.app.ui.efis.gauges.InstrumentSlot(
                    instrument = instrument,
                    state = state,
                    speedUnit = speedUnit,
                    showValues = true,
                    speedArcs = speedArcs,
                    altUnit = altUnit,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF222222)),
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            ) {
                Text("Fermer", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun DashboardFullScreenDialog(
    dashboard: com.airchecklists.app.data.model.Dashboard,
    state: com.airchecklists.app.data.sensors.EfisState,
    speedUnit: com.airchecklists.app.data.model.EfisSpeedUnit,
    altUnit: com.airchecklists.app.data.model.AltitudeUnit,
    speedArcs: com.airchecklists.app.data.model.SpeedArcs?,
    prefs: com.airchecklists.app.data.model.AppPreferences,
    onDismiss: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnClickOutside = true,
            decorFitsSystemWindows = false,
        ),
    ) {
        val dialogView = LocalView.current
        SideEffect { applyImmersiveDialog(dialogView) }
        Column(
            modifier = Modifier.fillMaxSize().background(Color.Black).padding(bottom = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                DashboardGrid(
                    dashboard = dashboard,
                    state = state,
                    speedUnit = speedUnit,
                    showValues = prefs.efisShowValues,
                    speedArcs = speedArcs,
                    trail = emptyList(),
                    mapOrientation = prefs.mapOrientation,
                    altUnit = altUnit,
                    focusDurationSec = prefs.focusDurationSec,
                    onOpenMap = {},
                )
            }
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF222222)),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            ) {
                Text("Fermer", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun DashboardPickerDialog(
    dashboards: List<com.airchecklists.app.data.model.Dashboard>,
    onDismiss: () -> Unit,
    onSelect: (com.airchecklists.app.data.model.Dashboard) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Choisir un tableau de bord") },
        text = {
            LazyColumn {
                items(dashboards) { dash ->
                    TextButton(
                        onClick = { onSelect(dash) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(dash.name, modifier = Modifier.weight(1f))
                    }
                    HorizontalDivider()
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Annuler") }
        },
    )
}
