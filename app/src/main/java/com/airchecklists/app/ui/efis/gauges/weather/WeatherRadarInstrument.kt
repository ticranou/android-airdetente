package com.airchecklists.app.ui.efis.gauges.weather

import android.graphics.BitmapFactory
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.airchecklists.app.data.model.VacChart
import com.airchecklists.app.data.net.RadarFrame
import com.airchecklists.app.data.net.WeatherRadarClient
import com.airchecklists.app.data.net.WindsAloft
import com.airchecklists.app.di.ServiceLocator
import com.airchecklists.app.ui.efis.gauges.compact.CompactStyle
import com.airchecklists.app.ui.efis.gauges.compact.compactText
import com.airchecklists.app.ui.efis.gauges.compact.drawGestureHints
import com.airchecklists.app.ui.efis.gauges.gaugeFace
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URL
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.tan

internal val client = WeatherRadarClient()

internal const val TILE = 256
internal const val MIN_Z = 5
internal const val MAX_Z = 11
/** RainViewer's public radar tiles are only served up to this zoom; above it the
 *  server returns a "Zoom Level Not Supported" image. We fetch tiles capped at
 *  this level and magnify them graphically for higher view zooms. */
internal const val MAX_TILE_Z = 7
/** Dial zoom (view). */
internal const val DIAL_Z = 7
/** Dialog starts here, then the user zooms/pans freely. */
internal const val DIALOG_Z0 = 8

/** Composited radar view: a block of tiles around the ship, plus the pixel offset
 *  (within the centre tile) of the ship's exact position. [viewZ] is the requested
 *  zoom; [tileZ] is the zoom actually fetched (capped at MAX_TILE_Z); [scale] is
 *  the graphical magnification applied so viewZ tiles look right (2^(viewZ-tileZ)). */
internal data class RadarBitmaps(
    val viewZ: Int,
    val tileZ: Int,
    val scale: Float,
    val tiles: List<TileImage>,
    val shipPxX: Float,   // ship X within the centre tile, at tileZ (0..TILE)
    val shipPxY: Float,
)
internal data class TileImage(val dx: Int, val dy: Int, val image: ImageBitmap)

/**
 * Weather instrument (analog / circular face, same style as Chrono / Rebours):
 * a precipitation radar centred on the aircraft position, the winds aloft at FL20
 * (≈ 950 hPa), and nearby aerodromes overlaid on the radar. Radar tiles from
 * RainViewer, winds from Open-Meteo (both free, keyless).
 *
 * Double-tap opens a large, pannable/zoomable map dialog. Needs a GPS fix and
 * network; degrades gracefully otherwise.
 */
@Composable
fun WeatherRadarInstrument(modifier: Modifier = Modifier) {
    val tm = rememberTextMeasurer()
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
        value = if (f != null && hasPos) loadRadar(f, lat, lon, DIAL_Z, span = 1) else null
    }

    Canvas(
        modifier = modifier.fillMaxSize().pointerInput(Unit) {
            detectTapGestures(onLongPress = { if (hasPos) showDialog = true })
        },
    ) {
        val (cx, cy, r) = gaugeFace()
        if (!hasPos) {
            compactText(tm, "METEO", cx, cy - r * 0.78f, sizeSp = 12f, color = CompactStyle.Dim)
            compactText(tm, "position GPS ?", cx, cy, sizeSp = 13f, color = CompactStyle.Dim)
            return@Canvas
        }
        val faceR = r * 0.92f
        val clip = Path().apply { addOval(Rect(cx - faceR, cy - faceR, cx + faceR, cy + faceR)) }
        clipPath(clip) {
            drawRadarLayer(radar, cx, cy, faceR, lat, lon, DIAL_Z, 0f, 0f, charts, tm, labelTerrains = false, maxTerrains = 0)
        }
        if (radar == null) compactText(tm, "radar…", cx, cy + r * 0.4f, sizeSp = 11f, color = CompactStyle.Dim)
        drawShip(cx, cy, r)
        drawWinds(tm, winds, cx, cy, r, arrowWide = true)
        // Title drawn LAST so the radar tiles never cover it.
        compactText(tm, "METEO", cx, cy - r * 0.78f, sizeSp = 12f, color = CompactStyle.Dim)
        drawGestureHints(cx - r * 0.98f, cy - r * 0.98f, hasLongPress = true, hasDoubleTap = false)
    }

    if (showDialog) {
        WeatherMapDialog(
            lat = lat, lon = lon, frame = frame, winds = winds, charts = charts,
            onDismiss = { showDialog = false },
        )
    }
}

