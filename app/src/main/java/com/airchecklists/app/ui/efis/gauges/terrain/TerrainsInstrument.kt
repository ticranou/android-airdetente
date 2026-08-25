package com.airchecklists.app.ui.efis.gauges.terrain

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import com.airchecklists.app.data.model.VacChart
import com.airchecklists.app.data.net.PdfOpener
import com.airchecklists.app.data.sensors.EfisState
import com.airchecklists.app.di.ServiceLocator
import com.airchecklists.app.ui.efis.gauges.compact.CompactStyle
import com.airchecklists.app.ui.efis.gauges.compact.compactText
import com.airchecklists.app.ui.efis.gauges.compact.drawGestureHints
import com.airchecklists.app.ui.efis.gauges.gaugeFace
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/** A terrain with its computed distance (km) and bearing (° true) from the ship. */
private data class NearTerrain(val chart: VacChart, val distanceKm: Double, val bearingDeg: Int, val hasCoords: Boolean)

private const val NEAR_COUNT = 3

/**
 * Virtual "terrains" instrument (analog / circular face). It shows the 3 nearest
 * aerodromes as stacked rows. Gestures:
 *  - double-tap a row → open that terrain's VAC (local PDF or SIA URL);
 *  - long-press (anywhere) → open the full list dialog (all terrains, by proximity).
 */
@Composable
fun TerrainsInstrument(modifier: Modifier = Modifier) {
    val tm = rememberTextMeasurer()
    val context = LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val charts by ServiceLocator.vacRepository.charts.collectAsStateWithLifecycle()
    val state by ServiceLocator.efisProvider.state.collectAsStateWithLifecycle()
    var showDialog by remember { mutableStateOf(false) }

    // Nearby terrains come from the full offline OpenAIP aerodrome directory,
    // enriched by the user's own VAC charts (by ICAO). Parsed off the UI thread;
    // falls back to the user's charts when the map layer/GPS is unavailable.
    val latKey = (state.latitude * 50).roundToInt()
    val lonKey = (state.longitude * 50).roundToInt()
    val merged by androidx.compose.runtime.produceState(
        initialValue = charts, charts, state.hasPosition, latKey, lonKey,
    ) {
        value = com.airchecklists.app.data.repository.AerodromeDirectory.nearbyCharts(
            state.latitude, state.longitude, state.hasPosition, charts, 40,
        )
    }
    val sorted = remember(merged, state.latitude, state.longitude, state.hasPosition) {
        sortByProximity(merged, state)
    }
    val nearest = sorted.take(NEAR_COUNT)

    fun openVac(chart: VacChart) {
        val cycle = ServiceLocator.preferences.preferences.value.vacAiracCycle
        PdfOpener.open(
            context = context,
            localFile = ServiceLocator.vacRepository.localPdf(chart),
            remoteUrl = ServiceLocator.vacRepository.remoteUrl(cycle, chart.icao),
        )
    }

    fun addToMyTerrains(chart: VacChart) {
        scope.launch {
            ServiceLocator.vacRepository.upsert(chart.copy(id = ""))
        }
    }

    Canvas(
        modifier = modifier.fillMaxSize().pointerInput(nearest) {
            detectTapGestures(
                onLongPress = { showDialog = true },
                onDoubleTap = { pos ->
                    rowIndexAtImpl(pos.y, size.width.toFloat(), size.height.toFloat())?.let { idx ->
                        nearest.getOrNull(idx)?.let { openVac(it.chart) }
                    }
                },
            )
        },
    ) {
        drawFace(tm, nearest, state.hasPosition)
    }

    if (showDialog) {
        TerrainsDialog(
            terrains = sorted,
            hasPosition = state.hasPosition,
            onDismiss = { showDialog = false },
            onSelect = { chart -> openVac(chart); showDialog = false },
            onAdd = { chart -> addToMyTerrains(chart) },
        )
    }
}

/** Which of the NEAR_COUNT rows a Y coordinate (inside the round face) falls on.
 *  Mirrors the band used by drawFace (centred on the circle, below the title). */
private fun rowIndexAtImpl(y: Float, w: Float, h: Float): Int? {
    val cy = h / 2f
    val r = minOf(w, h) / 2f * 0.92f   // matches gaugeFace()
    val bandTop = cy - r * 0.42f
    val bandBottom = cy + r * 0.60f
    if (y < bandTop || y > bandBottom) return null
    val idx = ((y - bandTop) / ((bandBottom - bandTop) / NEAR_COUNT)).toInt()
    return idx.coerceIn(0, NEAR_COUNT - 1)
}

