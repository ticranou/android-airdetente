package com.airchecklists.app.ui.efis.gauges.map

import com.airchecklists.app.ui.efis.gauges.drawNumTitleBar
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.DisposableEffect
import com.airchecklists.app.R
import com.airchecklists.app.data.model.EfisSpeedUnit
import com.airchecklists.app.data.model.AltitudeUnit
import com.airchecklists.app.data.model.MapLayerPrefs
import com.airchecklists.app.data.model.MapOrientation
import com.airchecklists.app.data.model.SpeedArcs
import com.airchecklists.app.data.sensors.EfisState
import com.airchecklists.app.di.ServiceLocator
import com.airchecklists.app.ui.efis.gauges.compact.CompactStyle
import com.airchecklists.app.ui.efis.gauges.compact.compactText
import com.airchecklists.app.ui.efis.gauges.compact.efisHeadingTape
import com.airchecklists.app.ui.efis.gauges.compact.speedColor
import com.airchecklists.app.ui.efis.gauges.weather.drawPlaneMarker
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.FillLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.sources.GeoJsonSource
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * In-cell moving map: an EFIS instrument header on top and the real MapLibre
 * basemap (basemap.mbtiles + OpenAIP overlays) below, following the aircraft.
 * A Compose overlay draws the GPS trail (green) + the projected route-ahead
 * (dashed). A "layers" button toggles which OpenAIP overlays are shown; the
 * choice is persisted in the app preferences.
 */