/** Full-screen weather map: larger view with pinch-to-zoom and pan. */
@Composable
internal fun WeatherMapDialog(
    lat: Double,
    lon: Double,
    frame: RadarFrame?,
    winds: WindsAloft?,
    charts: List<VacChart>,
    onDismiss: () -> Unit,
) {
    val tm = rememberTextMeasurer()
    val efisState by ServiceLocator.efisProvider.state.collectAsStateWithLifecycle()
    val headingDeg = efisState.headingDeg
    val prefs by ServiceLocator.preferences.preferences.collectAsStateWithLifecycle()
    val layers = prefs.wxLayers
    var zoom by remember { mutableIntStateOf(DIALOG_Z0) }
    var panX by remember { mutableFloatStateOf(0f) }
    var panY by remember { mutableFloatStateOf(0f) }
    var pinchAccum by remember { mutableFloatStateOf(1f) }
    var showLayers by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<WxSelection?>(null) }

    // Wider tile block (5×5) so panning has content around the edges.
    val radar by produceState<RadarBitmaps?>(null, frame, zoom) {
        val f = frame
        value = if (f != null) loadRadar(f, lat, lon, zoom, span = 2) else null
    }
    // Dark basemap tiles under the radar (coastlines/cities) for localisation.
    val basemap by produceState<RadarBitmaps?>(null, zoom) {
        value = loadBasemap(lat, lon, zoom, span = 2)
    }
    // METAR points around the ship: refresh when the map moves a "cell" or zooms.
    val latKey = (lat * 4).roundToInt()
    val lonKey = (lon * 4).roundToInt()
    val metars by produceState(emptyList<com.airchecklists.app.data.model.MetarPoint>(), latKey, lonKey) {
        // ~3° box around the ship — wide enough to cover the visible area at the
        // default zoom (e.g. Dinard LFRD, ~2° west of Argentan).
        value = ServiceLocator.aviationWeatherClient.metarsInBbox(lat - 3.0, lon - 3.0, lat + 3.0, lon + 3.0)
    }
    // SIGMET areas (global feed); we draw only those intersecting the view.
    val sigmets by produceState(emptyList<com.airchecklists.app.data.model.SigmetArea>()) {
        value = ServiceLocator.aviationWeatherClient.sigmets()
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            modifier = Modifier.fillMaxSize().background(Color(0xFF0A0A0A)),
        ) {
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
                            val cx = size.width / 2f; val cy = size.height / 2f
                            val shipX = cx + panX; val shipY = cy + panY
                            fun proj(la: Double, lo: Double) = wxProject(la, lo, lat, lon, shipX, shipY, zoom)
                            // METAR dots first (small targets), then SIGMET areas.
                            val hitMetar = if (layers.metar) metars.minByOrNull {
                                val o = proj(it.lat, it.lon); (o.x - pos.x) * (o.x - pos.x) + (o.y - pos.y) * (o.y - pos.y)
                            }?.takeIf {
                                val o = proj(it.lat, it.lon); (o.x - pos.x) * (o.x - pos.x) + (o.y - pos.y) * (o.y - pos.y) < 40f * 40f
                            } else null
                            selected = when {
                                hitMetar != null -> WxSelection.Metar(hitMetar)
                                layers.sigmet -> sigmets.firstOrNull { pointInSigmet(pos.x, pos.y, it) { la, lo -> proj(la, lo) } }
                                    ?.let { WxSelection.Sigmet(it) }
                                else -> null
                            }
                        }
                    },
            ) {
                val cx = size.width / 2f
                val cy = size.height / 2f
                val faceR = minOf(size.width, size.height) / 2f - 8f
                val shipX = cx + panX; val shipY = cy + panY
                fun proj(la: Double, lo: Double) = wxProject(la, lo, lat, lon, shipX, shipY, zoom)
                val clip = Path().apply { addRect(Rect(0f, 0f, size.width, size.height)) }
                clipPath(clip) {
                    // Dark basemap under everything for geographic context.
                    drawBasemapLayer(basemap, cx, cy, panX, panY, zoom)
                    if (layers.radar) {
                        // Radar tiles only — terrain markers are replaced by the METAR
                        // layer (coloured by flight category), so pass no charts here.
                        drawRadarLayer(radar, cx, cy, faceR, lat, lon, zoom, panX, panY, emptyList(), tm, labelTerrains = false, maxTerrains = 0)
                    }
                    if (layers.sigmet) drawSigmets(sigmets) { la, lo -> proj(la, lo) }
                    if (layers.metar) drawMetars(metars, { la, lo -> proj(la, lo) }, tm)
                }
                // Own-ship + future-track.
                drawTrackAhead(shipX, shipY, headingDeg, length = minOf(size.width, size.height) * 0.5f)
                rotate(headingDeg, pivot = Offset(shipX, shipY)) {
                    drawPlaneMarker(shipX, shipY, size = 64f)
                }
            }
            // Clean top header: centred title, data sources on the left, and the
            // FL20 wind (direction / speed + arrow) on the right.
            WeatherDialogHeader(
                winds = winds,
                onLayers = { showLayers = true },
                modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth(),
            )
            // Zoom controls (+ / current level / −), right-centre.
            Column(
                modifier = Modifier.align(Alignment.CenterEnd).padding(end = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                WxZoomButton("+", enabled = zoom < MAX_Z) { if (zoom < MAX_Z) { zoom++; panX /= 2f; panY /= 2f } }
                Surface(color = Color(0xCC14171C), shape = RoundedCornerShape(8.dp), modifier = Modifier.padding(vertical = 6.dp)) {
                    Text("Z$zoom", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                }
                WxZoomButton("−", enabled = zoom > MIN_Z) { if (zoom > MIN_Z) { zoom--; panX *= 2f; panY *= 2f } }
            }
            // Tapped-feature detail panel (METAR/TAF or SIGMET).
            selected?.let { sel ->
                WxDetailPanel(
                    selection = sel,
                    onDismiss = { selected = null },
                    modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(12.dp),
                )
            }
            if (selected == null) {
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(8.dp),
                ) {
                    Text("Fermer")
                }
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

/** What the user tapped on the weather map. */
internal sealed interface WxSelection {
    data class Metar(val point: com.airchecklists.app.data.model.MetarPoint) : WxSelection
    data class Sigmet(val area: com.airchecklists.app.data.model.SigmetArea) : WxSelection
}

/** Bottom detail panel for a tapped METAR (raw + TAF fetched on demand) or SIGMET. */
@Composable
private fun WxDetailPanel(selection: WxSelection, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = Color(0xFF14171C),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, Color(0xFF2A2E35)),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            when (selection) {
                is WxSelection.Metar -> {
                    val m = selection.point
                    Text("${m.icao} · ${m.flightCategory}", color = fltCatColor(m.flightCategory), fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text(m.rawOb, color = Color(0xFFE0E0E0), fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
                    // TAF on demand.
                    val taf by produceState<String?>(null, m.icao) {
                        value = runCatching { ServiceLocator.weatherClient.fetch(m.icao).taf?.raw }.getOrNull()
                    }
                    taf?.let {
                        Text("TAF", color = Color(0xFF9AA0A6), fontSize = 11.sp, modifier = Modifier.padding(top = 8.dp))
                        Text(it, color = Color(0xFFCFD3D8), fontSize = 12.sp)
                    }
                }
                is WxSelection.Sigmet -> {
                    val s = selection.area
                    Text("SIGMET ${s.hazard}", color = sigmetColor(s.hazard), fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    val sub = buildString {
                        if (s.firName.isNotBlank()) append(s.firName)
                        if (s.topFl != null) { if (isNotEmpty()) append(" · "); append("Sommet FL${s.topFl / 100}") }
                    }
                    if (sub.isNotBlank()) Text(sub, color = Color(0xFF9AA0A6), fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp))
                    Text(s.raw, color = Color(0xFFE0E0E0), fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
                }
            }
            TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) { Text("Fermer") }
        }
    }
}

/** Checkbox dialog for the weather overlay layers. */
@Composable
private fun WxLayerDialog(
    current: com.airchecklists.app.data.model.WxLayerPrefs,
    onDismiss: () -> Unit,
    onApply: (com.airchecklists.app.data.model.WxLayerPrefs) -> Unit,
) {
    var draft by remember { mutableStateOf(current) }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Calques météo") },
        text = {
            Column {
                WxLayerRow("Radar (pluie)", draft.radar) { draft = draft.copy(radar = it) }
                WxLayerRow("METAR / TAF", draft.metar) { draft = draft.copy(metar = it) }
                WxLayerRow("SIGMET", draft.sigmet) { draft = draft.copy(sigmet = it) }
            }
        },
        confirmButton = { TextButton(onClick = { onApply(draft) }) { Text("Appliquer") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } },
    )
}

