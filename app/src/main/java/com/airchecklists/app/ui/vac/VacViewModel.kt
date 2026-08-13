package com.airchecklists.app.ui.vac

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.airchecklists.app.data.model.VacChart
import com.airchecklists.app.data.repository.VacRepository
import com.airchecklists.app.di.ServiceLocator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

class VacViewModel(
    private val repository: VacRepository = ServiceLocator.vacRepository,
) : ViewModel() {

    private val _filter = MutableStateFlow("")
    val filter: StateFlow<String> = _filter.asStateFlow()

    val charts: StateFlow<List<VacChart>> =
        combine(repository.charts, _filter) { charts, filter ->
            if (filter.isBlank()) charts
            else charts.filter {
                it.icao.contains(filter, ignoreCase = true) ||
                    it.airfieldName.contains(filter, ignoreCase = true)
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun onFilter(value: String) {
        _filter.value = value
    }

    fun localPdf(chart: VacChart) = repository.localPdf(chart)

    fun remoteUrl(chart: VacChart): String {
        val cycle = ServiceLocator.preferences.preferences.value.vacAiracCycle
        return repository.remoteUrl(cycle, chart.icao)
    }
}
