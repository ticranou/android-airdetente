package com.airchecklists.app.data.net

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.HttpURLConnection
import java.net.URL

/** Winds aloft at ~FL20 (950 hPa) for a position. */
data class WindsAloft(
    val directionDeg: Int,   // FROM direction, ° true
    val speedKt: Int,
    val flightLevel: Int = 20,
)

/** The latest RainViewer radar frame, enough to build tile URLs. */
data class RadarFrame(
    val host: String,        // e.g. "https://tilecache.rainviewer.com"
    val path: String,        // e.g. "/v2/radar/1699999999"
    val timeUnix: Long,
) {
    /** RainViewer tile URL: {host}{path}/{size}/{z}/{x}/{y}/{color}/{options}.png
     *  color 4 = "The Weather Channel" scheme; options "1_1" = smooth + show snow. */
    fun tileUrl(z: Int, x: Int, y: Int, size: Int = 256, color: Int = 4): String =
        "$host$path/$size/$z/$x/$y/$color/1_1.png"
}

/**
 * Fetches free, keyless weather data for the meteo instrument:
 *  - precipitation radar frames from RainViewer (public API),
 *  - winds aloft at FL20 (~950 hPa) from Open-Meteo.
 * All calls run on Dispatchers.IO. Everything is best-effort (returns null on error).
 */
class WeatherRadarClient {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private val rainviewerIndex = "https://api.rainviewer.com/public/weather-maps.json"
    private val openMeteo = "https://api.open-meteo.com/v1/forecast"

    /** Latest available past radar frame (most recent observation). */
    suspend fun latestRadarFrame(): RadarFrame? = withContext(Dispatchers.IO) {
        runCatching {
            val body = httpGet(rainviewerIndex)
            val root = json.parseToJsonElement(body) as? JsonObject ?: return@runCatching null
            val host = root["host"]?.jsonPrimitive?.content ?: return@runCatching null
            val radar = root["radar"] as? JsonObject ?: return@runCatching null
            val past = radar["past"] as? JsonArray ?: return@runCatching null
            val last = past.lastOrNull() as? JsonObject ?: return@runCatching null
            val path = last["path"]?.jsonPrimitive?.content ?: return@runCatching null
            val time = last["time"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0L
            RadarFrame(host = host, path = path, timeUnix = time)
        }.getOrNull()
    }

    /** Winds at ~FL20 (950 hPa) for the given position, for the CURRENT hour. */
    suspend fun windsAloftFL20(lat: Double, lon: Double): WindsAloft? = withContext(Dispatchers.IO) {
        runCatching {
            // Locale.US so decimals use '.', never ',' (French-locale devices would
            // otherwise send "48,7000" and Open-Meteo would reject the request).
            val url = String.format(
                java.util.Locale.US,
                "%s?latitude=%.4f&longitude=%.4f", openMeteo, lat, lon,
            ) + "&hourly=windspeed_950hPa,winddirection_950hPa&windspeed_unit=kn&timezone=auto&forecast_days=1"
            val body = httpGet(url)
            val root = json.parseToJsonElement(body) as? JsonObject ?: return@runCatching null
            val hourly = root["hourly"] as? JsonObject ?: return@runCatching null
            val times = hourly["time"] as? JsonArray ?: return@runCatching null
            val speeds = hourly["windspeed_950hPa"] as? JsonArray ?: return@runCatching null
            val dirs = hourly["winddirection_950hPa"] as? JsonArray ?: return@runCatching null
            // Pick the index for the current local hour (times are local, "auto" tz).
            val idx = currentHourIndex(times).coerceIn(0, minOf(speeds.size, dirs.size) - 1)
            val spd = speeds[idx].jsonPrimitive.content.toDoubleOrNull() ?: return@runCatching null
            val dir = dirs[idx].jsonPrimitive.content.toDoubleOrNull() ?: return@runCatching null
            WindsAloft(directionDeg = ((Math.round(dir).toInt() % 360) + 360) % 360, speedKt = Math.round(spd).toInt())
        }.getOrNull()
    }

    /** Index into the hourly arrays whose timestamp is closest to now (local),
     *  falling back to 0. Open-Meteo hourly times look like "2026-08-03T14:00". */
    private fun currentHourIndex(times: JsonArray): Int {
        val nowHourIso = java.time.LocalDateTime.now()
            .withMinute(0).withSecond(0).withNano(0)
            .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm"))
        val exact = times.indexOfFirst { it.jsonPrimitive.content == nowHourIso }
        if (exact >= 0) return exact
        // Fallback: last timestamp that is <= now.
        val nowKey = nowHourIso
        var best = 0
        times.forEachIndexed { i, el ->
            if (el.jsonPrimitive.content <= nowKey) best = i
        }
        return best
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
}
