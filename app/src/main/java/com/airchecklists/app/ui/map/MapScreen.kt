package com.airchecklists.app.ui.map

import android.view.Gravity
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.foundation.clickable
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.airchecklists.app.R
import com.airchecklists.app.data.model.EfisSpeedUnit
import com.airchecklists.app.data.model.MapOrientation
import com.airchecklists.app.data.sensors.EfisState
import com.airchecklists.app.di.ServiceLocator
import com.airchecklists.app.ui.efis.EfisViewModel
import com.airchecklists.app.ui.efis.gauges.compact.CompactStyle
import com.airchecklists.app.ui.efis.gauges.compact.compactText
import com.airchecklists.app.ui.efis.gauges.compact.efisHeadingTape
import com.airchecklists.app.ui.efis.gauges.compact.speedColor
import com.airchecklists.app.ui.simpleViewModelFactory
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.FillLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.sources.GeoJsonSource
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Full-screen MapLibre moving map. Loads the downloaded local basemap.mbtiles with
 * the OACI-like style, keeps the aircraft centred (north-up / track-up), and draws
 * the EFIS instrument band + ownship as a Compose overlay on top.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapScreen(onBack: () -> Unit, onOpenSettings: () -> Unit) {
    val context = LocalContext.current
    val vm: EfisViewModel = viewModel(factory = simpleViewModelFactory { EfisViewModel(context) })
    val state by vm.state.collectAsStateWithLifecycle()
    val prefs by ServiceLocator.preferences.preferences.collectAsStateWithLifecycle()
    val basemap = remember { ServiceLocator.mapRepository.basemapFile() }
    val tm = rememberTextMeasurer()

    // Feed the EFIS sensors while this screen is visible.
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        vm.start()
        val obs = LifecycleEventObserver { _, e ->
            when (e) {
                Lifecycle.Event.ON_RESUME -> vm.start()
                Lifecycle.Event.ON_PAUSE -> vm.stop()
                else -> {}
            }
        }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs); vm.stop() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.map_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    titleContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    navigationIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
            )
        },
    ) { inner ->
        Box(modifier = Modifier.fillMaxSize().padding(inner)) {
            if (basemap == null) {
                NoMapMessage(onOpenSettings)
            } else {
                MapLibreView(basemap.absolutePath, state, prefs.mapOrientation)
                // Ownship marker fixed at the map centre (camera follows the aircraft).
                if (state.hasPosition) {
                    val acHeading = if (prefs.mapOrientation == MapOrientation.TRACK_UP) 0f else state.gpsTrackDeg
                    OwnshipMarker(acHeading, modifier = Modifier.align(Alignment.Center))
                }
            }
            // EFIS instrument band overlay (top).
            EfisHeaderOverlay(tm, state, prefs.efisSpeedUnit, ServiceLocator.currentAircraft()?.let {
                com.airchecklists.app.data.model.SpeedArcs.from(it).takeIf { a -> a.hasAny }
            })
            // Attribution (bottom-left).
            Text(
                "© OpenStreetMap · © OpenAIP",
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xCC000000),
                modifier = Modifier.align(Alignment.BottomStart).padding(6.dp)
                    .background(Color(0x66FFFFFF)).padding(horizontal = 4.dp),
            )
        }
    }
}

/** Yellow aircraft triangle fixed at the map centre, rotated to the heading. */
@Composable
private fun OwnshipMarker(headingDeg: Float, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(60.dp)) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        rotate(degrees = headingDeg, pivot = Offset(cx, cy)) {
            val p = androidx.compose.ui.graphics.Path().apply {
                moveTo(cx, cy - 26f)
                lineTo(cx + 18f, cy + 20f)
                lineTo(cx, cy + 10f)
                lineTo(cx - 18f, cy + 20f)
                close()
            }
            drawPath(p, Color(0xFFFFB300))
            drawPath(p, Color(0xFF6A3D00), style = Stroke(width = 3f))
        }
    }
}

@Composable
private fun NoMapMessage(onOpenSettings: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            stringResource(R.string.map_not_downloaded),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(onClick = onOpenSettings, modifier = Modifier.padding(top = 16.dp)) {
            Text(stringResource(R.string.map_go_to_settings))
        }
    }
}

