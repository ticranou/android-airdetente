package com.airchecklists.app.data.local

import android.content.Context
import android.util.Log
import com.airchecklists.app.data.model.AppPreferences
import kotlinx.serialization.json.Json
import java.io.File

/** Reads/writes the single settings.json file holding user preferences. */
class SettingsStore(context: Context) {

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val file    = File(context.filesDir, "settings.json")
    private val bakFile = File(context.filesDir, "settings.json.bak")
    private val tmpFile = File(context.filesDir, "settings.json.tmp")

    fun read(): AppPreferences {
        // Try primary file, then backup, then return defaults.
        val primary = tryRead(file)
        if (primary != null) return primary
        if (file.exists()) Log.w("SettingsStore", "settings.json corrupted — trying backup")
        val backup = tryRead(bakFile)
        if (backup != null) {
            Log.w("SettingsStore", "Restored from settings.json.bak")
            return backup
        }
        return AppPreferences()
    }

    private fun tryRead(f: File): AppPreferences? {
        if (!f.exists()) return null
        return runCatching {
            json.decodeFromString(AppPreferences.serializer(), f.readText())
        }.getOrElse {
            Log.e("SettingsStore", "Failed to read ${f.name}: ${it.message}")
            null
        }
    }

    fun write(prefs: AppPreferences) {
        runCatching {
            val encoded = json.encodeToString(AppPreferences.serializer(), prefs)
            // Atomic write: write to tmp, rename over target.
            tmpFile.writeText(encoded)
            if (!tmpFile.renameTo(file)) {
                file.writeText(tmpFile.readText())
                tmpFile.delete()
            }
            // Keep a backup copy of the last known-good write.
            runCatching { file.copyTo(bakFile, overwrite = true) }
        }.onFailure {
            Log.e("SettingsStore", "Failed to write settings: ${it.message}")
        }
    }
}
