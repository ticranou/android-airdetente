package com.airchecklists.app.ui.efis.gauges.map

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** Great-circle navigation helpers shared by the moving map and the nav planner. */
object NavMath {
    /** Initial great-circle bearing from (lat1,lon1) to (lat2,lon2), degrees 0..360. */
    fun bearingDeg(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Float {
        val p1 = Math.toRadians(lat1); val p2 = Math.toRadians(lat2)
        val dLon = Math.toRadians(lon2 - lon1)
        val y = sin(dLon) * cos(p2)
        val x = cos(p1) * sin(p2) - sin(p1) * cos(p2) * cos(dLon)
        var deg = Math.toDegrees(atan2(y, x)).toFloat()
        if (deg < 0) deg += 360f
        return deg
    }

    /** Great-circle distance in nautical miles between two lat/lon points. */
    fun distanceNm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val rNm = 3440.065  // Earth radius in NM
        val p1 = Math.toRadians(lat1); val p2 = Math.toRadians(lat2)
        val dP = Math.toRadians(lat2 - lat1); val dL = Math.toRadians(lon2 - lon1)
        val a = sin(dP / 2).let { it * it } + cos(p1) * cos(p2) * sin(dL / 2).let { it * it }
        return 2 * rNm * atan2(sqrt(a), sqrt(1 - a))
    }
}
