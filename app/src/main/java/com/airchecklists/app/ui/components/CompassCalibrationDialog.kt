package com.airchecklists.app.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.airchecklists.app.R
import kotlinx.coroutines.delay

private const val CALIBRATION_DURATION_MS = 30_000L

@Composable
fun CompassCalibrationDialog(
    driftDeg: Float,
    driftSignedDeg: Float = 0f,
    gpsDeg: Float = 0f,
    magDeg: Float = 0f,
    onDismiss: () -> Unit,
    onCalibrated: () -> Unit,
    onCompensate: ((Float) -> Unit)? = null,
) {
    var calibrating by remember { mutableStateOf(false) }
    val canCompensate = driftDeg > 0f && onCompensate != null

    if (!calibrating) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(R.string.calibration_title)) },
            text = {
                Column {
                    if (driftDeg > 0f) {
                        Text(stringResource(R.string.calibration_drift_message,
                            gpsDeg.toInt(), magDeg.toInt(), driftSignedDeg.toInt()))
                    } else {
                        Text(stringResource(R.string.calibration_drift_message_generic))
                    }
                    Spacer(Modifier.height(16.dp))
                    HorizontalDivider()
                    Spacer(Modifier.height(8.dp))
                    if (canCompensate) {
                        Button(
                            onClick = { onCompensate!!(driftSignedDeg); onDismiss() },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(stringResource(R.string.calibration_compensate))
                        }
                    }
                    OutlinedButton(
                        onClick = { calibrating = true },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.calibration_start))
                    }
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.calibration_ignore))
                    }
                }
            },
            confirmButton = {},
            dismissButton = {},
        )
    } else {
        var progress by remember { mutableFloatStateOf(0f) }
        var canFinish by remember { mutableStateOf(false) }

        val animatedProgress by animateFloatAsState(
            targetValue = progress,
            animationSpec = tween(durationMillis = 500),
            label = "calibProgress",
        )

        LaunchedEffect(Unit) {
            val startMs = System.currentTimeMillis()
            while (true) {
                val elapsed = System.currentTimeMillis() - startMs
                progress = (elapsed.toFloat() / CALIBRATION_DURATION_MS).coerceIn(0f, 1f)
                if (elapsed >= CALIBRATION_DURATION_MS) {
                    canFinish = true
                    break
                }
                delay(200)
            }
        }

        AlertDialog(
            onDismissRequest = { calibrating = false; onDismiss() },
            title = { Text(stringResource(R.string.calibration_in_progress_title)) },
            text = {
                Column {
                    Text(stringResource(R.string.calibration_instructions))
                    Spacer(modifier = Modifier.height(16.dp))
                    LinearProgressIndicator(
                        progress = { animatedProgress },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (canFinish) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            stringResource(R.string.calibration_ready),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = onCalibrated,
                    enabled = canFinish,
                ) {
                    Text(stringResource(R.string.calibration_done))
                }
            },
            dismissButton = {
                TextButton(onClick = { calibrating = false; onDismiss() }) {
                    Text(stringResource(R.string.calibration_cancel))
                }
            },
        )
    }
}
