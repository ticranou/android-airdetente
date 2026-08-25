package com.airchecklists.app.data.repository

import com.airchecklists.app.data.model.VacChart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/** One aerodrome from the offline OpenAIP layer. */
data class Aerodrome(
    val icao: String,
    val name: String,
    val type: Int,
    val lat: Double,
    val lon: Double,
    val elevationFt: Int?,
    val frequency: String?,
)

// ---- GeoJSON DTOs (parsed off the map, with kotlinx-serialization) ----

@Serializable
private data class GjCollection(val features: List<GjFeature> = emptyList())

@Serializable
private data class GjFeature(
    val geometry: GjGeom? = null,
    val properties: GjProps? = null,
)

@Serializable
private data class GjGeom(
    val type: String = "",
    val coordinates: List<Double> = emptyList(),
)

@Serializable
private data class GjProps(
    val name: String? = null,
    val icao: String? = null,
    val type: Int? = null,
    val elevation: GjElevation? = null,
    val frequencies: List<GjFrequency> = emptyList(),
)

@Serializable
private data class GjElevation(val value: Double? = null)

@Serializable
private data class GjFrequency(
    val value: String? = null,
    val primary: Boolean = false,
)

/**
 * Read-only directory of ALL French aerodromes, parsed once from the offline
 * OpenAIP GeoJSON layer (`filesDir/maps/aerodromes.geojson`) that ships with the
 * downloaded map. Used to surface truly-nearby aerodromes in the TERRAINS
 * instruments and the nav planner — independent of MapLibre rendering (so it
 * works without a visible map). Falls back gracefully (empty list) when the map
 * hasn't been downloaded yet.
 */
object AerodromeDirectory {

    /** Aerodrome types considered "useful" (public / usable), per the OpenAIP enum
     *  used on the map: 0 intl, 1 airport, 2 field, 9 aerodrome. Excludes heliports,
     *  seaplane bases, military, private/ULM, altiports, balloon. */
    private val USEFUL_TYPES = setOf(0, 1, 2, 9)

    private val json = Json { ignoreUnknownKeys = true }

    @Volatile private var cache: List<Aerodrome>? = null

    /** Parse (once) and return the useful aerodromes. Empty if the layer is absent. */
    suspend fun load(): List<Aerodrome> {
        cache?.let { return it }
        return withContext(Dispatchers.IO) {
            cache?.let { return@withContext it }
            val repo = com.airchecklists.app.di.ServiceLocator.mapRepository
            val file = repo.openAipFiles().firstOrNull { it.name.startsWith("aero") }
                ?: return@withContext emptyList<Aerodrome>().also { cache = it }
            val parsed = runCatching {
                val col = json.decodeFromString(GjCollection.serializer(), repo.readLayer(file))
                col.features.mapNotNull { it.toAerodrome() }.filter { it.type in USEFUL_TYPES }
            }.getOrDefault(emptyList())
            parsed.also { cache = it }
        }
    }

    /** The [n] useful aerodromes nearest to (lat, lon), closest first. */
    suspend fun nearest(lat: Double, lon: Double, n: Int): List<Aerodrome> =
        load().sortedBy { haversineKm(lat, lon, it.lat, it.lon) }.take(n)

    /**
     * The [n] nearest terrains as [VacChart]s for the TERRAINS instruments: takes the
     * nearest useful OpenAIP aerodromes and, for each, prefers the user's own VacChart
     * (richer: circuit, downloaded VAC) matched by ICAO, else an ephemeral chart.
     *
     * Falls back to the user's own charts (previous behaviour) when the OpenAIP layer
     * is absent (map not downloaded) or there is no GPS fix — so nothing regresses.
     */
    suspend fun nearbyCharts(
        lat: Double,
        lon: Double,
        hasFix: Boolean,
        userCharts: List<VacChart>,
        n: Int,
    ): List<VacChart> {
        val useful = if (hasFix) load() else emptyList()
        if (useful.isEmpty()) return userCharts
        val byIcao = userCharts.associateBy { it.icao.uppercase() }
        return useful
            .sortedBy { haversineKm(lat, lon, it.lat, it.lon) }
            .take(n)
            .map { a -> byIcao[a.icao] ?: a.toEphemeralChart() }
    }

    /** True when this chart id denotes an ephemeral (not-yet-saved) OpenAIP aerodrome. */
    fun isEphemeral(chart: VacChart): Boolean = chart.id.startsWith("oaip:")


    private fun GjFeature.toAerodrome(): Aerodrome? {
        val g = geometry ?: return null
        if (g.type != "Point" || g.coordinates.size < 2) return null
        val p = properties ?: return null
        val icao = p.icao?.takeIf { it.isNotBlank() } ?: return null
        val freq = (p.frequencies.firstOrNull { it.primary } ?: p.frequencies.firstOrNull())?.value
        return Aerodrome(
            icao = icao.uppercase(),
            name = p.name ?: icao,
            type = p.type ?: -1,
            lon = g.coordinates[0],
            lat = g.coordinates[1],
            elevationFt = p.elevation?.value?.roundToInt(),
            frequency = freq?.takeIf { it.isNotBlank() },
        )
    }

    /** As a VacChart (ephemeral, id "oaip:ICAO") so the existing terrains UI can use it. */
    fun Aerodrome.toEphemeralChart(): VacChart = VacChart(
        id = "oaip:$icao",
        icao = icao,
        airfieldName = name,
        altitude = elevationFt?.let { "${it}ft" } ?: "",
        circuit = "",
        frequencies = frequency ?: "",
        latitude = lat,
        longitude = lon,
    )

    private fun haversineKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2) * sin(dLon / 2)
        return r * 2 * atan2(sqrt(a), sqrt(1 - a))
    }
}
