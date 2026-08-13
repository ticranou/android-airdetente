package com.airchecklists.app.ui.detail

import androidx.lifecycle.ViewModel
import com.airchecklists.app.data.model.Aircraft
import com.airchecklists.app.data.repository.AircraftRepository
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import androidx.lifecycle.viewModelScope

class AircraftDetailViewModel(
    private val aircraftId: String,
    repository: AircraftRepository,
) : ViewModel() {

    val aircraft: StateFlow<Aircraft?> = repository.aircraft
        .map { list -> list.firstOrNull { it.id == aircraftId } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}
