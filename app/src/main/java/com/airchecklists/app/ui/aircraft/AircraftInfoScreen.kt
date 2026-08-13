package com.airchecklists.app.ui.aircraft

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.airchecklists.app.R
import com.airchecklists.app.data.model.Aircraft
import com.airchecklists.app.di.ServiceLocator

/** Read-only aircraft sheet: characteristics + speeds. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AircraftInfoScreen(aircraftId: String, onBack: () -> Unit) {
    val a: Aircraft? = ServiceLocator.repository.getById(aircraftId)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(a?.name ?: "") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    titleContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    navigationIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
            )
        },
    ) { inner ->
        if (a == null) return@Scaffold
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (a.subtitle.isNotBlank()) {
                Text(a.subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            // Characteristics.
            if (a.characteristics.isNotEmpty()) {
                SectionTitle(stringResource(R.string.tab_characteristics))
                a.characteristics.forEach { c ->
                    InfoRow(c.label, listOf(c.value, c.unit).filter { it.isNotBlank() }.joinToString(" "))
                }
            }
            // Speeds.
            val speeds = buildList {
                if (a.vs0 > 0) add("Vs0" to "${a.vs0} km/h")
                if (a.vs1 > 0) add("Vs1" to "${a.vs1} km/h")
                if (a.greenMin > 0 || a.greenMax > 0) add("Arc vert" to "${a.greenMin}–${a.greenMax} km/h")
                if (a.whiteMin > 0 || a.whiteMax > 0) add("Arc blanc" to "${a.whiteMin}–${a.whiteMid}–${a.whiteMax} km/h")
                if (a.vno > 0) add("Vno" to "${a.vno} km/h")
                if (a.vne > 0) add("Vne" to "${a.vne} km/h")
                if (a.vpl > 0) add("Vpl" to "${a.vpl} km/h")
            }
            if (speeds.isNotEmpty()) {
                SectionTitle(stringResource(R.string.aircraft_speeds_header))
                speeds.forEach { (k, v) -> InfoRow(k, v) }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
    )
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary)
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
}
