package com.airchecklists.app.ui.checks

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.airchecklists.app.R
import com.airchecklists.app.data.model.ChecklistType
import com.airchecklists.app.di.ServiceLocator
import com.airchecklists.app.ui.detail.ChecklistsTab

/** Checks tab for the current aircraft, with Normal / Emergency sub-tabs. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChecksScreen(
    contentPadding: PaddingValues,
    onOpenChecklist: (aircraftId: String, checklistId: String) -> Unit,
) {
    val aircraftList by ServiceLocator.repository.aircraft.collectAsStateWithLifecycle()
    val currentId by ServiceLocator.currentAircraftId.collectAsStateWithLifecycle()
    val aircraft = aircraftList.firstOrNull { it.id == currentId }

    var emergency by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(
                top = contentPadding.calculateTopPadding(),
                bottom = contentPadding.calculateBottomPadding(),
            ),
    ) {
        if (aircraft == null) {
            Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                Text(
                    stringResource(R.string.checks_no_aircraft),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            return@Box
        }
        androidx.compose.foundation.layout.Column(Modifier.fillMaxSize()) {
            SingleChoiceSegmentedButtonRow(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                SegmentedButton(
                    selected = !emergency,
                    onClick = { emergency = false },
                    shape = SegmentedButtonDefaults.itemShape(0, 2),
                ) { Text(stringResource(R.string.detail_nav_checklists)) }
                SegmentedButton(
                    selected = emergency,
                    onClick = { emergency = true },
                    shape = SegmentedButtonDefaults.itemShape(1, 2),
                ) { Text(stringResource(R.string.detail_nav_emergency)) }
            }
            val wanted = if (emergency) ChecklistType.EMERGENCY else ChecklistType.NORMAL
            ChecklistsTab(
                checklists = aircraft.checklists.filter { it.type == wanted },
                onOpenChecklist = { onOpenChecklist(aircraft.id, it) },
                emptyMessage = if (emergency) stringResource(R.string.emergency_empty)
                else stringResource(R.string.checklists_empty),
            )
        }
    }
}
