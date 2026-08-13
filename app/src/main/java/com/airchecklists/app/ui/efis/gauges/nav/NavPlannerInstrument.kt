package com.airchecklists.app.ui.efis.gauges.nav

import com.airchecklists.app.ui.efis.gauges.drawNumTitleBar
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.airchecklists.app.data.model.NavPlan
import com.airchecklists.app.data.model.SpeedArcs
import com.airchecklists.app.data.model.VacChart
import com.airchecklists.app.di.ServiceLocator
import com.airchecklists.app.ui.efis.gauges.compact.CompactStyle
import com.airchecklists.app.ui.efis.gauges.map.NavMath
import com.airchecklists.app.ui.terrain.QfuParser
import kotlin.math.roundToInt

private const val DEFAULT_CRUISE_KT = 90.0
private val ORANGE = Color(0xFFE8843A)
private val PANEL = Color(0xFF1A1A1A)

// Column weights shared by the log header AND the value cells (guarantees alignment).
private const val WEIGHT_RM = 1.0f
private const val WEIGHT_DIST = 0.75f
private const val WEIGHT_TSV = 0.75f
private const val WEIGHT_FICHE = 3.0f
private val TERRAIN_ROW = 84.dp   // fixed terrain-row height (legs straddle boundaries)

/** Computed leg between two consecutive terrains. */
private data class NavLeg(val rm: Int?, val distNm: Double?, val timeSec: Int?)

