package com.airchecklists.app.ui.efis.gauges.nav

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.airchecklists.app.data.model.VacChart
import com.airchecklists.app.di.ServiceLocator
import com.airchecklists.app.ui.efis.gauges.compact.compactText
import com.airchecklists.app.ui.efis.gauges.map.addOpenAipLayers
import com.airchecklists.app.ui.efis.gauges.map.buildStyleJson
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style

/**
 * A non-interactive MapLibre map framed on the whole nav route, with a Compose
 * overlay drawing the green route polyline + ICAO labels. Falls back to a plain
 * schematic (RouteMapSchematic) if the offline basemap isn't downloaded.
 */
@Composable
fun NavRouteMap(
    route: List<VacChart>,
    modifier: Modifier = Modifier,
    mapHolder: Array<MapLibreMap?>? = null,
) {
    val basemap = remember { ServiceLocator.mapRepository.basemapFile() }
    if (basemap == null) {
        RouteMapSchematic(route, modifier)
        return
    }
    val tm = rememberTextMeasurer()
    val owner = LocalLifecycleOwner.current
    val mapRef = remember { arrayOfNulls<MapLibreMap>(1) }
    val viewRef = remember { arrayOfNulls<MapView>(1) }
    var cameraTick by remember { mutableIntStateOf(0) }
    val pts = route.filter { it.latitude != null && it.longitude != null }

    Box(modifier = modifier.clipToBounds()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                MapView(ctx).also { mv ->
                    viewRef[0] = mv
                    mv.onCreate(null); mv.onStart(); mv.onResume()
                    mv.getMapAsync { map ->
                        mapRef[0] = map
                        mapHolder?.set(0, map)
                        map.uiSettings.setAllGesturesEnabled(false)
                        map.uiSettings.isLogoEnabled = false
                        map.uiSettings.isAttributionEnabled = false
                        map.addOnCameraIdleListener { cameraTick++ }
                        map.setStyle(Style.Builder().fromJson(buildStyleJson(basemap.absolutePath))) { style ->
                            // No OpenAIP overlays here: the planner map shows only the
                            // route's terrains (drawn as the Compose overlay below).
                            frameRoute(map, pts)
                        }
                    }
                }
            },
            update = { frameRoute(mapRef[0], pts) },
        )
        // Overlay: green route polyline + ICAO labels, projected through the camera.
        Canvas(modifier = Modifier.fillMaxSize()) {
            @Suppress("UNUSED_EXPRESSION") cameraTick
            val m = mapRef[0] ?: return@Canvas
            val proj = m.projection
            fun scr(c: VacChart) = proj.toScreenLocation(LatLng(c.latitude!!, c.longitude!!)).let { Offset(it.x, it.y) }
            if (pts.size >= 2) {
                val path = Path()
                pts.forEachIndexed { i, c -> val o = scr(c); if (i == 0) path.moveTo(o.x, o.y) else path.lineTo(o.x, o.y) }
                drawPath(path, Color(0xFF2ECC40), style = Stroke(width = 7f))
            }
            pts.forEach { c ->
                val o = scr(c)
                drawCircle(Color(0xFF1565C0), radius = 7f, center = o)
                // ICAO label: to the right of the dot, or to the LEFT when the dot is
                // near the right edge (so the label never overflows the map frame).
                val approxW = c.icao.length * 8f + 12f
                val toLeft = o.x + 12f + approxW > size.width
                val lx = if (toLeft) o.x - 12f - approxW else o.x + 12f
                val ly = o.y
                listOf(-1.5f to 0f, 1.5f to 0f, 0f to -1.5f, 0f to 1.5f).forEach { (ox, oy) ->
                    compactText(tm, c.icao, lx + ox, ly + oy, sizeSp = 12f, bold = true, color = Color.White, center = false)
                }
                compactText(tm, c.icao, lx, ly, sizeSp = 12f, bold = true, color = Color(0xFF10305A), center = false)
            }
        }
    }

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
            mapHolder?.set(0, null)
            viewRef[0]?.let { it.onPause(); it.onStop(); it.onDestroy() }
        }
    }
}

/** Frame the camera on the route's bounding box (with padding). */
private fun frameRoute(map: MapLibreMap?, pts: List<VacChart>) {
    val m = map ?: return
    if (pts.isEmpty()) return
    if (pts.size == 1) {
        m.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(pts[0].latitude!!, pts[0].longitude!!), 9.0))
        return
    }
    val b = LatLngBounds.Builder()
    pts.forEach { b.include(LatLng(it.latitude!!, it.longitude!!)) }
    runCatching { m.moveCamera(CameraUpdateFactory.newLatLngBounds(b.build(), 60)) }
}
