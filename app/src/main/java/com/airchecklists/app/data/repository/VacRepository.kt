package com.airchecklists.app.data.repository

import com.airchecklists.app.data.local.VacStore
import com.airchecklists.app.data.model.VacChart
import com.airchecklists.app.data.net.DownloadResult
import com.airchecklists.app.data.net.RemoteMeta
import com.airchecklists.app.data.net.VacDownloader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import java.util.UUID

/** Single source of truth for VAC charts. JSON file on disk is persistent store. */
class VacRepository(
    private val store: VacStore,
    private val downloader: VacDownloader,
) {

    private val _charts = MutableStateFlow<List<VacChart>>(emptyList())
    val charts: StateFlow<List<VacChart>> = _charts.asStateFlow()

    suspend fun load() = withContext(Dispatchers.IO) {
        _charts.value = store.read()
    }

    fun getById(id: String): VacChart? = _charts.value.firstOrNull { it.id == id }

    suspend fun upsert(chart: VacChart) = withContext(Dispatchers.IO) {
        val toSave = if (chart.id.isBlank()) chart.copy(id = UUID.randomUUID().toString()) else chart
        _charts.update { list ->
            val idx = list.indexOfFirst { it.id == toSave.id }
            if (idx >= 0) list.toMutableList().apply { this[idx] = toSave }
            else list + toSave
        }
        store.write(_charts.value)
    }

    suspend fun delete(id: String) = withContext(Dispatchers.IO) {
        val chart = getById(id)
        store.deletePdf(chart?.localFileName)
        _charts.update { list -> list.filterNot { it.id == id } }
        store.write(_charts.value)
    }

    suspend fun reorder(orderedIds: List<String>) = withContext(Dispatchers.IO) {
        val byId = _charts.value.associateBy { it.id }
        val reordered = orderedIds.mapNotNull { byId[it] } +
            _charts.value.filter { it.id !in orderedIds }
        _charts.value = reordered
        store.write(reordered)
    }

    /**
     * Replace the entire terrain list with [list] (used by the dataset import).
     * Local-download metadata coming from the file is cleared so the imported
     * terrains reference no device-local PDF; ids are regenerated when blank.
     */
    suspend fun replaceAll(list: List<VacChart>) = withContext(Dispatchers.IO) {
        val cleaned = list.map { c ->
            c.copy(
                id = c.id.ifBlank { java.util.UUID.randomUUID().toString() },
                localFileName = null,
                localSize = null,
                localEtag = null,
                downloadedAt = null,
                outdated = false,
            )
        }
        _charts.value = cleaned
        store.write(cleaned)
    }

    fun remoteUrl(cycle: String, icao: String): String = downloader.remoteUrl(cycle, icao)

    /** Local PDF file for a chart, or null if not downloaded / missing. */
    fun localPdf(chart: VacChart): java.io.File? {
        val name = chart.localFileName ?: return null
        return store.pdfFile(name).takeIf { it.exists() }
    }

    /** Downloads a chart's PDF and stores its metadata. Returns true on success. */
    suspend fun download(id: String, cycle: String): Boolean {
        val chart = getById(id) ?: return false
        val result: DownloadResult = downloader.download(chart, cycle) ?: return false
        upsert(
            chart.copy(
                localFileName = result.fileName,
                localSize = result.size,
                localEtag = result.etag,
                downloadedAt = System.currentTimeMillis(),
                outdated = false,
            )
        )
        return true
    }

    /**
     * Checks the remote copy of a downloaded chart, persists the `outdated` flag,
     * and returns it (null = couldn't determine / not downloaded).
     */
    suspend fun checkOutdated(id: String, cycle: String): Boolean? {
        val chart = getById(id) ?: return null
        if (!chart.isDownloaded) return null
        val remote: RemoteMeta = downloader.checkRemoteMeta(chart.icao, cycle) ?: return null
        val outdated = downloader.isOutdated(chart, remote)
        if (outdated != chart.outdated) upsert(chart.copy(outdated = outdated))
        return outdated
    }
}
