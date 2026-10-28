package com.airchecklists.app.data.local

import android.content.Context
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Creates a timestamped backup of settings.json at each app launch, keeping
 *  the [MAX_BACKUPS] most recent files in [dir] with automatic rotation. */
class SettingsBackupManager(context: Context) {

    private val dir    = File(context.filesDir, "settings_backups").also { it.mkdirs() }
    private val source = File(context.filesDir, "settings.json")

    fun backupNow() {
        if (!source.exists()) return
        runCatching {
            val ts   = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
            source.copyTo(File(dir, "settings-$ts.json"), overwrite = true)
            rotate()
        }.onFailure {
            Log.w("SettingsBackupManager", "Backup failed: ${it.message}")
        }
    }

    private fun rotate() {
        val files = dir.listFiles()
            ?.filter { it.isFile && it.name.startsWith("settings-") }
            ?.sortedByDescending { it.name }  // ISO timestamp → lexicographic = chronological
            ?: return
        files.drop(MAX_BACKUPS).forEach { it.delete() }
    }

    companion object {
        private const val MAX_BACKUPS = 10
    }
}
