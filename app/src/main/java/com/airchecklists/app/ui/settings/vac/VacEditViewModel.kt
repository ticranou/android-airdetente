package com.airchecklists.app.ui.settings.vac

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.airchecklists.app.data.model.VacChart
import com.airchecklists.app.data.repository.VacRepository
import com.airchecklists.app.di.ServiceLocator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

data class VacEditState(
    val icao: String = "",
    val airfieldName: String = "",
    val altitude: String = "",
    val circuit: String = "",
    val frequencies: String = "",
    val hasWeather: Boolean = false,
    val isEditing: Boolean = false,
) {
    val canSave: Boolean get() = icao.isNotBlank() && airfieldName.isNotBlank()
}

class VacEditViewModel(
    private val vacId: String?,
    private val repository: VacRepository = ServiceLocator.vacRepository,
) : ViewModel() {

    private val existing: VacChart? = vacId?.let { repository.getById(it) }

    private val _state = MutableStateFlow(
        existing?.let {
            VacEditState(it.icao, it.airfieldName, it.altitude, it.circuit, it.frequencies, it.hasWeather, isEditing = true)
        } ?: VacEditState(),
    )
    val state: StateFlow<VacEditState> = _state.asStateFlow()

    fun onIcao(v: String) = _state.update { it.copy(icao = v.uppercase()) }
    fun onAirfield(v: String) = _state.update { it.copy(airfieldName = v) }
    fun onAltitude(v: String) = _state.update { it.copy(altitude = v) }
    fun onCircuit(v: String) = _state.update { it.copy(circuit = v) }
    fun onFrequencies(v: String) = _state.update { it.copy(frequencies = v) }
    fun onHasWeather(v: Boolean) = _state.update { it.copy(hasWeather = v) }

    fun save(onDone: () -> Unit) {
        val s = _state.value
        if (!s.canSave) return
        // Preserve download metadata when editing; reset it if the ICAO changed.
        val base = existing ?: VacChart(id = UUID.randomUUID().toString(), icao = "", airfieldName = "")
        val icaoChanged = existing != null && !existing.icao.equals(s.icao.trim(), ignoreCase = true)
        val toSave = base.copy(
            icao = s.icao.trim().uppercase(),
            airfieldName = s.airfieldName.trim(),
            altitude = s.altitude.trim(),
            circuit = s.circuit.trim(),
            frequencies = s.frequencies.trim(),
            hasWeather = s.hasWeather,
            localFileName = if (icaoChanged) null else base.localFileName,
            localSize = if (icaoChanged) null else base.localSize,
            localEtag = if (icaoChanged) null else base.localEtag,
            downloadedAt = if (icaoChanged) null else base.downloadedAt,
            outdated = if (icaoChanged) false else base.outdated,
        )
        viewModelScope.launch {
            repository.upsert(toSave)
            onDone()
        }
    }
}
