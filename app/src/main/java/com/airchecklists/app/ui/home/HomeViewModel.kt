package com.airchecklists.app.ui.home

import androidx.lifecycle.ViewModel
import com.airchecklists.app.data.model.Aircraft
import com.airchecklists.app.data.repository.AircraftRepository
import kotlinx.coroutines.flow.StateFlow

class HomeViewModel(repository: AircraftRepository) : ViewModel() {
    val aircraft: StateFlow<List<Aircraft>> = repository.aircraft
}
