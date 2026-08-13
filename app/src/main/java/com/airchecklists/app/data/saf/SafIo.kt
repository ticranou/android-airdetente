package com.airchecklists.app.data.saf

import android.content.Context
import android.net.Uri

/**
 * Thin bridge between a SAF content Uri and a String, via ContentResolver.
 * Used by import (read a picked document) and export (write to a created one).
 */
class SafIo(private val context: Context) {

    fun readText(uri: Uri): String =
        context.contentResolver.openInputStream(uri)
            ?.bufferedReader()
            ?.use { it.readText() }
            ?: error("Impossible d'ouvrir le fichier sélectionné.")

    fun writeText(uri: Uri, text: String) {
        // "wt" truncates any existing content before writing.
        context.contentResolver.openOutputStream(uri, "wt")
            ?.bufferedWriter()
            ?.use { it.write(text) }
            ?: error("Impossible d'écrire dans le fichier sélectionné.")
    }
}
