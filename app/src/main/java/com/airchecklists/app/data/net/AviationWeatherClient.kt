package com.airchecklists.app.data.net

import com.airchecklists.app.data.model.MetarPoint
import com.airchecklists.app.data.model.SigmetArea
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.roundToInt

/**
 * Aeronautical weather from AviationWeather.gov (free, no key), returning map-ready
 * geometry: METAR station points (flight category + raw) within a bbox, and SIGMET
 * hazard polygons (international feed, covers the France FIRs). GeoJSON is parsed
 * leniently so missing fields never crash. All calls run on Dispatchers.IO.
 */
class AviationWeatherClient {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private val metarUrl = "https://aviationweather.gov/api/data/metar"
    private val sigmetUrl = "https://aviationweather.gov/api/data/isigmet"

    /** METAR stations within the [south,west,north,east] bbox, as flight-category points. */
    suspend fun metarsInBbox(
        south: Double, west: Double, north: Double, east: Double, cap: Int = 150,
    ): List<MetarPoint> = withContext(Dispatchers.IO) {
        runCatching {
            // Force a dot decimal separator regardless of device locale (a French
            // locale would otherwise format "47,5" and break the bbox query).
            val bbox = String.format(java.util.Locale.US, "%.3f,%.3f,%.3f,%.3f", south, west, north, east)
            val body = httpGet("$metarUrl?bbox=$bbox&format=geojson")
            val feats = (json.parseToJsonElement(body).jsonObject["features"] as? JsonArray).orEmpty()
            val all = feats.mapNotNull { it.asMetarPoint() }
            // If more than the cap, keep those closest to the bbox centre.
            if (all.size <= cap) all
            else {
                val cLat = (south + north) / 2; val cLon = (west + east) / 2
                all.sortedBy { (it.lat - cLat) * (it.lat - cLat) + (it.lon - cLon) * (it.lon - cLon) }.take(cap)
            }
        }.getOrDefault(emptyList())
    }

    /** Active international SIGMET hazard areas (polygons). */
    suspend fun sigmets(): List<SigmetArea> = withContext(Dispatchers.IO) {
        runCatching {
            val body = httpGet("$sigmetUrl?format=geojson")
            val feats = (json.parseToJsonElement(body).jsonObject["features"] as? JsonArray).orEmpty()
            feats.mapNotNull { it.asSigmetArea() }
        }.getOrDefault(emptyList())
    }

    // ---- parsing ----

    private fun kotlinx.serialization.json.JsonElement.asMetarPoint(): MetarPoint? = runCatching {
        val f = jsonObject
        val props = f["properties"]?.jsonObject ?: return null
        val geom = f["geometry"]?.jsonObject ?: return null
        val coords = geom["coordinates"]?.jsonArray ?: return null
        val lon = coords[0].jsonPrimitive.content.toDouble()
        val lat = coords[1].jsonPrimitive.content.toDouble()
        val icao = props.str("id") ?: return null
        MetarPoint(
            icao = icao,
            lat = lat,
            lon = lon,
            flightCategory = props.str("fltcat") ?: "UNKN",
            rawOb = props.str("rawOb") ?: "",
            windDir = props.int("wdir"),
            windKt = props.int("wspd"),
            tempC = props.int("temp"),
            qnhHpa = props.int("altim"),
        )
    }.getOrNull()

    private fun kotlinx.serialization.json.JsonElement.asSigmetArea(): SigmetArea? = runCatching {
        val f = jsonObject
        val props = f["properties"]?.jsonObject ?: return null
        val geom = f["geometry"]?.jsonObject ?: return null
        if (geom["type"]?.jsonPrimitive?.content != "Polygon") return null
        // GeoJSON Polygon: [ ring, ...] where ring = [ [lon,lat], ... ].
        val rings = geom["coordinates"]?.jsonArray?.mapNotNull { ring ->
            (ring as? JsonArray)?.mapNotNull { pt ->
                val a = pt as? JsonArray ?: return@mapNotNull null
                doubleArrayOf(a[1].jsonPrimitive.content.toDouble(), a[0].jsonPrimitive.content.toDouble())
            }
        }?.filter { it.size >= 3 } ?: return null
        if (rings.isEmpty()) return null
        SigmetArea(
            hazard = props.str("hazard") ?: "SIGMET",
            firName = props.str("firName") ?: "",
            topFl = props.int("top"),
            raw = props.str("rawSigmet") ?: "",
            rings = rings,
        )
    }.getOrNull()

    private fun JsonObject.str(key: String): String? =
        runCatching { this[key]?.jsonPrimitive?.content }.getOrNull()?.takeIf { it.isNotBlank() && it != "null" }

    private fun JsonObject.int(key: String): Int? =
        str(key)?.let { it.toIntOrNull() ?: it.toDoubleOrNull()?.roundToInt() }

    private fun JsonArray?.orEmpty(): List<kotlinx.serialization.json.JsonElement> = this ?: emptyList()

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
}