@Composable
fun MovingMap(
    state: EfisState,
    trail: List<DoubleArray>,
    unit: EfisSpeedUnit,
    arcs: SpeedArcs?,
    orientation: MapOrientation = MapOrientation.NORTH_UP,
    altUnit: AltitudeUnit = AltitudeUnit.FEET,
    modifier: Modifier = Modifier,
) {
    val tm = rememberTextMeasurer()
    val basemap = remember { ServiceLocator.mapRepository.basemapFile() }
    val prefs by ServiceLocator.preferences.preferences.collectAsStateWithLifecycle()
    val targetHeading by ServiceLocator.targetHeading.collectAsStateWithLifecycle()
    val navRoute by ServiceLocator.activeNavRoute.collectAsStateWithLifecycle()

    var showLayers by remember { mutableStateOf(false) }
    var featureInfo by remember { mutableStateOf<MapFeatureInfo?>(null) }
    // Goto target is session-scoped (survives leaving/re-entering the cockpit) so
    // the popup + magenta route persist until the user closes it explicitly.
    val activeGotoAny by ServiceLocator.activeGoto.collectAsStateWithLifecycle()
    val gotoTarget = activeGotoAny as? MapFeatureInfo
    val mapRef = remember { arrayOfNulls<MapLibreMap>(1) }
    val recenterAction = remember { arrayOfNulls<() -> Unit>(1) }
    var cameraTick by remember { mutableIntStateOf(0) }

    val mapBezel = com.airchecklists.app.ui.efis.gauges.LocalGaugeBezel.current
    Column(modifier = modifier.fillMaxSize().background(CompactStyle.Bg)) {
        // Title bar ("Carte de navigation") honouring the cell bezel colour/texture.
        Text(
            stringResource(R.string.instrument_nummap_title),
            color = CompactStyle.Dim,
            fontSize = 12.sp,
            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .drawBehind { drawNumTitleBar(mapBezel, size.width, size.height) }
                .padding(vertical = 3.dp),
        )
        Box(modifier = Modifier.fillMaxWidth().weight(1f).background(CompactStyle.Bg)) {
        if (basemap == null) {
            NoMapMessage()
        } else {
            // Map fills the whole cell; the EFIS header is drawn on top of it.
            MapLibreCell(
                mbtilesPath = basemap.absolutePath,
                state = state,
                orientation = orientation,
                layers = prefs.mapLayers,
                altUnit = altUnit,
                onMap = { mapRef[0] = it },
                onCameraMove = { cameraTick++ },
                onRecenter = { recenterAction[0] = it },
                onFeatureTap = { featureInfo = it },
                modifier = Modifier.fillMaxSize(),
            )
            // Trail + route-ahead + ownship, projected through the live map camera.
            TrackOverlay(
                map = mapRef[0],
                state = state,
                trail = trail,
                orientation = orientation,
                gotoTarget = gotoTarget,
                navRoute = navRoute,
                cameraTick = cameraTick,
                tm = tm,
                modifier = Modifier.fillMaxSize(),
            )
            // City / town labels (offline; queried from the basemap place layer).
            if (prefs.mapLayers.cities) {
                CityLabelsOverlay(
                    map = mapRef[0],
                    tm = tm,
                    cameraTick = cameraTick,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            // Aerodrome ICAO codes (offline; queried from the OpenAIP aerodrome layer).
            if (prefs.mapLayers.aerodromeCodes) {
                AerodromeCodesOverlay(
                    map = mapRef[0],
                    tm = tm,
                    cameraTick = cameraTick,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

        // EFIS instrument header band (drawn over everything, at the top).
        EfisHeaderBand(tm, state, unit, arcs, altUnit, targetHeading)

        // Overlay controls (right side), placed BELOW the header band so they
        // never overlap the SPEED cell / arc bar.
        Column(
            modifier = Modifier.align(Alignment.TopEnd).padding(top = 160.dp, end = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (basemap != null) {
                MapIconButton(Icons.Filled.Layers) { showLayers = true }
                MapIconButton(Icons.Filled.MyLocation) { recenterAction[0]?.invoke() }
                // North-up / track-up toggle.
                MapIconButton(
                    if (orientation == MapOrientation.TRACK_UP) Icons.Filled.Navigation else Icons.Filled.Explore,
                ) {
                    ServiceLocator.setMapOrientation(
                        if (orientation == MapOrientation.TRACK_UP) MapOrientation.NORTH_UP else MapOrientation.TRACK_UP,
                    )
                    // Re-enable follow so the new orientation is applied immediately
                    // (recentre + re-orient the camera on the next state update).
                    recenterAction[0]?.invoke()
                }
                if (prefs.mapShowZoomButtons) {
                    MapIconButton(Icons.Filled.Add) { zoomBy(mapRef[0], +1.0) }
                    MapIconButton(Icons.Filled.Remove) { zoomBy(mapRef[0], -1.0) }
                }
            }
        }

        // Goto Direct popup takes precedence over the info popup.
        val goto = gotoTarget
        if (goto != null) {
            GotoPopup(
                target = goto,
                onDismiss = { ServiceLocator.activeGoto.value = null },
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp, start = 12.dp, end = 12.dp),
            )
        } else {
            // Tapped-feature info popup, anchored bottom-centre above the controls.
            featureInfo?.let { info ->
                FeatureInfoPopup(
                    info = info,
                    onDismiss = { featureInfo = null },
                    onGoto = { ServiceLocator.activeGoto.value = info; featureInfo = null },
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp, start = 12.dp, end = 12.dp),
                )
            }
        }
    }
    }

    if (showLayers) {
        LayerDialog(
            current = prefs.mapLayers,
            onDismiss = { showLayers = false },
            onApply = { ServiceLocator.setMapLayers(it); showLayers = false },
        )
    }
}

/** The EFIS instrument band at the top of the map. Long-press the heading tape
 *  to set the heading bug. */
@Composable
private fun EfisHeaderBand(
    tm: TextMeasurer,
    state: EfisState,
    unit: EfisSpeedUnit,
    arcs: SpeedArcs?,
    altUnit: AltitudeUnit,
    targetHeading: Int?,
) {
    var showHeadingDialog by remember { mutableStateOf(false) }
    // Fixed-height band at the top of the cell so the banking aircraft is fully
    // visible and the long-press on the heading tape is reliably received.
    val bandHeight = 150.dp
    Canvas(
        modifier = Modifier.fillMaxWidth().height(bandHeight).pointerInput(Unit) {
            detectTapGestures(onLongPress = { pos ->
                // Top band (heading tape) → set the heading bug.
                if (pos.y <= size.height * 0.40f) showHeadingDialog = true
            })
        },
    ) {
        drawEfisHeader(tm, state, unit, arcs, size.height, Color(0xF01A1C1E), altUnit, targetHeading)
    }
    if (showHeadingDialog) {
        com.airchecklists.app.ui.components.HeadingEntryDialog(
            initial = targetHeading,
            onDismiss = { showHeadingDialog = false },
            onConfirm = { hdg -> ServiceLocator.targetHeading.value = hdg; showHeadingDialog = false },
            onClear = { ServiceLocator.targetHeading.value = null; showHeadingDialog = false },
        )
    }
}

@Composable
private fun MapIconButton(icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Surface(
        shape = CircleShape,
        color = Color(0xCC202325),
        modifier = Modifier.size(38.dp).clickable(onClick = onClick),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
        }
    }
}

@Composable
private fun NoMapMessage() {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            stringResource(R.string.map_not_downloaded),
            color = CompactStyle.Dim,
            style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
        )
        Text(
            stringResource(R.string.map_go_to_settings),
            color = CompactStyle.Accent2,
            style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

private const val NM_PER_DEG_LAT = 60.0
/** Query layer id for city/town features (drawn as a Compose label overlay). */
private const val PLACE_LAYER_ID = "place-query"
/** Query layer id for runway lines (used to derive an aerodrome's QFU). */
private const val RUNWAY_LAYER_ID = "runway-query"

/**
 * Transparent Compose overlay that draws the GPS trail and the route-ahead
 * vector by projecting geographic coordinates onto the live MapLibre camera.
 * [cameraTick] changes whenever the camera moves, forcing a redraw.
 */
@Composable
private fun TrackOverlay(
    map: MapLibreMap?,
    state: EfisState,
    trail: List<DoubleArray>,
    orientation: MapOrientation,
    gotoTarget: MapFeatureInfo?,
    navRoute: List<DoubleArray>,
    cameraTick: Int,
    tm: TextMeasurer,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        @Suppress("UNUSED_EXPRESSION") cameraTick // read so redraw tracks camera moves
        val m = map ?: return@Canvas
        val proj = m.projection

        fun toScreen(lat: Double, lon: Double): Offset {
            val p = proj.toScreenLocation(LatLng(lat, lon))
            return Offset(p.x, p.y)
        }

        // Active nav route (from the planner): green polyline through all waypoints.
        if (navRoute.size >= 2) {
            val path = Path()
            navRoute.forEachIndexed { i, wp ->
                val o = toScreen(wp[0], wp[1])
                if (i == 0) path.moveTo(o.x, o.y) else path.lineTo(o.x, o.y)
            }
            drawPath(path, Color(0xFF2ECC40), style = Stroke(width = 8f))
        }

        // Active Goto: magenta line from the ownship to the target aerodrome.
        if (gotoTarget?.lat != null && gotoTarget.lon != null && state.hasPosition) {
            val from = toScreen(state.latitude, state.longitude)
            val to = toScreen(gotoTarget.lat, gotoTarget.lon)
            drawLine(Color(0xFFD24DEA), from, to, strokeWidth = 8f)
        }

        // Position [minutes] ahead along the current track (great-circle-ish, flat).
        fun ahead(minutes: Double): Offset {
            val distNm = (state.gpsSpeedKmh / 1.852) * (minutes / 60.0)
            val trackRad = Math.toRadians(state.gpsTrackDeg.toDouble())
            val cosLat = cos(Math.toRadians(state.latitude)).coerceAtLeast(0.01)
            val lat = state.latitude + (distNm * cos(trackRad)) / NM_PER_DEG_LAT
            val lon = state.longitude + (distNm * sin(trackRad)) / (NM_PER_DEG_LAT * cosLat)
            return toScreen(lat, lon)
        }

        // GPS trail (green polyline) — wider for visibility.
        if (trail.size >= 2) {
            val path = Path()
            trail.forEachIndexed { i, pt ->
                val o = toScreen(pt[0], pt[1])
                if (i == 0) path.moveTo(o.x, o.y) else path.lineTo(o.x, o.y)
            }
            drawPath(path, CompactStyle.Climb, style = Stroke(width = 9f))
        }

        // Route-ahead: dashed yellow line to +6 min, with time pills at 2/4/6 min.
        if (state.hasPosition && state.gpsSpeedKmh > 1f) {
            val from = toScreen(state.latitude, state.longitude)
            val end = ahead(6.0)
            drawLine(
                CompactStyle.Accent,
                from,
                end,
                strokeWidth = 7f,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(20f, 14f)),
            )
            // Time pills (larger, with the minute count).
            listOf(2, 4, 6).forEach { min ->
                val p = ahead(min.toDouble())
                drawCircle(Color(0xFF1A1A1A), radius = 24f, center = p)
                drawCircle(CompactStyle.Accent, radius = 24f, center = p, style = Stroke(width = 3f))
                compactText(tm, "$min", p.x, p.y, sizeSp = 15f, bold = true, color = CompactStyle.Accent)
            }
        }

        // Ownship: drawn at its TRUE projected position. Its screen rotation is the
        // course RELATIVE to the map's current bearing, so:
        //  - track-up (camera bearing == track): nose points screen-up (0°), and
        //    stays up even after panning / at low speed;
        //  - north-up (camera bearing == 0): nose points along the real track.
        if (state.hasPosition) {
            val p = toScreen(state.latitude, state.longitude)
            val camBearing = m.cameraPosition.bearing.toFloat()
            val acHeading = state.gpsTrackDeg - camBearing
            rotate(degrees = acHeading, pivot = p) {
                drawPlaneMarker(p.x, p.y, size = 108f, fill = Color(0xFF101010), outline = Color.White)
            }
        }
    }
}

// ---- City labels overlay (offline; drawn from queried place features) ----

@Composable
private fun CityLabelsOverlay(
    map: MapLibreMap?,
    tm: TextMeasurer,
    cameraTick: Int,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        @Suppress("UNUSED_EXPRESSION") cameraTick
        val m = map ?: return@Canvas
        // Query the invisible place layer over the whole viewport.
        val screen = android.graphics.RectF(0f, 0f, size.width, size.height)
        val feats = runCatching { m.queryRenderedFeatures(screen, PLACE_LAYER_ID) }.getOrNull() ?: return@Canvas
        val proj = m.projection
        // De-dup by name so the same city (multiple tiles) isn't drawn twice.
        val seen = HashSet<String>()
        feats.forEach { f ->
            val g = f.geometry()
            if (g !is org.maplibre.geojson.Point) return@forEach
            val name = f.getStringProperty("name:fr")?.takeIf { it.isNotBlank() }
                ?: f.getStringProperty("name")?.takeIf { it.isNotBlank() }
                ?: return@forEach
            if (!seen.add(name)) return@forEach
            val cls = f.getStringProperty("class")
            val p = proj.toScreenLocation(LatLng(g.latitude(), g.longitude()))
            val sz = if (cls == "city") 13f else 11f
            // Cheap white halo (4 offsets) + dark text on top.
            listOf(-1.5f to 0f, 1.5f to 0f, 0f to -1.5f, 0f to 1.5f).forEach { (ox, oy) ->
                compactText(tm, name, p.x + ox, p.y + oy, sizeSp = sz, bold = true, color = Color.White)
            }
            compactText(tm, name, p.x, p.y, sizeSp = sz, bold = true, color = Color(0xFF2A2320))
        }
    }
}

// ---- Aerodrome ICAO code labels overlay ----

@Composable
private fun AerodromeCodesOverlay(
    map: MapLibreMap?,
    tm: TextMeasurer,
    cameraTick: Int,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        @Suppress("UNUSED_EXPRESSION") cameraTick
        val m = map ?: return@Canvas
        val feats = runCatching {
            m.queryRenderedFeatures(android.graphics.RectF(0f, 0f, size.width, size.height), "oaip-aerodromes-pt", "oaip-aerodromes-priv")
        }.getOrNull() ?: return@Canvas
        val proj = m.projection
        val seen = HashSet<String>()
        feats.forEach { f ->
            val g = f.geometry()
            if (g !is org.maplibre.geojson.Point) return@forEach
            val code = f.str("icao")?.takeIf { it.isNotBlank() }
                ?: f.str("name")?.takeIf { it.isNotBlank() } ?: return@forEach
            if (!seen.add(code)) return@forEach
            val p = proj.toScreenLocation(LatLng(g.latitude(), g.longitude()))
            // Draw the code to the RIGHT of the (now larger) marker, left-aligned,
            // with a white halo, so it never sits on top of the point.
            val lx = p.x + 14f
            val ly = p.y + 2f
            listOf(-1.5f to 0f, 1.5f to 0f, 0f to -1.5f, 0f to 1.5f).forEach { (ox, oy) ->
                compactText(tm, code, lx + ox, ly + oy, sizeSp = 13f, bold = true, color = Color.White, center = false)
            }
            compactText(tm, code, lx, ly, sizeSp = 13f, bold = true, color = Color(0xFF1565C0), center = false)
        }
    }
}

