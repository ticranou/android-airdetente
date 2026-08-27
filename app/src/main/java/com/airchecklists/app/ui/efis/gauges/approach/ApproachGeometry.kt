package com.airchecklists.app.ui.efis.gauges.approach

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tan

/**
 * Pure approach geometry (no Compose, JVM-testable): given the ship's position/altitude
 * and a landing [ApproachTarget] (threshold proxy + true runway heading), computes the
 * lateral deviation from the extended runway axis, the along-track distance to the
 * threshold, and the vertical deviation from a nominal glide plane.
 *
 * A local flat-earth frame (metres, valid for the few km of a final approach) is used,
 * matching the constants EfisSensorProvider already relies on.
 *
 * NOT a certified guidance source — indicative only. See [ApproachTarget] for how the
 * target is resolved (v1: nearest aerodrome ARP + text QFU).
 */
object ApproachGeometry {

    const val LAT_TOL_M = 15.0          // within ±15 m of the axis = "on axis" (green)
    const val VERT_TOL_FT = 60.0        // within ±60 ft of the plane = "on plane" (green)
    const val GLIDE_DEG = 3.0           // nominal descent plane
    const val M_PER_DEG = 111_320.0
    private const val FT_PER_M = 3.280839895

    /** Result of [compute]. All deviations signed; see field docs. */
    data class ApproachErrors(
        /** + = ship is RIGHT of the extended axis (looking toward the threshold), metres. */
        val lateralM: Double,
        /** + = ship is ABOVE the nominal 3° plane, feet. */
        val aboveFt: Double,
        /** + = threshold is ahead of the ship (still inbound), metres. */
        val alongM: Double,
        /** True when a usable target with a known QFU produced these numbers. */
        val hasTarget: Boolean,
    )

    private fun rad(deg: Double) = Math.toRadians(deg)

    /**
     * Local along/cross-track of the ship relative to the threshold, given a true
     * runway heading [qfuTrueDeg] (the landing direction).
     * Returns (alongM, crossM): along + = threshold ahead, cross + = ship right of axis.
     */
    fun alongCrossM(
        shipLat: Double, shipLon: Double,
        thrLat: Double, thrLon: Double,
        qfuTrueDeg: Double,
    ): Pair<Double, Double> {
        val mPerDegLon = M_PER_DEG * cos(rad(shipLat))
        val east = (shipLon - thrLon) * mPerDegLon
        val north = (shipLat - thrLat) * M_PER_DEG
        val hdg = rad(qfuTrueDeg)
        // Forward = the landing direction; right = 90° clockwise from forward.
        val fwdE = sin(hdg); val fwdN = cos(hdg)
        val rightE = cos(hdg); val rightN = -sin(hdg)
        // Ship is displaced from the threshold; inbound means the threshold is ahead,
        // i.e. the ship sits "behind" along the landing direction → negate the dot.
        val alongM = -(east * fwdE + north * fwdN)
        val crossM = east * rightE + north * rightN
        return alongM to crossM
    }

    /** Height of the 3° plane above field elevation, at [alongM] before the threshold, in feet. */
    fun planeHeightFt(alongM: Double, fieldElevFt: Double, glideDeg: Double = GLIDE_DEG): Double {
        if (alongM <= 0.0) return fieldElevFt
        return fieldElevFt + tan(rad(glideDeg)) * alongM * FT_PER_M
    }

    /**
     * Compute the deviations for [target] at the ship's current state. Returns
     * [ApproachErrors] with hasTarget=false (zeros) when the target/QFU is unusable.
     */
    fun compute(
        shipLat: Double, shipLon: Double, shipAltFt: Double,
        hasPosition: Boolean,
        target: ApproachTarget?,
    ): ApproachErrors {
        if (!hasPosition || target == null || !target.qfuKnown) {
            return ApproachErrors(0.0, 0.0, 0.0, hasTarget = false)
        }
        val (alongM, crossM) = alongCrossM(
            shipLat, shipLon, target.lat, target.lon, target.qfuTrueDeg.toDouble(),
        )
        val fieldElev = (target.fieldElevFt ?: 0).toDouble()
        val planeFt = planeHeightFt(alongM, fieldElev)
        val aboveFt = shipAltFt - planeFt
        return ApproachErrors(crossM, aboveFt, alongM, hasTarget = true)
    }

    fun onAxis(lateralM: Double) = abs(lateralM) <= LAT_TOL_M
    fun onPlane(aboveFt: Double) = abs(aboveFt) <= VERT_TOL_FT
}
