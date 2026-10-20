package com.airchecklists.app.data.local

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.ComposeView
import androidx.core.view.drawToBitmap
import com.airchecklists.app.ui.theme.AirDetenteTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.ByteArrayOutputStream
import kotlin.coroutines.resume

/**
 * Renders a Composable off-screen and returns it as a JPEG byte array.
 *
 * The content is text-only (no SurfaceView/MapLibre), so a transient ComposeView
 * attached to the Activity's content root composes and draws reliably. We attach it
 * invisibly (0 alpha, below everything), wait one layout pass, snapshot it, then
 * detach. Returns null on any failure — never throws.
 *
 * Must run with the main dispatcher available; call from a coroutine.
 */
suspend fun captureComposableJpeg(
    activity: Activity,
    widthPx: Int = 1000,
    quality: Int = 85,
    content: @Composable () -> Unit,
): ByteArray? = runCatching {
    val bitmap = withTimeoutOrNull(5_000) {
        withContext(Dispatchers.Main) {
            renderToBitmap(activity, widthPx, content)
        }
    } ?: return null
    if (bitmap == null) return null
    val stream = ByteArrayOutputStream()
    // Flatten potential transparency onto the dark panel colour so the JPEG looks right.
    val opaque = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
    android.graphics.Canvas(opaque).apply {
        drawColor(AndroidColor.rgb(0x0A, 0x0A, 0x0A))
        drawBitmap(bitmap, 0f, 0f, null)
    }
    opaque.compress(Bitmap.CompressFormat.JPEG, quality, stream)
    stream.toByteArray()
}.getOrNull()

private suspend fun renderToBitmap(
    activity: Activity,
    widthPx: Int,
    content: @Composable () -> Unit,
): Bitmap? = suspendCancellableCoroutine { cont ->
    val root = activity.window.decorView as ViewGroup
    val composeView = ComposeView(activity).apply {
        alpha = 0f  // invisible to the user
        setContent { AirDetenteTheme { content() } }
    }
    // Add below the current UI with a fixed width, height = wrap.
    val lp = FrameLayout.LayoutParams(widthPx, ViewGroup.LayoutParams.WRAP_CONTENT)
    root.addView(composeView, 0, lp)

    fun cleanup() { runCatching { root.removeView(composeView) } }

    // Wait for the first layout, then snapshot on the next frame.
    composeView.viewTreeObserver.addOnGlobalLayoutListener(
        object : android.view.ViewTreeObserver.OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                composeView.viewTreeObserver.removeOnGlobalLayoutListener(this)
                composeView.post {
                    val result = runCatching {
                        if (composeView.width > 0 && composeView.height > 0) composeView.drawToBitmap()
                        else null
                    }.getOrNull()
                    cleanup()
                    if (cont.isActive) cont.resume(result)
                }
            }
        },
    )
    composeView.requestLayout()
    cont.invokeOnCancellation { cleanup() }
}
