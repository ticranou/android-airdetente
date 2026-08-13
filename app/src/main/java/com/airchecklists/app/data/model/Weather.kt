package com.airchecklists.app.data.model

/** One cloud layer from a METAR (cover code + base in feet AGL). */
data class CloudLayer(
    val cover: String,     // FEW / SCT / BKN / OVC / CLR / SKC …
    val baseFt: Int?,
)

/** Decoded METAR for one station. */
data class MetarData(
    val icao: String,
    val name: String = "",
    val rawOb: String = "",
    val observedAt: String? = null,   // ISO-8601 (from reportTime)
    val windDir: Int? = null,
    val windSpeedKt: Int? = null,
    val windGustKt: Int? = null,
    val windVarFrom: Int? = null,
    val windVarTo: Int? = null,
    val visibility: String? = null,   // e.g. "6+", "9999", "5000"
    val tempC: Double? = null,
    val dewpointC: Double? = null,
    val qnhHpa: Int? = null,
    val clouds: List<CloudLayer> = emptyList(),
    val ceilingFt: Int? = null,
    val flightCategory: String = "UNKN", // VFR / MVFR / IFR / LIFR / UNKN
)

/** Light TAF: raw text split into change-group periods. */
data class TafData(
    val raw: String = "",
    val periods: List<String> = emptyList(),
)

/** Combined weather result for a station. */
data class WeatherResult(
    val metar: MetarData?,
    val taf: TafData?,
    val fetchedAt: Long,
)
