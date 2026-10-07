package com.airchecklists.app.ui.efis.gauges.weather

import com.airchecklists.app.ui.efis.gauges.drawNumTitleBar
import com.airchecklists.app.ui.efis.gauges.LocalGaugeBezel
import com.airchecklists.app.ui.efis.gauges.LocalHideTitle
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.airchecklists.app.data.net.RadarFrame
import com.airchecklists.app.data.net.WindsAloft
import com.airchecklists.app.di.ServiceLocator
import com.airchecklists.app.ui.efis.gauges.compact.CompactStyle
import com.airchecklists.app.ui.efis.gauges.compact.compactText
import kotlin.math.roundToInt

private const val DEFAULT_Z = 10

/**
 * Digital weather radar (double height, half width): a full interactive map
 * (basemap + radar + METAR + SIGMET) with pan/zoom, centred on the aircraft.
 * Equivalent to the former "long-press" dialog, now the default view at zoom 10.
 */
@Composable
fun WeatherRadarDigital(modifier: Modifier = Modifier) {
    val tm = rememberTextMeasurer()
    val bezel = LocalGaugeBezel.current
    val hideTitle = LocalHideTitle.current
    val state by ServiceLocator.efisProvider.state.collectAsStateWithLifecycle()
    val prefs by ServiceLocator.preferences.preferences.collectAsStateWithLifecycle()
    val layers = prefs.wxLayers

    val hasPos = state.hasPosition
    val lat = state.latitude
    val lon = state.longitude
    val headingDeg = state.headingDeg

    var zoom by remember { mutableIntStateOf(DEFAULT_Z) }
    var panX by remember { mutableFloatStateOf(0f) }
    var panY by remember { mutableFloatStateOf(0f) }
    var pinchAccum by remember { mutableFloatStateOf(1f) }
    var showLayers by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<WxSelection?>(null) }

    val frame by produceState<RadarFrame?>(null) { value = client.latestRadarFrame() }
    val winds by produceState<WindsAloft?>(null, hasPos, (lat * 50).roundToInt(), (lon * 50).roundToInt()) {
        value = if (hasPos) client.windsAloftFL20(lat, lon) else null
    }
    val radar by produceState<RadarBitmaps?>(null, frame, zoom) {
        val f = frame
        value = if (f != null) loadRadar(f, lat, lon, zoom, span = 2) else null
    }
    val basemap by produceState<RadarBitmaps?>(null, zoom) {
        value = loadBasemap(lat, lon, zoom, span = 2)
    }
    val latKey = (lat * 4).roundToInt()
    val lonKey = (lon * 4).roundToInt()
    val metars by produceState(emptyList<com.airchecklists.app.data.model.MetarPoint>(), latKey, lonKey) {
        value = ServiceLocator.aviationWeatherClient.metarsInBbox(lat - 3.0, lon - 3.0, lat + 3.0, lon + 3.0)
    }
    val sigmets by produceState(emptyList<com.airchecklists.app.data.model.SigmetArea>()) {
        value = ServiceLocator.aviationWeatherClient.sigmets()
    }

    Box(modifier = modifier.fillMaxSize()) {
        Canvas(
            modifier = Modifier.fillMaxSize()
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, gestureZoom, _ ->
                        panX += pan.x
                        panY += pan.y
                        pinchAccum *= gestureZoom
                        when {
                            pinchAccum > 1.5f && zoom < MAX_Z -> { zoom++; pinchAccum = 1f; panX /= 2f; panY /= 2f }
                            pinchAccum < 1f / 1.5f && zoom > MIN_Z -> { zoom--; pinchAccum = 1f; panX *= 2f; panY *= 2f }
                        }
                    }
                }
                .pointerInput(metars, sigmets, zoom, layers) {
                    detectTapGestures { pos ->
                        val cx = size.width / 2f
                        val cy = size.height / 2f
                        val shipX = cx + panX; val shipY = cy + panY
                        fun proj(la: Double, lo: Double) = wxProject(la, lo, lat, lon, shipX, shipY, zoom)
                        val hitMetar = if (layers.metar) metars.minByOrNull {
                            val o = proj(it.lat, it.lon)
                            (o.x - pos.x) * (o.x - pos.x) + (o.y - pos.y) * (o.y - pos.y)
                        }?.takeIf {
                            val o = proj(it.lat, it.lon)
                            (o.x - pos.x) * (o.x - pos.x) + (o.y - pos.y) * (o.y - pos.y) < 40f * 40f
                        } else null
                        selected = when {
                            hitMetar != null -> WxSelection.Metar(hitMetar)
                            layers.sigmet -> sigmets.firstOrNull {
                                pointInSigmet(pos.x, pos.y, it) { la, lo -> proj(la, lo) }
                            }?.let { WxSelection.Sigmet(it) }
                            else -> null
                        }
                    }
                },
        ) {
            val w = size.width
            val h = size.height
            val headerH = if (hideTitle) 0f else 20.dp.toPx().coerceAtMost(h * 0.2f)
            drawRect(Color(0xFF0A0A0A), size = size)
            if (headerH > 0f) drawNumTitleBar(bezel, w, headerH)

            if (!hasPos) {
                if (headerH > 0f) compactText(tm, "RADAR METEO", w / 2f, headerH / 2f, sizeSp = 12f, color = CompactStyle.Dim)
                compactText(tm, "position GPS ?", w / 2f, headerH + (h - headerH) / 2f, sizeSp = 12f, color = CompactStyle.Dim)
                return@Canvas
            }

            val mapTop = headerH
            val cx = w / 2f
            val cy = mapTop + (h - mapTop) / 2f
            val shipX = cx + panX
            val shipY = cy + panY
            fun proj(la: Double, lo: Double) = wxProject(la, lo, lat, lon, shipX, shipY, zoom)

            val clip = Path().apply { addRect(Rect(0f, mapTop, w, h)) }
            clipPath(clip) {
                drawBasemapLayer(basemap, cx, cy, panX, panY, zoom)
                if (layers.radar) {
                    drawRadarLayer(radar, cx, cy, maxOf(w, h), lat, lon, zoom, panX, panY, emptyList(), tm,
                        labelTerrains = false, maxTerrains = 0)
                }
                if (layers.sigmet) drawSigmets(sigmets) { la, lo -> proj(la, lo) }
                if (layers.metar) drawMetars(metars, { la, lo -> proj(la, lo) }, tm)
            }
            drawTrackAhead(shipX, shipY, headingDeg, length = minOf(w, h - mapTop) * 0.5f)
            rotate(headingDeg, pivot = Offset(shipX, shipY)) {
                drawPlaneMarker(shipX, shipY, size = 48f)
            }
            if (radar == null) compactText(tm, "radar…", cx, cy, sizeSp = 11f, color = CompactStyle.Dim)

            if (headerH > 0f) {
                compactText(tm, "RADAR METEO", cx, headerH / 2f, sizeSp = 12f, color = CompactStyle.Dim)
            }
        }

        // Header overlay: vent FL20 + bouton calques, en haut de la carte.
        val headerTopPad = if (hideTitle) 0.dp else 20.dp
        WeatherDialogHeader(
            winds = winds,
            onLayers = { showLayers = true },
            modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth().padding(top = headerTopPad),
            applyStatusBarPadding = false,
        )

        // Zoom controls — right edge, vertically centred.
        Column(
            modifier = Modifier.align(Alignment.CenterEnd).padding(end = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            WxZoomButton("+", enabled = zoom < MAX_Z) { if (zoom < MAX_Z) { zoom++; panX /= 2f; panY /= 2f } }
            Surface(color = Color(0xCC14171C), shape = RoundedCornerShape(8.dp),
                modifier = Modifier.padding(vertical = 4.dp)) {
                Text("Z$zoom", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
            }
            WxZoomButton("−", enabled = zoom > MIN_Z) { if (zoom > MIN_Z) { zoom--; panX *= 2f; panY *= 2f } }
        }

        // Tap METAR/SIGMET detail panel.
        selected?.let { sel ->
            when (sel) {
                is WxSelection.Metar -> WxTerrainDetailDialog(
                    icao = sel.point.icao,
                    onDismiss = { selected = null },
                )
                is WxSelection.Sigmet -> WxDetailPanel(
                    selection = sel,
                    onDismiss = { selected = null },
                    modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(12.dp),
                )
            }
        }
    }

    if (showLayers) {
        WxLayerDialog(
            current = layers,
            onDismiss = { showLayers = false },
            onApply = { ServiceLocator.setWxLayers(it); showLayers = false },
        )
    }
}
