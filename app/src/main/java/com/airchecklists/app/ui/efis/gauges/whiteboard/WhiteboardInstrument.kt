package com.airchecklists.app.ui.efis.gauges.whiteboard

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.airchecklists.app.di.ServiceLocator
import com.airchecklists.app.ui.efis.gauges.LocalGaugeBezel
import com.airchecklists.app.ui.efis.gauges.compact.CompactStyle
import com.airchecklists.app.ui.efis.gauges.compact.compactText
import com.airchecklists.app.ui.efis.gauges.drawNumTitleBar
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val MAX_HISTORY = 10
private const val LONG_PRESS_MS = 500L
private val TIME_FMT = SimpleDateFormat("HH:mm:ss", Locale.ROOT)

private val STROKE_WIDTHS = listOf(2.5f, 4.0f, 6.5f)
private val STROKE_LABELS = listOf("Moyen", "Épais", "Très épais")

/** CMNWHB — tableau blanc / notes. Dessin libre au doigt ou au stylet. */
@Composable
fun WhiteboardInstrument(modifier: Modifier = Modifier) {
    val tm = rememberTextMeasurer()
    val bezel = LocalGaugeBezel.current

    @Suppress("UNCHECKED_CAST")
    val strokes = remember {
        ServiceLocator.instrumentState("whiteboard.strokes") {
            mutableStateOf(emptyList<List<Offset>>())
        }
    } as androidx.compose.runtime.MutableState<List<List<Offset>>>

    @Suppress("UNCHECKED_CAST")
    val history = remember {
        ServiceLocator.instrumentState("whiteboard.history") {
            mutableStateOf(emptyList<Pair<Long, List<List<Offset>>>>())
        }
    } as androidx.compose.runtime.MutableState<List<Pair<Long, List<List<Offset>>>>>

    @Suppress("UNCHECKED_CAST")
    val darkMode = remember {
        ServiceLocator.instrumentState("whiteboard.darkMode") { mutableStateOf(true) }
    } as androidx.compose.runtime.MutableState<Boolean>

    @Suppress("UNCHECKED_CAST")
    val strokeWidthIdx = remember {
        ServiceLocator.instrumentState("whiteboard.strokeWidthIdx") { mutableStateOf(1) }
    } as androidx.compose.runtime.MutableState<Int>

    @Suppress("UNCHECKED_CAST")
    val readOnly = remember {
        ServiceLocator.instrumentState("whiteboard.readOnly") { mutableStateOf(false) }
    } as androidx.compose.runtime.MutableState<Boolean>

    val currentStroke = remember { mutableStateOf(emptyList<Offset>()) }
    var showHistory by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }

    // Hauteur du bandeau titre en dp (doit correspondre à la valeur Canvas)
    val headerDp = 20.dp

    val bgColor = if (darkMode.value) Color.Black else Color.White
    val inkColor = if (darkMode.value) Color.White else Color.Black
    val strokeW  = STROKE_WIDTHS[strokeWidthIdx.value.coerceIn(0, STROKE_WIDTHS.lastIndex)]

    Box(modifier = modifier) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(readOnly.value) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        val w = size.width.toFloat()
                        val h = size.height.toFloat()
                        val headerH = (20f * density).coerceAtMost(h * 0.08f)
                        val barTop  = h * 0.925f

                        val clearLeft  = w * 0.42f
                        val clearRight = w * 0.68f
                        val histLeft   = w * 0.71f
                        val histRight  = w * 0.97f

                        val inClear = down.position.x in clearLeft..clearRight && down.position.y >= barTop
                        val inHist  = down.position.x in histLeft..histRight   && down.position.y >= barTop

                        if (inHist) {
                            down.consume()
                            showHistory = true
                            return@awaitEachGesture
                        }

                        if (inClear) {
                            val downTime = System.currentTimeMillis()
                            var longPressed = false
                            down.consume()
                            do {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull() ?: break
                                if (System.currentTimeMillis() - downTime >= LONG_PRESS_MS) {
                                    longPressed = true
                                    change.consume()
                                    break
                                }
                                if (!change.pressed) break
                            } while (true)
                            if (longPressed) {
                                if (!readOnly.value && strokes.value.isNotEmpty()) {
                                    history.value = (listOf(System.currentTimeMillis() to strokes.value) + history.value).take(MAX_HISTORY)
                                }
                                strokes.value = emptyList()
                                currentStroke.value = emptyList()
                                readOnly.value = false
                            }
                            return@awaitEachGesture
                        }

                        if (down.position.y >= barTop) { down.consume(); return@awaitEachGesture }
                        if (down.position.y < headerH)  { down.consume(); return@awaitEachGesture }
                        if (readOnly.value) { down.consume(); return@awaitEachGesture }

                        currentStroke.value = listOf(down.position)
                        down.consume()
                        do {
                            val event = awaitPointerEvent()
                            val drag = event.changes.firstOrNull() ?: break
                            if (drag.pressed && drag.position.y < barTop && drag.position.y >= headerH) {
                                currentStroke.value = currentStroke.value + drag.position
                                drag.consume()
                            } else if (!drag.pressed) break
                        } while (event.changes.any { it.pressed })
                        if (currentStroke.value.size > 1) {
                            strokes.value = strokes.value + listOf(currentStroke.value)
                        }
                        currentStroke.value = emptyList()
                    }
                },
        ) {
            val w = size.width
            val h = size.height
            val headerH = 20.dp.toPx().coerceAtMost(h * 0.08f)
            val barH    = h * 0.075f
            val barTop  = h - barH
            val drawH   = barTop - headerH

            drawRect(CompactStyle.Bg, size = size)
            drawRect(bgColor, topLeft = Offset(0f, headerH), size = Size(w, drawH))

            drawNumTitleBar(bezel, w, headerH)
            val titleText = if (readOnly.value) "NOTES  [lecture seule]" else "NOTES"
            compactText(tm, titleText, w / 2f, headerH / 2f, sizeSp = 11f, color = CompactStyle.Dim)

            val strokeStyle = Stroke(width = strokeW.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
            for (stroke in strokes.value) {
                if (stroke.size < 2) continue
                val path = Path().apply {
                    moveTo(stroke[0].x, stroke[0].y)
                    for (i in 1 until stroke.size) lineTo(stroke[i].x, stroke[i].y)
                }
                drawPath(path, inkColor, style = strokeStyle)
            }
            if (currentStroke.value.size >= 2) {
                val path = Path().apply {
                    moveTo(currentStroke.value[0].x, currentStroke.value[0].y)
                    for (i in 1 until currentStroke.value.size) lineTo(currentStroke.value[i].x, currentStroke.value[i].y)
                }
                drawPath(path, inkColor, style = strokeStyle)
            }

            // ── Watermark "Lecture seule" juste au-dessus de la toolbar ──
            if (readOnly.value) {
                compactText(
                    tm, "— Lecture seule —",
                    w / 2f, barTop - h * 0.018f,
                    sizeSp = 22f, color = Color(0x88FF8800),
                )
            }

            // ── Barre de boutons (bas) ──
            drawRect(Color(0xFF111111), topLeft = Offset(0f, barTop), size = Size(w, barH))

            val btnH   = barH * 0.80f
            val btnTop = barTop + (barH - btnH) / 2f
            val corner = CornerRadius(btnH / 2f)

            val clearLeft  = w * 0.42f
            val clearRight = w * 0.68f
            val clearW     = clearRight - clearLeft
            drawRoundRect(Color(0xFFB01A2E), topLeft = Offset(clearLeft, btnTop), size = Size(clearW, btnH), cornerRadius = corner)
            compactText(tm, "🧹 Effacer", clearLeft + clearW / 2f, btnTop + btnH / 2f, sizeSp = 15f, bold = true, color = Color.White)

            val histLeft   = w * 0.71f
            val histRight  = w * 0.97f
            val histW      = histRight - histLeft
            val hasHistory = history.value.isNotEmpty()
            drawRoundRect(Color(0xFF222222), topLeft = Offset(histLeft, btnTop), size = Size(histW, btnH), cornerRadius = corner)
            drawRoundRect(
                color = if (hasHistory) Color(0xFF5599CC) else Color(0xFF444444),
                topLeft = Offset(histLeft, btnTop), size = Size(histW, btnH), cornerRadius = corner, style = Stroke(1.5f),
            )
            val histLabel = if (hasHistory) "🕔 (${history.value.size})" else "🕔"
            compactText(tm, histLabel, histLeft + histW / 2f, btnTop + btnH / 2f, sizeSp = 15f, bold = hasHistory,
                color = if (hasHistory) Color.White else Color(0xFF666666))
        }

        // ── Bouton ⚙ circulaire (style Moving Map) — coin haut droit de la surface ──
        Surface(
            shape = CircleShape,
            color = Color(0xCC202325),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = headerDp + 6.dp, end = 6.dp)
                .size(38.dp)
                .clickable { showSettings = true },
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.Settings, contentDescription = "Réglages", tint = Color.White, modifier = Modifier.size(22.dp))
            }
        }

        if (showHistory) {
            HistoryDialog(
                history = history.value,
                onRestore = { idx ->
                    strokes.value = history.value[idx].second
                    readOnly.value = true
                    showHistory = false
                },
                onDismiss = { showHistory = false },
            )
        }

        if (showSettings) {
            SettingsDialog(
                darkMode = darkMode.value,
                strokeWidthIdx = strokeWidthIdx.value,
                onDarkModeChange = { darkMode.value = it },
                onStrokeWidthChange = { strokeWidthIdx.value = it },
                onDismiss = { showSettings = false },
            )
        }
    }
}

