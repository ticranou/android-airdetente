package com.airchecklists.app.data.model

import kotlinx.serialization.Serializable

/**
 * A METAR station as a map point (from AviationWeather.gov bbox query). Enough to
 * draw a flight-category dot and open the full METAR/TAF on tap.
 */
data class MetarPoint(
    val icao: String,
    val lat: Double,
    val lon: Double,
    /** VFR / MVFR / IFR / LIFR / UNKN — drives the dot colour. */
    val flightCategory: String,
    val rawOb: String,
    val windDir: Int? = null,
    val windKt: Int? = null,
    val tempC: Int? = null,
    val qnhHpa: Int? = null,
)

/**
 * A SIGMET hazard area (from AviationWeather.gov isigmet GeoJSON). [rings] holds
 * the polygon outer ring(s) as [lat, lon] pairs (already converted from GeoJSON
 * lon/lat order).
 */
data class SigmetArea(
    val hazard: String,           // TS, TURB, ICE, MTW, IFR, ...
    val firName: String,
    val topFl: Int?,              // altitude top (feet)
    val raw: String,
    val rings: List<List<DoubleArray>>,  // each ring: list of [lat, lon]
)

/** Weather map overlay layers the user can toggle. */
@Serializable
data class WxLayerPrefs(
    val radar: Boolean = true,
    val metar: Boolean = true,
    val sigmet: Boolean = true,
)
