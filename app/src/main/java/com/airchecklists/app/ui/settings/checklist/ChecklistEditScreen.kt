package com.airchecklists.app.ui.settings.checklist

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.airchecklists.app.R
import com.airchecklists.app.data.model.ChecklistType
import com.airchecklists.app.ui.components.OrderableColumn
import com.airchecklists.app.ui.repoViewModelFactory

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChecklistEditScreen(
    aircraftId: String?,
    checklistId: String?,
    isSection: Boolean,
    onDone: () -> Unit,
    viewModel: ChecklistEditViewModel = viewModel(
        factory = repoViewModelFactory { ChecklistEditViewModel(aircraftId, checklistId, isSection, it) },
    ),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(
                            when {
                                state.isSection && state.isEditing -> R.string.checklist_section_edit_title_edit
                                state.isSection -> R.string.checklist_section_edit_title_new
                                state.isEditing -> R.string.checklist_edit_title_edit
                                else -> R.string.checklist_edit_title_new
                            }
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
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                AircraftDropdown(
                    aircraft = state.availableAircraft.map { it.id to it.name },
                    selectedId = state.selectedAircraftId,
                    onSelect = viewModel::onAircraftSelected,
                )
            }
            item {
                OutlinedTextField(
                    value = state.name,
                    onValueChange = viewModel::onName,
                    label = {
                        Text(
                            stringResource(
                                if (state.isSection) R.string.checklist_section_title
                                else R.string.checklist_field_name
                            )
                        )
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (!state.isSection) {
                item {
                    OutlinedTextField(
                        value = state.description,
                        onValueChange = viewModel::onDescription,
                        label = { Text(stringResource(R.string.checklist_field_description)) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            item {
                Text(
                    stringResource(R.string.checklist_field_type),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    SegmentedButton(
                        selected = state.type == ChecklistType.NORMAL,
                        onClick = { viewModel.onType(ChecklistType.NORMAL) },
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                    ) { Text(stringResource(R.string.checklist_type_normal)) }
                    SegmentedButton(
                        selected = state.type == ChecklistType.EMERGENCY,
                        onClick = { viewModel.onType(ChecklistType.EMERGENCY) },
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                    ) { Text(stringResource(R.string.checklist_type_emergency)) }
                }
            }
            if (!state.isSection) {
                item {
                    Text(
                        stringResource(R.string.checklist_field_items),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                item {
                    OrderableColumn(
                        items = state.items,
                        keyOf = { it.id },
                        onReordered = viewModel::reorderItems,
                    ) { draft ->
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(start = 8.dp, top = 4.dp, bottom = 4.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            if (draft.isSection) {
                                OutlinedTextField(
                                    value = draft.title,
                                    onValueChange = { viewModel.onItemTitle(draft.id, it) },
                                    label = { Text(stringResource(R.string.checklist_section_title)) },
                                    singleLine = true,
                                    textStyle = MaterialTheme.typography.titleMedium,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            } else {
                                OutlinedTextField(
                                    value = draft.title,
                                    onValueChange = { viewModel.onItemTitle(draft.id, it) },
                                    label = { Text(stringResource(R.string.checklist_item_title)) },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                OutlinedTextField(
                                    value = draft.description,
                                    onValueChange = { viewModel.onItemDescription(draft.id, it) },
                                    label = { Text(stringResource(R.string.checklist_item_description)) },
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }
                        IconButton(onClick = { viewModel.removeItem(draft.id) }) {
                            Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.action_delete))
                        }
                    }
                }
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OutlinedButton(onClick = viewModel::addItem, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Filled.Add, null)
                            Text(stringResource(R.string.checklist_add_item), Modifier.padding(start = 8.dp))
                        }
                        OutlinedButton(onClick = viewModel::addSection, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Filled.Add, null)
                            Text(stringResource(R.string.checklist_add_section), Modifier.padding(start = 8.dp))
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AircraftDropdown(
    aircraft: List<Pair<String, String>>,
    selectedId: String?,
    onSelect: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedName = aircraft.firstOrNull { it.first == selectedId }?.second ?: ""

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
    ) {
        OutlinedTextField(
            value = selectedName,
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(R.string.checklist_field_aircraft)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(MenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            aircraft.forEach { (id, name) ->
                DropdownMenuItem(
                    text = { Text(name) },
                    onClick = {
                        onSelect(id)
                        expanded = false
                    },
                )
            }
        }
    }
}