// ---- Tapped-feature info popup ----

@Composable
private fun FeatureInfoPopup(
    info: MapFeatureInfo,
    onDismiss: () -> Unit,
    onGoto: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val isAerodrome = info.icao != null
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = Color(0xF014181C),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF2B323C)),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            androidx.compose.foundation.layout.Row(verticalAlignment = Alignment.Top) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(info.title, color = Color.White, fontSize = 18.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                    // ICAO code: bigger + orange, like the other values.
                    info.subtitle?.let {
                        Text(it, color = CompactStyle.Accent2, fontSize = 20.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                    }
                }
                androidx.compose.material3.IconButton(onClick = onDismiss) {
                    Icon(androidx.compose.material.icons.Icons.Filled.Close, contentDescription = "Fermer", tint = Color(0xFFB0B0B0))
                }
            }
            // Info lines + action buttons side by side.
            androidx.compose.foundation.layout.Row(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    if (info.lines.isEmpty()) {
                        Text("Pas d'information détaillée.", color = Color(0xFF9AA0A6), fontSize = 13.sp)
                    } else info.lines.forEach { (label, value) ->
                        androidx.compose.foundation.layout.Row(modifier = Modifier.padding(top = 3.dp)) {
                            Text("$label : ", color = Color(0xFF9AA0A6), fontSize = 14.sp)
                            Text(value, color = CompactStyle.Accent2, fontSize = 14.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
                        }
                    }
                }
                if (isAerodrome) {
                    androidx.compose.foundation.layout.Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.Bottom,
                    ) {
                        // 1) Runway orientation (icon + designator/QFU above).
                        info.runwayHeadingDeg?.let { qfu ->
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                val rwLabel = info.runwayRef ?: "%03d°".format(qfu.roundToInt() % 360)
                                Text(rwLabel, color = CompactStyle.Accent2, fontSize = 12.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                                CircleButton(onClick = {}) { RunwayGlyph(qfu) }
                            }
                        }
                        // 2) VAC.
                        CircleButton(onClick = { openVac(context, info.icao) }) {
                            Text("VAC", color = Color.White, fontSize = 13.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                        }
                        // 3) Goto Direct.
                        CircleButton(onClick = onGoto) {
                            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Goto", tint = Color.White)
                        }
                    }
                }
            }
        }
    }
}

/** Circular action button (dark fill, white border) matching the mock. */
@Composable
private fun CircleButton(onClick: () -> Unit, content: @Composable () -> Unit) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = Color(0xFF1A2026),
        border = androidx.compose.foundation.BorderStroke(2.dp, Color.White),
        modifier = Modifier.size(52.dp),
    ) {
        Box(contentAlignment = Alignment.Center) { content() }
    }
}

/** Small rotated runway-strip glyph pointing along [qfu] degrees. */
@Composable
private fun RunwayGlyph(qfu: Float) {
    Canvas(modifier = Modifier.size(30.dp)) {
        rotate(degrees = qfu, pivot = center) {
            val cxp = size.width / 2f
            val len = size.height * 0.42f
            drawLine(Color(0xFF3A6EA5), Offset(cxp, center.y - len), Offset(cxp, center.y + len), strokeWidth = 12f)
            drawLine(Color(0xFFDDDDDD), Offset(cxp, center.y - len * 0.75f), Offset(cxp, center.y + len * 0.75f),
                strokeWidth = 1.5f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 4f)))
        }
    }
}

/** Open the VAC PDF for [icao] if a chart exists (local file, else remote URL). */
private fun openVac(context: android.content.Context, icao: String?) {
    if (icao == null) return
    val chart = ServiceLocator.vacRepository.charts.value
        .firstOrNull { it.icao.equals(icao, ignoreCase = true) } ?: return
    val cycle = ServiceLocator.preferences.preferences.value.vacAiracCycle
    com.airchecklists.app.data.net.PdfOpener.open(
        context,
        ServiceLocator.vacRepository.localPdf(chart),
        ServiceLocator.vacRepository.remoteUrl(cycle, chart.icao),
    )
}

// ---- Goto Direct popup ----

