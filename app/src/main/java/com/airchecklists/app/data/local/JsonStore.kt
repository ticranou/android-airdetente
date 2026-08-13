package com.airchecklists.app.data.local

import android.content.Context
import com.airchecklists.app.data.model.Aircraft
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Reads/writes one JSON file per aircraft in internal storage
 * (filesDir/aircraft/<id>.json). No permissions required.
 *
 * All calls are blocking file IO — callers must invoke them off the main
 * thread (the repository dispatches on Dispatchers.IO).
 */
class JsonStore(context: Context) {

    val json: Json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val dir: File = File(context.filesDir, "aircraft").apply { mkdirs() }

    private fun fileFor(id: String): File = File(dir, "$id.json")

    fun readAll(): List<Aircraft> =
        dir.listFiles { f -> f.isFile && f.extension == "json" }
            ?.mapNotNull { file ->
                runCatching { json.decodeFromString<Aircraft>(file.readText()) }.getOrNull()
            }
            ?.sortedWith(compareBy({ it.sortIndex }, { it.name.lowercase() }))
            ?: emptyList()

    fun read(id: String): Aircraft? {
        val file = fileFor(id)
        if (!file.exists()) return null
        return runCatching { json.decodeFromString<Aircraft>(file.readText()) }.getOrNull()
    }

    /** Atomic write: serialize to a temp file, then rename over the target. */
    fun write(aircraft: Aircraft) {
        val target = fileFor(aircraft.id)
        val tmp = File(dir, "${aircraft.id}.json.tmp")
        tmp.writeText(json.encodeToString(Aircraft.serializer(), aircraft))
        if (target.exists()) target.delete()
        if (!tmp.renameTo(target)) {
            // Fallback if rename fails (e.g. some filesystems): copy then clean up.
            target.writeText(tmp.readText())
            tmp.delete()
        }
    }

    fun delete(id: String): Boolean = fileFor(id).let { if (it.exists()) it.delete() else true }

    /** Parse a raw JSON string into an Aircraft (used by import). Throws on invalid JSON. */
    fun parse(text: String): Aircraft = json.decodeFromString(Aircraft.serializer(), text)

    /** Serialize an Aircraft to a JSON string (used by export). */
    fun stringify(aircraft: Aircraft): String = json.encodeToString(Aircraft.serializer(), aircraft)
}
