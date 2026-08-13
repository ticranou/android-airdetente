package com.airchecklists.app.ui.settings.vac

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.airchecklists.app.R
import com.airchecklists.app.ui.simpleViewModelFactory

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VacEditScreen(
    vacId: String?,
    onDone: () -> Unit,
    viewModel: VacEditViewModel = viewModel(
        factory = simpleViewModelFactory { VacEditViewModel(vacId) },
    ),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(
                            if (state.isEditing) R.string.vac_edit_title_edit
                            else R.string.vac_edit_title_new
                        )
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onDone) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back))
                    }
                },
                actions = {
                    TextButton(onClick = { viewModel.save(onDone) }, enabled = state.canSave) {
                        Text(stringResource(R.string.action_save))
                    }
                },
            )
        },
    ) { inner ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            OutlinedTextField(
                value = state.icao,
                onValueChange = viewModel::onIcao,
                label = { Text(stringResource(R.string.vac_field_icao)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = state.airfieldName,
                onValueChange = viewModel::onAirfield,
                label = { Text(stringResource(R.string.vac_field_airfield)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = state.altitude,
                onValueChange = viewModel::onAltitude,
                label = { Text(stringResource(R.string.vac_field_altitude)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = state.circuit,
                onValueChange = viewModel::onCircuit,
                label = { Text(stringResource(R.string.vac_field_circuit)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = state.frequencies,
                onValueChange = viewModel::onFrequencies,
                label = { Text(stringResource(R.string.vac_field_frequencies)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { viewModel.onHasWeather(!state.hasWeather) },
            ) {
                Checkbox(
                    checked = state.hasWeather,
                    onCheckedChange = { viewModel.onHasWeather(it) },
                )
                Text(
                    stringResource(R.string.vac_field_has_weather),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(start = 4.dp),
                )
            }
        }
    }
}
