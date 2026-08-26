package com.airchecklists.app.data.local

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.airchecklists.app.data.sensors.FdrKind
import com.airchecklists.app.data.sensors.FdrSample
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/**
 * Exports the flight-recorder buffer to the public Downloads folder so it can be
 * pulled off the device over USB. Two formats:
 *  - GPX 1.1 track (lat/lon/ele/time) — open in Google Earth, QGIS, flight tools;
 *  - raw CSV log (every sample: GPS, accel, gyro, baro, altitude).
 *
 * Uses MediaStore on Android 10+ (no permission needed); falls back to a direct
 * file on older versions.
 */
object FlightLogExporter {

    private fun stamp(): String =
        SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).apply {
            timeZone = TimeZone.getDefault()
        }.format(java.util.Date(nowMs()))

    // System.currentTimeMillis via a tiny indirection (keeps the object testable).
    private fun nowMs(): Long = System.currentTimeMillis()

    /** Writes a GPX track to Downloads. Returns the display file name, or null. */
    fun exportGpx(context: Context, samples: List<FdrSample>): String? {
        val name = "airdetente-trace-${stamp()}.gpx"
        val gpx = buildGpx(samples)
        return if (writeToDownloads(context, name, "application/gpx+xml", gpx)) name else null
    }

    /** Writes the raw sample log (CSV) to Downloads. Returns the file name, or null. */
    fun exportRaw(context: Context, samples: List<FdrSample>): String? {
        val name = "airdetente-log-${stamp()}.csv"
        val csv = buildCsv(samples)
        return if (writeToDownloads(context, name, "text/csv", csv)) name else null
    }

    // ---- content builders ----

    private val iso = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    private fun buildGpx(samples: List<FdrSample>): String {
        // Track points come from GPS samples; altitude = the ALT sample nearest in time.
        val alts = samples.filter { it.kind == FdrKind.ALT && it.v1 != null }
        fun nearestAltFt(tMs: Long): Double? =
            alts.minByOrNull { kotlin.math.abs(it.tMs - tMs) }?.v1

        val sb = StringBuilder()
        sb.append("""<?xml version="1.0" encoding="UTF-8"?>""").append('\n')
        sb.append("""<gpx version="1.1" creator="AirDetente" xmlns="http://www.topografix.com/GPX/1/1">""").append('\n')
        sb.append("  <trk><name>AirDetente ${stamp()}</name><trkseg>\n")
        samples.filter { it.kind == FdrKind.GPS && it.v1 != null && it.v2 != null }.forEach { s ->
            val lat = s.v1!!; val lon = s.v2!!
            sb.append("""    <trkpt lat="$lat" lon="$lon">""")
            nearestAltFt(s.tMs)?.let { ft -> sb.append("<ele>${ft / 3.28084}</ele>") }  // GPX ele in metres
            sb.append("<time>${iso.format(java.util.Date(s.tMs))}</time>")
            sb.append("</trkpt>\n")
        }
        sb.append("  </trkseg></trk>\n")
        sb.append("</gpx>\n")
        return sb.toString()
    }

    private fun buildCsv(samples: List<FdrSample>): String {
        val sb = StringBuilder()
        sb.append("t_iso,t_ms,kind,v1,v2,v3\n")
        samples.forEach { s ->
            sb.append(iso.format(java.util.Date(s.tMs))).append(',')
                .append(s.tMs).append(',')
                .append(s.kind.name).append(',')
                .append(s.v1?.toString() ?: "").append(',')
                .append(s.v2?.toString() ?: "").append(',')
                .append(s.v3?.toString() ?: "").append('\n')
        }
        return sb.toString()
    }

    // ---- Downloads writer ----

    private fun writeToDownloads(context: Context, name: String, mime: String, content: String): Boolean =
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, name)
                    put(MediaStore.Downloads.MIME_TYPE, mime)
                    put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                    put(MediaStore.Downloads.IS_PENDING, 1)
                }
                val resolver = context.contentResolver
                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                    ?: return false
                resolver.openOutputStream(uri)?.use { it.write(content.toByteArray()) } ?: return false
                values.clear(); values.put(MediaStore.Downloads.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
                true
            } else {
                @Suppress("DEPRECATION")
                val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                dir.mkdirs()
                File(dir, name).writeText(content)
                true
            }
        }.getOrDefault(false)
}
