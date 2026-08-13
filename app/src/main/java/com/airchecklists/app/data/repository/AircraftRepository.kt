package com.airchecklists.app.data.repository

import android.net.Uri
import com.airchecklists.app.data.model.Aircraft
import com.airchecklists.app.data.model.Checklist
import kotlinx.coroutines.flow.StateFlow

/**
 * Single source of truth for aircraft data. JSON files on disk are the
 * persistent store; an in-memory StateFlow cache is exposed for the UI.
 */
interface AircraftRepository {

    /** Observable list of all aircraft, sorted by name. */
    val aircraft: StateFlow<List<Aircraft>>

    /** Hydrate the cache from disk. Call once at startup. */
    suspend fun load()

    fun getById(id: String): Aircraft?

    fun getChecklist(aircraftId: String, checklistId: String): Checklist?

    suspend fun upsertAircraft(aircraft: Aircraft)

    suspend fun deleteAircraft(id: String)

    /** Reorder aircraft to match the given ordered list of ids (persists sortIndex). */
    suspend fun reorderAircraft(orderedIds: List<String>)

    suspend fun upsertChecklist(aircraftId: String, checklist: Checklist)

    suspend fun deleteChecklist(aircraftId: String, checklistId: String)

    /** Reorder an aircraft's checklists to match the given ordered list of ids. */
    suspend fun reorderChecklists(aircraftId: String, orderedIds: List<String>)

    /** Serialize the aircraft's full JSON to the given SAF Uri. */
    suspend fun exportAircraft(id: String, target: Uri)

    /** Read + parse an aircraft from a SAF Uri, assigning a fresh id on collision. Returns it. */
    suspend fun importAircraft(source: Uri): Aircraft

    /**
     * Read a JSON aircraft from [source] and replace the content (name, subtitle,
     * icon, characteristics, checklists) of the aircraft [targetId], keeping its
     * id and position. Returns the updated aircraft.
     */
    suspend fun importInto(targetId: String, source: Uri): Aircraft

    /**
     * Replace the entire aircraft store with [list] (used by the dataset import).
     * All existing aircraft files are removed first. Sort indices are reassigned
     * from the list order when missing.
     */
    suspend fun replaceAll(list: List<Aircraft>)
}