@Composable
private fun WxLayerRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    androidx.compose.foundation.layout.Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
    ) {
        androidx.compose.material3.Checkbox(checked = checked, onCheckedChange = onCheckedChange)
        Text(label, modifier = Modifier.padding(start = 8.dp))
    }
}

@Composable
private fun WxZoomButton(label: String, enabled: Boolean, onClick: () -> Unit) {
    Surface(
        color = Color(0xCC14171C),
        shape = androidx.compose.foundation.shape.CircleShape,
        border = BorderStroke(1.dp, Color(0x33FFFFFF)),
        modifier = Modifier.size(44.dp),
    ) {
        androidx.compose.material3.IconButton(onClick = onClick, enabled = enabled) {
            Text(label, color = if (enabled) Color.White else Color(0xFF666666), fontSize = 24.sp, fontWeight = FontWeight.Bold)
        }
    }
}

/** Two-level header for the weather dialog, matching the reference mock:
 *  a top row with the centred "METEO" title, then a second row with the data
 *  sources on the left and the FL20 wind (direction/speed + arrow → FL20) on the
 *  right. Sits below the system status bar. */
@Composable
private fun WeatherDialogHeader(winds: WindsAloft?, onLayers: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier = modifier.background(Color(0xFF0C0C0C)).androidStatusBarPadding()) {
        // Row 1 — centred title + a layers button on the right.
        androidx.compose.foundation.layout.Box(modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 2.dp)) {
            Text(
                "METEO",
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
            )
            androidx.compose.material3.IconButton(
                onClick = onLayers,
                modifier = Modifier.align(Alignment.CenterEnd).padding(end = 8.dp),
            ) {
                androidx.compose.material3.Icon(
                    Icons.Filled.Layers,
                    contentDescription = "Calques",
                    tint = Color.White,
                )
            }
        }
        // Row 2 — sources (left) + wind (right).
        androidx.compose.foundation.layout.Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 2.dp),
        ) {
            Column(modifier = Modifier.align(Alignment.CenterStart)) {
                Text("Radar : RainViewer", color = Color(0xFF9AA0A6), fontSize = 10.sp, lineHeight = 12.sp)
                Text("METAR/SIGMET : AviationWeather", color = Color(0xFF9AA0A6), fontSize = 10.sp, lineHeight = 12.sp)
                Text("Vent : Open-Meteo", color = Color(0xFF9AA0A6), fontSize = 10.sp, lineHeight = 12.sp)
            }
            androidx.compose.foundation.layout.Row(
                modifier = Modifier.align(Alignment.CenterEnd),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (winds == null) {
                    Text("vent —", color = Color(0xFF9AA0A6), fontSize = 13.sp)
                } else {
                    Column(horizontalAlignment = Alignment.End) {
                        Text("%03d°".format(winds.directionDeg), color = Color(0xFFE8843A), fontSize = 17.sp, fontWeight = FontWeight.Bold)
                        Text("%dkt".format(winds.speedKt), color = Color(0xFFE8843A), fontSize = 17.sp, fontWeight = FontWeight.Bold)
                    }
                    androidx.compose.foundation.layout.Spacer(Modifier.size(8.dp))
                    // Slim diagonal wind arrow pointing where the wind blows TO.
                    Canvas(modifier = Modifier.size(width = 48.dp, height = 30.dp)) {
                        rotate(winds.directionDeg + 180f, pivot = center) {
                            val cxp = size.width / 2f
                            val topY = size.height * 0.10f
                            val botY = size.height * 0.90f
                            val orange = Color(0xFFE8843A)
                            drawLine(orange, Offset(cxp, botY), Offset(cxp, topY + size.height * 0.24f), strokeWidth = 5f)
                            val head = Path().apply {
                                moveTo(cxp, topY)
                                lineTo(cxp - size.width * 0.16f, topY + size.height * 0.26f)
                                lineTo(cxp + size.width * 0.16f, topY + size.height * 0.26f)
                                close()
                            }
                            drawPath(head, orange)
                        }
                    }
                    androidx.compose.foundation.layout.Spacer(Modifier.size(6.dp))
                    Text("FL20", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        androidx.compose.material3.HorizontalDivider(color = Color(0xFF23262B), thickness = 1.dp)
    }
}

/** Status-bar top padding (edge-to-edge dialogs draw under the system bar). */
@Composable
private fun Modifier.androidStatusBarPadding(): Modifier =
    this.padding(top = androidx.compose.foundation.layout.WindowInsets.statusBars
        .asPaddingValues().calculateTopPadding())

// ---- shared drawing ----

/** Draws the radar tiles + nearby terrains, centred on (cx,cy) with a pan offset. */
internal fun DrawScope.drawRadarLayer(
    radar: RadarBitmaps?,
    cx: Float, cy: Float, faceR: Float,
    lat: Double, lon: Double, zoom: Int,
    panX: Float, panY: Float,
    charts: List<VacChart>,
    tm: TextMeasurer,
    labelTerrains: Boolean,
    maxTerrains: Int,
) {
    val shipX = cx + panX
    val shipY = cy + panY
    if (radar != null && radar.viewZ == zoom) {
        val ts = TILE * radar.scale
        radar.tiles.forEach { t ->
            val left = shipX - radar.shipPxX * radar.scale + t.dx * ts
            val top = shipY - radar.shipPxY * radar.scale + t.dy * ts
            drawImage(
                image = t.image,
                dstOffset = IntOffset(left.roundToInt(), top.roundToInt()),
                dstSize = IntSize(ts.roundToInt(), ts.roundToInt()),
            )
        }
    }
    // Terrains projected with the same web-mercator math as the tiles, at the VIEW
    // zoom (which equals tileZ*scale), so they line up with the magnified radar.
    // Keep only the [maxTerrains] closest to the ship so the dial isn't cluttered.
    val n = (1 shl zoom).toDouble()
    val shipTileX = (lon + 180.0) / 360.0 * n * TILE
    val shipLatRad = Math.toRadians(lat)
    val shipTileY = (1.0 - ln(tan(shipLatRad) + 1.0 / cos(shipLatRad)) / PI) / 2.0 * n * TILE
    charts
        .filter { it.latitude != null && it.longitude != null }
        .sortedBy { c ->
            val dLat = (c.latitude!! - lat); val dLon = (c.longitude!! - lon)
            dLat * dLat + dLon * dLon
        }
        .take(maxTerrains)
        .forEach { c ->
            val clat = c.latitude!!; val clon = c.longitude!!
            val tX = (clon + 180.0) / 360.0 * n * TILE
            val tLatRad = Math.toRadians(clat)
            val tY = (1.0 - ln(tan(tLatRad) + 1.0 / cos(tLatRad)) / PI) / 2.0 * n * TILE
            val px = shipX + (tX - shipTileX).toFloat()
            val py = shipY + (tY - shipTileY).toFloat()
            val dx = px - cx; val dy = py - cy
            if (dx * dx + dy * dy > faceR * faceR) return@forEach
            drawCircle(Color(0xFF64B5F6), radius = if (labelTerrains) 6f else 4f, center = Offset(px, py))
            compactText(tm, c.icao, px, py - (if (labelTerrains) 14f else 10f), sizeSp = if (labelTerrains) 12f else 9f, bold = true, color = Color(0xFFBBDEFB))
        }
}

private fun DrawScope.drawShip(cx: Float, cy: Float, r: Float) {
    val ship = Path().apply {
        moveTo(cx, cy - r * 0.12f)
        lineTo(cx - r * 0.08f, cy + r * 0.08f)
        lineTo(cx + r * 0.08f, cy + r * 0.08f)
        close()
    }
    drawPath(ship, CompactStyle.Accent)
}

/** Large, high-contrast own-ship marker for the dialog (absolute [half] size). */
internal fun DrawScope.drawShipMarker(cx: Float, cy: Float, half: Float) {
    val ship = Path().apply {
        moveTo(cx, cy - half)
        lineTo(cx - half * 0.7f, cy + half * 0.7f)
        lineTo(cx, cy + half * 0.35f)
        lineTo(cx + half * 0.7f, cy + half * 0.7f)
        close()
    }
    drawPath(ship, CompactStyle.Accent)
    // White outline so it stays visible over dense radar.
    drawPath(ship, Color.White, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3f))
}

/** Top-down plane silhouette (nose up), centred at (cx,cy). Caller rotates to heading. */
internal fun DrawScope.drawPlaneMarker(
    cx: Float,
    cy: Float,
    size: Float,
    fill: Color = CompactStyle.Accent,
    outline: Color = Color.White,
) {
    val s = size / 2f
    val plane = Path().apply {
        moveTo(cx, cy - s)                       // nose
        lineTo(cx - s * 0.16f, cy - s * 0.30f)
        lineTo(cx - s * 0.95f, cy + s * 0.10f)   // left wing tip
        lineTo(cx - s * 0.16f, cy + s * 0.25f)
        lineTo(cx - s * 0.16f, cy + s * 0.70f)
        lineTo(cx - s * 0.40f, cy + s * 0.95f)   // left tailplane
        lineTo(cx - s * 0.10f, cy + s * 0.80f)
        lineTo(cx, cy + s * 0.88f)
        lineTo(cx + s * 0.10f, cy + s * 0.80f)
        lineTo(cx + s * 0.40f, cy + s * 0.95f)   // right tailplane
        lineTo(cx + s * 0.16f, cy + s * 0.70f)
        lineTo(cx + s * 0.16f, cy + s * 0.25f)
        lineTo(cx + s * 0.95f, cy + s * 0.10f)   // right wing tip
        lineTo(cx + s * 0.16f, cy - s * 0.30f)
        close()
    }
    drawPath(plane, fill)
    drawPath(plane, outline, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.5f))
}

