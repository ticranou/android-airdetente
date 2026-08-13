package com.airchecklists.app.data.repository

import android.net.Uri
import com.airchecklists.app.data.local.JsonStore
import com.airchecklists.app.data.model.Aircraft
import com.airchecklists.app.data.model.Checklist
import com.airchecklists.app.data.saf.SafIo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import java.util.UUID

class AircraftRepositoryImpl(
    private val store: JsonStore,
    private val safIo: SafIo,
) : AircraftRepository {

    private val _aircraft = MutableStateFlow<List<Aircraft>>(emptyList())
    override val aircraft: StateFlow<List<Aircraft>> = _aircraft.asStateFlow()

    override suspend fun load() = withContext(Dispatchers.IO) {
        _aircraft.value = store.readAll()
    }

    override fun getById(id: String): Aircraft? =
        _aircraft.value.firstOrNull { it.id == id }

    override fun getChecklist(aircraftId: String, checklistId: String): Checklist? =
        getById(aircraftId)?.checklists?.firstOrNull { it.id == checklistId }

    override suspend fun upsertAircraft(aircraft: Aircraft) = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val existing = getById(aircraft.id)
        // New aircraft go to the end of the list.
        val nextIndex = (_aircraft.value.maxOfOrNull { it.sortIndex } ?: -1) + 1
        val toSave = aircraft.copy(
            createdAt = existing?.createdAt?.takeIf { it > 0 } ?: aircraft.createdAt.takeIf { it > 0 } ?: now,
            updatedAt = now,
            sortIndex = existing?.sortIndex ?: aircraft.sortIndex.takeIf { it > 0 } ?: nextIndex,
        )
        store.write(toSave)
        replaceInCache(toSave)
    }

    override suspend fun deleteAircraft(id: String) = withContext(Dispatchers.IO) {
        store.delete(id)
        _aircraft.update { list -> list.filterNot { it.id == id } }
    }

    override suspend fun reorderAircraft(orderedIds: List<String>) = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val byId = _aircraft.value.associateBy { it.id }
        // Assign a fresh sortIndex per position; persist each changed aircraft.
        orderedIds.forEachIndexed { index, id ->
            val a = byId[id] ?: return@forEachIndexed
            if (a.sortIndex != index) {
                val updated = a.copy(sortIndex = index, updatedAt = now)
                store.write(updated)
            }
        }
        _aircraft.update { list ->
            list.map { a ->
                val pos = orderedIds.indexOf(a.id)
                if (pos >= 0 && a.sortIndex != pos) a.copy(sortIndex = pos) else a
            }.sortedWith(compareBy({ it.sortIndex }, { it.name.lowercase() }))
        }
    }

    override suspend fun upsertChecklist(aircraftId: String, checklist: Checklist) =
        withContext(Dispatchers.IO) {
            val parent = getById(aircraftId) ?: return@withContext
            val updatedChecklists = parent.checklists.toMutableList().apply {
                val idx = indexOfFirst { it.id == checklist.id }
                if (idx >= 0) this[idx] = checklist else add(checklist)
            }
            val updated = parent.copy(
                checklists = updatedChecklists,
                updatedAt = System.currentTimeMillis(),
            )
            store.write(updated)
            replaceInCache(updated)
        }

    override suspend fun deleteChecklist(aircraftId: String, checklistId: String) =
        withContext(Dispatchers.IO) {
            val parent = getById(aircraftId) ?: return@withContext
            val updated = parent.copy(
                checklists = parent.checklists.filterNot { it.id == checklistId },
                updatedAt = System.currentTimeMillis(),
            )
            store.write(updated)
            replaceInCache(updated)
        }

    override suspend fun reorderChecklists(aircraftId: String, orderedIds: List<String>) =
        withContext(Dispatchers.IO) {
            val parent = getById(aircraftId) ?: return@withContext
            val byId = parent.checklists.associateBy { it.id }
            // Keep the requested order; append any checklist not mentioned (safety).
            val reordered = orderedIds.mapNotNull { byId[it] } +
                parent.checklists.filter { it.id !in orderedIds }
            val updated = parent.copy(
                checklists = reordered,
                updatedAt = System.currentTimeMillis(),
            )
            store.write(updated)
            replaceInCache(updated)
        }

    override suspend fun exportAircraft(id: String, target: Uri) = withContext(Dispatchers.IO) {
        val aircraft = getById(id) ?: error("Appareil introuvable.")
        safIo.writeText(target, store.stringify(aircraft))
    }

    override suspend fun importAircraft(source: Uri): Aircraft = withContext(Dispatchers.IO) {
        val text = safIo.readText(source)
        val parsed = store.parse(text)
        // Assign a fresh id if this id already exists, so import never overwrites.
        val existingIds = _aircraft.value.map { it.id }.toSet()
        val now = System.currentTimeMillis()
        val toSave = if (parsed.id.isBlank() || parsed.id in existingIds) {
            parsed.copy(id = UUID.randomUUID().toString(), updatedAt = now)
        } else {
            parsed.copy(updatedAt = now)
        }
        store.write(toSave)
        replaceInCache(toSave)
        toSave
    }

    override suspend fun importInto(targetId: String, source: Uri): Aircraft = withContext(Dispatchers.IO) {
        val target = getById(targetId) ?: error("Appareil introuvable.")
        val parsed = store.parse(safIo.readText(source))
        // Replace content but keep the target's id and creation time / place.
        val merged = target.copy(
            name = parsed.name,
            subtitle = parsed.subtitle,
            icon = parsed.icon,
            characteristics = parsed.characteristics,
            checklists = parsed.checklists,
            updatedAt = System.currentTimeMillis(),
        )
        store.write(merged)
        replaceInCache(merged)
        merged
    }

    override suspend fun replaceAll(list: List<Aircraft>) = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        // Wipe every existing aircraft file, then persist the new set.
        _aircraft.value.forEach { store.delete(it.id) }
        val normalized = list.mapIndexed { index, a ->
            a.copy(
                id = a.id.ifBlank { UUID.randomUUID().toString() },
                sortIndex = if (a.sortIndex > 0) a.sortIndex else index,
                createdAt = a.createdAt.takeIf { it > 0 } ?: now,
                updatedAt = now,
            )
        }
        normalized.forEach { store.write(it) }
        _aircraft.value = normalized.sortedWith(compareBy({ it.sortIndex }, { it.name.lowercase() }))
    }

    private fun replaceInCache(aircraft: Aircraft) {
        _aircraft.update { list ->
            val mutable = list.toMutableList()
            val idx = mutable.indexOfFirst { it.id == aircraft.id }
            if (idx >= 0) mutable[idx] = aircraft else mutable.add(aircraft)
            mutable.sortedWith(compareBy({ it.sortIndex }, { it.name.lowercase() }))
        }
    }
}