/**
 * CMNNAV — Prépa Navigation. Pick terrains → auto-computes per-leg course / distance
 * / no-wind time, shows a schematic route map + free-text notes, and can launch the
 * route on the moving map (NUMMAP) or export a PDF.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NavPlannerInstrument(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val plan by ServiceLocator.navPlan.collectAsStateWithLifecycle()
    val charts by ServiceLocator.vacRepository.charts.collectAsStateWithLifecycle()
    var showPicker by remember { mutableStateOf(false) }
    // Holds the live MapLibre map so the PDF export can snapshot the real basemap.
    val mapHolder = remember { arrayOfNulls<org.maplibre.android.maps.MapLibreMap>(1) }

    // Resolve the ordered plan into VacCharts (skip unknown ICAOs).
    val byIcao = charts.associateBy { it.icao.uppercase() }
    val route = plan.icaos.mapNotNull { byIcao[it.uppercase()] }

    // Cruise speed (kt) from the current aircraft's green-arc top, else default.
    val cruiseKt = remember(ServiceLocator.currentAircraft()?.id) {
        val a = ServiceLocator.currentAircraft()
        val arcs = a?.let { SpeedArcs.from(it) }
        val kmh = arcs?.greenMax ?: arcs?.vno
        if (kmh != null && kmh > 0) kmh / 1.852 else DEFAULT_CRUISE_KT
    }

    // Leg values between consecutive terrains: leg[i] = terrain(i) → terrain(i+1).
    val legs = (0 until (route.size - 1).coerceAtLeast(0)).map { i ->
        val a = route[i]; val b = route[i + 1]
        if (a.latitude == null || a.longitude == null || b.latitude == null || b.longitude == null) {
            NavLeg(null, null, null)
        } else {
            val brg = NavMath.bearingDeg(a.latitude, a.longitude, b.latitude, b.longitude).roundToInt()
            val dist = NavMath.distanceNm(a.latitude, a.longitude, b.latitude, b.longitude)
            val t = if (cruiseKt > 0) (dist / cruiseKt * 3600.0).roundToInt() else null
            NavLeg(brg, dist, t)
        }
    }

    Column(modifier = modifier.fillMaxSize().background(CompactStyle.Bg)) {
        // ---- Top: map (left) + notes (right) ----
        Row(modifier = Modifier.fillMaxWidth().weight(1.1f)) {
            PanelBox("CARTE", modifier = Modifier.weight(1f).fillMaxSize().padding(4.dp)) {
                NavRouteMap(route, modifier = Modifier.fillMaxSize(), mapHolder = mapHolder)
            }
            PanelBox("NOTES", modifier = Modifier.weight(1f).fillMaxSize().padding(4.dp)) {
                OutlinedTextField(
                    value = plan.notes,
                    onValueChange = { ServiceLocator.setNavPlan(plan.copy(notes = it)) },
                    modifier = Modifier.fillMaxSize(),
                    textStyle = androidx.compose.ui.text.TextStyle(color = Color.White, fontSize = 13.sp),
                )
            }
        }

        // ---- Log: leg values (RM/DIST/TSV) straddle consecutive terrains ----
        PanelBox("LOG", modifier = Modifier.fillMaxWidth().weight(1.4f).padding(4.dp)) {
            Column(Modifier.fillMaxSize()) {
                // Header aligned to the exact same weights as the value columns.
                // Neutral background: this is a TABLE column header, not a title bar,
                // so it must NOT take the bezel accent colour.
                Row(Modifier.fillMaxWidth().background(Color(0xFF2A2A2A)).padding(vertical = 4.dp)) {
                    LogHeaderCell("RM", WEIGHT_RM)
                    LogHeaderCell("DIST", WEIGHT_DIST)
                    LogHeaderCell("TSV", WEIGHT_TSV)
                    Text("Terrain et infos", color = CompactStyle.Dim, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(WEIGHT_FICHE).padding(start = 8.dp))
                }
                if (route.isEmpty()) {
                    Text("Ajoutez des terrains avec « + ».", color = CompactStyle.Dim, fontSize = 13.sp,
                        modifier = Modifier.padding(12.dp))
                } else {
                    NavLog(
                        route = route, legs = legs, plan = plan,
                        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                    )
                }
            }
        }

        // ---- Bottom action buttons ----
        Row(
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            NavCircle(onClick = { showPicker = true }) { Icon(Icons.Filled.Add, null, tint = Color.White) }
            NavCircle(onClick = {
                val exp = route.mapIndexed { i, c ->
                    val inbound = if (i > 0) legs.getOrNull(i - 1) else null
                    val dsg = runwayDesignator(c.circuit)
                    NavStepExport(
                        icao = c.icao,
                        rm = inbound?.rm?.let { "%03d°".format(it) },
                        dist = inbound?.distNm?.let { "%d nm".format(it.roundToInt()) },
                        tsv = inbound?.timeSec?.let { "%d’".format(it / 60) },
                        detail = terrainDetail(c),
                        qfu = QfuParser.primaryHeading(c.circuit)?.toFloat() ?: designatorToQfu(dsg),
                        designator = dsg,
                    )
                }
                // Snapshot the real MapLibre basemap (async), then build the PDF; if
                // no map is available, export with the schematic fallback.
                val map = mapHolder[0]
                if (map != null) {
                    map.snapshot { bmp -> exportPdf(context, route, exp, plan.notes, bmp) }
                } else {
                    exportPdf(context, route, exp, plan.notes, null)
                }
            }) {
                Text("PDF", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
            NavCircle(onClick = {
                ServiceLocator.activeNavRoute.value = route
                    .filter { it.latitude != null && it.longitude != null }
                    .map { doubleArrayOf(it.latitude!!, it.longitude!!) }
                android.widget.Toast.makeText(context, "Nav chargée dans la carte (NUMMAP)", android.widget.Toast.LENGTH_SHORT).show()
            }) { Icon(Icons.Filled.Map, null, tint = Color.White) }
        }
    }

    if (showPicker) {
        TerrainPickerDialog(
            charts = charts,
            onDismiss = { showPicker = false },
            onPick = { icao -> ServiceLocator.setNavPlan(plan.copy(icaos = plan.icaos + icao)); showPicker = false },
        )
    }
}

private fun removeAt(plan: NavPlan, index: Int) {
    ServiceLocator.setNavPlan(plan.copy(icaos = plan.icaos.toMutableList().also { if (index in it.indices) it.removeAt(index) }))
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.LogHeaderCell(text: String, weight: Float) {
    Text(text, color = CompactStyle.Dim, fontSize = 12.sp, fontWeight = FontWeight.Bold,
        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        modifier = Modifier.weight(weight))
}

/**
 * The log, matching the MYLOG mock: one block per terrain (name+town | freq+alt |
 * runway circle + designator), and BELOW each block (except the last) the leg band
 * RM/DIST/TSV to the next terrain, then a separator line.
 */