/** Dashed line ahead of the ship along [headingDeg] (0° = up, clockwise). */
internal fun DrawScope.drawTrackAhead(cx: Float, cy: Float, headingDeg: Float, length: Float) {
    val rad = Math.toRadians(headingDeg.toDouble())
    val dx = kotlin.math.sin(rad).toFloat()
    val dy = -kotlin.math.cos(rad).toFloat()
    val end = Offset(cx + dx * length, cy + dy * length)
    drawLine(
        color = CompactStyle.Accent,
        start = Offset(cx, cy),
        end = end,
        strokeWidth = 3f,
        pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(16f, 12f), 0f),
    )
}

private fun DrawScope.drawWinds(tm: TextMeasurer, winds: WindsAloft?, cx: Float, cy: Float, r: Float, arrowWide: Boolean) {
    if (winds != null) {
        val wx = cx + r * 0.42f
        val wy = cy - r * 0.30f
        drawWindArrow(wx, wy, r * 0.16f, winds.directionDeg)
        compactText(tm, "FL20", wx, wy - r * 0.24f, sizeSp = 10f, color = CompactStyle.Dim)
        compactText(tm, "%03d°/%dkt".format(winds.directionDeg, winds.speedKt), cx, cy + r * 0.72f, sizeSp = 13f, bold = true, color = CompactStyle.Accent2)
    } else {
        compactText(tm, "vent FL20 —", cx, cy + r * 0.72f, sizeSp = 12f, color = CompactStyle.Dim)
    }
}