private fun DrawScope.drawFace(tm: TextMeasurer, nearest: List<NearTerrain>, hasPosition: Boolean) {
    // Round black face + bezel, like the other analog gauges.
    val (cx, cy, r) = gaugeFace()
    compactText(tm, "TERRAINS", cx, cy - r * 0.78f, sizeSp = 12f, color = CompactStyle.Dim)
    drawGestureHints(cx - r * 0.98f, cy - r * 0.98f, hasLongPress = true, hasDoubleTap = true)

    if (nearest.isEmpty()) {
        compactText(tm, "—", cx, cy, sizeSp = 24f, bold = true, color = CompactStyle.Dim)
        compactText(tm, if (hasPosition) "aucun terrain" else "position GPS ?", cx, cy + r * 0.25f, sizeSp = 12f, color = CompactStyle.Dim)
        return
    }

    // Lay the rows out inside the circle: a vertical band centred on cy, below the
    // title. All rows share ONE width, sized to the circle's chord at the most
    // constraining row (the one furthest from the centre) so every cell fits inside
    // the bezel AND the three rectangles are identical in width.
    val bandTop = cy - r * 0.42f
    val bandBottom = cy + r * 0.60f
    val rowH = (bandBottom - bandTop) / NEAR_COUNT
    val cellH = rowH * 0.82f
    // Largest |dy| reached by any row's top/bottom edge → tightest chord.
    var dyMax = 0f
    for (i in nearest.indices) {
        val rowCy = bandTop + rowH * (i + 0.5f)
        dyMax = maxOf(dyMax, kotlin.math.abs(rowCy - cellH / 2 - cy), kotlin.math.abs(rowCy + cellH / 2 - cy))
    }
    val chordHalf = if (dyMax < r) kotlin.math.sqrt(r * r - dyMax * dyMax) else 0f
    val cellW = (chordHalf * 2f - 8f).coerceAtLeast(0f)
    if (cellW <= 0f) return
    val left = cx - cellW / 2
    for (i in nearest.indices) {
        val t = nearest[i]
        val rowCy = bandTop + rowH * (i + 0.5f)
        drawRect(Color(0xFF141414), topLeft = Offset(left, rowCy - cellH / 2), size = Size(cellW, cellH))
        drawRect(Color(0xFF3A3A3A), topLeft = Offset(left, rowCy - cellH / 2), size = Size(cellW, cellH), style = Stroke(width = 1.5f))
        // ICAO (left, accent for the nearest), distance·bearing (right).
        val icaoColor = if (i == 0) CompactStyle.Accent else CompactStyle.Mark
        compactText(tm, t.chart.icao, cx - cellW * 0.28f, rowCy, sizeSp = 15f, bold = true, mono = true, color = icaoColor)
        val right = if (hasPosition && t.hasCoords) "${t.distanceKm.roundToInt()}km ${t.bearingDeg}°" else "—"
        compactText(tm, right, cx + cellW * 0.24f, rowCy, sizeSp = 12f, color = CompactStyle.Dim)
    }
}

@Composable
private fun TerrainsDialog(
    terrains: List<NearTerrain>,
    hasPosition: Boolean,
    onDismiss: () -> Unit,
    onSelect: (VacChart) -> Unit,
    onAdd: (VacChart) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (hasPosition) "Terrains (par proximité)" else "Terrains") },
        text = {
            if (terrains.isEmpty()) {
                Text("Aucun terrain disponible.")
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    items(terrains, key = { it.chart.id }) { t ->
                        TerrainRow(t, hasPosition, onClick = { onSelect(t.chart) }, onAdd = { onAdd(t.chart) })
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Fermer") } },
    )
}

@Composable
private fun TerrainRow(t: NearTerrain, hasPosition: Boolean, onClick: () -> Unit, onAdd: () -> Unit) {
    val ephemeral = com.airchecklists.app.data.repository.AerodromeDirectory.isEphemeral(t.chart)
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text("${t.chart.icao} · ${t.chart.airfieldName}", style = MaterialTheme.typography.titleMedium)
            Text(
                if (ephemeral) "Aérodrome — appuyer pour ouvrir la VAC" else t.chart.circuit,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        // Nearby aerodrome not yet in the user's list → offer to add it.
        if (ephemeral) {
            androidx.compose.material3.IconButton(onClick = onAdd) {
                androidx.compose.material3.Icon(
                    Icons.Filled.Add,
                    contentDescription = "Ajouter à mes terrains",
                )
            }
        }
        if (hasPosition && t.hasCoords) {
            Column(horizontalAlignment = Alignment.End) {
                Text("${t.distanceKm.roundToInt()} km", style = MaterialTheme.typography.titleMedium)
                Text("${t.bearingDeg}°", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** Sorts terrains by great-circle distance from the ship. Terrains without
 *  coordinates (or when there's no GPS fix) sort last, keeping the given order. */
private fun sortByProximity(charts: List<VacChart>, state: EfisState): List<NearTerrain> {
    val haveFix = state.hasPosition
    val mapped = charts.map { c ->
        if (haveFix && c.latitude != null && c.longitude != null) {
            val d = haversineKm(state.latitude, state.longitude, c.latitude, c.longitude)
            val b = bearingDeg(state.latitude, state.longitude, c.latitude, c.longitude)
            NearTerrain(c, d, b, hasCoords = true)
        } else {
            NearTerrain(c, Double.MAX_VALUE, 0, hasCoords = false)
        }
    }
    return mapped.sortedBy { it.distanceKm }
}

private fun haversineKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val r = 6371.0
    val dLat = Math.toRadians(lat2 - lat1)
    val dLon = Math.toRadians(lon2 - lon1)
    val a = sin(dLat / 2) * sin(dLat / 2) +
        cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2) * sin(dLon / 2)
    return r * 2 * atan2(sqrt(a), sqrt(1 - a))
}

private fun bearingDeg(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Int {
    val phi1 = Math.toRadians(lat1)
    val phi2 = Math.toRadians(lat2)
    val dLon = Math.toRadians(lon2 - lon1)
    val y = sin(dLon) * cos(phi2)
    val x = cos(phi1) * sin(phi2) - sin(phi1) * cos(phi2) * cos(dLon)
    val deg = Math.toDegrees(atan2(y, x))
    return (((deg % 360) + 360) % 360).roundToInt() % 360
}
