package com.airchecklists.app.data.net

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

/** Opens a VAC PDF: the local file if present, otherwise the remote URL. */
object PdfOpener {

    /**
     * @return true if an activity was launched, false if nothing could handle it.
     */
    fun open(context: Context, localFile: File?, remoteUrl: String): Boolean {
        val intent = if (localFile != null && localFile.exists()) {
            val uri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                localFile,
            )
            Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/pdf")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        } else {
            Intent(Intent.ACTION_VIEW, Uri.parse(remoteUrl)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }
        return try {
            context.startActivity(intent)
            true
        } catch (e: ActivityNotFoundException) {
            false
        }
    }
}
