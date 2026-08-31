package com.airchecklists.app.data.terrain

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.floor

/**
 * SRTM GL1 (1 arc-second, ~30 m) elevation provider.
 *
 * Tiles are stored as HGT files (INT16 big-endian, 3601×3601 samples)
 * under filesDir/terrain/srtm/, named e.g. "N45E006.hgt".
 *
 * Download on demand from the NASA/USGS AWS public mirror:
 *   https://s3.amazonaws.com/elevation-tiles-prod/skadi/<NS><lat>/<NS><lat><EW><lon3>.hgt.gz
 *
 * The file is decompressed and cached locally. Void values (−32768) are
 * treated as sea-level (0).
 */
class SrtmProvider(context: Context) {

    private val cacheDir = File(context.filesDir, "terrain/srtm").also { it.mkdirs() }

    // ── Public API ─────────────────────────────────────────────────────────────

    /**
     * Return ground elevation in feet at [lat],[lon], or null if the tile is
     * unavailable and the download failed.  Performs IO on the calling coroutine —
     * call from Dispatchers.IO or via withContext.
     */
    suspend fun elevationFt(lat: Double, lon: Double): Int? = withContext(Dispatchers.IO) {
        val tileLat = floor(lat).toInt()
        val tileLon = floor(lon).toInt()
        val file = hgtFile(tileLat, tileLon)
        if (!file.exists()) {
            if (!download(tileLat, tileLon, file)) return@withContext null
        }
        readElevationMeters(file, lat, lon, tileLat, tileLon)?.let { m ->
            (m * 3.28084).toInt()
        }
    }

    /** Delete all cached HGT tiles (frees storage). */
    fun clearCache() { cacheDir.listFiles()?.forEach { it.delete() } }

    /** Approximate on-disk cache size in bytes. */
    fun cacheBytes(): Long = cacheDir.listFiles()?.sumOf { it.length() } ?: 0L

    // ── File naming ────────────────────────────────────────────────────────────

    private fun hgtFile(tileLat: Int, tileLon: Int): File =
        File(cacheDir, tileName(tileLat, tileLon) + ".hgt")

    private fun tileName(tileLat: Int, tileLon: Int): String {
        val ns = if (tileLat >= 0) "N" else "S"
        val ew = if (tileLon >= 0) "E" else "W"
        return "%s%02d%s%03d".format(ns, kotlin.math.abs(tileLat), ew, kotlin.math.abs(tileLon))
    }

    // ── Download ───────────────────────────────────────────────────────────────

    private fun download(tileLat: Int, tileLon: Int, dest: File): Boolean {
        val name = tileName(tileLat, tileLon)
        val ns = if (tileLat >= 0) "N" else "S"
        val dirPart = "%s%02d".format(ns, kotlin.math.abs(tileLat))
        val url = "https://s3.amazonaws.com/elevation-tiles-prod/skadi/$dirPart/$name.hgt.gz"
        return try {
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.connectTimeout = 10_000
            conn.readTimeout    = 30_000
            conn.setRequestProperty("User-Agent", "AirChecklists-ANLPRX/1.0")
            conn.connect()
            if (conn.responseCode != 200) { conn.disconnect(); return false }
            val tmp = File(cacheDir, "$name.hgt.tmp")
            java.util.zip.GZIPInputStream(conn.inputStream).use { gz ->
                tmp.outputStream().use { out -> gz.copyTo(out) }
            }
            conn.disconnect()
            // Validate minimum expected size: 3601*3601*2 = ~25.9 MB
            if (tmp.length() < 25_000_000L) { tmp.delete(); return false }
            tmp.renameTo(dest)
            true
        } catch (_: Exception) {
            false
        }
    }

    // ── HGT read + bilinear interpolation ─────────────────────────────────────

    /**
     * Read elevation in meters at sub-pixel precision using bilinear interpolation.
     * HGT format: 3601×3601 INT16 big-endian, row 0 = north edge, col 0 = west edge.
     */
    private fun readElevationMeters(
        file: File,
        lat: Double,
        lon: Double,
        tileLat: Int,
        tileLon: Int,
    ): Double? {
        val fracLat = lat - tileLat   // 0..1, 0=south, 1=north
        val fracLon = lon - tileLon   // 0..1, 0=west,  1=east
        // Convert to 0-based sample index (row 0 = north = fracLat 1.0)
        val rowF = (1.0 - fracLat) * 3600.0
        val colF = fracLon * 3600.0
        val row0 = rowF.toInt().coerceIn(0, 3600)
        val col0 = colF.toInt().coerceIn(0, 3600)
        val row1 = (row0 + 1).coerceAtMost(3600)
        val col1 = (col0 + 1).coerceAtMost(3600)
        val dr = rowF - row0
        val dc = colF - col0

        return try {
            RandomAccessFile(file, "r").use { raf ->
                fun sample(r: Int, c: Int): Double {
                    raf.seek(((r * 3601L + c) * 2L))
                    val v = raf.readShort().toInt()
                    return if (v == -32768) 0.0 else v.toDouble()
                }
                val z00 = sample(row0, col0)
                val z01 = sample(row0, col1)
                val z10 = sample(row1, col0)
                val z11 = sample(row1, col1)
                z00 * (1-dr) * (1-dc) + z01 * (1-dr) * dc +
                z10 * dr    * (1-dc) + z11 * dr     * dc
            }
        } catch (_: Exception) { null }
    }
}