/** The MapLibre MapView, following the aircraft position/orientation. */
@Composable
private fun MapLibreView(mbtilesPath: String, state: EfisState, orientation: MapOrientation) {
    val owner = LocalLifecycleOwner.current
    val mapRef = remember { arrayOfNulls<MapLibreMap>(1) }
    val viewRef = remember { arrayOfNulls<MapView>(1) }
    // Remember whether we've already applied the initial zoom (so we don't fight
    // the user's pinch-zoom on later position updates).
    val initedZoom = remember { booleanArrayOf(false) }

    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { ctx ->
            MapView(ctx).also { mv ->
                viewRef[0] = mv
                mv.onCreate(null)
                mv.onStart()
                mv.onResume()
                mv.getMapAsync { map ->
                    mapRef[0] = map
                    map.uiSettings.isRotateGesturesEnabled = false
                    map.uiSettings.isZoomGesturesEnabled = true      // pinch-to-zoom
                    map.uiSettings.isScrollGesturesEnabled = true    // pan
                    map.uiSettings.isDoubleTapGesturesEnabled = true
                    map.uiSettings.setAttributionMargins(15, 0, 0, 15)
                    map.setStyle(Style.Builder().fromJson(buildStyleJson(mbtilesPath))) { style ->
                        addOpenAipLayers(style)
                    }
                }
            }
        },
        update = { _ ->
            val map = mapRef[0]
            if (map != null && state.hasPosition) {
                val bearing = if (orientation == MapOrientation.TRACK_UP) state.gpsTrackDeg.toDouble() else 0.0
                // Keep the user's current zoom once initialised; only recentre + rotate.
                val zoom = if (initedZoom[0]) map.cameraPosition.zoom else 11.0
                initedZoom[0] = true
                map.cameraPosition = CameraPosition.Builder()
                    .target(LatLng(state.latitude, state.longitude))
                    .bearing(bearing)
                    .zoom(zoom)
                    .build()
            }
        },
    )

    // Forward the GL MapView lifecycle to the composition lifecycle.
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

/**
 * Add the extracted OpenAIP GeoJSON layers on top of the basemap:
 *  - airspaces : translucent magenta fill + outline
 *  - aerodromes / navaids / reporting points / obstacles : coloured circle markers
 */
private fun addOpenAipLayers(style: Style) {
    val files = ServiceLocator.mapRepository.openAipFiles()
    files.forEach { file ->
        val json = ServiceLocator.mapRepository.readLayer(file)
        if (json.isBlank()) return@forEach
        val base = file.nameWithoutExtension          // e.g. "airspaces", "aerodromes"
        val srcId = "oaip-$base"
        runCatching { style.addSource(GeoJsonSource(srcId, json)) }.onFailure { return@forEach }

        when (base) {
            "airspaces" -> {
                style.addLayer(
                    FillLayer("$srcId-fill", srcId).withProperties(
                        PropertyFactory.fillColor("#BE4DD6"),
                        PropertyFactory.fillOpacity(0.12f),
                    ),
                )
                style.addLayer(
                    LineLayer("$srcId-line", srcId).withProperties(
                        PropertyFactory.lineColor("#BE4DD6"),
                        PropertyFactory.lineWidth(1.6f),
                    ),
                )
            }
            "obstacles" -> style.addLayer(
                CircleLayer("$srcId-pt", srcId).withProperties(
                    PropertyFactory.circleColor("#D32F2F"),
                    PropertyFactory.circleRadius(3.5f),
                    PropertyFactory.circleStrokeColor("#7A0000"),
                    PropertyFactory.circleStrokeWidth(1f),
                ),
            )
            else -> {
                // aerodromes / navaids / reporting_points -> amber markers.
                val color = if (base.startsWith("aero")) "#1565C0" else "#C9741A"
                style.addLayer(
                    CircleLayer("$srcId-pt", srcId).withProperties(
                        PropertyFactory.circleColor(color),
                        PropertyFactory.circleRadius(4.5f),
                        PropertyFactory.circleStrokeColor("#FFFFFF"),
                        PropertyFactory.circleStrokeWidth(1.2f),
                    ),
                )
            }
        }
    }
}

/**
 * Build the OACI-like style JSON in code, with the vector source pointing at the
 * local mbtiles. Fills + lines only (labels need a glyphs endpoint, added later).
 */
