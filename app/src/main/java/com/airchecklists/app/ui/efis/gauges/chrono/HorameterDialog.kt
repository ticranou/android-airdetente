package com.airchecklists.app.ui.efis.gauges.chrono

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

/**
 * Shared numeric keypad dialog for entering a horamètre (hour-meter) value.
 * Format: NNN.NN (hundredths of an hour, e.g. 10622 → 106.22 h).
 * Used by ANLHRM, NUMHRM and the ENGINE_START / ENGINE_STOP ACTION flows.
 *
 * @param initial  Current value in hundredths, displayed as reference (nullable).
 * @param onDismiss Called when the dialog is cancelled.
 * @param onConfirm Called with the new value in hundredths when the user confirms.
 * @param onClear   Called when the user wants to erase the current value.
 */
@Composable
internal fun HorameterInputDialog(
    initial: Int?,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit,
    onClear: () -> Unit,
) {
    var buf by remember { mutableStateOf("") }

    val value = buf.toIntOrNull()
    val valid = value != null && value > 0
    val padded = buf.padStart(3, '0')
    val displayInt = padded.dropLast(2).ifEmpty { "0" }
    val displayDec = padded.takeLast(2)

    fun push(d: Char) { if (buf.length < 5) buf += d }
    fun back() { buf = buf.dropLast(1) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Horamètre") },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (initial != null) {
                    val iPad = initial.toString().padStart(3, '0')
                    val iInt = iPad.dropLast(2).ifEmpty { "0" }
                    val iDec = iPad.takeLast(2)
                    Text(
                        "Valeur actuelle : $iInt.$iDec h",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(4.dp))
                }
                Text(
                    if (buf.isEmpty()) "0.00" else "$displayInt.$displayDec",
                    style = MaterialTheme.typography.displayMedium,
                    color = when {
                        buf.isEmpty() -> MaterialTheme.colorScheme.onSurfaceVariant
                        valid         -> MaterialTheme.colorScheme.primary
                        else          -> MaterialTheme.colorScheme.error
                    },
                )
                Spacer(Modifier.height(12.dp))
                val rows = listOf(
                    listOf("1", "2", "3"), listOf("4", "5", "6"),
                    listOf("7", "8", "9"), listOf("C", "0", "⌫"),
                )
                rows.forEach { row ->
                    Row {
                        row.forEach { key ->
                            TextButton(
                                onClick = {
                                    when (key) {
                                        "C"  -> buf = ""
                                        "⌫" -> back()
                                        else -> push(key[0])
                                    }
                                },
                                modifier = Modifier.width(72.dp).height(56.dp),
                            ) {
                                Text(key, style = MaterialTheme.typography.headlineSmall)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { value?.let(onConfirm) }, enabled = valid) { Text("OK") }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onClear) { Text("Effacer") }
                TextButton(onClick = onDismiss) { Text("Annuler") }
            }
        },
    )
}
