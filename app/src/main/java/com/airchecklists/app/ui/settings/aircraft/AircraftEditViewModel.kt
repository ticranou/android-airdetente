package com.airchecklists.app.ui.settings.aircraft

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.airchecklists.app.data.model.Aircraft
import com.airchecklists.app.data.model.AircraftIcon
import com.airchecklists.app.data.model.Characteristic
import com.airchecklists.app.data.repository.AircraftRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

data class AircraftEditState(
    val name: String = "",
    val subtitle: String = "",
    val icon: AircraftIcon = AircraftIcon.PLANE,
    val characteristics: List<Characteristic> = emptyList(),
    val vs0: String = "",
    val vs1: String = "",
    val greenMin: String = "",
    val greenMax: String = "",
    val whiteMin: String = "",
    val whiteMid: String = "",
    val whiteMax: String = "",
    val vno: String = "",
    val vne: String = "",
    val vpl: String = "",
    val isEditing: Boolean = false,
) {
    val canSave: Boolean get() = name.isNotBlank()
}

class AircraftEditViewModel(
    private val aircraftId: String?,
    private val repository: AircraftRepository,
) : ViewModel() {

    private val existing: Aircraft? = aircraftId?.let { repository.getById(it) }

    private fun Int.orEmpty(): String = if (this > 0) toString() else ""

    private val _state = MutableStateFlow(
        existing?.let {
            AircraftEditState(
                it.name, it.subtitle, it.icon,
                it.characteristics,
                it.vs0.orEmpty(), it.vs1.orEmpty(),
                it.greenMin.orEmpty(), it.greenMax.orEmpty(),
                it.whiteMin.orEmpty(), it.whiteMid.orEmpty(), it.whiteMax.orEmpty(),
                it.vno.orEmpty(), it.vne.orEmpty(), it.vpl.orEmpty(),
                isEditing = true,
            )
        } ?: AircraftEditState(),
    )
    val state: StateFlow<AircraftEditState> = _state.asStateFlow()

    fun onName(v: String) = _state.update { it.copy(name = v) }
    fun onSubtitle(v: String) = _state.update { it.copy(subtitle = v) }
    fun onIcon(v: AircraftIcon) = _state.update { it.copy(icon = v) }
    private fun digits(v: String) = v.filter { it.isDigit() }.take(4)
    fun onVs0(v: String) = _state.update { it.copy(vs0 = digits(v)) }
    fun onVs1(v: String) = _state.update { it.copy(vs1 = digits(v)) }
    fun onGreenMin(v: String) = _state.update { it.copy(greenMin = digits(v)) }
    fun onGreenMax(v: String) = _state.update { it.copy(greenMax = digits(v)) }
    fun onWhiteMin(v: String) = _state.update { it.copy(whiteMin = digits(v)) }
    fun onWhiteMid(v: String) = _state.update { it.copy(whiteMid = digits(v)) }
    fun onWhiteMax(v: String) = _state.update { it.copy(whiteMax = digits(v)) }
    fun onVno(v: String) = _state.update { it.copy(vno = digits(v)) }
    fun onVne(v: String) = _state.update { it.copy(vne = digits(v)) }
    fun onVpl(v: String) = _state.update { it.copy(vpl = digits(v)) }

    // ---- Characteristics ----

    fun addCharacteristic() = _state.update {
        it.copy(characteristics = it.characteristics + Characteristic(UUID.randomUUID().toString(), "", "", ""))
    }

    fun updateCharacteristic(id: String, label: String? = null, value: String? = null, unit: String? = null) =
        _state.update { s ->
            s.copy(characteristics = s.characteristics.map { c ->
                if (c.id != id) c
                else c.copy(
                    label = label ?: c.label,
                    value = value ?: c.value,
                    unit = unit ?: c.unit,
                )
            })
        }

    fun removeCharacteristic(id: String) = _state.update { s ->
        s.copy(characteristics = s.characteristics.filterNot { it.id == id })
    }

    fun reorderCharacteristics(orderedIds: List<String>) = _state.update { s ->
        val byId = s.characteristics.associateBy { it.id }
        s.copy(characteristics = orderedIds.mapNotNull { byId[it] } + s.characteristics.filter { it.id !in orderedIds })
    }

    fun save(onDone: () -> Unit) {
        val s = _state.value
        if (!s.canSave) return
        // Drop empty rows (no label and no value).
        val cleanedCharacteristics = s.characteristics.filter { it.label.isNotBlank() || it.value.isNotBlank() }
        val toSave = (existing ?: Aircraft(id = UUID.randomUUID().toString(), name = "")).copy(
            name = s.name.trim(),
            subtitle = s.subtitle.trim(),
            icon = s.icon,
            characteristics = cleanedCharacteristics.map {
                it.copy(label = it.label.trim(), value = it.value.trim(), unit = it.unit.trim())
            },
            vs0 = s.vs0.toIntOrNull() ?: 0,
            vs1 = s.vs1.toIntOrNull() ?: 0,
            greenMin = s.greenMin.toIntOrNull() ?: 0,
            greenMax = s.greenMax.toIntOrNull() ?: 0,
            whiteMin = s.whiteMin.toIntOrNull() ?: 0,
            whiteMid = s.whiteMid.toIntOrNull() ?: 0,
            whiteMax = s.whiteMax.toIntOrNull() ?: 0,
            vno = s.vno.toIntOrNull() ?: 0,
            vne = s.vne.toIntOrNull() ?: 0,
            vpl = s.vpl.toIntOrNull() ?: 0,
        )
        viewModelScope.launch {
            repository.upsertAircraft(toSave)
            onDone()
        }
    }
}