@Composable
private fun HistoryDialog(
    history: List<Pair<Long, List<List<Offset>>>>,
    onRestore: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Historique des notes") },
        text = {
            if (history.isEmpty()) {
                Text("Aucune note enregistrée.")
            } else {
                LazyColumn {
                    itemsIndexed(history) { idx, entry ->
                        val timeStr = TIME_FMT.format(Date(entry.first))
                        Column {
                            TextButton(onClick = { onRestore(idx) }) {
                                Text("Note ${idx + 1} — $timeStr")
                            }
                            if (idx < history.lastIndex) HorizontalDivider()
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Fermer") } },
    )
}

@Composable
private fun SettingsDialog(
    darkMode: Boolean,
    strokeWidthIdx: Int,
    onDarkModeChange: (Boolean) -> Unit,
    onStrokeWidthChange: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Réglages") },
        text = {
            Column {
                Text("Fond", style = androidx.compose.material3.MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                    RadioButton(selected = !darkMode, onClick = { onDarkModeChange(false) })
                    Text("Blanc / encre noire", modifier = Modifier.padding(start = 8.dp))
                }
                Row(verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                    RadioButton(selected = darkMode, onClick = { onDarkModeChange(true) })
                    Text("Noir / encre blanche", modifier = Modifier.padding(start = 8.dp))
                }
                Spacer(Modifier.height(12.dp))
                Text("Épaisseur du trait", style = androidx.compose.material3.MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(4.dp))
                STROKE_LABELS.forEachIndexed { idx, label ->
                    Row(verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                        RadioButton(selected = strokeWidthIdx == idx, onClick = { onStrokeWidthChange(idx) })
                        Text(label, modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Fermer") } },
    )
}
