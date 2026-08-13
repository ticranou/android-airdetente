package com.airchecklists.app.ui.terrain

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Map
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.airchecklists.app.R
import com.airchecklists.app.data.net.PdfOpener
import com.airchecklists.app.di.ServiceLocator

/**
 * Terrain detail sheet: entry point to the terrain's functions
 * (Weather, VAC chart, and future actions).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TerrainDetailScreen(
    vacId: String,
    onBack: () -> Unit,
    onOpenWeather: () -> Unit,
) {
    val context = LocalContext.current
    val repo = ServiceLocator.vacRepository
    val chart = repo.getById(vacId)
    val title = chart?.let { "${it.icao} - ${it.airfieldName}" } ?: ""

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (chart?.hasWeather == true) {
                ActionTile(
                    icon = { Icon(Icons.Filled.Cloud, contentDescription = null) },
                    title = stringResource(R.string.terrain_action_weather),
                    subtitle = stringResource(R.string.terrain_action_weather_sub),
                    onClick = onOpenWeather,
                )
            }
            ActionTile(
                icon = { Icon(Icons.Filled.Map, contentDescription = null) },
                title = stringResource(R.string.terrain_action_vac),
                subtitle = stringResource(R.string.terrain_action_vac_sub),
                onClick = {
                    if (chart != null) {
                        PdfOpener.open(
                            context = context,
                            localFile = repo.localPdf(chart),
                            remoteUrl = repo.remoteUrl(
                                ServiceLocator.preferences.preferences.value.vacAiracCycle,
                                chart.icao,
                            ),
                        )
                    }
                },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ActionTile(
    icon: @Composable () -> Unit,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        ListItem(
            leadingContent = icon,
            headlineContent = { Text(title, style = MaterialTheme.typography.titleMedium) },
            supportingContent = { Text(subtitle) },
            trailingContent = {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
            },
        )
    }
}
