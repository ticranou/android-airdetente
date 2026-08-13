package com.airchecklists.app.ui.efis.gauges.terrain

import com.airchecklists.app.ui.efis.gauges.drawNumTitleBar
import com.airchecklists.app.ui.efis.gauges.LocalGaugeBezel
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.airchecklists.app.data.model.VacChart
import com.airchecklists.app.data.net.PdfOpener
import com.airchecklists.app.di.ServiceLocator
import com.airchecklists.app.ui.efis.gauges.compact.CompactStyle
import com.airchecklists.app.ui.efis.gauges.compact.compactText
import com.airchecklists.app.ui.efis.gauges.compact.drawGestureHints
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

private const val N_NEAR = 3

/**
 * Digital terrains (single height, full width): fixed 20dp "TERRAINS" header, then
 * the 3 nearest aerodromes shown side by side, each a cell with ICAO + distance and
 * bearing from the ship. Double-tap a cell opens its VAC (local PDF or SIA URL).
 */
@Composable
fun TerrainsDigital(modifier: Modifier = Modifier) {
    val tm = rememberTextMeasurer()
    val bezel = LocalGaugeBezel.current
    val context = LocalContext.current
    val charts by ServiceLocator.vacRepository.charts.collectAsStateWithLifecycle()
    val state by ServiceLocator.efisProvider.state.collectAsStateWithLifecycle()
    var showDialog by remember { mutableStateOf(false) }

    val hasFix = state.hasPosition
    // All terrains sorted by proximity (for the dialog); the instrument shows the top 3.
    val sortedAll = charts
        .map { c ->
            val d = if (hasFix && c.latitude != null && c.longitude != null)
                haversineKm(state.latitude, state.longitude, c.latitude!!, c.longitude!!) else Double.MAX_VALUE
            Triple(c, d, if (hasFix && c.latitude != null && c.longitude != null)
                bearingDeg(state.latitude, state.longitude, c.latitude!!, c.longitude!!) else 0)
        }
        .sortedBy { it.second }
    val nearest = sortedAll.take(N_NEAR)

    fun openVac(chart: VacChart) {
        val cycle = ServiceLocator.preferences.preferences.value.vacAiracCycle
        PdfOpener.open(
            context = context,
            localFile = ServiceLocator.vacRepository.localPdf(chart),
            remoteUrl = ServiceLocator.vacRepository.remoteUrl(cycle, chart.icao),
        )
    }

    Canvas(
        modifier = modifier.fillMaxSize().pointerInput(nearest) {
            detectTapGestures(
                onLongPress = { showDialog = true },
                onDoubleTap = { pos ->
                    val idx = (pos.x / (size.width / N_NEAR)).toInt().coerceIn(0, N_NEAR - 1)
                    nearest.getOrNull(idx)?.let { openVac(it.first) }
                },
            )
        },
    ) {
        val w = size.width; val h = size.height
        val headerH = 20.dp.toPx().coerceAtMost(h * 0.4f)
        drawRect(CompactStyle.Bg, size = size)
        drawNumTitleBar(bezel, w, headerH)
        drawRect(Color(0xFF3A3A3A), size = size, style = Stroke(width = 2f))
        compactText(tm, "TERRAINS", w / 2f, headerH / 2f, sizeSp = 12f, color = CompactStyle.Dim)
        drawGestureHints(6f, headerH / 2f, hasLongPress = true, hasDoubleTap = true)

        if (nearest.isEmpty()) {
            compactText(tm, if (hasFix) "aucun terrain" else "position GPS ?", w / 2f, headerH + (h - headerH) / 2f,
                sizeSp = 12f, color = CompactStyle.Dim)
            return@Canvas
        }

        val mainTop = headerH
        val cellH = (h - mainTop) * 0.66f
        val cy = mainTop + (h - mainTop) / 2f
        val slotW = w / N_NEAR
        for (i in 0 until N_NEAR) {
            val t = nearest.getOrNull(i) ?: continue
            val cx = slotW * i + slotW / 2f
            val cellW = slotW * 0.9f
            drawRoundRect(Color(0xFF1C1C1C), topLeft = Offset(cx - cellW / 2, cy - cellH / 2),
                size = Size(cellW, cellH), cornerRadius = CornerRadius(6f, 6f))
            drawRoundRect(Color(0xFF5A5A5A), topLeft = Offset(cx - cellW / 2, cy - cellH / 2),
                size = Size(cellW, cellH), cornerRadius = CornerRadius(6f, 6f), style = Stroke(width = 1.5f))
            // ICAO (accent for nearest) big-left, dist/bearing small-right.
            val icaoColor = if (i == 0) CompactStyle.Accent else CompactStyle.Mark
            compactText(tm, t.first.icao, cx - cellW * 0.18f, cy, sizeSp = 18f, bold = true, mono = true, color = icaoColor)
            val info = if (hasFix && t.second != Double.MAX_VALUE) "${t.second.roundToInt()}km\n${t.third}°" else "—"
            compactText(tm, info, cx + cellW * 0.28f, cy, sizeSp = 13f, color = CompactStyle.Dim)
        }
    }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text(if (hasFix) "Terrains (par proximité)" else "Terrains") },
            text = {
                if (sortedAll.isEmpty()) {
                    Text("Aucun terrain disponible.")
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        items(sortedAll, key = { it.first.id }) { t ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { openVac(t.first); showDialog = false }
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("${t.first.icao} · ${t.first.airfieldName}", style = MaterialTheme.typography.titleMedium)
                                    Text(t.first.circuit, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                if (hasFix && t.second != Double.MAX_VALUE) {
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text("${t.second.roundToInt()} km", style = MaterialTheme.typography.titleMedium)
                                        Text("${t.third}°", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showDialog = false }) { Text("Fermer") } },
        )
    }
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
