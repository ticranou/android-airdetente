package com.airchecklists.app.ui.efis.gauges.compact

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.airchecklists.app.di.ServiceLocator
import com.airchecklists.app.ui.efis.gauges.LocalGaugeBezel
import com.airchecklists.app.ui.efis.gauges.drawNumTitleBar
import kotlinx.coroutines.launch

private val FDR_GREEN = Color(0xFF32C832)
private val FDR_ORANGE = Color(0xFFE8843A)
private val FDR_PROGRESS = Color(0xFFB25E1E)   // dark orange (flush-progress bar)

/**
 * Flight Recorder — numeric/banded variant (NUMFDR): a single full-width line (best
 * over two cells). Mirrors [com.airchecklists.app.ui.efis.gauges.FlightRecorderInstrument]:
 * a "FLIGHT RECORDER" title bar, a large centred Recording/Paused badge, a bottom row
 * of 5 availability chips with inline labels (Position GPS / Accélération / Altitude /
 * Inclinaison / Baromètre), and a dark-orange flush-progress bar along the bottom edge.
 *
 * As a single-line (NUMFDR) instrument, the enclosing slot caps its height to a fixed
 * natural value and centres it, so merging it across a tall cell leaves black margin
 * above/below rather than scattering the content. Double-tap toggles pause/resume;
 * long-press opens the export dialog. The recorder itself runs with the flight service
 * (app lifetime), not this instrument.
 */