/** A small arrow showing where the wind blows FROM (meteorological convention). */
/** A clean red wind arrow centred on (x,y): straight shaft + filled triangular
 *  head, pointing where the wind blows TO (fromDeg + 180); 0° = up, clockwise.
 *  [len] is the half-length (tip is len from centre). */
private fun DrawScope.drawWindArrow(x: Float, y: Float, len: Float, fromDeg: Int) {
    val red = Color(0xFFFF3B30)
    val toRad = Math.toRadians((fromDeg + 180).toDouble())
    val ux = sin(toRad).toFloat()          // unit vector toward the tip
    val uy = -cos(toRad).toFloat()
    val px = -uy                            // perpendicular
    val py = ux
    val tail = Offset(x - ux * len, y - uy * len)
    val tip = Offset(x + ux * len, y + uy * len)
    // Shaft (stops a bit short of the tip so the head sits cleanly on top).
    val neck = Offset(tip.x - ux * (len * 0.6f), tip.y - uy * (len * 0.6f))
    drawLine(red, tail, neck, strokeWidth = 6f)
    // Filled triangular head.
    val hw = len * 0.42f
    val head = Path().apply {
        moveTo(tip.x, tip.y)
        lineTo(neck.x + px * hw, neck.y + py * hw)
        lineTo(neck.x - px * hw, neck.y - py * hw)
        close()
    }
    drawPath(head, red)
}

