package com.airchecklists.app.ui.efis.gauges

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.drawText
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.airchecklists.app.di.ServiceLocator
import com.airchecklists.app.ui.efis.gauges.compact.drawGestureHints
import kotlinx.coroutines.launch
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle

private val FDR_GREEN = Color(0xFF32C832)
private val FDR_ORANGE = Color(0xFFE8843A)
private val FDR_PROGRESS = Color(0xFFB25E1E)   // dark orange (flush-progress ring)

/**
 * Flight Recorder (ANLFDR): round gauge showing the recorder status
 * (Recording green / Paused orange) and the availability of each logged parameter
 * (green = available & recorded, orange = unavailable on this device).
 * Double-tap toggles pause/resume. The recorder itself runs with the cockpit screen.
 */
@Composable
fun FlightRecorderInstrument(modifier: Modifier = Modifier) {
    val tm = rememberTextMeasurer()
    val bezel = LocalGaugeBezel.current
    val status by ServiceLocator.flightRecorder.status.collectAsStateWithLifecycle()
    val efis by ServiceLocator.efisProvider.state.collectAsStateWithLifecycle()
    val hasFix = efis.hasPosition
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var showExport by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    // Tick ~1/s so the flush-progress ring animates.
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
        val (cx, cy, r) = gaugeFace(bezel)
        drawGestureHints(cx - r * 0.9f, cy - r * 0.9f, hasLongPress = true, hasDoubleTap = true)

        // Progress ring along the inner edge of the face: time until next disk write.
        // Reads tick so it re-evaluates each second.
        @Suppress("UNUSED_EXPRESSION") tick
        val progress = ServiceLocator.flightRecorder.flushProgress()
        if (status.recording && progress > 0f) {
            val ringR = r * 0.97f
            val stroke = r * 0.05f
            // Faint full track + green sweep from top (12 o'clock), clockwise, right at the edge.
            drawArc(
                color = Color(0x33FFFFFF),
                startAngle = -90f, sweepAngle = 360f, useCenter = false,
                topLeft = Offset(cx - ringR, cy - ringR),
                size = Size(ringR * 2, ringR * 2),
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = stroke),
            )
            drawArc(
                color = FDR_PROGRESS,
                startAngle = -90f, sweepAngle = 360f * progress, useCenter = false,
                topLeft = Offset(cx - ringR, cy - ringR),
                size = Size(ringR * 2, ringR * 2),
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = stroke, cap = androidx.compose.ui.graphics.StrokeCap.Round),
            )
        }

        gaugeTitle(tm, "FLIGHT RECORDER", cx, cy, r)

        // Parameter list with availability dots, centred as a block within the circle.
        data class Row(val label: String, val ok: Boolean)
        val rows = listOf(
            Row("Position GPS", status.hasGps && hasFix),
            Row("Accélération", status.hasAccel),
            Row("Altitude", status.hasBaro || (status.hasGps && hasFix)),
            Row("Inclinaison", status.hasGyro),
            Row("Baromètre", status.hasBaro),
        )
        val labelSp = (r * 0.095f).coerceIn(8f, 12f)
        val labelStyle = TextStyle(color = GaugeColors.Mark, fontSize = labelSp.sp)
        val dotR = r * 0.045f
        val gap = r * 0.09f
        val widest = rows.maxOf { tm.measure(it.label, labelStyle).size.width }
        val blockW = dotR * 2 + gap + widest
        val blockLeft = cx - blockW / 2f
        val dotX = blockLeft + dotR
        val textX = blockLeft + dotR * 2 + gap
        val step = r * 0.185f
        val listTop = cy - r * 0.50f   // starts higher now that badge is in the lobe
        rows.forEachIndexed { i, row ->
            val ry = listTop + i * step
            drawCircle(if (row.ok) FDR_GREEN else FDR_ORANGE, radius = dotR, center = Offset(dotX, ry))
            gaugeTextLeft(tm, row.label, textX, ry, sizeSp = labelSp, color = GaugeColors.Mark)
        }

        // Status lobe at the bottom — ellipse centrale seule (pas de lobes latéraux).
        val recording = status.recording
        val badgeColor = if (recording) FDR_GREEN else FDR_ORANGE
        val badgeText = if (recording) "Recording" else "Paused"
        val badgeSp = (r * 0.145f).coerceIn(13f, 20f)
        val badgeMeasured = tm.measure(badgeText, TextStyle(color = badgeColor, fontSize = badgeSp.sp, fontWeight = FontWeight.Bold))
        val padH = r * 0.08f; val padV = r * 0.05f
        val cW = 1.6f * r
        val cH = (badgeMeasured.size.height + padV * 2f).coerceAtLeast(r * 0.28f)
        val cCentreY = cy + r + r * 0.06f - cH / 2f
        val chipBg = Color(0xE8101418); val chipBorder = Color(0x99FFFFFF)
        drawOval(chipBg,     topLeft = androidx.compose.ui.geometry.Offset(cx - cW / 2f, cCentreY - cH / 2f), size = Size(cW, cH))
        drawOval(chipBorder, topLeft = androidx.compose.ui.geometry.Offset(cx - cW / 2f, cCentreY - cH / 2f), size = Size(cW, cH),
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = (r * 0.013f).coerceAtLeast(1.2f)))
        drawText(badgeMeasured, topLeft = androidx.compose.ui.geometry.Offset(cx - badgeMeasured.size.width / 2f, cCentreY - badgeMeasured.size.height / 2f))
    }

    if (showExport) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showExport = false },
            title = { androidx.compose.material3.Text("Exporter l'enregistrement") },
            text = {
                androidx.compose.foundation.layout.Column {
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

/** Like [gaugeText] but left-anchored at (x,y) vertical-centre. */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.gaugeTextLeft(
    tm: androidx.compose.ui.text.TextMeasurer,
    text: String,
    x: Float,
    y: Float,
    sizeSp: Float,
    color: Color,
) {
    val m = tm.measure(
        text,
        androidx.compose.ui.text.TextStyle(color = color, fontSize = sizeSp.sp),
    )
    drawText(m, topLeft = Offset(x, y - m.size.height / 2f))
}
