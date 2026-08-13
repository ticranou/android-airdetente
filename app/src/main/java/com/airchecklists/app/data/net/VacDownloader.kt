package com.airchecklists.app.data.net

import com.airchecklists.app.data.local.VacStore
import com.airchecklists.app.data.model.VacChart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/** Metadata read from the remote PDF without downloading its full content. */
data class RemoteMeta(val size: Long?, val etag: String?)

/** Result of a successful download. */
data class DownloadResult(val fileName: String, val size: Long, val etag: String?)

/**
 * Fetches SIA VAC PDFs over HTTP (no external dependency, HttpURLConnection).
 * All calls run on Dispatchers.IO.
 */
class VacDownloader(private val store: VacStore) {

    private fun fileNameFor(icao: String) = "AD-2.${icao.uppercase()}.pdf"

    fun remoteUrl(cycle: String, icao: String): String =
        "https://www.sia.aviation-civile.gouv.fr/media/dvd/$cycle" +
            "/Atlas-VAC/PDF_AIPparSSection/VAC/AD/AD-2.${icao.uppercase()}.pdf"

    /** Downloads the PDF and stores it locally. Returns metadata, or null on failure. */
    suspend fun download(chart: VacChart, cycle: String): DownloadResult? = withContext(Dispatchers.IO) {
        runCatching {
            val conn = (URL(remoteUrl(cycle, chart.icao)).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 15_000
                readTimeout = 30_000
                instanceFollowRedirects = true
            }
            conn.connect()
            if (conn.responseCode !in 200..299) {
                conn.disconnect()
                return@runCatching null
            }
            val fileName = fileNameFor(chart.icao)
            val etag = conn.getHeaderField("ETag")
            val target = store.pdfFile(fileName)
            conn.inputStream.use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            }
            conn.disconnect()
            DownloadResult(fileName = fileName, size = target.length(), etag = etag)
        }.getOrNull()
    }

    /** Reads remote size/ETag via HEAD (falls back to a ranged GET). Null on failure. */
    suspend fun checkRemoteMeta(icao: String, cycle: String): RemoteMeta? = withContext(Dispatchers.IO) {
        runCatching {
            val url = URL(remoteUrl(cycle, icao))
            var conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "HEAD"
                connectTimeout = 15_000
                readTimeout = 15_000
                instanceFollowRedirects = true
            }
            conn.connect()
            var code = conn.responseCode
            var size = conn.getHeaderFieldLong("Content-Length", -1L).takeIf { it >= 0 }
            var etag = conn.getHeaderField("ETag")
            conn.disconnect()

            // Some servers don't answer HEAD well — fall back to a tiny ranged GET.
            if (code !in 200..299 || size == null) {
                conn = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    setRequestProperty("Range", "bytes=0-0")
                    connectTimeout = 15_000
                    readTimeout = 15_000
                    instanceFollowRedirects = true
                }
                conn.connect()
                code = conn.responseCode
                // Content-Range: "bytes 0-0/123456" => total after the slash.
                val range = conn.getHeaderField("Content-Range")
                val total = range?.substringAfter('/', "")?.toLongOrNull()
                size = total ?: conn.getHeaderFieldLong("Content-Length", -1L).takeIf { it >= 0 }
                etag = etag ?: conn.getHeaderField("ETag")
                conn.disconnect()
            }
            if (code in 200..299) RemoteMeta(size, etag) else null
        }.getOrNull()
    }

    /** True if the local copy differs from the remote (ETag preferred, else size). */
    fun isOutdated(chart: VacChart, remote: RemoteMeta): Boolean {
        if (!chart.isDownloaded) return false
        return when {
            chart.localEtag != null && remote.etag != null -> chart.localEtag != remote.etag
            chart.localSize != null && remote.size != null -> chart.localSize != remote.size
            else -> false // Can't tell — don't cry wolf.
        }
    }
}