/** Wind widget for the dialog. Everything is laid out relative to a single card
 *  rect so pieces never overlap: a clean filled red arrow in the top zone, then
 *  FL20 / direction° / speed kt stacked on three rows. [cx],[cy] = card centre-top
 *  anchor (top edge at cy). */
// ---- radar tile math + loading ----

/** Downloads the (2*span+1)² radar tiles around (lat,lon) for a requested view
 *  zoom [viewZ]. Tiles are fetched at min(viewZ, MAX_TILE_Z) — RainViewer serves
 *  radar only up to MAX_TILE_Z — and magnified graphically for higher view zooms. */
internal suspend fun loadRadar(frame: RadarFrame, lat: Double, lon: Double, viewZ: Int, span: Int): RadarBitmaps? = withContext(Dispatchers.IO) {
    runCatching {
        val tileZ = minOf(viewZ, MAX_TILE_Z)
        val scale = (1 shl (viewZ - tileZ)).toFloat()
        val n = 1 shl tileZ
        val xF = (lon + 180.0) / 360.0 * n
        val latRad = Math.toRadians(lat)
        val yF = (1.0 - ln(tan(latRad) + 1.0 / cos(latRad)) / PI) / 2.0 * n
        val xC = floor(xF).toInt()
        val yC = floor(yF).toInt()
        val shipPxX = ((xF - xC) * TILE).toFloat()
        val shipPxY = ((yF - yC) * TILE).toFloat()

        val tiles = mutableListOf<TileImage>()
        for (dy in -span..span) for (dx in -span..span) {
            val tx = ((xC + dx) % n + n) % n
            val ty = yC + dy
            if (ty < 0 || ty >= n) continue
            val url = frame.tileUrl(tileZ, tx, ty, size = TILE)
            val img = runCatching { downloadImage(url) }.getOrNull() ?: continue
            tiles.add(TileImage(dx, dy, img))
        }
        if (tiles.isEmpty()) null else RadarBitmaps(viewZ, tileZ, scale, tiles, shipPxX, shipPxY)
    }.getOrNull()
}

