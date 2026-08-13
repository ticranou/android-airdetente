package com.airchecklists.app.ui.settings.aircraft

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import com.airchecklists.app.ui.components.IconPickerRow
import com.airchecklists.app.ui.repoViewModelFactory

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AircraftEditScreen(
    aircraftId: String?,
    onDone: () -> Unit,
    viewModel: AircraftEditViewModel = viewModel(
        factory = repoViewModelFactory { AircraftEditViewModel(aircraftId, it) },
    ),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(
                            if (state.isEditing) R.string.aircraft_edit_title_edit
                            else R.string.aircraft_edit_title_new
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
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(stringResource(R.string.aircraft_field_icon), style = androidx.compose.material3.MaterialTheme.typography.titleMedium)
            IconPickerRow(
                selected = state.icon,
                onSelect = viewModel::onIcon,
            )
            OutlinedTextField(
                value = state.name,
                onValueChange = viewModel::onName,
                label = { Text(stringResource(R.string.aircraft_field_name)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = state.subtitle,
                onValueChange = viewModel::onSubtitle,
                label = { Text(stringResource(R.string.aircraft_field_subtitle)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            // Characteristics (free-form technical info: masse à vide, moteur, …).
            Text(
                stringResource(R.string.aircraft_characteristics_header),
                style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
                color = androidx.compose.material3.MaterialTheme.colorScheme.primary,
            )
            state.characteristics.forEach { c ->
                androidx.compose.foundation.layout.Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    OutlinedTextField(
                        value = c.label,
                        onValueChange = { viewModel.updateCharacteristic(c.id, label = it) },
                        label = { Text(stringResource(R.string.aircraft_char_label)) },
                        singleLine = true,
                        modifier = Modifier.weight(1.2f),
                    )
                    OutlinedTextField(
                        value = c.value,
                        onValueChange = { viewModel.updateCharacteristic(c.id, value = it) },
                        label = { Text(stringResource(R.string.aircraft_char_value)) },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = { viewModel.removeCharacteristic(c.id) }) {
                        Icon(
                            Icons.Filled.Delete,
                            contentDescription = stringResource(R.string.action_delete),
                            tint = androidx.compose.material3.MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
            androidx.compose.material3.OutlinedButton(
                onClick = viewModel::addCharacteristic,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Text(
                    stringResource(R.string.aircraft_char_add),
                    modifier = Modifier.padding(start = 8.dp),
                )
            }

            Text(
                stringResource(R.string.aircraft_speeds_header),
                style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
                color = androidx.compose.material3.MaterialTheme.colorScheme.primary,
            )
            val kbd = androidx.compose.foundation.text.KeyboardOptions(
                keyboardType = androidx.compose.ui.text.input.KeyboardType.Number,
            )
            OutlinedTextField(state.vs0, viewModel::onVs0, label = { Text(stringResource(R.string.aircraft_field_vs0)) },
                singleLine = true, keyboardOptions = kbd, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(state.vs1, viewModel::onVs1, label = { Text(stringResource(R.string.aircraft_field_vs1)) },
                singleLine = true, keyboardOptions = kbd, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(state.greenMin, viewModel::onGreenMin, label = { Text(stringResource(R.string.aircraft_field_green_min)) },
                singleLine = true, keyboardOptions = kbd, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(state.greenMax, viewModel::onGreenMax, label = { Text(stringResource(R.string.aircraft_field_green_max)) },
                singleLine = true, keyboardOptions = kbd, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(state.whiteMin, viewModel::onWhiteMin, label = { Text(stringResource(R.string.aircraft_field_white_min)) },
                singleLine = true, keyboardOptions = kbd, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(state.whiteMid, viewModel::onWhiteMid, label = { Text(stringResource(R.string.aircraft_field_white_mid)) },
                singleLine = true, keyboardOptions = kbd, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(state.whiteMax, viewModel::onWhiteMax, label = { Text(stringResource(R.string.aircraft_field_white_max)) },
                singleLine = true, keyboardOptions = kbd, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(state.vno, viewModel::onVno, label = { Text(stringResource(R.string.aircraft_field_vno)) },
                singleLine = true, keyboardOptions = kbd, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(state.vne, viewModel::onVne, label = { Text(stringResource(R.string.aircraft_field_vne)) },
                singleLine = true, keyboardOptions = kbd, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(state.vpl, viewModel::onVpl, label = { Text(stringResource(R.string.aircraft_field_vpl)) },
                singleLine = true, keyboardOptions = kbd, modifier = Modifier.fillMaxWidth())
        }
    }
}
