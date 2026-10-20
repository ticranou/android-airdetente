package com.airchecklists.app.data.local

import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tan

/**
 * Pure-Kotlin NOAA solar algorithm.
 * Computes sunrise, sunset, civil dawn/dusk (sun at −6°) for a given
 * latitude/longitude and Julian Day Number.
 *
 * All times are expressed as minutes-since-midnight UTC (Int).
 * Null means the sun never rises/sets that day (polar conditions).
 */
object SunCalc {

    data class SunTimes(
        /** Sunrise (sun upper limb on horizon), minutes UTC since midnight. Null = midnight sun / polar night. */
        val sunriseUtcMin: Int?,
        /** Sunset, minutes UTC since midnight. Null = midnight sun / polar night. */
        val sunsetUtcMin: Int?,
        /** Civil dawn: sun at −6° on the way up, minutes UTC. Null when sun stays above/below −6°. */
        val dawnUtcMin: Int?,
        /** Civil dusk: sun at −6° on the way down, minutes UTC. Null when sun stays above/below −6°. */
        val duskUtcMin: Int?,
    )

    /** Convert System.currentTimeMillis() epoch to Julian Day Number (fractional). */
    fun epochMsToJulianDay(epochMs: Long): Double = epochMs / 86_400_000.0 + 2_440_587.5

    /**
     * Compute solar events for [latDeg]/[lonDeg] on the calendar day of [julianDay].
     * The JD used for intermediate calculations is truncated to local noon.
     */
    fun compute(latDeg: Double, lonDeg: Double, julianDay: Double): SunTimes {
        val jd = julianDay
        val lat = Math.toRadians(latDeg)

        // --- Julian century ---
        val jc = (jd - 2_451_545.0) / 36_525.0

        // --- Geometric mean longitude of the sun (degrees) ---
        val geomMeanLong = (280.46646 + jc * (36_000.76983 + jc * 0.0003032)) % 360.0

        // --- Geometric mean anomaly (degrees) ---
        val geomMeanAnom = 357.52911 + jc * (35_999.05029 - 0.0001537 * jc)

        // --- Equation of centre ---
        val anomRad = Math.toRadians(geomMeanAnom)
        val eqCentre = sin(anomRad) * (1.914602 - jc * (0.004817 + 0.000014 * jc)) +
            sin(2.0 * anomRad) * (0.019993 - 0.000101 * jc) +
            sin(3.0 * anomRad) * 0.000289

        // --- Sun true longitude / apparent longitude ---
        val sunTrueLon = geomMeanLong + eqCentre
        val omegaRad = Math.toRadians(125.04 - 1934.136 * jc)
        val sunAppLon = sunTrueLon - 0.00569 - 0.00478 * sin(omegaRad)

        // --- Mean obliquity of ecliptic (degrees) + correction ---
        val meanObliq = 23.0 + (26.0 + (21.448 - jc * (46.8150 + jc * (0.00059 - jc * 0.001813))) / 60.0) / 60.0
        val obliqCorr = meanObliq + 0.00256 * cos(omegaRad)

        // --- Sun declination (radians) ---
        val decl = asin(sin(Math.toRadians(obliqCorr)) * sin(Math.toRadians(sunAppLon)))

        // --- Equation of time (minutes) ---
        val y = tan(Math.toRadians(obliqCorr / 2.0)).let { it * it }
        val sinM = sin(Math.toRadians(geomMeanAnom))
        val cos2L = cos(Math.toRadians(2.0 * geomMeanLong))
        val sin4L = sin(Math.toRadians(4.0 * geomMeanLong))
        val sin2M = sin(Math.toRadians(2.0 * geomMeanAnom))
        val ecc = 0.016708634 - jc * (0.000042037 + 0.0000001267 * jc)
        val eqTime = 4.0 * Math.toDegrees(
            y * sin(Math.toRadians(2.0 * geomMeanLong)) -
                2.0 * ecc * sinM +
                4.0 * ecc * y * sinM * cos2L -
                0.5 * y * y * sin4L -
                1.25 * ecc * ecc * sin2M,
        )

        // --- Compute rise/set for a given zenith angle (degrees) ---
        // zenith = 90.833 for sunrise/sunset (accounts for refraction + solar disc)
        // zenith = 96.0   for civil twilight (sun at −6°)
        fun riseSetUtcMin(zenithDeg: Double): Pair<Int?, Int?> {
            val cosHA = cos(Math.toRadians(zenithDeg)) / (cos(lat) * cos(decl)) - tan(lat) * tan(decl)
            if (cosHA < -1.0 || cosHA > 1.0) return null to null   // polar condition
            val ha = Math.toDegrees(acos(cosHA))                    // degrees, always positive
            val riseUtc = 720.0 - 4.0 * (lonDeg + ha) - eqTime
            val setUtc  = 720.0 - 4.0 * (lonDeg - ha) - eqTime
            return riseUtc.toInt() to setUtc.toInt()
        }

        val (sunrise, sunset) = riseSetUtcMin(90.833)
        val (dawn, dusk)      = riseSetUtcMin(96.0)

        return SunTimes(
            sunriseUtcMin = sunrise,
            sunsetUtcMin  = sunset,
            dawnUtcMin    = dawn,
            duskUtcMin    = dusk,
        )
    }
}