private fun downloadImage(url: String): ImageBitmap {
    URL(url).openStream().use { stream ->
        val bmp = BitmapFactory.decodeStream(stream) ?: error("decode failed")
        return bmp.asImageBitmap()
    }
}

// ---- Dark basemap tiles (CARTO), drawn UNDER the radar to help localisation ----

/** Max zoom we fetch basemap tiles at (CARTO serves higher, but this is plenty and
 *  lets higher view zooms magnify one level like the radar does). */
private const val MAX_BASE_Z = 10

/** Loads CARTO "dark_all" raster tiles around (lat,lon) for view zoom [viewZ],
 *  reusing the same block/scale model as [loadRadar]. */
internal suspend fun loadBasemap(lat: Double, lon: Double, viewZ: Int, span: Int): RadarBitmaps? = withContext(Dispatchers.IO) {
    runCatching {
        val tileZ = minOf(viewZ, MAX_BASE_Z)
        val scale = (1 shl (viewZ - tileZ)).toFloat()
        val n = 1 shl tileZ
        val xF = (lon + 180.0) / 360.0 * n
        val latRad = Math.toRadians(lat)
        val yF = (1.0 - ln(tan(latRad) + 1.0 / cos(latRad)) / PI) / 2.0 * n
        val xC = floor(xF).toInt()
        val yC = floor(yF).toInt()
        val shipPxX = ((xF - xC) * TILE).toFloat()
        val shipPxY = ((yF - yC) * TILE).toFloat()
        val tiles = mutableListOf<TileImage>()
        for (dy in -span..span) for (dx in -span..span) {
            val tx = ((xC + dx) % n + n) % n
            val ty = yC + dy
            if (ty < 0 || ty >= n) continue
            val url = "https://a.basemaps.cartocdn.com/dark_all/$tileZ/$tx/$ty.png"
            val img = runCatching { downloadImage(url) }.getOrNull() ?: continue
            tiles.add(TileImage(dx, dy, img))
        }
        if (tiles.isEmpty()) null else RadarBitmaps(viewZ, tileZ, scale, tiles, shipPxX, shipPxY)
    }.getOrNull()
}

/** Draws a pre-loaded basemap tile block centred on the ship (same math as radar). */
internal fun DrawScope.drawBasemapLayer(base: RadarBitmaps?, cx: Float, cy: Float, panX: Float, panY: Float, zoom: Int) {
    if (base == null || base.viewZ != zoom) return
    val shipX = cx + panX; val shipY = cy + panY
    val ts = TILE * base.scale
    base.tiles.forEach { t ->
        val left = shipX - base.shipPxX * base.scale + t.dx * ts
        val top = shipY - base.shipPxY * base.scale + t.dy * ts
        drawImage(
            image = t.image,
            dstOffset = IntOffset(left.roundToInt(), top.roundToInt()),
            dstSize = IntSize(ts.roundToInt(), ts.roundToInt()),
        )
    }
}

// ---- Web-mercator projection shared by the weather overlays (matches the radar tiles) ----

/** World-pixel X of [lon] at [zoom] (TILE * 2^zoom pixels around the globe). */
private fun worldX(lon: Double, zoom: Int): Double =
    (lon + 180.0) / 360.0 * (1 shl zoom).toDouble() * TILE

