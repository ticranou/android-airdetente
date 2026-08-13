package com.airchecklists.app.data.model

import kotlinx.serialization.Serializable

/**
 * A VAC (Visual Approach Chart) entry for a French airfield. The PDF itself is
 * fetched from the SIA; only lightweight descriptive fields plus local-download
 * metadata are persisted here.
 */
@Serializable
data class VacChart(
    val id: String,               // UUID
    val icao: String,             // "LFAJ"
    val airfieldName: String,     // "Argentan"
    val altitude: String = "",    // "581ft"
    val circuit: String = "",     // "(1600ft) 04G/22G"
    /** Free-text frequency/frequencies, e.g. "A/A 123.500" or "TWR 118.7". */
    val frequencies: String = "",
    /** Airfield reference point (decimal degrees). Used to sort terrains by
     *  proximity to the current GPS position. Null when unknown. */
    val latitude: Double? = null,
    val longitude: Double? = null,
    // Metadata of the locally downloaded PDF (null => not downloaded yet).
    val localFileName: String? = null,
    val localSize: Long? = null,
    val localEtag: String? = null,
    val downloadedAt: Long? = null,
    /** Set by the last "check updates" run: true if the remote PDF differs. */
    val outdated: Boolean = false,
    /** Whether this terrain has a METAR/TAF station (enables the Weather action). */
    val hasWeather: Boolean = false,
) {
    val isDownloaded: Boolean get() = localFileName != null
}
