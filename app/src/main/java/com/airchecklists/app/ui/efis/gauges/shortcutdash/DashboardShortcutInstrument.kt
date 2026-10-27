package com.airchecklists.app.ui.efis.gauges.shortcutdash

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.airchecklists.app.data.model.Dashboard
import com.airchecklists.app.data.model.EfisInstrument
import com.airchecklists.app.data.model.ShortcutTarget
import com.airchecklists.app.data.model.VacChart
import com.airchecklists.app.data.net.PdfOpener
import com.airchecklists.app.di.ServiceLocator
import com.airchecklists.app.ui.components.efisInstrumentLabel
import com.airchecklists.app.ui.efis.gauges.GaugeColors
import com.airchecklists.app.ui.efis.gauges.LocalGaugeBezel
import com.airchecklists.app.ui.efis.gauges.compact.CompactStyle
import com.airchecklists.app.ui.efis.gauges.compact.compactText
import com.airchecklists.app.ui.efis.gauges.compact.drawGestureHints
import com.airchecklists.app.ui.efis.gauges.drawGaugeTitlePill
import com.airchecklists.app.ui.efis.gauges.gaugeFace

private enum class AanlsctStep { CHOOSE_TYPE, PICK_DASHBOARD_NAV, PICK_DASHBOARD_FOCUS, PICK_TERRAIN }

/**
 * ANLSCT — Raccourci (round gauge).
 * Long-press: pick shortcut type and target.
 * Tap: trigger focus shortcuts (Dashboard Focus, TerrainVac).
 * Double-tap: trigger DashboardNavigate (jump to dashboard in pager).
 */