/** World-pixel Y of [lat] at [zoom]. */
private fun worldY(lat: Double, zoom: Int): Double {
    val r = Math.toRadians(lat)
    return (1.0 - ln(tan(r) + 1.0 / cos(r)) / PI) / 2.0 * (1 shl zoom).toDouble() * TILE
}

/** Screen offset for (lat,lon) given the ship anchor at (shipX,shipY) and [zoom]. */
private fun wxProject(lat: Double, lon: Double, shipLat: Double, shipLon: Double, shipX: Float, shipY: Float, zoom: Int): Offset {
    val px = shipX + (worldX(lon, zoom) - worldX(shipLon, zoom)).toFloat()
    val py = shipY + (worldY(lat, zoom) - worldY(shipLat, zoom)).toFloat()
    return Offset(px, py)
}

/** Inverse of [wxProject]: screen (px,py) → (lat,lon). */
private fun wxUnproject(px: Float, py: Float, shipLat: Double, shipLon: Double, shipX: Float, shipY: Float, zoom: Int): Pair<Double, Double> {
    val n = (1 shl zoom).toDouble() * TILE
    val wx = worldX(shipLon, zoom) + (px - shipX)
    val wy = worldY(shipLat, zoom) + (py - shipY)
    val lon = wx / n * 360.0 - 180.0
    val latRad = kotlin.math.atan(kotlin.math.sinh(PI * (1.0 - 2.0 * wy / n)))
    return Math.toDegrees(latRad) to lon
}

/** Flight-category dot colour (FAA convention). */
internal fun fltCatColor(cat: String): Color = when (cat.uppercase()) {
    "VFR" -> Color(0xFF2ECC40)
    "MVFR" -> Color(0xFF2196F3)
    "IFR" -> Color(0xFFE53935)
    "LIFR" -> Color(0xFFD24DEA)
    else -> Color(0xFF9AA0A6)
}

/** Hazard fill colour for SIGMET areas. */
internal fun sigmetColor(hazard: String): Color = when (hazard.uppercase()) {
    "TS", "CONVECTIVE" -> Color(0xFFFF6D00)
    "TURB" -> Color(0xFFFFB300)
    "ICE" -> Color(0xFF29B6F6)
    "MTW" -> Color(0xFFAB47BC)
    else -> Color(0xFFEF5350)
}

/** Draw SIGMET hazard polygons (filled + outlined) using [project]. */
internal fun DrawScope.drawSigmets(
    areas: List<com.airchecklists.app.data.model.SigmetArea>,
    project: (Double, Double) -> Offset,
) {
    areas.forEach { area ->
        val ring = area.rings.firstOrNull() ?: return@forEach
        val path = Path()
        ring.forEachIndexed { i, p ->
            val o = project(p[0], p[1])
            if (i == 0) path.moveTo(o.x, o.y) else path.lineTo(o.x, o.y)
        }
        path.close()
        val c = sigmetColor(area.hazard)
        drawPath(path, c.copy(alpha = 0.20f))
        drawPath(path, c, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3f))
    }
}

/** Draw METAR flight-category dots + ICAO labels using [project]. */
internal fun DrawScope.drawMetars(
    metars: List<com.airchecklists.app.data.model.MetarPoint>,
    project: (Double, Double) -> Offset,
    tm: TextMeasurer,
) {
    metars.forEach { m ->
        val o = project(m.lat, m.lon)
        drawCircle(fltCatColor(m.flightCategory), radius = 9f, center = o)
        drawCircle(Color.White, radius = 9f, center = o, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2f))
        compactText(tm, m.icao, o.x, o.y - 16f, sizeSp = 10f, bold = true, color = Color(0xFFE0E0E0))
    }
}

/** True if screen point (px,py) is inside the polygon's first ring (projected). */
internal fun pointInSigmet(px: Float, py: Float, area: com.airchecklists.app.data.model.SigmetArea, project: (Double, Double) -> Offset): Boolean {
    val ring = area.rings.firstOrNull() ?: return false
    val pts = ring.map { project(it[0], it[1]) }
    var inside = false
    var j = pts.size - 1
    for (i in pts.indices) {
        val a = pts[i]; val b = pts[j]
        if (((a.y > py) != (b.y > py)) &&
            (px < (b.x - a.x) * (py - a.y) / (b.y - a.y) + a.x)
        ) inside = !inside
        j = i
    }
    return inside
}