private fun buildStyleJson(mbtilesPath: String): String {
    // MapLibre resolves a local mbtiles vector source via the mbtiles:// scheme.
    val src = "mbtiles:///$mbtilesPath".replace("\\", "/")
    fun line(id: String, cls: String, color: String, w: Double, minZoom: Int? = null): String {
        val mz = if (minZoom != null) """"minzoom": $minZoom,""" else ""
        return """{ "id":"$id","type":"line","source":"basemap","source-layer":"transportation",
            $mz "filter":["==","class","$cls"],
            "paint":{"line-color":"$color","line-width":$w} }"""
    }
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

// ---- EFIS header overlay (reuses the compact drawing helpers) ----

@Composable
private fun EfisHeaderOverlay(
    tm: TextMeasurer,
    state: EfisState,
    unit: EfisSpeedUnit,
    arcs: com.airchecklists.app.data.model.SpeedArcs?,
) {
    val targetHeading by com.airchecklists.app.di.ServiceLocator.targetHeading.collectAsStateWithLifecycle()
    Canvas(
        modifier = Modifier.fillMaxWidth().height(150.dp),
    ) {
        drawEfisHeader(tm, state, unit, arcs, size.height, Color(0xF01A1C1E), targetHeading)
    }
}

private fun DrawScope.drawEfisHeader(
    tm: TextMeasurer,
    state: EfisState,
    unit: EfisSpeedUnit,
    arcs: com.airchecklists.app.data.model.SpeedArcs?,
    headerH: Float,
    headerColor: Color,
    targetHeading: Int? = null,
) {
    val w = size.width
    drawRect(headerColor, topLeft = Offset(0f, 0f), size = Size(w, headerH))
    val topPad = headerH * 0.08f
    val tapeH = headerH * 0.34f
    val heading = ((state.headingDeg.roundToInt() % 360) + 360) % 360
    efisHeadingTape(tm, Rect(0f, topPad, w, topPad + tapeH), heading.toFloat(), showValue = true, targetHeading)
    val rowTop = topPad + tapeH
    val rowH = headerH - rowTop
    val cy = rowTop + rowH / 2f

    val toUnit = if (unit == EfisSpeedUnit.KNOTS) 1f / 1.852f else 1f
    val speed = state.gpsSpeedKmh * toUnit
    val speedLabel = if (unit == EfisSpeedUnit.KNOTS) "kt" else "km/h"
    val trend = if (state.verticalSpeedFtMin >= 0f) CompactStyle.Climb else CompactStyle.Descent

    fun cell(cxc: Float, cyc: Float, wc: Float, hc: Float, value: String, unitTxt: String, color: Color) {
        drawRect(Color(0xFF111111), topLeft = Offset(cxc - wc / 2, cyc - hc / 2), size = Size(wc, hc))
        drawRect(Color(0xFF5A5A5A), topLeft = Offset(cxc - wc / 2, cyc - hc / 2), size = Size(wc, hc), style = Stroke(width = 1.3f))
        compactText(tm, value, cxc - wc * 0.10f, cyc, sizeSp = 22f, bold = true, color = color)
        compactText(tm, unitTxt, cxc + wc * 0.36f, cyc, sizeSp = 10f, color = CompactStyle.Dim)
    }
    val cellW = w * 0.22f
    val cellH = rowH * 0.42f
    val leftCx = w * 0.15f
    cell(leftCx, rowTop + rowH * 0.28f, cellW, cellH, state.gpsAltitudeFt.roundToInt().toString(), "ft", trend)
    cell(leftCx, rowTop + rowH * 0.74f, cellW, cellH, state.verticalSpeedFtMin.roundToInt().toString(), "ft/min", trend)

    val rightCx = w * 0.85f
    cell(rightCx, cy - rowH * 0.08f, cellW, cellH, speed.roundToInt().toString(), speedLabel, speedColor(speed, arcs?.scaled(toUnit)))
    drawRect(CompactStyle.Climb, topLeft = Offset(rightCx - cellW / 2, cy + rowH * 0.22f), size = Size(cellW, 6f))

    // Centre: aircraft + slip ball + roll.
    val ccx = w / 2f
    val wing = w * 0.08f
    val ay = rowTop + rowH * 0.30f
    drawLine(CompactStyle.Accent, Offset(ccx - wing, ay), Offset(ccx - wing * 0.2f, ay), strokeWidth = 6f)
    drawLine(CompactStyle.Accent, Offset(ccx + wing * 0.2f, ay), Offset(ccx + wing, ay), strokeWidth = 6f)
    drawLine(CompactStyle.Accent, Offset(ccx, ay), Offset(ccx, ay - wing * 0.45f), strokeWidth = 6f)
    drawCircle(CompactStyle.Accent, radius = 5f, center = Offset(ccx, ay))
    val pillW = w * 0.28f; val pillH = rowH * 0.28f; val pcy = rowTop + rowH * 0.60f
    drawRoundRect(Color(0xFF2A2A2A), topLeft = Offset(ccx - pillW / 2, pcy - pillH / 2), size = Size(pillW, pillH),
        cornerRadius = CornerRadius(pillH / 2, pillH / 2))
    val ballR = pillH * 0.42f
    drawCircle(CompactStyle.Accent, radius = ballR, center = Offset(ccx + state.slip.coerceIn(-1f, 1f) * (pillW * 0.32f), pcy))
    val side = if (state.rollDeg >= 0f) "D" else "G"
    compactText(tm, "${abs(state.rollDeg).roundToInt()}° $side", ccx, rowTop + rowH * 0.90f, sizeSp = 18f, bold = true, color = CompactStyle.Accent)
}
