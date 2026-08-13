package com.airchecklists.app.data.model

import kotlinx.serialization.Serializable

/** Type of a checklist: a normal procedure or an emergency one. */
@Serializable
enum class ChecklistType {
    NORMAL,
    EMERGENCY,
}

/**
 * A single checklist item.
 *
 * If [isSection] is true, this is a non-checkable sub-heading used to group the
 * following items: only its [title] is meaningful, it is never ticked and does
 * not count toward progress.
 *
 * Note: there is intentionally NO "checked" field here. Whether an item is
 * ticked is execution-time runtime state (held in ChecklistExecutionViewModel),
 * not persisted data — running a checklist always starts fresh.
 */
@Serializable
data class ChecklistItem(
    val id: String,
    val title: String,
    val description: String = "",
    val isSection: Boolean = false,
)

/**
 * A named checklist (e.g. "Prévol") owned by an aircraft.
 *
 * If [isSection] is true, this entry is a non-openable sub-heading used to group
 * the checklists that follow it (within the same [type]): only [name] matters,
 * it has no items and cannot be executed.
 */
@Serializable
data class Checklist(
    val id: String,
    val name: String,
    val description: String = "",
    val type: ChecklistType = ChecklistType.NORMAL,
    val items: List<ChecklistItem> = emptyList(),
    val isSection: Boolean = false,
)
