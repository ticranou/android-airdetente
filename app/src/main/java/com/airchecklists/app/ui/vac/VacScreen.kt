package com.airchecklists.app.ui.vac

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.airchecklists.app.R
import com.airchecklists.app.data.model.VacChart
import com.airchecklists.app.ui.components.PrimaryTopBar
import com.airchecklists.app.ui.theme.scaledByPrefs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VacScreen(
    contentPadding: PaddingValues,
    onOpenTerrain: (String) -> Unit,
    viewModel: VacViewModel = viewModel(),
) {
    val charts by viewModel.charts.collectAsStateWithLifecycle()
    val filter by viewModel.filter.collectAsStateWithLifecycle()
    val context = androidx.compose.ui.platform.LocalContext.current

    // Open a terrain's VAC PDF directly (local if downloaded, else the SIA URL).
    fun openVac(chart: VacChart) {
        val repo = com.airchecklists.app.di.ServiceLocator.vacRepository
        com.airchecklists.app.data.net.PdfOpener.open(
            context = context,
            localFile = repo.localPdf(chart),
            remoteUrl = repo.remoteUrl(
                com.airchecklists.app.di.ServiceLocator.preferences.preferences.value.vacAiracCycle,
                chart.icao,
            ),
        )
    }

    Scaffold(
        topBar = { PrimaryTopBar(title = stringResource(R.string.vac_title)) },
    ) { inner ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    top = inner.calculateTopPadding(),
                    bottom = contentPadding.calculateBottomPadding(),
                ),
        ) {
            OutlinedTextField(
                value = filter,
                onValueChange = viewModel::onFilter,
                label = { Text(stringResource(R.string.vac_filter_hint)) },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )

            if (charts.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(R.string.vac_empty),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(charts, key = { it.id }) { chart ->
                        VacRow(
                            chart = chart,
                            // Weather station → detail sheet (Weather + VAC). Otherwise the
                            // detail sheet would show only the VAC tile, so open it directly.
                            onClick = {
                                if (chart.hasWeather) onOpenTerrain(chart.id) else openVac(chart)
                            },
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VacRow(
    chart: VacChart,
    onClick: () -> Unit,
) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        ListItem(
            colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
            headlineContent = {
                // Terrains that are also a weather station (METAR/TAF) get a blue title.
                Text(
                    text = "${chart.icao} - ${chart.airfieldName}",
                    style = MaterialTheme.typography.titleMedium.scaledByPrefs(),
                    color = if (chart.hasWeather) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurface,
                )
            },
            supportingContent = {
                Text(
                    text = stringResource(
                        R.string.vac_line_secondary,
                        chart.altitude.ifBlank { "—" },
                        chart.circuit.ifBlank { "—" },
                    ),
                    style = MaterialTheme.typography.bodyMedium.scaledByPrefs(),
                )
            },
            trailingContent = {
                when {
                    chart.outdated -> Icon(
                        Icons.Filled.Warning,
                        contentDescription = stringResource(R.string.vac_status_outdated),
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(24.dp),
                    )
                    chart.isDownloaded -> Icon(
                        Icons.Filled.CloudDone,
                        contentDescription = stringResource(R.string.vac_status_offline),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp),
                    )
                    else -> Icon(
                        Icons.Filled.CloudDownload,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(24.dp),
                    )
                }
            },
        )
    }
}
