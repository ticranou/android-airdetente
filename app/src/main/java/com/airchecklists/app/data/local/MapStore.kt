package com.airchecklists.app.data.local

import android.content.Context
import java.io.File

/**
 * Owns the on-disk map directories in internal storage:
 *  - basemap/basemap.mbtiles : the downloaded MapLibre vector basemap
 *  - maps/ : the extracted OpenAIP GeoJSON layers
 */
class MapStore(context: Context) {

    val basemapDir: File = File(context.filesDir, "basemap").apply { mkdirs() }

    /** Directory holding the extracted OpenAIP GeoJSON layers. */
    val mapsDir: File = File(context.filesDir, "maps").apply { mkdirs() }

    val basemapFile: File get() = File(basemapDir, "basemap.mbtiles")

    fun layerFile(fileName: String): File = File(mapsDir, fileName)

    /** Remove all extracted OpenAIP layers (before extracting a fresh set). */
    fun clearLayers() {
        mapsDir.listFiles { f -> f.name.endsWith(".geojson") }?.forEach { it.delete() }
    }
}
