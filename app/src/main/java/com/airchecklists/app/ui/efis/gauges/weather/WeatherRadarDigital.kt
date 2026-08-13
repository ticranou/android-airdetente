package com.airchecklists.app.ui.efis.gauges.weather

import com.airchecklists.app.ui.efis.gauges.drawNumTitleBar
import com.airchecklists.app.ui.efis.gauges.LocalGaugeBezel
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.airchecklists.app.data.net.RadarFrame
import com.airchecklists.app.data.net.WindsAloft
import com.airchecklists.app.di.ServiceLocator
import com.airchecklists.app.ui.efis.gauges.compact.CompactStyle
import com.airchecklists.app.ui.efis.gauges.compact.compactText
import com.airchecklists.app.ui.efis.gauges.compact.drawGestureHints
import kotlin.math.roundToInt

/**
 * Digital weather radar (double height, half width): a fixed 20dp "RADAR METEO"
 * header over a rectangular precipitation-radar map centred on the aircraft, with
 * nearby aerodromes overlaid. Double-tap opens the large pannable/zoomable dialog.
 * Reuses the same RainViewer tiles + Open-Meteo winds as the analog version.
 */
@Composable
fun WeatherRadarDigital(modifier: Modifier = Modifier) {
    val tm = rememberTextMeasurer()
    val bezel = LocalGaugeBezel.current
    val state by ServiceLocator.efisProvider.state.collectAsStateWithLifecycle()
    val charts by ServiceLocator.vacRepository.charts.collectAsStateWithLifecycle()

    val hasPos = state.hasPosition
    val lat = state.latitude
    val lon = state.longitude
    val latKey = (lat * 50).roundToInt()
    val lonKey = (lon * 50).roundToInt()

    var showDialog by remember { mutableStateOf(false) }

    val frame by produceState<RadarFrame?>(null) { value = client.latestRadarFrame() }
    val winds by produceState<WindsAloft?>(null, hasPos, latKey, lonKey) {
        value = if (hasPos) client.windsAloftFL20(lat, lon) else null
    }
    val radar by produceState<RadarBitmaps?>(null, frame, hasPos, latKey, lonKey) {
        val f = frame
        value = if (f != null && hasPos) loadRadar(f, lat, lon, DIAL_Z, span = 2) else null
    }

    Canvas(
        modifier = modifier.fillMaxSize().pointerInput(Unit) {
            detectTapGestures(onLongPress = { if (hasPos) showDialog = true })
        },
    ) {
        val w = size.width
        val h = size.height
        val headerH = 20.dp.toPx().coerceAtMost(h * 0.2f)
        drawRect(CompactStyle.Bg, size = size)
        drawNumTitleBar(bezel, w, headerH)
        drawRect(Color(0xFF3A3A3A), size = size, style = Stroke(width = 2f))

        if (!hasPos) {
            compactText(tm, "RADAR METEO", w / 2f, headerH / 2f, sizeSp = 12f, color = CompactStyle.Dim)
            compactText(tm, "position GPS ?", w / 2f, headerH + (h - headerH) / 2f, sizeSp = 12f, color = CompactStyle.Dim)
            return@Canvas
        }

        // Rectangular map area below the header.
        val mapTop = headerH
        val cx = w / 2f
        val cy = mapTop + (h - mapTop) / 2f
        val faceR = minOf(w, h - mapTop) / 2f    // used only to bound terrain culling
        val clip = Path().apply { addRect(Rect(0f, mapTop, w, h)) }
        clipPath(clip) {
            drawRadarLayer(radar, cx, cy, maxOf(w, h), lat, lon, DIAL_Z, 0f, 0f, charts, tm,
                labelTerrains = true, maxTerrains = 3)
            // Own-ship marker at the map centre.
            drawShipMarker(cx, cy, 16f)
        }
        if (radar == null) compactText(tm, "radar…", cx, cy, sizeSp = 11f, color = CompactStyle.Dim)

        // Title on top of everything.
        compactText(tm, "RADAR METEO", cx, headerH / 2f, sizeSp = 12f, color = CompactStyle.Dim)
        drawGestureHints(6f, headerH / 2f, hasLongPress = true, hasDoubleTap = false)
    }

    if (showDialog) {
        WeatherMapDialog(
            lat = lat, lon = lon, frame = frame, winds = winds, charts = charts,
            onDismiss = { showDialog = false },
        )
    }
}