@Composable
private fun GotoPopup(target: MapFeatureInfo, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val state by ServiceLocator.efisProvider.state.collectAsStateWithLifecycle()
    val lat = target.lat; val lon = target.lon

    // Live nav computations from the current GPS position to the target.
    val hasNav = state.hasPosition && lat != null && lon != null
    val bearing = if (hasNav) NavMath.bearingDeg(state.latitude, state.longitude, lat!!, lon!!).roundToInt() else null
    val distNm = if (hasNav) NavMath.distanceNm(state.latitude, state.longitude, lat!!, lon!!) else null
    val gsKt = state.gpsSpeedKmh / 1.852f
    val eteSec = if (distNm != null && gsKt > 3f) (distNm / gsKt * 3600.0).toInt() else null

    // Drive the heading bug continuously while the Goto popup is open.
    LaunchedEffect(bearing) { if (bearing != null) ServiceLocator.targetHeading.value = bearing }
    // Arm the countdown once with the estimated flight time (when moving).
    LaunchedEffect(target) { /* armed on first valid ETE below */ }
    val armed = remember { booleanArrayOf(false) }
    LaunchedEffect(eteSec) {
        if (!armed[0] && eteSec != null && eteSec > 0) {
            com.airchecklists.app.ui.efis.gauges.chrono.armNumCountdown(eteSec * 1000L)
            armed[0] = true
        }
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = Color(0xF014181C),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF2B323C)),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            androidx.compose.foundation.layout.Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = Color.White, modifier = Modifier.padding(end = 8.dp))
                Text(target.title, color = Color.White, fontSize = 17.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, modifier = Modifier.weight(1f))
                androidx.compose.material3.IconButton(onClick = onDismiss) {
                    Icon(androidx.compose.material.icons.Icons.Filled.Close, contentDescription = "Fermer", tint = Color(0xFFB0B0B0))
                }
            }
            androidx.compose.foundation.layout.Row(
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    gotoLine("Cap à suivre", if (bearing != null) "%03d°".format(bearing) else "--")
                    gotoLine("Distance", if (distNm != null) "%d nm".format(distNm.roundToInt()) else "--")
                    gotoLine("Temps sans vent", if (eteSec != null) "%02d:%02d".format(eteSec / 60, eteSec % 60) else "--:--")
                    target.aaFreq?.let { gotoLine("A/A", "$it MHz") }
                }
                if (target.icao != null) {
                    CircleButton(onClick = { openVac(context, target.icao) }) {
                        Text("VAC", color = Color.White, fontSize = 13.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun gotoLine(label: String, value: String) {
    androidx.compose.foundation.layout.Row(modifier = Modifier.padding(top = 3.dp)) {
        Text("$label : ", color = Color(0xFF9AA0A6), fontSize = 15.sp)
        Text(value, color = CompactStyle.Accent2, fontSize = 15.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
    }
}

// ---- Layer selection dialog ----
@Composable
private fun LayerDialog(
    current: MapLayerPrefs,
    onDismiss: () -> Unit,
    onApply: (MapLayerPrefs) -> Unit,
) {
    var draft by remember { mutableStateOf(current) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.map_layers_title)) },
        text = {
            Column {
                LayerRow(stringResource(R.string.map_layer_cities), draft.cities) { draft = draft.copy(cities = it) }
                LayerRow(stringResource(R.string.map_layer_aerodrome_codes), draft.aerodromeCodes) { draft = draft.copy(aerodromeCodes = it) }
                LayerRow(stringResource(R.string.map_layer_aerodromes), draft.aerodromes) { draft = draft.copy(aerodromes = it) }
                LayerRow(stringResource(R.string.map_layer_private), draft.privateAirfields) { draft = draft.copy(privateAirfields = it) }
                LayerRow(stringResource(R.string.map_layer_airspaces), draft.airspaces) { draft = draft.copy(airspaces = it) }
                LayerRow(stringResource(R.string.map_layer_navaids), draft.navaids) { draft = draft.copy(navaids = it) }
                LayerRow(stringResource(R.string.map_layer_obstacles), draft.obstacles) { draft = draft.copy(obstacles = it) }
                LayerRow(stringResource(R.string.map_layer_reporting), draft.reportingPoints) { draft = draft.copy(reportingPoints = it) }
            }
        },
        confirmButton = { TextButton(onClick = { onApply(draft) }) { Text(stringResource(R.string.action_save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@Composable
private fun LayerRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    androidx.compose.foundation.layout.Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().clickable { onCheckedChange(!checked) },
    ) {
        Checkbox(checked = checked, onCheckedChange = onCheckedChange)
        Text(label, modifier = Modifier.padding(start = 4.dp))
    }
}

// ---- Tapped-feature info (aerodrome / airspace) ----

/** Info shown in the popup when a map feature is tapped. */
data class MapFeatureInfo(
    val title: String,
    val subtitle: String?,
    val lines: List<Pair<String, String>>,   // label -> value
    /** Runway QFU (magnetic orientation, °) for an aerodrome, if known. */
    val runwayHeadingDeg: Float? = null,
    /** Runway designator text, e.g. "07/25", if known. */
    val runwayRef: String? = null,
    // ---- Goto Direct target data (aerodromes only) ----
    val icao: String? = null,
    val lat: Double? = null,
    val lon: Double? = null,
    /** Air-to-air / info frequency (first frequency), e.g. "123.500". */
    val aaFreq: String? = null,
)

/**
 * Query the OpenAIP layers at [pt] (screen px) and build an info card for the
 * best match. Aerodromes take priority over airspaces (points over polygons).
 * Altitudes are shown in the user's [altUnit] (FL stays FL).
 */
private fun queryFeatureInfo(map: MapLibreMap, pt: android.graphics.PointF, altUnit: AltitudeUnit): MapFeatureInfo? {
    // Small tap tolerance box around the finger.
    val r = 24f
    val box = android.graphics.RectF(pt.x - r, pt.y - r, pt.x + r, pt.y + r)

    // Aerodromes (points) first — public and private sub-layers. Also look for the
    // nearest runway to show its orientation (from the line geometry) + designator.
    runCatching { map.queryRenderedFeatures(box, "oaip-aerodromes-pt", "oaip-aerodromes-priv") }.getOrNull()
        ?.firstOrNull()?.let { aero ->
            val aeroPt = (aero.geometry() as? org.maplibre.geojson.Point)
            val rwy = nearestRunway(map, aeroPt)
            return aerodromeInfo(it = aero, altUnit = altUnit, runwayRef = rwy?.first, runwayHeadingDeg = rwy?.second)
        }
    // Then airspaces (polygons): query at the exact point.
    runCatching { map.queryRenderedFeatures(pt, "oaip-airspaces-fill") }.getOrNull()
        ?.firstOrNull()?.let { return airspaceInfo(it, altUnit) }
    // Navaids / obstacles / reporting points as a fallback.
    for (base in listOf("navaids", "obstacles", "reporting_points")) {
        runCatching { map.queryRenderedFeatures(box, "oaip-$base-pt") }.getOrNull()
            ?.firstOrNull()?.let { return simplePointInfo(base, it, altUnit) }
    }
    return null
}

private fun org.maplibre.geojson.Feature.str(key: String): String? =
    if (hasProperty(key) && !getProperty(key).isJsonNull) getStringProperty(key) else null

/** Read a nested {value,unit,referenceDatum} altitude object, formatted in [altUnit]. */
private fun org.maplibre.geojson.Feature.altStr(key: String, altUnit: AltitudeUnit): String? {
    val el = if (hasProperty(key)) getProperty(key) else return null
    if (!el.isJsonObject) return null
    val o = el.asJsonObject
    val value = o.get("value")?.asDouble ?: return null
    val unit = o.get("unit")?.asInt ?: 1
    val datum = o.get("referenceDatum")?.asInt ?: 1
    return formatAltitude(value, unit, datum, altUnit)
}

private fun aerodromeInfo(
    it: org.maplibre.geojson.Feature,
    altUnit: AltitudeUnit,
    runwayRef: String?,
    runwayHeadingDeg: Float?,
): MapFeatureInfo {
    val f = it
    val name = f.str("name") ?: "Aérodrome"
    val icao = f.str("icao")
    val pt = f.geometry() as? org.maplibre.geojson.Point
    // Configured terrain (VAC) matched by ICAO — richest source when present.
    val vac = icao?.let { ic ->
        ServiceLocator.vacRepository.charts.value.firstOrNull { it.icao.equals(ic, ignoreCase = true) }
    }
    val lines = mutableListOf<Pair<String, String>>()
    aerodromeTypeLabel(f)?.let { lines += "Type" to it }
    f.altStr("elevation", altUnit)?.let { lines += "Altitude" to it }

    // Runway designator/QFU: prefer the VAC "circuit" field, then the basemap.
    val vacQfu = vac?.circuit?.let { com.airchecklists.app.ui.terrain.QfuParser.primaryHeading(it)?.toFloat() }
    val qfu = vacQfu ?: runwayRef?.let { parseRunwayHeading(it) } ?: runwayHeadingDeg
    // Designator label to show above the runway icon.
    val rwLabel = vac?.circuit?.let { runwayDesignatorFromCircuit(it) } ?: runwayRef

    // Frequencies: prefer the VAC "frequencies" field, else the OpenAIP data.
    val freqs = frequencyLines(f)
    val aaFreq = vac?.frequencies?.takeIf { it.isNotBlank() }
        ?: freqs.firstOrNull()?.second?.removeSuffix(" MHz")
    aaFreq?.let { lines += "A/A" to if (it.contains("MHz")) it else "$it MHz" }

    return MapFeatureInfo(
        title = name, subtitle = icao, lines = lines,
        runwayHeadingDeg = qfu, runwayRef = rwLabel,
        icao = icao, lat = pt?.latitude(), lon = pt?.longitude(), aaFreq = aaFreq,
    )
}

/** Extract a runway designator like "04/22" from a free-text circuit field. */
private fun runwayDesignatorFromCircuit(circuit: String): String? {
    val m = Regex("""\b(\d{2})\s*[GD/]*\s*/\s*(\d{2})""").find(circuit)
        ?: Regex("""\b(\d{2})/(\d{2})\b""").find(circuit)
    return m?.let { "${it.groupValues[1]}/${it.groupValues[2]}" }
}

/**
 * Find the runway line nearest to [aeroPt] among the currently rendered runway
 * features, and return its designator (if any) + orientation in degrees derived
 * from the line's endpoints (0..179, i.e. the strip axis). Null if none found.
 */
private fun nearestRunway(
    map: MapLibreMap,
    aeroPt: org.maplibre.geojson.Point?,
): Pair<String?, Float?>? {
    val feats = runCatching {
        // Query the whole plausible viewport (a large rect covers any screen size).
        map.queryRenderedFeatures(android.graphics.RectF(0f, 0f, 5000f, 5000f), RUNWAY_LAYER_ID)
    }.getOrNull() ?: return null
    if (feats.isEmpty()) return null
    fun lineCoords(f: org.maplibre.geojson.Feature): List<org.maplibre.geojson.Point> = when (val g = f.geometry()) {
        is org.maplibre.geojson.LineString -> g.coordinates()
        is org.maplibre.geojson.MultiLineString -> g.coordinates().flatten()
        else -> emptyList()
    }
    // Pick the runway whose first vertex is closest to the aerodrome point.
    val best = if (aeroPt != null) {
        feats.minByOrNull { f ->
            val c = lineCoords(f).firstOrNull() ?: return@minByOrNull Double.MAX_VALUE
            val dLat = c.latitude() - aeroPt.latitude(); val dLon = c.longitude() - aeroPt.longitude()
            dLat * dLat + dLon * dLon
        }
    } else feats.first()
    best ?: return null
    val coords = lineCoords(best)
    if (coords.size < 2) return best.str("ref") to null
    val a = coords.first(); val b = coords.last()
    val dLon = (b.longitude() - a.longitude()) * cos(Math.toRadians(a.latitude()))
    val dLat = (b.latitude() - a.latitude())
    // Azimuth of the strip axis, 0..360 clockwise from north.
    var az = Math.toDegrees(kotlin.math.atan2(dLon, dLat)).toFloat()
    if (az < 0) az += 360f
    return best.str("ref") to az
}

// ---- Navigation helpers moved to NavMath.kt (shared with the nav planner) ----

/** Parse a runway designator like "07/25" or "07L" → orientation in degrees
 *  (first number ×10). Returns null if not parseable. */
private fun parseRunwayHeading(ref: String): Float? {
    val m = Regex("""(\d{1,2})""").find(ref) ?: return null
    val n = m.groupValues[1].toIntOrNull() ?: return null
    if (n !in 1..36) return null
    return (n * 10).toFloat()
}

private fun airspaceInfo(f: org.maplibre.geojson.Feature, altUnit: AltitudeUnit): MapFeatureInfo {
    val name = f.str("name") ?: "Espace aérien"
    val lines = mutableListOf<Pair<String, String>>()
    airspaceTypeLabel(f)?.let { lines += "Type" to it }
    val lower = f.altStr("lowerLimit", altUnit)
    val upper = f.altStr("upperLimit", altUnit)
    if (lower != null || upper != null) {
        lines += "Plancher / Plafond" to "${lower ?: "?"} → ${upper ?: "?"}"
    }
    frequencyLines(f).forEach { lines += it }
    return MapFeatureInfo(title = name, subtitle = "Espace aérien", lines = lines)
}

private fun simplePointInfo(base: String, f: org.maplibre.geojson.Feature, altUnit: AltitudeUnit): MapFeatureInfo {
    val label = when {
        base.startsWith("navaid") -> "Balise"
        base.startsWith("obstacle") -> "Obstacle"
        else -> "Point de report"
    }
    val lines = mutableListOf<Pair<String, String>>()
    f.altStr("elevation", altUnit)?.let { lines += (if (base.startsWith("obstacle")) "Sommet" else "Altitude") to it }
    return MapFeatureInfo(title = f.str("name") ?: label, subtitle = label, lines = lines)
}

/** Build the frequency rows from the "frequencies" JSON array property. */
private fun frequencyLines(f: org.maplibre.geojson.Feature): List<Pair<String, String>> {
    val el = if (f.hasProperty("frequencies")) f.getProperty("frequencies") else return emptyList()
    if (!el.isJsonArray) return emptyList()
    return el.asJsonArray.mapNotNull { item ->
        val o = item.asJsonObject
        val value = o.get("value")?.asString ?: return@mapNotNull null
        val fname = o.get("name")?.asString?.takeIf { it.isNotBlank() }
        val label = fname ?: "Fréquence"
        label to "$value MHz"
    }
}

// ---- OpenAIP enum decode (stable public v2 values; unknown → "Type N") ----

/**
 * Format an OpenAIP altitude ({value, sourceUnit, datum}) into the user's
 * [altUnit]. FL (source unit 6) is a flight level and is always shown as "FLxxx".
 * Source unit 0 = metres, 1 = feet.
 */
private fun formatAltitude(value: Double, sourceUnit: Int, datum: Int, altUnit: AltitudeUnit): String {
    if (sourceUnit == 6) return "FL${value.roundToInt()}"      // flight level — unit-agnostic
    // Normalise the source to feet, then convert to the chosen display unit.
    val ft = if (sourceUnit == 0) value * 3.280839895 else value
    return if (altUnit == AltitudeUnit.METERS) {
        "${(ft / 3.280839895).roundToInt()} m ${datumSuffix(datum)}".trim()
    } else {
        "${ft.roundToInt()} ft ${datumSuffix(datum)}".trim()
    }
}

private fun datumSuffix(datum: Int): String = when (datum) {
    0 -> "AGL"    // ground
    1 -> "AMSL"   // mean sea level
    2 -> ""       // standard (FL) — no suffix
    else -> ""
}

private fun aerodromeTypeLabel(f: org.maplibre.geojson.Feature): String? {
    val t = if (f.hasProperty("type")) f.getProperty("type").asInt else return null
    return when (t) {
        0 -> "Aéroport intl."
        1 -> "Aéroport"
        2 -> "Terrain"
        3 -> "Héliport"
        4 -> "Base hydravions"
        5 -> "Militaire"
        6 -> "Terrain privé / ULM"
        7 -> "Altisurface"
        8 -> "Ballon"
        9 -> "Aérodrome"
        else -> "Type $t"
    }
}

private fun airspaceTypeLabel(f: org.maplibre.geojson.Feature): String? {
    val t = if (f.hasProperty("type")) f.getProperty("type").asInt else return null
    return when (t) {
        0 -> "Autre"
        1 -> "CTR"
        2 -> "TMZ"
        3 -> "RMZ"
        4 -> "TMA"
        5 -> "TRA"
        6 -> "CTA"
        7 -> "Zone D (danger)"
        8 -> "Zone R (réglementée)"
        9 -> "Zone P (interdite)"
        10 -> "Zone d'entraînement"
        21 -> "AWY (voie aérienne)"
        26 -> "Zone d'activité"
        28 -> "FIR"
        33 -> "SIV"
        34 -> "Zone protégée"
        else -> "Type $t"
    }
}

// ---- Embedded MapLibre view ----

@Composable
private fun MapLibreCell(
    mbtilesPath: String,
    state: EfisState,
    orientation: MapOrientation,
    layers: MapLayerPrefs,
    altUnit: AltitudeUnit,
    onMap: (MapLibreMap) -> Unit,
    onCameraMove: () -> Unit,
    onRecenter: (() -> Unit) -> Unit,
    onFeatureTap: (MapFeatureInfo) -> Unit,
    modifier: Modifier = Modifier,
) {
    val owner = LocalLifecycleOwner.current
    val mapRef = remember { arrayOfNulls<MapLibreMap>(1) }
    val styleRef = remember { arrayOfNulls<Style>(1) }
    // Latest altitude unit, read by the (once-created) tap listener.
    val altUnitRef = remember { arrayOf(altUnit) }
    altUnitRef[0] = altUnit
    val viewRef = remember { arrayOfNulls<MapView>(1) }
    val initedZoom = remember { booleanArrayOf(false) }
    // Follow-aircraft mode: recentre on the ownship every frame. A manual pan
    // switches it off (so the map isn't yanked back); the "recentre" button
    // (or leaving/re-entering) turns it back on.
    val follow = remember { booleanArrayOf(true) }
    // Last applied track-up bearing (kept when GPS track is unreliable at low speed).
    val lastBearing = remember { doubleArrayOf(0.0) }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            MapView(ctx).also { mv ->
                viewRef[0] = mv
                // Route touch handling so the cockpit pager keeps single-finger
                // horizontal swipes, while two-finger gestures (pan + pinch-zoom)
                // stay with the map.
                mv.setOnTouchListener { v, ev ->
                    v.parent?.requestDisallowInterceptTouchEvent(ev.pointerCount >= 2)
                    false // let MapLibre process the event as usual
                }
                mv.onCreate(null)
                mv.onStart()
                mv.onResume()
                mv.getMapAsync { map ->
                    mapRef[0] = map
                    onMap(map)
                    map.uiSettings.isRotateGesturesEnabled = false
                    map.uiSettings.isZoomGesturesEnabled = true      // pinch-to-zoom
                    // Allow panning the map. So the single-finger cockpit-pager swipe
                    // isn't hijacked, ask the parent not to intercept touches while a
                    // gesture is in progress on the map (see the MapView wrapper below).
                    map.uiSettings.isScrollGesturesEnabled = true
                    map.uiSettings.isDoubleTapGesturesEnabled = true
                    map.uiSettings.setAttributionMargins(12, 0, 0, 12)
                    map.addOnCameraMoveListener { onCameraMove() }
                    // A manual pan/fling turns off follow-aircraft so the camera
                    // isn't snapped back to the ownship on the next state update.
                    map.addOnMoveListener(object : org.maplibre.android.maps.MapLibreMap.OnMoveListener {
                        override fun onMoveBegin(detector: org.maplibre.android.gestures.MoveGestureDetector) { follow[0] = false }
                        override fun onMove(detector: org.maplibre.android.gestures.MoveGestureDetector) {}
                        override fun onMoveEnd(detector: org.maplibre.android.gestures.MoveGestureDetector) {}
                    })
                    onRecenter { follow[0] = true }
                    // Tap a feature → query the OpenAIP layers at that point and
                    // surface an info popup for the nearest aerodrome / airspace.
                    map.addOnMapClickListener { latLng ->
                        val pt = map.projection.toScreenLocation(latLng)
                        val info = queryFeatureInfo(map, pt, altUnitRef[0])
                        if (info != null) onFeatureTap(info)
                        info != null
                    }
                    map.setStyle(Style.Builder().fromJson(buildStyleJson(mbtilesPath))) { style ->
                        styleRef[0] = style
                        addOpenAipLayers(style, layers)
                        addPlaceQueryLayer(style)
                        addRunwayQueryLayer(style)
                    }
                }
            }
        },
        update = { _ ->
            val map = mapRef[0]
            if (map != null && state.hasPosition && follow[0]) {
                // Track-up: rotate the map so the course is at the top. GPS track is
                // unreliable at very low speed, so keep the last bearing when slow.
                val bearing = if (orientation == MapOrientation.TRACK_UP) {
                    if (state.gpsSpeedKmh >= 5f) {
                        lastBearing[0] = state.gpsTrackDeg.toDouble(); lastBearing[0]
                    } else lastBearing[0]
                } else 0.0
                val zoom = if (initedZoom[0]) map.cameraPosition.zoom else 11.0
                initedZoom[0] = true
                map.cameraPosition = CameraPosition.Builder()
                    .target(LatLng(state.latitude, state.longitude))
                    .bearing(bearing)
                    .zoom(zoom)
                    .build()
            }
            // Apply layer visibility live (cheap; idempotent).
            styleRef[0]?.let { applyLayerVisibility(it, layers) }
        },
    )

    DisposableEffect(owner) {
        val obs = LifecycleEventObserver { _, e ->
            val mv = viewRef[0] ?: return@LifecycleEventObserver
            when (e) {
                Lifecycle.Event.ON_START -> mv.onStart()
                Lifecycle.Event.ON_RESUME -> mv.onResume()
                Lifecycle.Event.ON_PAUSE -> mv.onPause()
                Lifecycle.Event.ON_STOP -> mv.onStop()
                else -> {}
            }
        }
        owner.lifecycle.addObserver(obs)
        onDispose {
            owner.lifecycle.removeObserver(obs)
            viewRef[0]?.let { it.onPause(); it.onStop(); it.onDestroy() }
        }
    }
}

/** Zoom the map camera in/out by [delta] zoom levels, keeping the current target. */
private fun zoomBy(map: MapLibreMap?, delta: Double) {
    val m = map ?: return
    val cam = m.cameraPosition
    val newZoom = (cam.zoom + delta).coerceIn(3.0, 16.0)
    m.cameraPosition = CameraPosition.Builder(cam).zoom(newZoom).build()
}

/**
 * Add a (near-)invisible circle layer on the basemap "place" source-layer so we
 * can queryRenderedFeatures() the city/town names and draw them ourselves as a
 * Compose overlay — avoiding the need for offline glyph fonts for symbol labels.
 */
private fun addPlaceQueryLayer(style: Style) {
    runCatching {
        style.addLayer(
            CircleLayer(PLACE_LAYER_ID, "basemap").apply {
                sourceLayer = "place"
                setProperties(
                    PropertyFactory.circleRadius(2f),
                    PropertyFactory.circleOpacity(0f),      // invisible; only for querying
                    PropertyFactory.circleColor("#000000"),
                )
                setFilter(
                    Expression.any(
                        Expression.eq(Expression.get("class"), Expression.literal("city")),
                        Expression.eq(Expression.get("class"), Expression.literal("town")),
                    ),
                )
            },
        )
    }
}

/** Invisible line layer on the basemap "aeroway" runways so we can query a
 *  tapped aerodrome's runway designator (ref, e.g. "07/25") for its orientation. */
private fun addRunwayQueryLayer(style: Style) {
    runCatching {
        style.addLayer(
            LineLayer(RUNWAY_LAYER_ID, "basemap").apply {
                sourceLayer = "aeroway"
                setProperties(
                    // Effectively invisible but still RENDERED, so queryRenderedFeatures
                    // returns it (a strict 0 opacity can be culled and never queried).
                    PropertyFactory.lineOpacity(0.02f),
                    PropertyFactory.lineWidth(16f),       // wide hit area
                    PropertyFactory.lineColor("#000000"),
                )
                setFilter(Expression.eq(Expression.get("class"), Expression.literal("runway")))
            },
        )
    }
}

/** Add OpenAIP GeoJSON overlays, applying the initial [layers] visibility. */
internal fun addOpenAipLayers(style: Style, layers: MapLayerPrefs) {
    val files = ServiceLocator.mapRepository.openAipFiles()
    files.forEach { file ->
        val json = ServiceLocator.mapRepository.readLayer(file)
        if (json.isBlank()) return@forEach
        val base = file.nameWithoutExtension
        val srcId = "oaip-$base"
        runCatching { style.addSource(GeoJsonSource(srcId, json)) }.onFailure { return@forEach }
        val vis = if (layers.visible(base)) Property.VISIBLE else Property.NONE
        when {
            base.startsWith("airspace") -> {
                style.addLayer(
                    FillLayer("$srcId-fill", srcId).withProperties(
                        PropertyFactory.fillColor("#BE4DD6"),
                        PropertyFactory.fillOpacity(0.12f),
                        PropertyFactory.visibility(vis),
                    ),
                )
                style.addLayer(
                    LineLayer("$srcId-line", srcId).withProperties(
                        PropertyFactory.lineColor("#BE4DD6"),
                        PropertyFactory.lineWidth(1.6f),
                        PropertyFactory.visibility(vis),
                    ),
                )
            }
            base.startsWith("obstacle") -> style.addLayer(
                CircleLayer("$srcId-pt", srcId).withProperties(
                    PropertyFactory.circleColor("#D32F2F"),
                    PropertyFactory.circleRadius(6f),
                    PropertyFactory.circleStrokeColor("#7A0000"),
                    PropertyFactory.circleStrokeWidth(1.5f),
                    PropertyFactory.visibility(vis),
                ),
            )
            base.startsWith("aero") -> {
                // Split aerodromes into "main" (ICAO aerodromes/airports, types 9 & 2)
                // and private/restricted (all other types) so each toggles separately.
                val pubVis = if (layers.aerodromes) Property.VISIBLE else Property.NONE
                val privVis = if (layers.privateAirfields) Property.VISIBLE else Property.NONE
                val isMain = Expression.any(
                    Expression.eq(Expression.get("type"), Expression.literal(9L)),
                    Expression.eq(Expression.get("type"), Expression.literal(2L)),
                )
                style.addLayer(
                    CircleLayer("$srcId-pt", srcId).withProperties(
                        PropertyFactory.circleColor("#1565C0"),
                        PropertyFactory.circleRadius(8.5f),
                        PropertyFactory.circleStrokeColor("#FFFFFF"),
                        PropertyFactory.circleStrokeWidth(2f),
                        PropertyFactory.visibility(pubVis),
                    ).withFilter(isMain),
                )
                style.addLayer(
                    CircleLayer("$srcId-priv", srcId).withProperties(
                        PropertyFactory.circleColor("#7A57C2"),   // purple-ish for private
                        PropertyFactory.circleRadius(7f),
                        PropertyFactory.circleStrokeColor("#FFFFFF"),
                        PropertyFactory.circleStrokeWidth(1.5f),
                        PropertyFactory.visibility(privVis),
                    ).withFilter(Expression.not(isMain)),
                )
            }
            else -> {
                style.addLayer(
                    CircleLayer("$srcId-pt", srcId).withProperties(
                        PropertyFactory.circleColor("#C9741A"),
                        PropertyFactory.circleRadius(8.5f),
                        PropertyFactory.circleStrokeColor("#FFFFFF"),
                        PropertyFactory.circleStrokeWidth(2f),
                        PropertyFactory.visibility(vis),
                    ),
                )
            }
        }
    }
}

/** Toggle the visibility of already-added OpenAIP layers per [layers]. */
private fun applyLayerVisibility(style: Style, layers: MapLayerPrefs) {
    ServiceLocator.mapRepository.openAipFiles().forEach { file ->
        val base = file.nameWithoutExtension
        val srcId = "oaip-$base"
        if (base.startsWith("aero")) {
            // Public (type 9) vs private (type != 9) sub-layers.
            style.getLayer("$srcId-pt")?.setProperties(
                PropertyFactory.visibility(if (layers.aerodromes) Property.VISIBLE else Property.NONE))
            style.getLayer("$srcId-priv")?.setProperties(
                PropertyFactory.visibility(if (layers.privateAirfields) Property.VISIBLE else Property.NONE))
        } else {
            val vis = if (layers.visible(base)) Property.VISIBLE else Property.NONE
            listOf("$srcId-fill", "$srcId-line", "$srcId-pt").forEach { id ->
                style.getLayer(id)?.setProperties(PropertyFactory.visibility(vis))
            }
        }
    }
}

/**
 * Build the OACI-like style JSON with the vector source pointing at the local
 * mbtiles. Fills + lines only.
 */
internal fun buildStyleJson(mbtilesPath: String): String {
    val src = "mbtiles:///$mbtilesPath".replace("\\", "/")
    fun line(id: String, cls: String, color: String, w: Double): String =
        """{ "id":"$id","type":"line","source":"basemap","source-layer":"transportation",
            "filter":["==","class","$cls"],
            "paint":{"line-color":"$color","line-width":$w} }"""
    return """
    {
      "version": 8,
      "name": "AirDetente VFR",
      "sources": { "basemap": { "type": "vector", "url": "$src" } },
      "layers": [
        { "id":"bg","type":"background","paint":{"background-color":"#f6f4ee"} },
        { "id":"wood","type":"fill","source":"basemap","source-layer":"landcover",
          "filter":["==","class","wood"],"paint":{"fill-color":"#cfe4c2","fill-opacity":0.7} },
        { "id":"water","type":"fill","source":"basemap","source-layer":"water",
          "paint":{"fill-color":"#a6cfe3"} },
        { "id":"waterway","type":"line","source":"basemap","source-layer":"waterway",
          "paint":{"line-color":"#6fb0d2","line-width":1.1} },
        { "id":"urban","type":"fill","source":"basemap","source-layer":"landuse",
          "filter":["in","class","residential","suburb","quarter","neighbourhood"],
          "paint":{"fill-color":"#e6e2da","fill-opacity":0.6} },
        ${line("road-sec", "secondary", "#f4c542", 1.4)},
        ${line("road-pri", "primary", "#e8823a", 2.0)},
        ${line("road-trunk", "trunk", "#e8823a", 2.2)},
        ${line("road-mot", "motorway", "#d9463b", 3.0)}
      ]
    }
    """.trimIndent()
}

// ---- EFIS header (unchanged rendering, matches the reference screenshot) ----

private fun DrawScope.drawEfisHeader(
    tm: TextMeasurer,
    state: EfisState,
    unit: EfisSpeedUnit,
    arcs: SpeedArcs?,
    headerH: Float,
    headerColor: Color,
    altUnit: AltitudeUnit = AltitudeUnit.FEET,
    targetHeading: Int? = null,
) {
    val w = size.width
    drawRect(headerColor, topLeft = Offset(0f, 0f), size = Size(w, headerH))

    val topPad = headerH * 0.03f
    val tapeH = headerH * 0.36f
    val heading = ((state.headingDeg.roundToInt() % 360) + 360) % 360
    efisHeadingTape(tm, Rect(0f, topPad, w, topPad + tapeH), heading.toFloat(), showValue = true, targetHeading)
    val rowTop = topPad + tapeH

    val rowH = headerH - rowTop

    val toUnit = if (unit == EfisSpeedUnit.KNOTS) 1f / 1.852f else 1f
    val speed = state.gpsSpeedKmh * toUnit
    val speedLabel = if (unit == EfisSpeedUnit.KNOTS) "kt" else "km/h"
    val trend = if (state.verticalSpeedFtMin >= 0f) CompactStyle.Climb else CompactStyle.Descent
    val meters = altUnit == AltitudeUnit.METERS

    // ---- Left: ALTI over VARIO, unit label to the RIGHT of each box ----
    val cellW = w * 0.20f
    val cellH = rowH * 0.34f
    val boxLeft = w * 0.03f
    fun boxWithUnit(cyc: Float, value: String, unitTxt: String, color: Color) {
        drawRect(Color(0xFF111111), topLeft = Offset(boxLeft, cyc - cellH / 2), size = Size(cellW, cellH))
        drawRect(Color(0xFF5A5A5A), topLeft = Offset(boxLeft, cyc - cellH / 2), size = Size(cellW, cellH), style = Stroke(width = 1.3f))
        compactText(tm, value, boxLeft + cellW / 2, cyc, sizeSp = 22f, bold = true, color = color)
        compactText(tm, unitTxt, boxLeft + cellW + 6f, cyc, sizeSp = 12f, color = Color(0xFFCFCFCF), center = false)
    }
    val altTxt = com.airchecklists.app.data.model.AltitudeFormat.altValue(state.gpsAltitudeFt, altUnit).roundToInt().toString()
    val vsTxt = if (meters) "%.1f".format(com.airchecklists.app.data.model.AltitudeFormat.vsValue(state.verticalSpeedFtMin, altUnit))
        else state.verticalSpeedFtMin.roundToInt().toString()
    boxWithUnit(rowTop + rowH * 0.30f, altTxt, com.airchecklists.app.data.model.AltitudeFormat.altLabel(altUnit), trend)
    boxWithUnit(rowTop + rowH * 0.72f, vsTxt, com.airchecklists.app.data.model.AltitudeFormat.vsLabel(altUnit), trend)

    // ---- Right: unit label LEFT of the SPEED box, arc bar just under it ----
    // The speed box is taller + wider than ALT/VARIO for readability.
    val spCellH = cellH * 1.5f
    val spCellW = cellW * 1.15f
    val spBoxLeft = w - w * 0.03f - spCellW
    val spCy = rowTop + rowH * 0.34f
    compactText(tm, speedLabel, spBoxLeft - 48f, spCy, sizeSp = 13f, color = Color(0xFFCFCFCF))
    drawRect(Color(0xFF111111), topLeft = Offset(spBoxLeft, spCy - spCellH / 2), size = Size(spCellW, spCellH))
    drawRect(Color(0xFF5A5A5A), topLeft = Offset(spBoxLeft, spCy - spCellH / 2), size = Size(spCellW, spCellH), style = Stroke(width = 1.3f))
    compactText(tm, speed.roundToInt().toString(), spBoxLeft + spCellW / 2, spCy, sizeSp = 32f, bold = true, color = speedColor(speed, arcs?.scaled(toUnit)))
    // Speed-arc bar: taller and close under the box.
    val barTop = spCy + spCellH / 2 + 4f
    val barRect = Rect(spBoxLeft, barTop, spBoxLeft + spCellW, barTop + 22f)
    drawSpeedArcBar(barRect, arcs?.scaled(toUnit), speed)

    // ---- Centre: banking aircraft + slim slip ball + roll text ----
    val ccx = w / 2f
    val wing = w * 0.075f
    val ay = rowTop + rowH * 0.28f
    val stroke = 6f
    // Bank the wings WITH the roll: roll>0 = bank right ("D") → right wing down,
    // which is a clockwise (positive) screen rotation.
    rotate(degrees = state.rollDeg, pivot = Offset(ccx, ay)) {
        drawLine(CompactStyle.Accent, Offset(ccx - wing, ay), Offset(ccx - wing * 0.2f, ay), strokeWidth = stroke)
        drawLine(CompactStyle.Accent, Offset(ccx + wing * 0.2f, ay), Offset(ccx + wing, ay), strokeWidth = stroke)
        drawLine(CompactStyle.Accent, Offset(ccx, ay), Offset(ccx, ay - wing * 0.4f), strokeWidth = stroke)
        drawCircle(CompactStyle.Accent, radius = 5f, center = Offset(ccx, ay))
    }
    // Slim ball pill.
    val pillW = w * 0.26f; val pillH = rowH * 0.16f; val pcy = rowTop + rowH * 0.58f
    drawRoundRect(Color(0xFF2A2A2A), topLeft = Offset(ccx - pillW / 2, pcy - pillH / 2), size = Size(pillW, pillH),
        cornerRadius = CornerRadius(pillH / 2, pillH / 2))
    drawRoundRect(Color(0xFF6A6A6A), topLeft = Offset(ccx - pillW / 2, pcy - pillH / 2), size = Size(pillW, pillH),
        cornerRadius = CornerRadius(pillH / 2, pillH / 2), style = Stroke(width = 2f))
    val ballR = pillH * 0.40f
    listOf(-ballR - 5f, ballR + 5f).forEach { dx ->
        drawLine(CompactStyle.Mark, Offset(ccx + dx, pcy - ballR - 2f), Offset(ccx + dx, pcy + ballR + 2f), strokeWidth = 2f)
    }
    drawCircle(CompactStyle.Accent, radius = ballR, center = Offset(ccx + state.slip.coerceIn(-1f, 1f) * (pillW * 0.32f), pcy))
    val side = if (state.rollDeg >= 0f) "D" else "G"
    compactText(tm, "${abs(state.rollDeg).roundToInt()}° $side", ccx, rowTop + rowH * 0.86f, sizeSp = 18f, bold = true, color = CompactStyle.Accent)
}

/** Horizontal speed-arc bar (Vs0..Vne) with a white cursor at the current speed,
 *  matching the NUMSPD colour bands. Values are expected in the display unit. */
private fun DrawScope.drawSpeedArcBar(r: Rect, arcs: SpeedArcs?, speed: Float) {
    // Track background.
    drawRect(Color(0xFF1E1E1E), topLeft = Offset(r.left, r.top), size = Size(r.width, r.height))
    if (arcs == null || !arcs.hasAny) return
    // Scale the bar from 0 to Vne (or the largest known speed).
    val maxV = (arcs.vne ?: arcs.vno ?: arcs.greenMax ?: arcs.whiteMax ?: 0).toFloat()
    if (maxV <= 0f) return
    fun x(v: Float) = r.left + (v / maxV).coerceIn(0f, 1f) * r.width
    fun band(fromV: Int?, toV: Int?, color: Color) {
        if (fromV == null || toV == null || toV <= fromV) return
        drawRect(color, topLeft = Offset(x(fromV.toFloat()), r.top), size = Size(x(toV.toFloat()) - x(fromV.toFloat()), r.height))
    }
    // White arc (flap range), green arc (normal), then Vne block in red.
    band(arcs.whiteMin, arcs.whiteMax, Color(0xFFEFEFEF))
    band(arcs.greenMin, arcs.greenMax, CompactStyle.Climb)
    arcs.vno?.let { vno -> arcs.vne?.let { vne -> band(vno, vne, Color(0xFFFFC107)) } }
    arcs.vne?.let { band(it, (maxV).toInt(), CompactStyle.Descent) }
    // Cursor lines (no room for triangles): red = stall speeds + Vne, magenta = Vpl.
    // Overshoot only upward so nothing dangles below the bar (saves vertical space).
    fun cursorLine(v: Int?, color: Color) {
        if (v == null) return
        val cx = x(v.toFloat())
        drawLine(color, Offset(cx, r.top - 3f), Offset(cx, r.bottom), strokeWidth = 3f)
    }
    cursorLine(arcs.vs0, CompactStyle.Descent)
    cursorLine(arcs.vs1, CompactStyle.Descent)
    cursorLine(arcs.vne, CompactStyle.Descent)
    cursorLine(arcs.vpl, Color(0xFFD24DEA))
    // Current-speed cursor (white line).
    val cx = x(speed)
    drawLine(Color.White, Offset(cx, r.top - 4f), Offset(cx, r.bottom), strokeWidth = 4f)
}
