package com.airchecklists.app.data.local

import android.content.Context
import com.airchecklists.app.data.model.VacChart
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Reads/writes the single vac_charts.json file (a list of VacChart) in internal
 * storage. Also owns the vac/ directory where downloaded PDFs live.
 */
class VacStore(context: Context) {

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val file = File(context.filesDir, "vac_charts.json")

    /** Directory holding downloaded VAC PDFs. */
    val pdfDir: File = File(context.filesDir, "vac").apply { mkdirs() }

    fun read(): List<VacChart> {
        if (!file.exists()) return emptyList()
        return runCatching {
            json.decodeFromString(ListSerializer(VacChart.serializer()), file.readText())
        }.getOrDefault(emptyList())
    }

    fun write(charts: List<VacChart>) {
        val tmp = File(file.parentFile, "vac_charts.json.tmp")
        tmp.writeText(json.encodeToString(ListSerializer(VacChart.serializer()), charts))
        if (file.exists()) file.delete()
        if (!tmp.renameTo(file)) {
            file.writeText(tmp.readText())
            tmp.delete()
        }
    }

    fun pdfFile(fileName: String): File = File(pdfDir, fileName)

    fun deletePdf(fileName: String?) {
        if (fileName == null) return
        pdfFile(fileName).takeIf { it.exists() }?.delete()
    }
}
