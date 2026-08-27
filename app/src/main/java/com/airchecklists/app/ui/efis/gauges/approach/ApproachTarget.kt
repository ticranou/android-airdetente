package com.airchecklists.app.ui.efis.gauges.approach

import com.airchecklists.app.data.model.VacChart
import com.airchecklists.app.data.repository.AerodromeDirectory
import com.airchecklists.app.ui.efis.gauges.map.NavMath
import com.airchecklists.app.ui.terrain.QfuParser
import kotlin.math.abs

/**
 * A resolved landing target for the CMNAPP approach instrument: the runway threshold
 * proxy (v1 = the aerodrome reference point), the field elevation, and the chosen
 * landing QFU (true degrees).
 *
 * v1 limitations (documented, "non certifié"): the ARP stands in for the real threshold
 * and the QFU comes from the free-text circuit at 10° granularity. A later upgrade
 * (precomputed runways.geojson) can feed a real threshold + true azimuth into
 * [ApproachGeometry] without changing the geometry.
 */
data class ApproachTarget(
    val icao: String,
    val name: String,
    val lat: Double,
    val lon: Double,
    val fieldElevFt: Int?,
    /** Landing runway heading, degrees true. NaN when unknown (no QFU parsed). */
    val qfuTrueDeg: Float,
    /** Usable runway length in metres from the VAC chart, or null when unknown. */
    val runwayLengthM: Int?,
    val source: Source,
) {
    enum class Source { AUTO, LOCKED }

    val qfuKnown: Boolean get() = !qfuTrueDeg.isNaN()
}

/**
 * Resolves the approach target off the UI thread. AUTO picks the nearest useful
 * aerodrome from the offline [AerodromeDirectory] (no MapView needed) and enriches it
 * with the user's own [VacChart] of the same ICAO when present (richer circuit text).
 */
object ApproachTargetResolver {

    /** Nearest aerodrome as an AUTO target, or null when none is reachable. */
    suspend fun auto(lat: Double, lon: Double, userCharts: List<VacChart>): ApproachTarget? {
        val a = AerodromeDirectory.nearest(lat, lon, 1).firstOrNull() ?: return null
        // Prefer the user's own chart (richer circuit) matched by ICAO.
        val chart = userCharts.firstOrNull { it.icao.equals(a.icao, ignoreCase = true) }
        val circuit = chart?.circuit ?: ""
        val qfu = pickLandingQfu(lat, lon, a.lat, a.lon, circuit)
        return ApproachTarget(
            icao = a.icao,
            name = a.name,
            lat = a.lat,
            lon = a.lon,
            fieldElevFt = a.elevationFt,
            qfuTrueDeg = qfu,
            runwayLengthM = chart?.runwayLengthM,
            source = ApproachTarget.Source.AUTO,
        )
    }

    /** A LOCKED target built from a chosen chart and an explicit QFU (degrees). */
    fun fromChart(chart: VacChart, qfuDeg: Int): ApproachTarget? {
        val lat = chart.latitude ?: return null
        val lon = chart.longitude ?: return null
        return ApproachTarget(
            icao = chart.icao,
            name = chart.airfieldName,
            lat = lat,
            lon = lon,
            fieldElevFt = parseElevationFt(chart.altitude),
            qfuTrueDeg = qfuDeg.toFloat(),
            runwayLengthM = chart.runwayLengthM,
            source = ApproachTarget.Source.LOCKED,
        )
    }

    /**
     * Choose the landing QFU: of the runway headings parsed from the circuit text, keep
     * the one closest to the bearing ship→ARP (i.e. the runway we're flying toward).
     *
     * When no QFU can be parsed (empty/absent circuit — common for OpenAIP aerodromes),
     * fall back to the bearing ship→ARP itself: since we're heading to the field, our
     * line to it is a reasonable approach-axis proxy. This keeps the tunnel live even
     * without circuit data (v1 aid, non certifié). Only returns NaN with no position at
     * all — but callers only invoke this once a fix exists.
     */
    fun pickLandingQfu(
        shipLat: Double, shipLon: Double,
        arpLat: Double, arpLon: Double,
        circuit: String,
    ): Float {
        val toArp = NavMath.bearingDeg(shipLat, shipLon, arpLat, arpLon).toDouble()
        val headings = QfuParser.parse(circuit)
        if (headings.isEmpty()) return toArp.toFloat()
        return headings.minByOrNull { angularDiff(it.toDouble(), toArp) }!!.toFloat()
    }

    /** Smallest absolute angular difference between two headings, degrees 0..180. */
    private fun angularDiff(a: Double, b: Double): Double {
        val d = abs(((a - b + 540.0) % 360.0) - 180.0)
        return d
    }

    /** Parse a leading integer out of an altitude string like "581ft" / "581 ft". */
    private fun parseElevationFt(altitude: String): Int? =
        Regex("""\d+""").find(altitude)?.value?.toIntOrNull()
}
