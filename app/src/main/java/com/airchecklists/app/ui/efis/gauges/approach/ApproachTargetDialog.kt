package com.airchecklists.app.ui.efis.gauges.approach

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.dp
import com.airchecklists.app.data.model.VacChart
import com.airchecklists.app.ui.terrain.QfuParser
import kotlin.math.cos
import kotlin.math.sin

/**
 * Long-press override dialog for NUMAPP. Two steps:
 *  1. pick a terrain (nearest first) — or "AUTO" to release the lock;
 *  2. pick the landing QFU parsed from that terrain's circuit (or a default when none).
 * Emits the chosen [ApproachTarget] (LOCKED) via [onLock], or clears the lock via [onAuto].
 */
@Composable
fun ApproachTargetDialog(
    terrains: List<VacChart>,
    onDismiss: () -> Unit,
    onAuto: () -> Unit,
    onLock: (ApproachTarget) -> Unit,
) {
    var picked by remember { mutableStateOf<VacChart?>(null) }
    val chart = picked

    if (chart == null) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Cible d'approche") },
            text = {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth()
                                .clickable { onAuto(); onDismiss() }
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("AUTO — aérodrome le plus proche", style = MaterialTheme.typography.titleMedium)
                        }
                    }
                    items(terrains.filter { it.latitude != null && it.longitude != null }, key = { it.id }) { c ->
                        Row(
                            modifier = Modifier.fillMaxWidth()
                                .clickable { picked = c }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("${c.icao} · ${c.airfieldName}", style = MaterialTheme.typography.titleMedium)
                                if (c.circuit.isNotBlank()) {
                                    Text(
                                        c.circuit,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = onDismiss) { Text("Fermer") } },
        )
    } else {
        // Step 2: QFU choice with orientation schema.
        val qfus = QfuParser.parse(chart.circuit)
        val options = qfus.ifEmpty { listOf(0, 90, 180, 270) }
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("${chart.icao} — Piste atterrissage") },
            text = {
                Column {
                    if (qfus.isEmpty()) {
                        Text(
                            "Aucun QFU dans la fiche : choisir un cap d'atterrissage approché.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 6.dp),
                        )
                    }
                    // Schéma d'orientation : cercle boussole avec un trait pour chaque QFU
                    RunwayOrientationSchema(qfus = options, modifier = Modifier.fillMaxWidth().height(120.dp))
                    Spacer(Modifier.height(8.dp))
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().heightIn(max = 240.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        items(options) { qfu ->
                            Button(
                                onClick = {
                                    ApproachTargetResolver.fromChart(chart, qfu)?.let(onLock)
                                    onDismiss()
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                ),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text("QFU %03d°".format(qfu), style = MaterialTheme.typography.titleMedium)
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { picked = null }) { Text("Retour") }
                    TextButton(onClick = onDismiss) { Text("Fermer") }
                }
            },
        )
    }
}

/** Draws a compass circle with one line per QFU, N label at top. */
@Composable
private fun RunwayOrientationSchema(qfus: List<Int>, modifier: Modifier = Modifier) {
    val dimColor = Color(0xFF888888)
    val runwayColor = Color(0xFF4FC3F7)
    Canvas(modifier = modifier) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val r = (size.width.coerceAtMost(size.height) / 2f) * 0.80f
        // Outer ring
        drawCircle(Color(0xFF333333), radius = r, center = Offset(cx, cy), style = Stroke(2f))
        // Cardinal tick marks (N/E/S/W)
        for (angle in 0 until 360 step 90) {
            val rad = Math.toRadians((angle - 90.0))
            val inner = r * 0.85f
            val outer2 = r * 0.98f
            drawLine(
                dimColor,
                start = Offset(cx + (inner * cos(rad)).toFloat(), cy + (inner * sin(rad)).toFloat()),
                end   = Offset(cx + (outer2 * cos(rad)).toFloat(), cy + (outer2 * sin(rad)).toFloat()),
                strokeWidth = 2f,
            )
        }
        // Runway lines for each QFU (bidirectional: QFU and QFU+180)
        val drawnPairs = mutableSetOf<Int>()
        for (qfu in qfus) {
            val norm = ((qfu % 360) + 360) % 360
            val pair = minOf(norm, (norm + 180) % 360)
            if (drawnPairs.contains(pair)) continue
            drawnPairs.add(pair)
            val rad = Math.toRadians((norm - 90.0))
            val x1 = cx + (r * 0.85f * cos(rad)).toFloat()
            val y1 = cy + (r * 0.85f * sin(rad)).toFloat()
            val radOpp = Math.toRadians((norm + 180 - 90.0))
            val x2 = cx + (r * 0.85f * cos(radOpp)).toFloat()
            val y2 = cy + (r * 0.85f * sin(radOpp)).toFloat()
            drawLine(runwayColor, Offset(x1, y1), Offset(x2, y2), strokeWidth = 5f, cap = StrokeCap.Round)
        }
        // North label
        drawIntoCanvas { canvas ->
            canvas.nativeCanvas.drawText("N", cx - 6f, cy - r * 0.55f,
                android.graphics.Paint().apply {
                    color = android.graphics.Color.WHITE
                    textSize = 28f
                    isFakeBoldText = true
                }
            )
        }
    }
}
