package com.airchecklists.app.data.local

import android.content.Context
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

    private val file = File(context.filesDir, "settings.json")

    fun read(): AppPreferences {
        if (!file.exists()) return AppPreferences()
        return runCatching {
            json.decodeFromString(AppPreferences.serializer(), file.readText())
        }.getOrDefault(AppPreferences())
    }

    fun write(prefs: AppPreferences) {
        runCatching {
            file.writeText(json.encodeToString(AppPreferences.serializer(), prefs))
        }
    }
}