@Composable
private fun NavLog(route: List<VacChart>, legs: List<NavLeg>, plan: NavPlan, modifier: Modifier) {
    Column(modifier = modifier) {
        route.forEachIndexed { i, c ->
            TerrainBlock(chart = c, onRemove = { removeAt(plan, i) })
            // Leg band to the next terrain (left-aligned RM/DIST/TSV).
            if (i < route.size - 1) LegBand(legs.getOrNull(i) ?: NavLeg(null, null, null))
            androidx.compose.material3.HorizontalDivider(color = Color(0xFF333333), thickness = 1.dp)
        }
    }
}

@Composable
private fun TerrainBlock(chart: VacChart, onRemove: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Name + town.
        Column(modifier = Modifier.weight(1.3f)) {
            Text(chart.icao, color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.Bold)
            Text(chart.airfieldName, color = Color(0xFFBBBBBB), fontSize = 11.sp, maxLines = 1)
        }
        // Frequency + altitude.
        Column(modifier = Modifier.weight(1.1f)) {
            chart.frequencies.takeIf { it.isNotBlank() }?.let { Text("$it MHz", color = ORANGE, fontSize = 13.sp, maxLines = 1) }
            chart.altitude.takeIf { it.isNotBlank() }?.let { Text(it, color = ORANGE, fontSize = 13.sp, maxLines = 1) }
        }
        // Runway circle + designator below. QFU from the circuit, else from the
        // designator (e.g. "04/22" → 40°); always shown when we have either.
        val designator = runwayDesignator(chart.circuit)
        val qfu = QfuParser.primaryHeading(chart.circuit)?.toFloat() ?: designatorToQfu(designator)
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(60.dp)) {
            RunwayCircle(qfu ?: 0f)
            (designator ?: qfu?.let { "%03d°".format(it.toInt()) })?.let {
                Text(it, color = ORANGE, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
        // Remove.
        Box(Modifier.size(28.dp).clickable { onRemove() }, contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.Close, "Retirer", tint = Color(0xFF888888), modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun LegBand(leg: NavLeg) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LegCell(leg.rm?.let { "%03d°".format(it) } ?: "—", WEIGHT_RM)
        LegCell(leg.distNm?.let { "%d".format(it.roundToInt()) } ?: "—", WEIGHT_DIST)
        LegCell(leg.timeSec?.let { "%d’".format(it / 60) } ?: "—", WEIGHT_TSV)
        Box(Modifier.weight(WEIGHT_FICHE))  // spacer under the fiche columns
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.LegCell(text: String, weight: Float) {
    Box(
        modifier = Modifier.weight(weight).height(38.dp).padding(horizontal = 2.dp).background(PANEL),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
    }
}

/** Runway circle: white disc, dark border, blue strip rotated to [qfu] (like the mock). */
@Composable
private fun RunwayCircle(qfu: Float) {
    Surface(shape = CircleShape, color = Color.White,
        border = androidx.compose.foundation.BorderStroke(2.dp, Color(0xFF10305A)), modifier = Modifier.size(40.dp)) {
        Box(contentAlignment = Alignment.Center) {
            Canvas(modifier = Modifier.size(30.dp)) {
                rotate(degrees = qfu, pivot = center) {
                    val cx = size.width / 2f
                    val len = size.height * 0.42f
                    drawLine(Color(0xFF1565C0), Offset(cx, center.y - len), Offset(cx, center.y + len), strokeWidth = 12f)
                }
            }
        }
    }
}

@Composable
private fun PanelBox(title: String, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val bezel = com.airchecklists.app.ui.efis.gauges.LocalGaugeBezel.current
    Column(modifier = modifier.border(1.dp, Color(0xFF333333))) {
        Text(title, color = CompactStyle.Dim, fontSize = 12.sp, fontWeight = FontWeight.Bold,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .drawBehind { drawNumTitleBar(bezel, size.width, size.height) }
                .padding(vertical = 3.dp))
        Box(Modifier.fillMaxSize()) { content() }
    }
}

@Composable
private fun NavCircle(onClick: () -> Unit, content: @Composable () -> Unit) {
    Surface(
        onClick = onClick, shape = CircleShape, color = Color(0xFF1A2026),
        border = androidx.compose.foundation.BorderStroke(2.dp, Color.White),
        modifier = Modifier.size(48.dp),
    ) { Box(contentAlignment = Alignment.Center) { content() } }
}

/** Schematic route map (fallback when the offline basemap isn't available). */
@Composable
internal fun RouteMapSchematic(route: List<VacChart>, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.background(Color(0xFFE9E6DE))) {
        val pts = route.filter { it.latitude != null && it.longitude != null }
        if (pts.isEmpty()) return@Canvas
        val lats = pts.map { it.latitude!! }; val lons = pts.map { it.longitude!! }
        val minLat = lats.min(); val maxLat = lats.max(); val minLon = lons.min(); val maxLon = lons.max()
        val pad = 24f
        val w = size.width - 2 * pad; val h = size.height - 2 * pad
        val spanLat = (maxLat - minLat).coerceAtLeast(0.01); val spanLon = (maxLon - minLon).coerceAtLeast(0.01)
        fun sx(lon: Double) = pad + ((lon - minLon) / spanLon * w).toFloat()
        fun sy(lat: Double) = pad + ((maxLat - lat) / spanLat * h).toFloat()
        // Polyline.
        if (pts.size >= 2) {
            val path = Path()
            pts.forEachIndexed { i, c ->
                val x = sx(c.longitude!!); val y = sy(c.latitude!!)
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(path, Color(0xFF2ECC40), style = Stroke(width = 6f))
        }
        // Waypoint dots.
        pts.forEach { c -> drawCircle(Color(0xFF1565C0), radius = 6f, center = Offset(sx(c.longitude!!), sy(c.latitude!!))) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TerrainPickerDialog(charts: List<VacChart>, onDismiss: () -> Unit, onPick: (String) -> Unit) {
    var query by remember { mutableStateOf("") }
    val q = query.trim()
    val filtered = charts.filter {
        q.isEmpty() || it.icao.contains(q, true) || it.airfieldName.contains(q, true)
    }.sortedBy { it.icao }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Ajouter un terrain") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = query, onValueChange = { query = it }, singleLine = true,
                    leadingIcon = { Icon(Icons.Filled.Search, null) },
                    placeholder = { Text("Rechercher (OACI / nom)") },
                    modifier = Modifier.fillMaxWidth(),
                )
                LazyColumn(Modifier.fillMaxWidth().heightIn(max = 340.dp)) {
                    items(filtered, key = { it.id }) { c ->
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable { onPick(c.icao) }.padding(vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(c.icao, fontWeight = FontWeight.Bold, modifier = Modifier.padding(end = 10.dp))
                            Text(c.airfieldName, maxLines = 1)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Fermer") } },
    )
}

/** Runway designator from a free-text circuit, KEEPING the circuit-direction letter
 *  (G = main gauche, D = droite): "04/22", "04G/22G", "(1600ft) 04G/22G" → "04G/22G". */
private fun runwayDesignator(circuit: String): String? {
    val m = Regex("""(\d{2}[A-Za-z]?)\s*/\s*(\d{2}[A-Za-z]?)""").find(circuit) ?: return null
    return "${m.groupValues[1]}/${m.groupValues[2]}"
}

/** QFU (degrees) from a designator like "04G/22G" → 40 (leading 2 digits ×10). */
internal fun designatorToQfu(designator: String?): Float? =
    designator?.take(2)?.toIntOrNull()?.times(10)?.toFloat()
