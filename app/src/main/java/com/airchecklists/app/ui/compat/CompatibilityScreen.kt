package com.airchecklists.app.ui.compat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.airchecklists.app.R
import com.airchecklists.app.data.sensors.DeviceCapabilities

/**
 * Startup hardware-compatibility screen. Shown once (after the disclaimer, before
 * aircraft selection) when a flight-relevant sensor is missing, so the user knows
 * which features will be unavailable. [onContinue] receives whether "ne plus
 * afficher" was ticked so the caller can persist the choice.
 */
@Composable
fun CompatibilityScreen(caps: DeviceCapabilities, onContinue: (dontShowAgain: Boolean) -> Unit) {
    var dontShowAgain by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            Icons.Filled.Sensors,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.tertiary,
            modifier = Modifier.size(56.dp),
        )
        Text(
            stringResource(R.string.compat_title),
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 16.dp),
        )
        Column(
            modifier = Modifier
                .weight(1f, fill = false)
                .verticalScroll(rememberScrollState())
                .padding(vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(stringResource(R.string.compat_intro), style = MaterialTheme.typography.bodyLarge)
            if (!caps.hasOrientation) CompatRow(false, stringResource(R.string.compat_gyro_missing))
            if (!caps.hasBarometer) CompatRow(false, stringResource(R.string.compat_baro_missing))
            if (!caps.hasGps) CompatRow(false, stringResource(R.string.compat_gps_missing))
            if (caps.hasOrientation && caps.hasBarometer && caps.hasGps) {
                CompatRow(true, stringResource(R.string.compat_ok))
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .toggleable(
                    value = dontShowAgain,
                    role = Role.Checkbox,
                    onValueChange = { dontShowAgain = it },
                )
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(checked = dontShowAgain, onCheckedChange = null)
            Text(
                stringResource(R.string.compat_dont_show_again),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
        Button(onClick = { onContinue(dontShowAgain) }, modifier = Modifier.padding(top = 8.dp)) {
            Text(stringResource(R.string.compat_continue))
        }
    }
}

@Composable
private fun CompatRow(ok: Boolean, text: String) {
    val icon: ImageVector = if (ok) Icons.Filled.CheckCircle else Icons.Filled.Error
    val tint = if (ok) Color(0xFF2E9E4F) else MaterialTheme.colorScheme.error
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = 10.dp))
    }
}
