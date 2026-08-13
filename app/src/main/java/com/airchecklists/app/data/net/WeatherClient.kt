package com.airchecklists.app.data.net

import com.airchecklists.app.data.model.CloudLayer
import com.airchecklists.app.data.model.MetarData
import com.airchecklists.app.data.model.TafData
import com.airchecklists.app.data.model.WeatherResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.HttpURLConnection
import java.net.URL

/**
 * Fetches METAR + TAF from NOAA Aviation Weather (free, no key), decoding the
 * already-structured JSON. Inspired by C:\SAPDevelop\ha-metar-taf.
 * All calls run on Dispatchers.IO.
 */
class WeatherClient {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private val metarUrl = "https://aviationweather.gov/api/data/metar"
    private val tafUrl = "https://aviationweather.gov/api/data/taf"

    suspend fun fetch(icao: String): WeatherResult = withContext(Dispatchers.IO) {
        val code = icao.trim().uppercase()
        val metar = runCatching { fetchMetar(code) }.getOrNull()
        val taf = runCatching { fetchTaf(code) }.getOrNull()
        WeatherResult(metar = metar, taf = taf, fetchedAt = System.currentTimeMillis())
    }

    private fun httpGet(url: String): String {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 15_000
            readTimeout = 20_000
            instanceFollowRedirects = true
            setRequestProperty("Accept", "application/json")
        }
        conn.connect()
        if (conn.responseCode !in 200..299) {
            conn.disconnect()
            error("HTTP ${conn.responseCode}")
        }
        return conn.inputStream.bufferedReader().use { it.readText() }.also { conn.disconnect() }
    }

    private fun fetchMetar(icao: String): MetarData? {
        val body = httpGet("$metarUrl?ids=$icao&format=json&hours=3")
        val arr = json.parseToJsonElement(body) as? JsonArray ?: return null
        // Keep the most recent row (largest obsTime).
        val row = arr.mapNotNull { it as? JsonObject }
            .maxByOrNull { it.longOrNull("obsTime") ?: 0L } ?: return null

        val clouds = (row["clouds"] as? JsonArray).orEmpty().mapNotNull { c ->
            val o = c as? JsonObject ?: return@mapNotNull null
            val cover = o.stringOrNull("cover") ?: return@mapNotNull null
            CloudLayer(cover = cover, baseFt = o.intOrNull("base"))
        }
        val ceiling = clouds
            .filter { it.cover in setOf("BKN", "OVC", "OVX") && it.baseFt != null }
            .minByOrNull { it.baseFt!! }?.baseFt

        val visibility = row["visib"]?.let { el ->
            runCatching { el.jsonPrimitive.content }.getOrNull()
        }
        val fltCat = row.stringOrNull("fltCat")
        val rawOb = row.stringOrNull("rawOb") ?: ""
        // Variable wind e.g. "180V260" in the raw METAR.
        val varMatch = Regex("""\b(\d{3})V(\d{3})\b""").find(rawOb)

        return MetarData(
            icao = row.stringOrNull("icaoId") ?: icao,
            name = row.stringOrNull("name") ?: "",
            rawOb = rawOb,
            observedAt = row.stringOrNull("reportTime"),
            windDir = row.intOrNull("wdir"),
            windSpeedKt = row.intOrNull("wspd"),
            windGustKt = row.intOrNull("wgst"),
            windVarFrom = varMatch?.groupValues?.get(1)?.toIntOrNull(),
            windVarTo = varMatch?.groupValues?.get(2)?.toIntOrNull(),
            visibility = visibility,
            tempC = row.doubleOrNull("temp"),
            dewpointC = row.doubleOrNull("dewp"),
            qnhHpa = row.doubleOrNull("altim")?.let { Math.round(it).toInt() },
            clouds = clouds,
            ceilingFt = ceiling,
            flightCategory = fltCat?.takeIf { it.isNotBlank() }
                ?: flightCategory(visibility, ceiling),
        )
    }

    private fun fetchTaf(icao: String): TafData? {
        val body = httpGet("$tafUrl?ids=$icao&format=json")
        val arr = json.parseToJsonElement(body) as? JsonArray ?: return null
        val raw = arr.mapNotNull { it as? JsonObject }
            .firstNotNullOfOrNull { it.stringOrNull("rawTAF") } ?: return null
        return TafData(raw = raw, periods = splitTafPeriods(raw))
    }

    // ---- helpers ----

    private fun JsonObject.stringOrNull(key: String): String? =
        runCatching { this[key]?.jsonPrimitive?.content }.getOrNull()?.takeIf { it.isNotBlank() && it != "null" }

    private fun JsonObject.intOrNull(key: String): Int? =
        stringOrNull(key)?.let { it.toIntOrNull() ?: it.toDoubleOrNull()?.let { d -> Math.round(d).toInt() } }

    private fun JsonObject.doubleOrNull(key: String): Double? = stringOrNull(key)?.toDoubleOrNull()

    private fun JsonObject.longOrNull(key: String): Long? = stringOrNull(key)?.toLongOrNull()

    private fun JsonArray?.orEmpty(): List<Any?> = this ?: emptyList()

    /** FAA-style flight category — fallback when NOAA fltCat is absent (from parser.py). */
    private fun flightCategory(visibility: String?, ceilingFt: Int?): String {
        val sm: Double? = when {
            visibility == null -> null
            visibility.contains("+") -> 7.0                       // "6+" => plenty
            else -> visibility.toDoubleOrNull()?.let { v ->
                if (v > 100) v / 1609.34 else v                   // meters vs statute miles
            }
        }
        val c = ceilingFt
        return when {
            (c != null && c < 500) || (sm != null && sm < 1) -> "LIFR"
            (c != null && c < 1000) || (sm != null && sm < 3) -> "IFR"
            (c != null && c < 3000) || (sm != null && sm < 5) -> "MVFR"
            c == null && sm == null -> "UNKN"
            else -> "VFR"
        }
    }

    private val tafPeriodRegex = Regex(
        """\b(?:FM\d{6}|BECMG\s+\d{4}/\d{4}|TEMPO\s+\d{4}/\d{4}|PROB\d{2}\s+(?:TEMPO\s+)?\d{4}/\d{4})\b"""
    )

    private fun splitTafPeriods(raw: String): List<String> {
        val text = raw.trim()
        if (text.isEmpty()) return emptyList()
        val matches = tafPeriodRegex.findAll(text).toList()
        if (matches.isEmpty()) return listOf(text)
        val periods = mutableListOf<String>()
        periods.add(text.substring(0, matches.first().range.first).trim())
        matches.forEachIndexed { i, m ->
            val end = if (i + 1 < matches.size) matches[i + 1].range.first else text.length
            periods.add(text.substring(m.range.first, end).trim())
        }
        return periods.filter { it.isNotBlank() }
    }
}
