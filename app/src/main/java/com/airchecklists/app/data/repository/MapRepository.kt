package com.airchecklists.app.data.repository

import com.airchecklists.app.data.local.MapStore
import com.airchecklists.app.data.net.MapDownloader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.util.zip.ZipInputStream

/** Progress of a map download: bytes done / total (total < 0 if unknown). */
data class MapDownloadProgress(val phase: String, val bytesDone: Long, val bytesTotal: Long)

/**
 * Owns the offline VFR map: the downloaded basemap.mbtiles + extracted OpenAIP
 * GeoJSON layers, plus update checks against the remote release manifest.
 */
class MapRepository(
    private val store: MapStore,
    private val downloader: MapDownloader,
    private val prefs: PreferencesRepository,
) {
    private val _progress = MutableStateFlow<MapDownloadProgress?>(null)
    val progress: StateFlow<MapDownloadProgress?> = _progress.asStateFlow()

    /** Remote tag found newer than the installed one (null = up to date / unknown). */
    private val _updateTag = MutableStateFlow<String?>(null)
    val updateTag: StateFlow<String?> = _updateTag.asStateFlow()

    fun load() { /* nothing to preload; state derives from prefs + files */ }

    val installedTag: String? get() = prefs.preferences.value.installedMapTag
    fun hasBasemap(): Boolean = store.basemapFile.exists() && store.basemapFile.length() > 0
    fun basemapFile(): File? = store.basemapFile.takeIf { it.exists() && it.length() > 0 }

    /** Extracted OpenAIP layers (parsed lazily by the map screen when needed). */
    fun openAipFiles(): List<File> =
        store.mapsDir.listFiles { f -> f.name.endsWith(".geojson") }?.toList() ?: emptyList()

    /** Read one OpenAIP GeoJSON file's raw text (for a MapLibre GeoJsonSource). */
    fun readLayer(file: File): String = runCatching { file.readText() }.getOrDefault("")

    /**
     * Check the remote manifest; publishes [updateTag] if a newer release exists
     * (or if nothing is installed yet). Returns the remote tag, or null on failure.
     */
    suspend fun checkForUpdate(): String? {
        val manifest = downloader.fetchManifest() ?: return null
        val installed = installedTag
        _updateTag.value = if (manifest.tag.isNotBlank() && manifest.tag != installed) manifest.tag else null
        return _updateTag.value
    }

    /**
     * Download the full map release (basemap + openaip.zip), extract the GeoJSON
     * layers, and record the installed tag. Returns true on success.
     */
    suspend fun downloadMaps(): Boolean = withContext(Dispatchers.IO) {
        val manifest = downloader.fetchManifest()
            ?: error("Manifeste introuvable (réseau ou URL).")

        // 1) Basemap (the large file).
        _progress.value = MapDownloadProgress("Fond de carte", 0, manifest.file(MapDownloader.Config.BASEMAP)?.bytes ?: -1)
        val basemapOk = downloader.download(MapDownloader.Config.BASEMAP, store.basemapFile) { done, total ->
            _progress.value = MapDownloadProgress("Fond de carte", done, total)
        }
        if (!basemapOk) { _progress.value = null; error("Échec du téléchargement du fond de carte.") }

        // 2) OpenAIP layers (small zip) -> extract the GeoJSON files into maps/.
        _progress.value = MapDownloadProgress("Données aéro", 0, manifest.file(MapDownloader.Config.OPENAIP)?.bytes ?: -1)
        val zipFile = File(store.basemapDir, "openaip.zip")
        val zipOk = downloader.download(MapDownloader.Config.OPENAIP, zipFile) { done, total ->
            _progress.value = MapDownloadProgress("Données aéro", done, total)
        }
        if (zipOk) {
            runCatching {
                store.clearLayers()
                extractGeoJson(zipFile, store.mapsDir)
                zipFile.delete()
            }
        }

        prefs.setInstalledMapTag(manifest.tag)
        _updateTag.value = null
        _progress.value = null
        true
    }

    /** Extract only the *.geojson entries of [zip] into [dir] (flat). */
    private fun extractGeoJson(zip: File, dir: File) {
        ZipInputStream(zip.inputStream().buffered()).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                val name = File(entry.name).name  // flatten any path
                if (!entry.isDirectory && name.endsWith(".geojson")) {
                    File(dir, name).outputStream().use { out -> zis.copyTo(out) }
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
    }
}
