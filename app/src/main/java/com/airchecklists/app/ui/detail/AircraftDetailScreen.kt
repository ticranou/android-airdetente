package com.airchecklists.app.ui.detail

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.airchecklists.app.R
import com.airchecklists.app.data.model.ChecklistType
import com.airchecklists.app.ui.components.PrimaryTopBar
import com.airchecklists.app.ui.repoViewModelFactory

private enum class DetailSection { AIRCRAFT, CHECKLISTS, EMERGENCY }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AircraftDetailScreen(
    aircraftId: String,
    onHome: () -> Unit,
    onOpenChecklist: (String) -> Unit,
    viewModel: AircraftDetailViewModel = viewModel(
        factory = repoViewModelFactory { AircraftDetailViewModel(aircraftId, it) },
    ),
) {
    val aircraft by viewModel.aircraft.collectAsStateWithLifecycle()
    var section by remember { mutableStateOf(DetailSection.CHECKLISTS) }

    val current = aircraft
    val normalChecklists = current?.checklists?.filter { it.type == ChecklistType.NORMAL } ?: emptyList()
    val emergencyChecklists = current?.checklists?.filter { it.type == ChecklistType.EMERGENCY } ?: emptyList()

    Scaffold(
        topBar = { PrimaryTopBar(title = current?.name ?: "") },
        bottomBar = {
            NavigationBar {
                // Accueil : returns to the app home.
                NavigationBarItem(
                    selected = false,
                    onClick = onHome,
                    icon = { Icon(Icons.Filled.Home, contentDescription = null) },
                    label = { Text(stringResource(R.string.detail_nav_home)) },
                )
                // Appareil : characteristics.
                NavigationBarItem(
                    selected = section == DetailSection.AIRCRAFT,
                    onClick = { section = DetailSection.AIRCRAFT },
                    icon = { Icon(Icons.Filled.Info, contentDescription = null) },
                    label = { Text(stringResource(R.string.detail_nav_aircraft)) },
                )
                // Checklists : normal checklists.
                NavigationBarItem(
                    selected = section == DetailSection.CHECKLISTS,
                    onClick = { section = DetailSection.CHECKLISTS },
                    icon = { Icon(Icons.AutoMirrored.Filled.List, contentDescription = null) },
                    label = { Text(stringResource(R.string.detail_nav_checklists)) },
                )
                // Urgences : emergency checklists, shown in red, on the far right.
                NavigationBarItem(
                    selected = section == DetailSection.EMERGENCY,
                    onClick = { section = DetailSection.EMERGENCY },
                    icon = { Icon(Icons.Filled.Warning, contentDescription = null) },
                    label = { Text(stringResource(R.string.detail_nav_emergency)) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color.White,
                        selectedTextColor = MaterialTheme.colorScheme.error,
                        indicatorColor = MaterialTheme.colorScheme.error,
                        unselectedIconColor = MaterialTheme.colorScheme.error,
                        unselectedTextColor = MaterialTheme.colorScheme.error,
                    ),
                )
            }
        },
    ) { inner ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner),
        ) {
            when (section) {
                DetailSection.AIRCRAFT ->
                    CharacteristicsTab(characteristics = current?.characteristics ?: emptyList())
                DetailSection.CHECKLISTS ->
                    ChecklistsTab(
                        checklists = normalChecklists,
                        onOpenChecklist = onOpenChecklist,
                    )
                DetailSection.EMERGENCY ->
                    ChecklistsTab(
                        checklists = emergencyChecklists,
                        onOpenChecklist = onOpenChecklist,
                        emptyMessage = stringResource(R.string.emergency_empty),
                    )
            }
        }
    }
}