@Composable
fun FlightRecorderDigital(modifier: Modifier = Modifier) {
    val tm = rememberTextMeasurer()
    val bezel = LocalGaugeBezel.current
    val status by ServiceLocator.flightRecorder.status.collectAsStateWithLifecycle()
    val efis by ServiceLocator.efisProvider.state.collectAsStateWithLifecycle()
    val hasFix = efis.hasPosition
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var showExport by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    // Tick ~1/s so the flush-progress bar animates.
    var tick by androidx.compose.runtime.remember { androidx.compose.runtime.mutableIntStateOf(0) }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        while (true) { kotlinx.coroutines.delay(1000); tick++ }
    }

    fun toast(msg: String) =
        android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_LONG).show()

    fun exportGpx() {
        scope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val samples = ServiceLocator.flightRecorder.snapshotTrack()
            val name = com.airchecklists.app.data.local.FlightLogExporter.exportGpx(context, samples)
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                toast(if (name != null) "Trace exportée : Téléchargements/$name" else "Échec de l'export GPX.")
            }
        }
    }

    fun exportRaw() {
        scope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val samples = ServiceLocator.flightRecorder.snapshotSamples()
            val name = com.airchecklists.app.data.local.FlightLogExporter.exportRaw(context, samples)
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                toast(if (name != null) "Log exporté : Téléchargements/$name" else "Échec de l'export du log.")
            }
        }
    }

    fun exportKml() {
        scope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val samples = ServiceLocator.flightRecorder.snapshotTrack()
            val name = com.airchecklists.app.data.local.FlightLogExporter.exportKml(context, samples)
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                toast(if (name != null) "Trace exportée : Téléchargements/$name" else "Échec de l'export KML.")
            }
        }
    }

    Canvas(
        modifier = modifier.fillMaxSize().pointerInput(Unit) {
            detectTapGestures(
                onDoubleTap = {
                    ServiceLocator.flightRecorder.togglePause()
                    ServiceLocator.setFdrPaused(ServiceLocator.flightRecorder.status.value.paused)
                },
                onLongPress = { showExport = true },
            )
        },
    ) {
        val w = size.width
        val h = size.height

        val headerH = 20.dp.toPx().coerceAtMost(h * 0.28f)

        drawRect(CompactStyle.Bg, size = size)

        val recording = status.recording

        // Panel: title bar + border.
        drawNumTitleBar(bezel, w, headerH)
        drawRect(
            Color(0xFF3A3A3A),
            topLeft = Offset(0f, 0f),
            size = Size(w, h),
            style = Stroke(width = 2f),
        )
        drawGestureHints(6f, headerH / 2f, hasLongPress = true, hasDoubleTap = true)
        compactText(tm, "FLIGHT RECORDER", w / 2f, headerH / 2f, sizeSp = 13f, color = CompactStyle.Dim)

        val bodyTop = headerH
        val bodyH = h - headerH

        // --- Chip row (bottom): a small square + inline label, evenly spread. -----
        data class Param(val label: String, val ok: Boolean)
        val params = listOf(
            Param("Position GPS", status.hasGps && hasFix),
            Param("Accélération", status.hasAccel),
            Param("Altitude", status.hasBaro || (status.hasGps && hasFix)),
            Param("Inclinaison", status.hasGyro),
            Param("Baromètre", status.hasBaro),
        )
        val chipRowCy = bodyTop + bodyH * 0.80f
        val chip = (bodyH * 0.20f).coerceIn(9f, 16f)
        val labelSp = 11f
        // Measure each label so a square+label group can be centred in its column.
        val n = params.size
        val colW = w / n
        params.forEachIndexed { i, p ->
            val colCx = colW * (i + 0.5f)
            val color = if (p.ok) FDR_GREEN else FDR_ORANGE
            val m = tm.measure(p.label, TextStyle(fontSize = labelSp.sp, color = CompactStyle.Mark))
            val labelW = m.size.width.toFloat()
            val gap = 6f
            val groupW = chip + gap + labelW
            val startX = (colCx - groupW / 2f).coerceAtLeast(colW * i + 3f)
            // Square colour chip.
            drawRoundRect(
                color = color,
                topLeft = Offset(startX, chipRowCy - chip / 2f),
                size = Size(chip, chip),
                cornerRadius = CornerRadius(3f, 3f),
            )
            // Inline label to the right of the chip (left-anchored).
            compactText(
                tm, p.label, startX + chip + gap, chipRowCy,
                sizeSp = labelSp, color = CompactStyle.Mark, center = false,
            )
        }

        // --- Large centred Recording / Paused badge (upper body). ----------------
        val badgeColor = if (recording) FDR_GREEN else FDR_ORANGE
        val badgeText = if (recording) "Recording" else "Paused"
        val badgeCy = bodyTop + bodyH * 0.38f
        val bh = (bodyH * 0.60f).coerceIn(34f, 62f)
        // Font a bit smaller than the pill height so the word sits comfortably
        // inside it (not crowding the rounded top/bottom).
        val badgeSp = (bh * 0.40f).coerceIn(15f, 24f)
        val badgeMeasure = tm.measure(
            badgeText,
            TextStyle(color = badgeColor, fontSize = badgeSp.sp, fontWeight = FontWeight.Bold),
        )
        // Generous horizontal padding so the border isn't glued to the word.
        val bw = (badgeMeasure.size.width + bh * 2.2f).coerceAtMost(w * 0.96f)
        drawRoundRect(
            color = Color(0xF2101010),
            topLeft = Offset(w / 2f - bw / 2f, badgeCy - bh / 2f),
            size = Size(bw, bh),
            cornerRadius = CornerRadius(bh / 2f, bh / 2f),
        )
        drawRoundRect(
            color = Color(0xFF555555),
            topLeft = Offset(w / 2f - bw / 2f, badgeCy - bh / 2f),
            size = Size(bw, bh),
            cornerRadius = CornerRadius(bh / 2f, bh / 2f),
            style = Stroke(width = 2f),
        )
        compactText(tm, badgeText, w / 2f, badgeCy, sizeSp = badgeSp, bold = true, color = badgeColor)

        // --- Flush-progress bar along the band's bottom edge (dark orange). ------
        @Suppress("UNUSED_EXPRESSION") tick
        val progress = ServiceLocator.flightRecorder.flushProgress()
        if (recording && progress > 0f) {
            val barH = (h * 0.04f).coerceIn(3f, 6f)
            val barY = h - barH
            drawRect(Color(0x33FFFFFF), topLeft = Offset(0f, barY), size = Size(w, barH))
            drawRect(FDR_PROGRESS, topLeft = Offset(0f, barY), size = Size(w * progress, barH))
        }
    }

    if (showExport) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showExport = false },
            title = { androidx.compose.material3.Text("Exporter l'enregistrement") },
            text = {
                Column {
                    androidx.compose.material3.Text(
                        "Écrit un fichier dans le dossier Téléchargements (accessible en branchant l'appareil au PC).",
                    )
                    androidx.compose.material3.TextButton(
                        onClick = { showExport = false; exportKml() },
                        modifier = Modifier.fillMaxWidth(),
                    ) { androidx.compose.material3.Text("Trace KML (Google Earth)") }
                    androidx.compose.material3.TextButton(
                        onClick = { showExport = false; exportGpx() },
                        modifier = Modifier.fillMaxWidth(),
                    ) { androidx.compose.material3.Text("Trace GPX (QGIS, logiciels de vol)") }
                    androidx.compose.material3.TextButton(
                        onClick = { showExport = false; exportRaw() },
                        modifier = Modifier.fillMaxWidth(),
                    ) { androidx.compose.material3.Text("Log brut CSV (X dernières minutes)") }
                }
            },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = { showExport = false }) {
                    androidx.compose.material3.Text("Fermer")
                }
            },
        )
    }
}
