package com.airchecklists.app.data.local

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Creates a timestamped backup of settings.json in Downloads/AirDetente at each
 *  app launch, keeping the [MAX_BACKUPS] most recent files with automatic rotation.
 *
 *  Uses MediaStore on Android 10+ (no permission needed); falls back to a direct
 *  file write on older versions (same pattern as FlightLogExporter). */
class SettingsBackupManager(private val context: Context) {

    private val source = File(context.filesDir, "settings.json")

    fun backupNow() {
        if (!source.exists()) return
        runCatching {
            val ts    = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
            val name  = "settings-$ts.json"
            val bytes = source.readBytes()
            writeToDownloads(name, bytes)
            rotate()
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

    /** Remove MediaStore entries for AirDetente backup files beyond the [MAX_BACKUPS] most recent. */
    private fun rotate() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val projection = arrayOf(
                MediaStore.Downloads._ID,
                MediaStore.Downloads.DISPLAY_NAME,
            )
            val selection  = "${MediaStore.Downloads.RELATIVE_PATH} = ? " +
                "AND ${MediaStore.Downloads.DISPLAY_NAME} LIKE ?"
            val selectionArgs = arrayOf(
                "${Environment.DIRECTORY_DOWNLOADS}/AirDetente/",
                "settings-%.json",
            )
            val resolver = context.contentResolver
            val entries  = mutableListOf<Pair<Long, String>>()   // id → name
            resolver.query(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                projection, selection, selectionArgs,
                "${MediaStore.Downloads.DISPLAY_NAME} DESC",
            )?.use { cursor ->
                val idCol   = cursor.getColumnIndexOrThrow(MediaStore.Downloads._ID)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Downloads.DISPLAY_NAME)
                while (cursor.moveToNext()) {
                    entries.add(cursor.getLong(idCol) to cursor.getString(nameCol))
                }
            }
            // Entries are already sorted DESC by name (= DESC by timestamp).
            entries.drop(MAX_BACKUPS).forEach { (id, _) ->
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
                ?.filter { it.isFile && it.name.startsWith("settings-") && it.name.endsWith(".json") }
                ?.sortedByDescending { it.name }
                ?.drop(MAX_BACKUPS)
                ?.forEach { it.delete() }
        }
    }

    companion object {
        private const val MAX_BACKUPS = 10
    }
}
