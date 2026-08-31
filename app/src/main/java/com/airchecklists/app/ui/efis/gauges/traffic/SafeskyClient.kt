package com.airchecklists.app.ui.efis.gauges.traffic

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.net.HttpURLConnection
import java.net.URL

@Serializable
data class SafeskyBeacon(
    val id: String = "",
    @SerialName("call_sign")   val callSign: String = "",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val altitude: Int = 0,          // feet MSL
    val course: Int = 0,
    @SerialName("ground_speed") val groundSpeed: Int = 0,
    @SerialName("vertical_rate") val verticalRate: Int = 0,
    @SerialName("beacon_type")   val beaconType: String = "",
    @SerialName("transponder_type") val transponderType: String = "",
    val status: String = "",
    @SerialName("last_update") val lastUpdate: String = "",
    val accuracy: String = "",
)

/** Fetches nearby beacons from the Safesky public REST API.
 *  The [apiKey] is passed by the caller — never stored internally.
 *  All calls run on Dispatchers.IO; returns empty list on any error. */
class SafeskyClient {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val baseUrl = "https://public-api.safesky.app/v1"

    suspend fun fetchBeacons(
        lat: Double,
        lon: Double,
        radiusNm: Double = 5.0,
        apiKey: String?,
    ): List<SafeskyBeacon> {
        if (apiKey.isNullOrBlank()) return emptyList()
        return withContext(Dispatchers.IO) {
            runCatching {
                val delta = radiusNm / 60.0   // 1 NM ≈ 1/60 degree latitude
                val latMin = lat - delta; val latMax = lat + delta
                val lonMin = lon - delta; val lonMax = lon + delta
                val viewport = "$latMin,$lonMin,$latMax,$lonMax"
                val body = httpGet("$baseUrl/beacons?viewport=$viewport", apiKey)
                json.decodeFromString<List<SafeskyBeacon>>(body)
            }.getOrElse { emptyList() }
        }
    }

    private fun httpGet(url: String, apiKey: String): String {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 10_000
            readTimeout = 15_000
            instanceFollowRedirects = true
            setRequestProperty("Accept", "application/json")
            setRequestProperty("x-api-key", apiKey)
        }
        conn.connect()
        if (conn.responseCode !in 200..299) {
            conn.disconnect()
            error("HTTP ${conn.responseCode}")
        }
        return conn.inputStream.bufferedReader().use { it.readText() }.also { conn.disconnect() }
    }
}
