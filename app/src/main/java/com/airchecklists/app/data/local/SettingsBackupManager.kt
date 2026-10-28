package com.airchecklists.app.data.local

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import com.airchecklists.app.data.model.AppDataset
import com.airchecklists.app.data.model.DatasetPrefs
import com.airchecklists.app.data.model.VacChart
import com.airchecklists.app.data.model.Aircraft
import com.airchecklists.app.data.model.AppPreferences
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Writes a full AppDataset backup (aircraft + terrains + business prefs) to
 *  Downloads/AirDetente at app startup, keeping the [MAX_BACKUPS] most recent files. */
class SettingsBackupManager(private val context: Context) {

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun backupNow(
        aircraft: List<Aircraft>,
        terrains: List<VacChart>,
        prefs: AppPreferences,
    ) {
        runCatching {
            val dataset = AppDataset(
                aircraft    = aircraft,
                terrains    = terrains,
                preferences = DatasetPrefs.from(prefs),
            )
            val text  = json.encodeToString(AppDataset.serializer(), dataset)
            val ts    = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
            val name  = "airdetente-backup-$ts.json"
            writeToDownloads(name, text.toByteArray())
            rotate()
            Log.i("SettingsBackupManager",
                "Backup created: $name (${aircraft.size} aircraft, ${terrains.size} terrains)")
        }.onFailure {
            Log.w("SettingsBackupManager", "Backup failed: ${it.message}")
        }
    }

    private fun writeToDownloads(name: String, bytes: ByteArray) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, name)
                put(MediaStore.Downloads.MIME_TYPE, "application/json")
                put(MediaStore.Downloads.RELATIVE_PATH,
                    "${Environment.DIRECTORY_DOWNLOADS}/AirDetente")
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: return
            resolver.openOutputStream(uri)?.use { it.write(bytes) }
            values.clear(); values.put(MediaStore.Downloads.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
        } else {
            @Suppress("DEPRECATION")
            val dir = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                "AirDetente",
            )
            dir.mkdirs()
            File(dir, name).writeBytes(bytes)
        }
    }

    private fun rotate() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val projection = arrayOf(MediaStore.Downloads._ID, MediaStore.Downloads.DISPLAY_NAME)
            val selection  = "${MediaStore.Downloads.RELATIVE_PATH} = ? " +
                "AND ${MediaStore.Downloads.DISPLAY_NAME} LIKE ?"
            val selectionArgs = arrayOf(
                "${Environment.DIRECTORY_DOWNLOADS}/AirDetente/",
                "airdetente-backup-%.json",
            )
            val resolver = context.contentResolver
            val entries  = mutableListOf<Long>()
            resolver.query(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                projection, selection, selectionArgs,
                "${MediaStore.Downloads.DISPLAY_NAME} DESC",
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Downloads._ID)
                while (cursor.moveToNext()) entries.add(cursor.getLong(idCol))
            }
            entries.drop(MAX_BACKUPS).forEach { id ->
                resolver.delete(
                    MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                    "${MediaStore.Downloads._ID} = ?",
                    arrayOf(id.toString()),
                )
            }
        } else {
            @Suppress("DEPRECATION")
            val dir = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                "AirDetente",
            )
            dir.listFiles()
                ?.filter { it.isFile && it.name.startsWith("airdetente-backup-") }
                ?.sortedByDescending { it.name }
                ?.drop(MAX_BACKUPS)
                ?.forEach { it.delete() }
        }
    }

    companion object {
        private const val MAX_BACKUPS = 10
    }
}
