package com.airchecklists.app.data.local

import com.airchecklists.app.data.sensors.FdrKind
import com.airchecklists.app.di.ServiceLocator
import com.airchecklists.app.ui.efis.gauges.map.NavMath
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Snapshot of a flight session for the end-of-flight summary card (shown on
 * ENGINE_STOP and captured into the exported ZIP as resume.jpg).
 *
 * Combines the CMNFGT session data (date / aircraft / engine times / duration /
 * calibrated altitude / recorder status) with stats derived from the flight
 * recorder track (distance flown, max altitude, max ground speed).
 */
data class FlightSummary(
    val date: String,
    val aircraftName: String,
    val startStr: String,
    val stopStr: String,
    val durationStr: String,
    val calibAltFt: Int?,
    val recording: Boolean,
    val distanceNm: Double,
    val maxAltFt: Double,
    val maxSpeedKmh: Double,
    val horameterStart: Int?,
    val horameterEnd: Int?,
)

private val dateFmt = SimpleDateFormat("dd/MM/yyyy", Locale.FRANCE)
private val timeFmt = SimpleDateFormat("HH'h'mm", Locale.FRANCE)

/** Formats a horamètre value (hundredths) as "NNN.NN h", or "---" when null. */
fun horameterLabel(hundredths: Int?): String =
    hundredths?.let { "%d.%02d h".format(it / 100, it % 100) } ?: "---"

/** Returns a human-readable duration between two horamètre readings, or null. */
fun horameterDuration(start: Int?, end: Int?): String? {
    if (start == null || end == null) return null
    val delta = end - start
    if (delta <= 0) return null
    val totalMin = delta * 6
    val h = totalMin / 60
    val m = totalMin % 60
    return if (h > 0) "${h}h%02d".format(m) else "${totalMin}'"
}

/** Build the current flight summary from the live app state + recorder track. */
fun buildFlightSummary(): FlightSummary {
    val persist = ServiceLocator.instrumentPersist
    val startMs = persist.engineStartMs
    val stopMs = persist.engineStopMs

    val durationStr = if (startMs != null && stopMs != null) {
        val mins = ((stopMs - startMs) / 60000L).coerceAtLeast(0L)
        "${mins / 60}h${(mins % 60).toString().padStart(2, '0')}"
    } else "---"

    // Stats from the whole-session track (GPS: v1=lat, v2=lon, v3=speedKmh; ALT: v1=ft).
    val track = ServiceLocator.flightRecorder.snapshotTrack()
    val gps = track.filter { it.kind == FdrKind.GPS && it.v1 != null && it.v2 != null }
    var distanceNm = 0.0
    for (i in 1 until gps.size) {
        val a = gps[i - 1]; val b = gps[i]
        distanceNm += NavMath.distanceNm(a.v1!!, a.v2!!, b.v1!!, b.v2!!)
    }
    val maxAltFt = track.filter { it.kind == FdrKind.ALT }.maxOfOrNull { it.v1 ?: 0.0 } ?: 0.0
    val maxSpeedKmh = gps.maxOfOrNull { it.v3 ?: 0.0 } ?: 0.0

    return FlightSummary(
        date = dateFmt.format(Date()),
        aircraftName = ServiceLocator.currentAircraft()?.name?.takeIf { it.isNotBlank() } ?: "---",
        startStr = startMs?.let { timeFmt.format(Date(it)) } ?: "--h--",
        stopStr = stopMs?.let { timeFmt.format(Date(it)) } ?: "--h--",
        durationStr = durationStr,
        calibAltFt = ServiceLocator.altCalibrationFt.value,
        recording = ServiceLocator.flightRecorder.status.value.recording,
        distanceNm = distanceNm,
        maxAltFt = maxAltFt,
        maxSpeedKmh = maxSpeedKmh,
        horameterStart = persist.horameterNum.a,
        horameterEnd = persist.horameterNum.b,
    )
}
