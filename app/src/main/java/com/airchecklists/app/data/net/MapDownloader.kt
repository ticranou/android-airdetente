package com.airchecklists.app.data.net

import com.airchecklists.app.data.model.MapManifest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Downloads the VFR map release (basemap.mbtiles + openaip.zip + manifest.json)
 * from a GitHub Release, over HTTP with progress + resume. No external dep
 * (HttpURLConnection), same idiom as [VacDownloader]. All calls on Dispatchers.IO.
 */
class MapDownloader {

    object Config {
        /** Release asset base; `latest` always resolves to the newest release. */
        const val BASE = "https://github.com/ticranou/android-flight-application/releases"
        const val LATEST = "$BASE/latest/download"
        const val MANIFEST = "manifest.json"
        const val BASEMAP = "basemap.mbtiles"
        const val OPENAIP = "openaip.zip"
        fun url(file: String) = "$LATEST/$file"
    }

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /** Fetch and parse the remote manifest, or null on failure. */
    suspend fun fetchManifest(): MapManifest? = withContext(Dispatchers.IO) {
        runCatching {
            val conn = (URL(Config.url(Config.MANIFEST)).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 15_000
                readTimeout = 20_000
                instanceFollowRedirects = true
            }
            conn.connect()
            if (conn.responseCode !in 200..299) { conn.disconnect(); return@runCatching null }
            val text = conn.inputStream.use { it.bufferedReader().readText() }
                .removePrefix("﻿")  // strip a UTF-8 BOM (PowerShell writes one)
                .trim()
            conn.disconnect()
            json.decodeFromString(MapManifest.serializer(), text)
        }.getOrNull()
    }

    /**
     * Download [fileName] to [target], resuming if a partial file exists.
     * [onProgress] is called with (bytesDone, bytesTotal); bytesTotal may be -1
     * if unknown. Returns true on success.
     */
    suspend fun download(
        fileName: String,
        target: File,
        onProgress: (done: Long, total: Long) -> Unit,
    ): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            target.parentFile?.mkdirs()
            val part = File(target.parentFile, target.name + ".part")
            val existing = if (part.exists()) part.length() else 0L

            val conn = (URL(Config.url(fileName)).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 15_000
                readTimeout = 30_000
                instanceFollowRedirects = true
                if (existing > 0) setRequestProperty("Range", "bytes=$existing-")
            }
            conn.connect()
            val code = conn.responseCode
            // 206 = resumed partial; 200 = full (server ignored Range -> restart).
            val resumed = code == HttpURLConnection.HTTP_PARTIAL && existing > 0
            if (code !in 200..299) { conn.disconnect(); return@runCatching false }
            if (!resumed && existing > 0) part.delete()  // can't resume -> start over

            val startAt = if (resumed) existing else 0L
            // Total size (add the already-downloaded part when the server reports
            // only the remaining length for a 206 response).
            val contentLen = conn.getHeaderFieldLong("Content-Length", -1L)
            val total = if (contentLen >= 0) contentLen + startAt else -1L

            var done = startAt
            conn.inputStream.use { input ->
                java.io.RandomAccessFile(part, "rw").use { out ->
                    out.seek(startAt)
                    val buf = ByteArray(64 * 1024)
                    var lastReport = 0L
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        done += n
                        // Throttle progress callbacks (~ every 512 KB).
                        if (done - lastReport >= 512 * 1024) {
                            onProgress(done, total)
                            lastReport = done
                        }
                    }
                }
            }
            conn.disconnect()
            onProgress(done, total)
            // Atomically move .part -> target.
            if (target.exists()) target.delete()
            if (!part.renameTo(target)) {
                part.copyTo(target, overwrite = true); part.delete()
            }
            true
        }.getOrDefault(false)
    }
}
