package com.airchecklists.app.ui.efis.gauges.fuel

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
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
import kotlin.math.roundToInt

@Composable
internal fun FuelConfigDialog(
    currentL: Float,
    capacityL: Float,
    onDismiss: () -> Unit,
    onConfirm: (startL: Float, startTracking: Boolean) -> Unit,
) {
    var buf by remember { mutableStateOf("") }
    val value = buf.toIntOrNull() ?: 0
    val valid = buf.isNotEmpty() && value in 1..capacityL.roundToInt()

    fun push(d: Char) { if (buf.length < 4) buf += d }
    fun back() { buf = buf.dropLast(1) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Carburant embarqué") },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "${if (buf.isEmpty()) currentL.roundToInt().toString() else buf} L / ${capacityL.roundToInt()} L",
                    style = MaterialTheme.typography.displaySmall,
                    color = if (valid || buf.isEmpty()) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.error,
                )
                Spacer(Modifier.height(4.dp))
                TextButton(onClick = { buf = capacityL.roundToInt().toString() }) {
                    Text("Plein (${capacityL.roundToInt()} L)")
                }
                Spacer(Modifier.height(8.dp))
                val rows = listOf(
                    listOf("1", "2", "3"),
                    listOf("4", "5", "6"),
                    listOf("7", "8", "9"),
                    listOf("C", "0", "⌫"),
                )
                rows.forEach { row ->
                    Row {
                        row.forEach { key ->
                            TextButton(
                                onClick = {
                                    when (key) {
                                        "C" -> buf = ""
                                        "⌫" -> back()
                                        else -> push(key[0])
                                    }
                                },
                                modifier = Modifier.width(72.dp).height(56.dp),
                            ) { Text(key, style = MaterialTheme.typography.headlineSmall) }
                        }
                    }
                }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } },
        confirmButton = {
            Row {
                TextButton(onClick = {
                    val l = if (buf.isEmpty()) currentL else (buf.toIntOrNull()?.toFloat() ?: currentL)
                    onConfirm(l, false)
                }) { Text("Arrêter") }
                Spacer(Modifier.width(4.dp))
                TextButton(
                    onClick = {
                        val l = if (buf.isEmpty()) currentL else (buf.toIntOrNull()?.toFloat() ?: currentL)
                        onConfirm(l, true)
                    },
                    enabled = buf.isEmpty() || valid,
                ) { Text("Démarrer") }
            }
        },
    )
}
