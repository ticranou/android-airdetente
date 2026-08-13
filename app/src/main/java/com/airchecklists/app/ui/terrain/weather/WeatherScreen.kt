package com.airchecklists.app.ui.terrain.weather

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.airchecklists.app.R
import com.airchecklists.app.data.model.MetarData
import com.airchecklists.app.data.model.TafData
import com.airchecklists.app.ui.simpleViewModelFactory
import com.airchecklists.app.ui.theme.FlightCatIfr
import com.airchecklists.app.ui.theme.FlightCatLifr
import com.airchecklists.app.ui.theme.FlightCatMvfr
import com.airchecklists.app.ui.theme.FlightCatUnknown
import com.airchecklists.app.ui.theme.FlightCatVfr

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeatherScreen(
    vacId: String,
    onBack: () -> Unit,
    viewModel: WeatherViewModel = viewModel(factory = simpleViewModelFactory { WeatherViewModel(vacId) }),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.weather_title, viewModel.terrainTitle)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { viewModel.refresh() },
                        enabled = state !is WeatherUiState.Loading,
                    ) {
                        Icon(Icons.Filled.Refresh, contentDescription = stringResource(R.string.weather_refresh))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    titleContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    navigationIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    actionIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
            )
        },
    ) { inner ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner),
        ) {
            when (val s = state) {
                is WeatherUiState.Loading -> LoadingView()
                is WeatherUiState.Error -> ErrorView(onRetry = viewModel::refresh)
                is WeatherUiState.Success -> WeatherContent(
                    metar = s.result.metar,
                    taf = s.result.taf,
                    runwayHeading = viewModel.runwayHeading,
                )
            }
        }
    }
}

@Composable
private fun LoadingView() {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator()
        Text(
            stringResource(R.string.weather_loading),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 16.dp),
        )
    }
}

@Composable
private fun ErrorView(onRetry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            stringResource(R.string.weather_error),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(onClick = onRetry, modifier = Modifier.padding(top = 16.dp)) {
            Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(stringResource(R.string.weather_refresh), modifier = Modifier.padding(start = 8.dp))
        }
    }
}

@Composable
private fun WeatherContent(metar: MetarData?, taf: TafData?, runwayHeading: Int?) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (metar != null) {
            CategoryHeader(metar)
            // Combined wind dial (compass + anemometer + runway).
            WindDial(
                windDir = metar.windDir,
                windSpeedKt = metar.windSpeedKt,
                windGustKt = metar.windGustKt,
                variableFrom = metar.windVarFrom,
                variableTo = metar.windVarTo,
                runwayHeading = runwayHeading,
                windColor = flightCategoryColor(metar.flightCategory),
                modifier = Modifier.padding(horizontal = 24.dp),
            )
            DecodedBlock(metar)
            if (metar.clouds.isNotEmpty()) {
                SectionCard(stringResource(R.string.weather_clouds)) {
                    CloudChart(clouds = metar.clouds, ceilingFt = metar.ceilingFt)
                }
            }
            SectionCard(stringResource(R.string.weather_section_metar)) {
                MonoText(metar.rawOb.ifBlank { "—" })
            }
        } else {
            Text(
                stringResource(R.string.weather_no_data),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        SectionCard(stringResource(R.string.weather_section_taf)) {
            val periods = taf?.periods ?: emptyList()
            if (periods.isEmpty()) {
                Text(stringResource(R.string.weather_no_taf), color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    periods.forEach { MonoText(it) }
                }
            }
        }
    }
}

@Composable
private fun CategoryHeader(metar: MetarData) {
    val color = flightCategoryColor(metar.flightCategory)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .background(color, RoundedCornerShape(8.dp))
                .padding(horizontal = 14.dp, vertical = 8.dp),
        ) {
            Text(
                metar.flightCategory,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White,
            )
        }
        metar.observedAt?.let {
            Text(
                stringResource(R.string.weather_observed_at, formatObserved(it)),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 12.dp),
            )
        }
    }
}

@Composable
private fun DecodedBlock(m: MetarData) {
    SectionCard(title = null) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val wind = when {
                m.windSpeedKt == null || (m.windSpeedKt == 0) -> stringResource(R.string.weather_wind_calm)
                m.windGustKt != null -> stringResource(
                    R.string.weather_wind_gust, m.windDir ?: 0, m.windSpeedKt, m.windGustKt
                )
                else -> stringResource(R.string.weather_wind_value, m.windDir ?: 0, m.windSpeedKt)
            }
            InfoRow(stringResource(R.string.weather_wind), wind)
            m.visibility?.let { InfoRow(stringResource(R.string.weather_visibility), it) }
            if (m.tempC != null || m.dewpointC != null) {
                InfoRow(
                    stringResource(R.string.weather_temp),
                    stringResource(
                        R.string.weather_temp_value,
                        m.tempC?.toString() ?: "—",
                        m.dewpointC?.toString() ?: "—",
                    ),
                )
            }
            m.qnhHpa?.let { InfoRow(stringResource(R.string.weather_qnh), stringResource(R.string.weather_qnh_value, it)) }
            m.ceilingFt?.let { InfoRow(stringResource(R.string.weather_ceiling), stringResource(R.string.weather_ceiling_value, it)) }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun SectionCard(title: String?, content: @Composable () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (title != null) {
                Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            }
            content()
        }
    }
}

@Composable
private fun MonoText(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, fontFamily = FontFamily.Monospace)
}

private fun flightCategoryColor(cat: String): Color = when (cat.uppercase()) {
    "VFR" -> FlightCatVfr
    "MVFR" -> FlightCatMvfr
    "IFR" -> FlightCatIfr
    "LIFR" -> FlightCatLifr
    else -> FlightCatUnknown
}

/** "2026-07-22T13:30:00.000Z" -> "22/07 13:30Z" (best-effort, no parsing lib). */
private fun formatObserved(iso: String): String {
    return runCatching {
        val date = iso.substringBefore('T')          // 2026-07-22
        val time = iso.substringAfter('T').take(5)   // 13:30
        val (_, mo, d) = date.split("-")
        "$d/$mo ${time}Z"
    }.getOrDefault(iso)
}
