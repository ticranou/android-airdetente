package com.airchecklists.app.ui.efis.gauges.approach

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.unit.dp
import com.airchecklists.app.data.model.VacChart
import com.airchecklists.app.ui.terrain.QfuParser

/**
 * Long-press override dialog for CMNAPP. Two steps:
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
        // Step 2: QFU choice for the picked terrain.
        val qfus = QfuParser.parse(chart.circuit)
        val options = qfus.ifEmpty { listOf(0, 90, 180, 270) }
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("QFU — ${chart.icao}") },
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
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().heightIn(max = 360.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        items(options) { qfu ->
                            Row(
                                modifier = Modifier.fillMaxWidth()
                                    .clickable {
                                        ApproachTargetResolver.fromChart(chart, qfu)?.let(onLock)
                                        onDismiss()
                                    }
                                    .padding(vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text("QFU %03d°".format(qfu), style = MaterialTheme.typography.titleMedium)
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = onDismiss) { Text("Fermer") } },
            dismissButton = { TextButton(onClick = { picked = null }) { Text("Retour") } },
        )
    }
}
