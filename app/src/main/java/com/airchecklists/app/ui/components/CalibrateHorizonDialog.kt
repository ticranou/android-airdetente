package com.airchecklists.app.ui.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.airchecklists.app.R

/**
 * Confirmation dialog for "Caler l'horizon" (set the current device attitude as the
 * level 0/0 reference). Shared by the top-bar calibrate button and the long-press on
 * the attitude instruments (ANLHRZ / NUMHRZ / NUMEFS central horizon).
 */
@Composable
fun CalibrateHorizonDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.efis_calibrate_confirm_title)) },
        text = { Text(stringResource(R.string.efis_calibrate_confirm_message)) },
        confirmButton = {
            TextButton(onClick = { onConfirm(); onDismiss() }) {
                Text(stringResource(R.string.efis_calibrate_confirm_ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}
