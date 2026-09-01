package com.airchecklists.app.ui.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.airchecklists.app.data.model.Aircraft
import com.airchecklists.app.data.model.AppPreferences
import com.airchecklists.app.data.model.ChecklistType
import com.airchecklists.app.data.model.ThemeMode
import com.airchecklists.app.data.model.VacChart
import com.airchecklists.app.data.repository.AircraftRepository
import com.airchecklists.app.di.ServiceLocator
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

/** A flat row describing one checklist together with its owning aircraft. */
data class ChecklistRow(
    val aircraftId: String,
    val aircraftName: String,
    val checklistId: String,
    val checklistName: String,
    val type: ChecklistType,
    val itemCount: Int,
    val isSection: Boolean,
)

/** Progress of a bulk VAC download (null = idle). */
data class VacDownloadProgress(val done: Int, val total: Int)

class SettingsViewModel(
    private val repository: AircraftRepository,
) : ViewModel() {

    private val prefsRepo = ServiceLocator.preferences
    private val vacRepo = ServiceLocator.vacRepository
    private val mapRepo = ServiceLocator.mapRepository

    val aircraft: StateFlow<List<Aircraft>> = repository.aircraft
    val preferences: StateFlow<AppPreferences> = prefsRepo.preferences
    val vacCharts: StateFlow<List<VacChart>> = vacRepo.charts
    val mapProgress: StateFlow<com.airchecklists.app.data.repository.MapDownloadProgress?> = mapRepo.progress
    val mapUpdateTag: StateFlow<String?> = mapRepo.updateTag
    fun mapInstalledTag(): String? = mapRepo.installedTag

    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages = _messages.receiveAsFlow()

    private val _vacProgress = MutableStateFlow<VacDownloadProgress?>(null)
    val vacProgress: StateFlow<VacDownloadProgress?> = _vacProgress.asStateFlow()

    fun checklistRows(list: List<Aircraft>): List<ChecklistRow> =
        list.flatMap { a ->
            a.checklists.map { c ->
                ChecklistRow(a.id, a.name, c.id, c.name, c.type, c.items.size, c.isSection)
            }
        }

    fun deleteAircraft(id: String) {
        viewModelScope.launch { repository.deleteAircraft(id) }
    }

    fun reorderAircraft(orderedIds: List<String>) {
        viewModelScope.launch { repository.reorderAircraft(orderedIds) }
    }

    fun deleteChecklist(aircraftId: String, checklistId: String) {
        viewModelScope.launch { repository.deleteChecklist(aircraftId, checklistId) }
    }

    /** Persist a new order of checklist ids for one aircraft (after a drag). */
    fun reorderChecklists(aircraftId: String, orderedIds: List<String>) {
        viewModelScope.launch { repository.reorderChecklists(aircraftId, orderedIds) }
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { prefsRepo.setThemeMode(mode) }
    }

    fun setFontScale(scale: Float) {
        viewModelScope.launch { prefsRepo.setFontScale(scale) }
    }

    fun setSplashSeconds(seconds: Int) {
        viewModelScope.launch { prefsRepo.setSplashSeconds(seconds) }
    }

    fun setEfisHeadingSource(source: com.airchecklists.app.data.model.EfisHeadingSource) {
        viewModelScope.launch { prefsRepo.setEfisHeadingSource(source) }
    }

    fun setEfisVarioSource(source: com.airchecklists.app.data.model.EfisVarioSource) {
        viewModelScope.launch { prefsRepo.setEfisVarioSource(source) }
    }

    fun setEfisGrid(cols: Int, rows: Int) {
        viewModelScope.launch { prefsRepo.setEfisGrid(cols, rows) }
    }

    fun setEfisSlot(index: Int, instrument: com.airchecklists.app.data.model.EfisInstrument) {
        viewModelScope.launch { prefsRepo.setEfisSlot(index, instrument) }
    }

    fun setEfisSpeedUnit(unit: com.airchecklists.app.data.model.EfisSpeedUnit) {
        viewModelScope.launch { prefsRepo.setEfisSpeedUnit(unit) }
    }

    fun setAltitudeUnit(unit: com.airchecklists.app.data.model.AltitudeUnit) {
        viewModelScope.launch { prefsRepo.setAltitudeUnit(unit) }
    }

    fun setMapShowZoomButtons(show: Boolean) {
        viewModelScope.launch { prefsRepo.setMapShowZoomButtons(show) }
    }

    fun setEfisResponsiveness(value: Float) {
        viewModelScope.launch { prefsRepo.setEfisResponsiveness(value) }
    }

    fun setEfisShowValues(show: Boolean) {
        viewModelScope.launch { prefsRepo.setEfisShowValues(show) }
    }

    fun setKeepScreenOn(on: Boolean) {
        viewModelScope.launch { prefsRepo.setKeepScreenOn(on) }
    }

    fun setFdrBufferMinutes(min: Int) {
        viewModelScope.launch { prefsRepo.setFdrBufferMinutes(min) }
    }

    fun setFdrFlushMinutes(min: Int) {
        viewModelScope.launch { prefsRepo.setFdrFlushMinutes(min) }
    }

    fun setSafeskyApiKey(key: String?) {
        viewModelScope.launch { prefsRepo.setSafeskyApiKey(key) }
    }

    // ---- Dashboards ----

    /** Create a new empty dashboard and return its id (for immediate editing). */
    fun addDashboard(name: String): String {
        val id = java.util.UUID.randomUUID().toString()
        viewModelScope.launch {
            prefsRepo.upsertDashboard(
                com.airchecklists.app.data.model.Dashboard(
                    id = id, name = name, rows = 2,
                    cells = List(4) { com.airchecklists.app.data.model.DashboardCell() },
                    showInCockpit = true,
                ),
            )
        }
        return id
    }

    fun deleteDashboard(id: String) {
        viewModelScope.launch { prefsRepo.deleteDashboard(id) }
    }

    /** Clone the dashboard [srcId] and return the new dashboard's id (for immediate editing). */
    fun duplicateDashboard(srcId: String, nameSuffix: String): String {
        val newId = java.util.UUID.randomUUID().toString()
        viewModelScope.launch { prefsRepo.duplicateDashboard(srcId, newId, nameSuffix) }
        return newId
    }

    fun setDashboardName(id: String, name: String) {
        viewModelScope.launch { prefsRepo.setDashboardName(id, name) }
    }

    fun setDashboardShowInCockpit(id: String, show: Boolean) {
        viewModelScope.launch { prefsRepo.setDashboardShowInCockpit(id, show) }
    }

    fun setDashboardShowTitle(id: String, show: Boolean) {
        viewModelScope.launch { prefsRepo.setDashboardShowTitle(id, show) }
    }

    fun setDashboardRows(id: String, rows: Int) {
        viewModelScope.launch { prefsRepo.setDashboardRows(id, rows) }
    }

    fun setDashboardCellInstrument(id: String, index: Int, instrument: com.airchecklists.app.data.model.EfisInstrument) {
        viewModelScope.launch { prefsRepo.setDashboardCellInstrument(id, index, instrument) }
    }

    fun setDashboardCellAccent(id: String, index: Int, color: Long?) {
        viewModelScope.launch { prefsRepo.setDashboardCellAccent(id, index, color) }
    }

    fun setDashboardCellBezelStyle(id: String, index: Int, style: com.airchecklists.app.data.model.GaugeBezelStyle?) {
        viewModelScope.launch { prefsRepo.setDashboardCellBezelStyle(id, index, style) }
    }

    fun mergeDashboardCell(id: String, index: Int, dir: com.airchecklists.app.data.repository.MergeDir) {
        viewModelScope.launch { prefsRepo.mergeDashboardCell(id, index, dir) }
    }

    fun unmergeDashboardCell(id: String, index: Int) {
        viewModelScope.launch { prefsRepo.unmergeDashboardCell(id, index) }
    }

    fun reorderDashboards(orderedIds: List<String>) {
        viewModelScope.launch { prefsRepo.reorderDashboards(orderedIds) }
    }

    // ---- Moving map ----

    fun setMapOrientation(orientation: com.airchecklists.app.data.model.MapOrientation) {
        viewModelScope.launch { prefsRepo.setMapOrientation(orientation) }
    }

    fun setCockpitPagerStyle(style: com.airchecklists.app.data.model.CockpitPagerStyle) {
        viewModelScope.launch { prefsRepo.setCockpitPagerStyle(style) }
    }

    fun setCockpitPagerPosition(position: com.airchecklists.app.data.model.CockpitPagerPosition) {
        viewModelScope.launch { prefsRepo.setCockpitPagerPosition(position) }
    }

    fun setGaugeBezelStyle(style: com.airchecklists.app.data.model.GaugeBezelStyle) {
        viewModelScope.launch { prefsRepo.setGaugeBezelStyle(style) }
    }

    fun setGaugeBezelColor(color: Long) {
        viewModelScope.launch { prefsRepo.setGaugeBezelColor(color) }
    }

    /** Download / update the offline VFR map (basemap + OpenAIP layers). */
    fun downloadMaps() {
        viewModelScope.launch {
            runCatching { mapRepo.downloadMaps() }
                .onSuccess { ok ->
                    _messages.send(if (ok) "Carte téléchargée." else "Échec du téléchargement de la carte.")
                }
                .onFailure { _messages.send("Échec du téléchargement : ${it.message}") }
        }
    }

    fun checkMapUpdate() {
        viewModelScope.launch {
            val tag = runCatching { mapRepo.checkForUpdate() }.getOrNull()
            _messages.send(
                if (tag != null) "Nouvelle carte disponible ($tag)." else "Carte à jour.",
            )
        }
    }

    /** Whether the device has a pressure sensor (barometer). */
    val hasBarometer: Boolean =
        (ServiceLocator.appContext.getSystemService(android.content.Context.SENSOR_SERVICE)
            as android.hardware.SensorManager)
            .getDefaultSensor(android.hardware.Sensor.TYPE_PRESSURE) != null

    fun export(aircraftId: String, target: Uri) {
        viewModelScope.launch {
            runCatching { repository.exportAircraft(aircraftId, target) }
                .onSuccess { _messages.send("Appareil exporté.") }
                .onFailure { _messages.send("Échec de l'export : ${it.message}") }
        }
    }

    /** The system file picker returned no destination (cancelled or failed). */
    fun notifyExportCancelled() {
        viewModelScope.launch { _messages.send("Export annulé.") }
    }

    fun import(source: Uri) {
        viewModelScope.launch {
            runCatching { repository.importAircraft(source) }
                .onSuccess { _messages.send("Appareil importé : ${it.name}") }
                .onFailure { _messages.send("Échec de l'import : ${it.message}") }
        }
    }

    /** Replace the content of an existing aircraft with a JSON file. */
    fun importInto(targetId: String, source: Uri) {
        viewModelScope.launch {
            runCatching { repository.importInto(targetId, source) }
                .onSuccess { _messages.send("Contenu importé : ${it.name}") }
                .onFailure { _messages.send("Échec de l'import : ${it.message}") }
        }
    }

    // ---- Hidden "Data" tab: full-dataset export / import ----

    private val safIo = com.airchecklists.app.data.saf.SafIo(ServiceLocator.appContext)
    private val datasetJson = kotlinx.serialization.json.Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    /** Serialize the whole user dataset (aircraft + terrains + business prefs) to [target]. */
    fun exportDataset(target: Uri) {
        viewModelScope.launch {
            runCatching {
                val dataset = com.airchecklists.app.data.model.AppDataset(
                    aircraft = repository.aircraft.value,
                    terrains = vacRepo.charts.value,
                    preferences = com.airchecklists.app.data.model.DatasetPrefs.from(prefsRepo.preferences.value),
                )
                val text = datasetJson.encodeToString(
                    com.airchecklists.app.data.model.AppDataset.serializer(), dataset,
                )
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { safIo.writeText(target, text) }
                dataset
            }
                .onSuccess { _messages.send("Jeu de données exporté (${it.aircraft.size} appareils, ${it.terrains.size} terrains).") }
                .onFailure { _messages.send("Échec de l'export : ${it.message}") }
        }
    }

    /** Read a dataset JSON from [source] and REPLACE all aircraft, terrains and business prefs. */
    fun importDataset(source: Uri) {
        viewModelScope.launch {
            runCatching {
                val text = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { safIo.readText(source) }
                val dataset = datasetJson.decodeFromString(
                    com.airchecklists.app.data.model.AppDataset.serializer(), text,
                )
                repository.replaceAll(dataset.aircraft)
                vacRepo.replaceAll(dataset.terrains)
                prefsRepo.applyDataset(dataset.preferences)
                dataset
            }
                .onSuccess { _messages.send("Jeu de données importé (${it.aircraft.size} appareils, ${it.terrains.size} terrains).") }
                .onFailure { _messages.send("Échec de l'import : ${it.message}") }
        }
    }

    // ---- VAC ----

    fun setVacAiracCycle(cycle: String) {
        viewModelScope.launch { prefsRepo.setVacAiracCycle(cycle) }
    }

    fun deleteVac(id: String) {
        viewModelScope.launch { vacRepo.delete(id) }
    }

    fun reorderVac(orderedIds: List<String>) {
        viewModelScope.launch { vacRepo.reorder(orderedIds) }
    }

    /** Downloads all charts that are missing a local PDF or flagged outdated. */
    fun downloadVacCharts(msgDone: (Int) -> String) {
        viewModelScope.launch {
            val cycle = prefsRepo.preferences.value.vacAiracCycle
            val targets = vacRepo.charts.value.filter { !it.isDownloaded || it.outdated }
            if (targets.isEmpty()) {
                _messages.send(msgDone(0))
                return@launch
            }
            var ok = 0
            _vacProgress.value = VacDownloadProgress(0, targets.size)
            targets.forEachIndexed { index, chart ->
                if (vacRepo.download(chart.id, cycle)) ok++
                _vacProgress.value = VacDownloadProgress(index + 1, targets.size)
            }
            _vacProgress.value = null
            _messages.send(msgDone(ok))
        }
    }

    /** Checks every downloaded chart for a newer remote version. */
    fun checkVacUpdates(msgDone: (Int) -> String, msgNone: String) {
        viewModelScope.launch {
            val cycle = prefsRepo.preferences.value.vacAiracCycle
            val downloaded = vacRepo.charts.value.filter { it.isDownloaded }
            var outdated = 0
            downloaded.forEach { chart ->
                if (vacRepo.checkOutdated(chart.id, cycle) == true) outdated++
            }
            _messages.send(if (outdated > 0) msgDone(outdated) else msgNone)
        }
    }
}
