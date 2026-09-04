package com.airchecklists.app.ui.components

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
 * Big-digit heading entry (0..359) with a numeric keypad. Digits fill from the
 * right; the value is shown as a 3-digit heading with a ° suffix. Includes a
 * "clear" action to remove the current bug.
 */
@Composable
fun HeadingEntryDialog(
    initial: Int?,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit,
    onClear: () -> Unit,
) {
    var buf by remember { mutableStateOf("") }
    val hdg = (buf.toIntOrNull() ?: 0)
    val valid = buf.isNotEmpty() && hdg in 0..359

    fun push(d: Char) { if (buf.length < 3) buf += d }
    fun back() { buf = buf.dropLast(1) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Cap à suivre") },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "${buf.padStart(3, '0')}°",
                    style = MaterialTheme.typography.displayMedium,
                    color = if (valid || buf.isEmpty()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                )
                Spacer(Modifier.height(12.dp))
                val rows = listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"), listOf("C", "0", "⌫"))
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
        confirmButton = {
            TextButton(onClick = { onConfirm(hdg % 360) }, enabled = valid) { Text("OK") }
        },
        dismissButton = {
            Row {
                if (initial != null) {
                    TextButton(onClick = onClear) { Text("Effacer") }
                    Spacer(Modifier.width(4.dp))
                }
                TextButton(onClick = onDismiss) { Text("Annuler") }
            }
        },
    )
}

/**
 * Big-digit altitude calibration entry (feet MSL) with a numeric keypad.
 * Always shows an "Effacer" button (removes the calibration override).
 * Pre-fills with [initial] when provided.
 */
@Composable
fun AltCalibrationDialog(
    initial: Int?,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit,
    onClear: () -> Unit,
) {
    var buf by remember { mutableStateOf(initial?.toString() ?: "") }
    val alt = buf.toIntOrNull() ?: 0
    val valid = buf.isNotEmpty() && alt in 0..60000

    fun push(d: Char) { if (buf.length < 5) buf += d }
    fun back() { buf = buf.dropLast(1) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Étalonnage") },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "${if (buf.isEmpty()) "0" else buf} ft MSL",
                    style = MaterialTheme.typography.displayMedium,
                    color = if (valid || buf.isEmpty()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                )
                Spacer(Modifier.height(12.dp))
                val rows = listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"), listOf("C", "0", "⌫"))
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
        confirmButton = {
            TextButton(onClick = { onConfirm(alt) }, enabled = valid) { Text("OK") }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onClear) { Text("Effacer") }
                Spacer(Modifier.width(4.dp))
                TextButton(onClick = onDismiss) { Text("Annuler") }
            }
        },
    )
}
@Composable
fun AltitudeEntryDialog(
    initial: Int?,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit,
    onClear: () -> Unit,
) {
    var buf by remember { mutableStateOf("") }
    val alt = buf.toIntOrNull() ?: 0
    val valid = buf.isNotEmpty() && alt in 0..60000

    fun push(d: Char) { if (buf.length < 5) buf += d }
    fun back() { buf = buf.dropLast(1) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Altitude à suivre") },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "${if (buf.isEmpty()) "0" else buf} ft",
                    style = MaterialTheme.typography.displayMedium,
                    color = if (valid || buf.isEmpty()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                )
                Spacer(Modifier.height(12.dp))
                val rows = listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"), listOf("C", "0", "⌫"))
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
        confirmButton = {
            TextButton(onClick = { onConfirm(alt) }, enabled = valid) { Text("OK") }
        },
        dismissButton = {
            Row {
                if (initial != null) {
                    TextButton(onClick = onClear) { Text("Effacer") }
                    Spacer(Modifier.width(4.dp))
                }
                TextButton(onClick = onDismiss) { Text("Annuler") }
            }
        },
    )
}
