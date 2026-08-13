package com.airchecklists.app.ui.terrain.weather

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.airchecklists.app.data.model.WeatherResult
import com.airchecklists.app.di.ServiceLocator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface WeatherUiState {
    data object Loading : WeatherUiState
    data class Success(val result: WeatherResult) : WeatherUiState
    data object Error : WeatherUiState
}

class WeatherViewModel(private val vacId: String) : ViewModel() {

    private val repo = ServiceLocator.vacRepository
    private val client = ServiceLocator.weatherClient

    /** Terrain header: "ICAO - Name". */
    val terrainTitle: String
    /** Primary runway heading (from the terrain's circuit field), for the dial. */
    val runwayHeading: Int?
    private val icao: String?

    private val _state = MutableStateFlow<WeatherUiState>(WeatherUiState.Loading)
    val state: StateFlow<WeatherUiState> = _state.asStateFlow()

    init {
        val chart = repo.getById(vacId)
        icao = chart?.icao
        terrainTitle = chart?.let { "${it.icao} - ${it.airfieldName}" } ?: ""
        runwayHeading = chart?.circuit?.let {
            com.airchecklists.app.ui.terrain.QfuParser.primaryHeading(it)
        }
        refresh()
    }

    fun refresh() {
        val code = icao ?: run { _state.value = WeatherUiState.Error; return }
        _state.value = WeatherUiState.Loading
        viewModelScope.launch {
            val result = runCatching { client.fetch(code) }.getOrNull()
            _state.value = if (result?.metar != null || result?.taf != null) {
                WeatherUiState.Success(result)
            } else {
                WeatherUiState.Error
            }
        }
    }
}