@Composable
fun DashboardShortcutInstrument(cellIdx: Int, modifier: Modifier = Modifier) {
    val tm = rememberTextMeasurer()
    val bezel = LocalGaugeBezel.current
    val context = LocalContext.current
    val prefs by ServiceLocator.preferences.preferences.collectAsStateWithLifecycle()
    val allDashboards = prefs.effectiveDashboards
    val allCharts by ServiceLocator.vacRepository.charts.collectAsStateWithLifecycle()

    // Migrate from old dashboardShortcutSlots if no anlsctTarget set yet
    val target: ShortcutTarget? = prefs.instruments.anlsctTargets[cellIdx]
        ?: prefs.instruments.dashboardShortcutSlots[cellIdx]
            ?.let { ShortcutTarget.DashboardNavigate(it) }

    // Pre-compute the instrument label outside Canvas (efisInstrumentLabel is @Composable)
    val instrLabel = if (target is ShortcutTarget.Instrument)
        efisInstrumentLabel(target.instrument)
            .substringAfter(" - ", missingDelimiterValue = target.instrument.name)
            .replace(Regex("""\s*\([^)]*\)$"""), "")
    else ""

    var showPicker by remember { mutableStateOf(false) }
    var step by remember { mutableStateOf(AanlsctStep.CHOOSE_TYPE) }
    var showWxDialog by remember { mutableStateOf(false) }

    Canvas(
        modifier = modifier.pointerInput(target) {
            detectTapGestures(
                onLongPress = { showPicker = true; step = AanlsctStep.CHOOSE_TYPE },
                onDoubleTap = {
                    if (target is ShortcutTarget.DashboardNavigate) {
                        ServiceLocator.requestedDashboardId.value = target.dashboardId
                    }
                },
                onTap = {
                    when (target) {
                        is ShortcutTarget.Dashboard -> {
                            val dash = allDashboards.firstOrNull { it.id == target.dashboardId }
                            if (dash != null) ServiceLocator.focusDashboard.value = dash
                        }
                        is ShortcutTarget.TerrainVac -> {
                            val chart = allCharts.firstOrNull { it.id == target.vacId }
                                ?: VacChart(id = target.vacId, icao = target.icao, airfieldName = target.icao)
                            val cycle = ServiceLocator.preferences.preferences.value.vacAiracCycle
                            PdfOpener.open(
                                context = context,
                                localFile = ServiceLocator.vacRepository.localPdf(chart),
                                remoteUrl = ServiceLocator.vacRepository.remoteUrl(cycle, chart.icao),
                            )
                        }
                        is ShortcutTarget.WeatherStation -> showWxDialog = true
                        else -> Unit
                    }
                },
            )
        },
    ) {
        val (cx, cy, r) = gaugeFace(bezel)

        drawGestureHints(cx - r * 0.82f, cy - r * 0.65f,
            hasLongPress = true, hasDoubleTap = target is ShortcutTarget.DashboardNavigate)

        if (target == null) {
            compactText(tm, "---", cx, cy, sizeSp = 18f, color = GaugeColors.MarkDim)
            compactText(tm, "appui long", cx, cy + r * 0.35f, sizeSp = 10f, color = CompactStyle.Dim)
        } else {
            val (label, subLabel, nameColor) = when (target) {
                is ShortcutTarget.DashboardNavigate -> Triple(
                    allDashboards.firstOrNull { it.id == target.dashboardId }?.name ?: "---",
                    ">>", Color(0xFF4FC3F7)
                )
                is ShortcutTarget.Dashboard -> Triple(
                    allDashboards.firstOrNull { it.id == target.dashboardId }?.name ?: "---",
                    "Focus", Color(0xFF4FC3F7)
                )
                is ShortcutTarget.Instrument -> Triple(
                    instrLabel,
                    "Focus", GaugeColors.MarkDim
                )
                is ShortcutTarget.TerrainVac -> Triple(target.icao, "VAC", Color(0xFFFFCC44))
                is ShortcutTarget.WeatherStation -> Triple(
                    allCharts.firstOrNull { it.icao.equals(target.icao, ignoreCase = true) }
                        ?.airfieldName ?: target.icao,
                    "METEO", Color(0xFF4CAF50)
                )
            }

            // Name: word-wrap on up to 2 lines
            val maxCharsPerLine = 12
            val words = label.split(" ")
            val lines = mutableListOf<String>()
            var cur = ""
            for (word in words) {
                val test = if (cur.isEmpty()) word else "$cur $word"
                if (test.length <= maxCharsPerLine) cur = test
                else {
                    if (cur.isNotEmpty()) lines.add(cur)
                    cur = word
                    if (lines.size == 2) { cur = "$cur…"; break }
                }
            }
            if (cur.isNotEmpty()) lines.add(cur)

            val lineH = r * 0.28f
            val startY = cy - (lines.size - 1) * lineH / 2f - r * 0.08f
            lines.forEachIndexed { li, line ->
                compactText(tm, line, cx, startY + li * lineH, sizeSp = 18f, bold = true, color = nameColor)
            }

            // Sub-label at bottom (type indicator)
            compactText(tm, subLabel, cx, cy + r * 0.72f, sizeSp = 11f, color = CompactStyle.Dim)
        }
        drawGaugeTitlePill(tm, "RACCOURCI", cx, cy, r, bezel.hideTitle)
    }

    if (showWxDialog && target is ShortcutTarget.WeatherStation) {
        com.airchecklists.app.ui.efis.gauges.weather.WxTerrainDetailDialog(
            icao = target.icao,
            onDismiss = { showWxDialog = false },
        )
    }

    // ── Picker flow ───────────────────────────────────────────────────────────

    if (showPicker) {
        when (step) {
            AanlsctStep.CHOOSE_TYPE -> {
                AlertDialog(
                    onDismissRequest = { showPicker = false },
                    title = { Text("Choisir le type de raccourci") },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { step = AanlsctStep.PICK_DASHBOARD_NAV },
                                modifier = Modifier.fillMaxWidth()) { Text("Tableau de bord (Lien)") }
                            Button(onClick = { step = AanlsctStep.PICK_DASHBOARD_FOCUS },
                                modifier = Modifier.fillMaxWidth()) { Text("Tableau de bord (Focus)") }
                            Button(onClick = { step = AanlsctStep.PICK_TERRAIN },
                                modifier = Modifier.fillMaxWidth()) { Text("Terrain (Carte VAC)") }
                        }
                    },
                    confirmButton = {},
                    dismissButton = {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (target != null) {
                                TextButton(onClick = {
                                    ServiceLocator.updateInstruments {
                                        it.copy(anlsctTargets = it.anlsctTargets - cellIdx)
                                    }
                                    showPicker = false
                                }) { Text("Effacer") }
                            }
                            TextButton(onClick = { showPicker = false }) { Text("Fermer") }
                        }
                    },
                )
            }
            AanlsctStep.PICK_DASHBOARD_NAV -> {
                DashboardListDialog(
                    dashboards = allDashboards,
                    onDismiss = { showPicker = false },
                    onSelect = { dash ->
                        ServiceLocator.updateInstruments {
                            it.copy(anlsctTargets = it.anlsctTargets + (cellIdx to ShortcutTarget.DashboardNavigate(dash.id)))
                        }
                        showPicker = false
                    },
                )
            }
            AanlsctStep.PICK_DASHBOARD_FOCUS -> {
                DashboardListDialog(
                    dashboards = allDashboards,
                    onDismiss = { showPicker = false },
                    onSelect = { dash ->
                        ServiceLocator.updateInstruments {
                            it.copy(anlsctTargets = it.anlsctTargets + (cellIdx to ShortcutTarget.Dashboard(dash.id)))
                        }
                        showPicker = false
                    },
                )
            }
            AanlsctStep.PICK_TERRAIN -> {
                TerrainListDialog(
                    charts = allCharts,
                    onDismiss = { showPicker = false },
                    onSelect = { chart ->
                        ServiceLocator.updateInstruments {
                            it.copy(anlsctTargets = it.anlsctTargets + (cellIdx to ShortcutTarget.TerrainVac(chart.id, chart.icao)))
                        }
                        showPicker = false
                    },
                )
            }
        }
    }
}

// ── Sub-dialogs ───────────────────────────────────────────────────────────────

@Composable
private fun DashboardListDialog(
    dashboards: List<Dashboard>,
    onDismiss: () -> Unit,
    onSelect: (Dashboard) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Choisir un tableau de bord") },
        text = {
            LazyColumn {
                items(dashboards) { dash ->
                    TextButton(onClick = { onSelect(dash) }, modifier = Modifier.fillMaxWidth()) {
                        Text(dash.name, modifier = Modifier.weight(1f),
                            overflow = TextOverflow.Ellipsis, maxLines = 2)
                    }
                    HorizontalDivider()
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } },
    )
}

@Composable
private fun TerrainListDialog(
    charts: List<VacChart>,
    onDismiss: () -> Unit,
    onSelect: (VacChart) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Choisir un terrain") },
        text = {
            if (charts.isEmpty()) {
                Text("Aucun terrain disponible. Ajoutez des terrains dans l'onglet Terrains.")
            } else {
                LazyColumn {
                    items(charts) { chart ->
                        TextButton(onClick = { onSelect(chart) }, modifier = Modifier.fillMaxWidth()) {
                            Text("${chart.icao} — ${chart.airfieldName}",
                                modifier = Modifier.weight(1f),
                                overflow = TextOverflow.Ellipsis, maxLines = 1)
                        }
                        HorizontalDivider()
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } },
    )
}

