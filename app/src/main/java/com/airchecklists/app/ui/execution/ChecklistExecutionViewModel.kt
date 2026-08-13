package com.airchecklists.app.ui.execution

import androidx.lifecycle.ViewModel
import com.airchecklists.app.data.model.ChecklistItem
import com.airchecklists.app.data.repository.AircraftRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class ExecutionUiState(
    val name: String = "",
    val description: String = "",
    val items: List<ChecklistItem> = emptyList(),
    /** Index into [items] of the current checkable item; == items.size when done. */
    val currentIndex: Int = 0,
    /** Next checklist of the same type, in aircraft order (null if none). */
    val nextChecklistId: String? = null,
    val nextChecklistName: String? = null,
) {
    /** Total number of checkable items (sections excluded). */
    val total: Int get() = items.count { !it.isSection }

    /** Number of checkable items already ticked (those before currentIndex). */
    val checkedCount: Int get() = items.take(currentIndex).count { !it.isSection }

    val isComplete: Boolean get() = total > 0 && checkedCount >= total
    val progress: Float get() = if (total == 0) 0f else checkedCount.toFloat() / total
}

class ChecklistExecutionViewModel(
    private val aircraftId: String,
    checklistId: String,
    repository: AircraftRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ExecutionUiState())
    val uiState: StateFlow<ExecutionUiState> = _uiState.asStateFlow()

    init {
        val aircraft = repository.getById(aircraftId)
        val checklist = aircraft?.checklists?.firstOrNull { it.id == checklistId }
        if (checklist != null) {
            // Find the next OPENABLE checklist of the SAME type, in the aircraft's
            // own order, skipping section separators.
            val sameType = aircraft.checklists.filter { it.type == checklist.type }
            val posInType = sameType.indexOfFirst { it.id == checklistId }
            val next = sameType.drop(posInType + 1).firstOrNull { !it.isSection }

            _uiState.value = ExecutionUiState(
                name = checklist.name,
                description = checklist.description,
                items = checklist.items,
                currentIndex = firstCheckableFrom(checklist.items, 0),
                nextChecklistId = next?.id,
                nextChecklistName = next?.name,
            )
        }
    }

    /** Index of the first checkable (non-section) item at or after [from]; items.size if none. */
    private fun firstCheckableFrom(items: List<ChecklistItem>, from: Int): Int {
        var i = from
        while (i < items.size && items[i].isSection) i++
        return i
    }

    /** Check the current item — only the current checkable item may be ticked. Advances the cursor. */
    fun check(index: Int) {
        _uiState.update { state ->
            if (index == state.currentIndex && index < state.items.size && !state.items[index].isSection) {
                state.copy(currentIndex = firstCheckableFrom(state.items, index + 1))
            } else {
                state
            }
        }
    }

    fun reset() {
        _uiState.update { it.copy(currentIndex = firstCheckableFrom(it.items, 0)) }
    }
}
