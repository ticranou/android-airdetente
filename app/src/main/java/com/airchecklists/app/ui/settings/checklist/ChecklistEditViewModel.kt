package com.airchecklists.app.ui.settings.checklist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.airchecklists.app.data.model.Aircraft
import com.airchecklists.app.data.model.Checklist
import com.airchecklists.app.data.model.ChecklistItem
import com.airchecklists.app.data.model.ChecklistType
import com.airchecklists.app.data.repository.AircraftRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

/** An editable item row (mutable draft, keyed by a stable local id). */
data class ItemDraft(
    val id: String = UUID.randomUUID().toString(),
    val title: String = "",
    val description: String = "",
    val isSection: Boolean = false,
)

data class ChecklistEditState(
    val availableAircraft: List<Aircraft> = emptyList(),
    val selectedAircraftId: String? = null,
    val name: String = "",
    val description: String = "",
    val type: ChecklistType = ChecklistType.NORMAL,
    val items: List<ItemDraft> = listOf(ItemDraft()),
    val isEditing: Boolean = false,
    /** True when editing/creating a section separator (title + type only). */
    val isSection: Boolean = false,
) {
    val canSave: Boolean
        get() = selectedAircraftId != null &&
            name.isNotBlank() &&
            // A section only needs a title; a real checklist needs a checkable item.
            (isSection || items.any { !it.isSection && it.title.isNotBlank() })
}

class ChecklistEditViewModel(
    private val aircraftIdArg: String?,
    private val checklistIdArg: String?,
    private val isSectionArg: Boolean,
    private val repository: AircraftRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(ChecklistEditState())
    val state: StateFlow<ChecklistEditState> = _state.asStateFlow()

    init {
        val allAircraft = repository.aircraft.value
        val editing = aircraftIdArg?.let { aid ->
            checklistIdArg?.let { cid -> repository.getChecklist(aid, cid) }
        }
        _state.value = if (editing != null) {
            ChecklistEditState(
                availableAircraft = allAircraft,
                selectedAircraftId = aircraftIdArg,
                name = editing.name,
                description = editing.description,
                type = editing.type,
                items = editing.items.map { ItemDraft(it.id, it.title, it.description, it.isSection) }
                    .ifEmpty { listOf(ItemDraft()) },
                isEditing = true,
                isSection = editing.isSection,
            )
        } else {
            ChecklistEditState(
                availableAircraft = allAircraft,
                selectedAircraftId = aircraftIdArg ?: allAircraft.firstOrNull()?.id,
                isSection = isSectionArg,
            )
        }
    }

    fun onAircraftSelected(id: String) = _state.update { it.copy(selectedAircraftId = id) }
    fun onName(v: String) = _state.update { it.copy(name = v) }
    fun onDescription(v: String) = _state.update { it.copy(description = v) }
    fun onType(v: ChecklistType) = _state.update { it.copy(type = v) }

    fun onItemTitle(id: String, v: String) = _state.update { s ->
        s.copy(items = s.items.map { if (it.id == id) it.copy(title = v) else it })
    }

    fun onItemDescription(id: String, v: String) = _state.update { s ->
        s.copy(items = s.items.map { if (it.id == id) it.copy(description = v) else it })
    }

    fun addItem() = _state.update { it.copy(items = it.items + ItemDraft()) }

    fun addSection() = _state.update { it.copy(items = it.items + ItemDraft(isSection = true)) }

    fun removeItem(id: String) = _state.update { s ->
        val filtered = s.items.filterNot { it.id == id }
        s.copy(items = filtered.ifEmpty { listOf(ItemDraft()) })
    }

    /** Reorder items to match the given ordered list of draft ids. */
    fun reorderItems(orderedIds: List<String>) = _state.update { s ->
        val byId = s.items.associateBy { it.id }
        val reordered = orderedIds.mapNotNull { byId[it] } +
            s.items.filter { it.id !in orderedIds }
        s.copy(items = reordered)
    }

    fun save(onDone: () -> Unit) {
        val s = _state.value
        val aircraftId = s.selectedAircraftId ?: return
        if (!s.canSave) return
        val checklist = if (s.isSection) {
            Checklist(
                id = checklistIdArg ?: UUID.randomUUID().toString(),
                name = s.name.trim(),
                type = s.type,
                isSection = true,
            )
        } else {
            Checklist(
                id = checklistIdArg ?: UUID.randomUUID().toString(),
                name = s.name.trim(),
                description = s.description.trim(),
                type = s.type,
                items = s.items
                    .filter { it.title.isNotBlank() }
                    .map { ChecklistItem(it.id, it.title.trim(), if (it.isSection) "" else it.description.trim(), it.isSection) },
            )
        }
        viewModelScope.launch {
            repository.upsertChecklist(aircraftId, checklist)
            onDone()
        }
    }
}
