package com.airchecklists.app.ui.disclaimer

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
import androidx.compose.material.icons.filled.WarningAmber
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.airchecklists.app.R

/**
 * The disclaimer heading + body (icon, title, scrollable text). Reused by the
 * startup [DisclaimerScreen] and by the "Avertissement" tab in Réglages, so the
 * exact same wording/style is shown in both places.
 */
@Composable
fun DisclaimerContent(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            Icons.Filled.WarningAmber,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.tertiary,
            modifier = Modifier.size(56.dp),
        )
        Text(
            text = stringResource(R.string.disclaimer_title),
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 16.dp),
        )
        Text(
            text = stringResource(R.string.disclaimer_body),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Start,
            modifier = Modifier.padding(top = 20.dp),
        )
    }
}

/**
 * Liability disclaimer shown at startup. It is shown on every launch until the
 * user ticks "ne plus afficher" and accepts. [onAccept] receives whether the
 * "don't show again" box was checked, so the caller can persist acceptance.
 */
@Composable
fun DisclaimerScreen(onAccept: (dontShowAgain: Boolean) -> Unit) {
    var dontShowAgain by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        DisclaimerContent(
            modifier = Modifier
                .weight(1f, fill = false)
                .verticalScroll(rememberScrollState()),
        )
        androidx.compose.foundation.layout.Spacer(Modifier.size(20.dp))
        // "J'ai compris, ne plus afficher" — the whole row is clickable.
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
                text = stringResource(R.string.disclaimer_dont_show_again),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
        Button(
            onClick = { onAccept(dontShowAgain) },
            modifier = Modifier.padding(top = 8.dp),
        ) {
            Text(stringResource(R.string.disclaimer_accept))
        }
    }
}
