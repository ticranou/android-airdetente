package com.airchecklists.app.ui.efis.gauges.circuit

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.airchecklists.app.data.model.VacChart
import com.airchecklists.app.data.repository.AerodromeDirectory
import com.airchecklists.app.di.ServiceLocator
import com.airchecklists.app.ui.efis.gauges.GaugeColors
import com.airchecklists.app.ui.efis.gauges.GaugeLobe
import com.airchecklists.app.ui.efis.gauges.GaugeLobeCentre
import com.airchecklists.app.ui.efis.gauges.LocalGaugeBezel
import com.airchecklists.app.ui.efis.gauges.drawGaugeLobes
import com.airchecklists.app.ui.efis.gauges.gaugeText
import com.airchecklists.app.ui.efis.gauges.gaugeTitle
import com.airchecklists.app.ui.efis.gauges.gaugeFace
import com.airchecklists.app.ui.efis.gauges.polar
import com.airchecklists.app.ui.terrain.QfuParser
import kotlin.math.abs
import kotlin.math.roundToInt

private val CIRCUIT_COLOR = Color(0xFFFFCC00)   // yellow cursor
private val ALT_OK   = Color(0xFF32C832)         // ±50 ft green
private val ALT_WARN = Color(0xFFFFC107)         // > ±50 ft yellow
private val ALT_LOW  = Color(0xFFE53935)         // below circuit red

/**
 * ANLCCT — Circuit de piste (round gauge).
 *
 * Compass card (like ANLCAP) with:
 * - Enlarged yellow cursor triangle at the top index.
 * - Nearest runway QFU drawn on the compass ring.
 * - Three lobes: Cap | Circuit (ICAO + alti circuit) | Altitude.
 *   Altitude lobe is color-coded: RED below circuit, GREEN within ±50 ft, YELLOW otherwise.
 *
 * Circuit altitude = nearest airfield elevation + 1000 ft (standard VFR circuit).
 * INDICATIVE ONLY, not certified.
 */
@Composable
fun CircuitInstrument(
    headingDeg: Float,
    altitudeFt: Float,
    showValue: Boolean,
    modifier: Modifier = Modifier,
) {
    val tm = rememberTextMeasurer()
    val bezel = LocalGaugeBezel.current
    val charts by ServiceLocator.vacRepository.charts.collectAsStateWithLifecycle()
    val state by ServiceLocator.efisProvider.state.collectAsStateWithLifecycle()

    // Resolve nearest aerodrome off-UI-thread; recompute every ~550 m of movement.
    val latKey = (state.latitude * 200).roundToInt()
    val lonKey = (state.longitude * 200).roundToInt()

    data class CircuitTarget(
        val icao: String,
        val elevationFt: Int?,
        val qfuDeg: Float?,   // primary runway heading, degrees
    )

    val target by produceState<CircuitTarget?>(
        initialValue = null, charts, state.hasPosition, latKey, lonKey,
    ) {
        value = if (!state.hasPosition) null else {
            val mPerDegLat = 111_320.0

            fun distM(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
                val dlat = (lat2 - lat1) * mPerDegLat
                val dlon = (lon2 - lon1) * mPerDegLat * kotlin.math.cos(Math.toRadians(lat1))
                return kotlin.math.sqrt(dlat * dlat + dlon * dlon)
            }

            // AerodromeDirectory is only useful when the nearest entry is within 15 km.
            val a = AerodromeDirectory.nearest(state.latitude, state.longitude, 1)
                .firstOrNull { distM(state.latitude, state.longitude, it.lat, it.lon) < 15_000 }

            if (a != null) {
                val chart = charts.firstOrNull { it.icao.equals(a.icao, ignoreCase = true) }
                val circuit = chart?.circuit ?: ""
                val qfu = QfuParser.primaryHeading(circuit)?.toFloat()
                    ?: pickNearestQfu(state.latitude, state.longitude, a.lat, a.lon, circuit)
                CircuitTarget(a.icao, a.elevationFt, qfu)
            } else {
                // Fallback: nearest user chart with known position, within 15 km.
                val c = charts.filter { it.latitude != null && it.longitude != null }
                    .map { ch -> ch to distM(state.latitude, state.longitude, ch.latitude!!, ch.longitude!!) }
                    .filter { (_, d) -> d < 15_000 }
                    .minByOrNull { (_, d) -> d }
                    ?.first
                if (c != null) {
                    val elevFt = Regex("""\d+""").find(c.altitude)?.value?.toIntOrNull()
                    val qfu = QfuParser.primaryHeading(c.circuit)?.toFloat()
                    CircuitTarget(c.icao, elevFt, qfu)
                } else null
            }
        }
    }

    val circuitAltFt: Int? = target?.elevationFt?.let { it + 1000 }

    Canvas(modifier = modifier.fillMaxSize()) {
        val (cx, cy, r) = gaugeFace(bezel)

        // ── Compass card (rotates so current heading sits under top index) ──
        rotate(degrees = -headingDeg, pivot = Offset(cx, cy)) {
            // Tick marks every 5°.
            for (deg in 0 until 360 step 5) {
                val major = deg % 10 == 0
                val outer = r * 0.96f
                val inner = r * (if (major) 0.84f else 0.90f)
                val p1 = polar(cx, cy, inner, deg.toFloat())
                val p2 = polar(cx, cy, outer, deg.toFloat())
                drawLine(GaugeColors.Mark, p1, p2, strokeWidth = if (major) 2.5f else 1.5f)
            }
            // Labels every 30°.
            for (deg in 0 until 360 step 30) {
                val label = when (deg) {
                    0 -> "N"; 90 -> "E"; 180 -> "S"; 270 -> "W"
                    else -> (deg / 10).toString()
                }
                val pos = polar(cx, cy, r * 0.70f, deg.toFloat())
                rotate(degrees = headingDeg, pivot = pos) {
                    gaugeText(
                        tm, label, pos.x, pos.y,
                        sizeSp = if (deg % 90 == 0) 20f else 16f, bold = true,
                    )
                }
            }

            // QFU runway indicator — grey rectangle traversing the compass centre.
            target?.qfuDeg?.let { qfu ->
                val rwyLen = r * 0.52f
                val rwyW   = r * 0.040f
                rotate(degrees = qfu, pivot = Offset(cx, cy)) {
                    drawRect(
                        color = GaugeColors.MarkDim,
                        topLeft = Offset(cx - rwyW, cy - rwyLen),
                        size = Size(rwyW * 2f, rwyLen * 2f),
                    )
                }
            }
        }

        // ── Fixed top cursor (yellow) — index indicating current heading ──
        val tip = polar(cx, cy, r * 0.99f, 0f)
        val cursorPath = Path().apply {
            moveTo(tip.x, tip.y)
            lineTo(cx - 26f, tip.y + 52f)
            lineTo(cx + 26f, tip.y + 52f)
            close()
        }
        drawPath(cursorPath, CIRCUIT_COLOR)

        // ── Fixed aircraft silhouette ──
        drawAircraftSilhouette(cx, cy, r * 0.42f)

        // ── Title ──
        gaugeTitle(tm, "CIRCUIT", cx, cy, r)

        // ── Three lobes ──
        val hdg = (((headingDeg.toInt()) % 360) + 360) % 360
        val capTxt = "${hdg.toString().padStart(3, '0')}°"

        val altTxt = if (state.hasPosition) "${altitudeFt.roundToInt()} ft" else "—"
        val altColor = when {
            circuitAltFt == null || !state.hasPosition -> GaugeColors.MarkDim
            altitudeFt < circuitAltFt - 50 -> ALT_LOW
            abs(altitudeFt - circuitAltFt) <= 50 -> ALT_OK
            else -> ALT_WARN
        }

        val circAltiTxt = circuitAltFt?.let { "$it ft" } ?: "—"

        drawGaugeLobes(
            tm, cx, cy, r,
            left = GaugeLobe("CAP", capTxt, GaugeColors.Mark),
            centre = GaugeLobeCentre(
                primary = target?.icao ?: "—",
                sub = altTxt,
                primaryColor = if (target != null) GaugeColors.Accent else GaugeColors.MarkDim,
                subColor = altColor,
            ),
            right = GaugeLobe("CIRC", circAltiTxt, GaugeColors.MarkDim),
        )
    }
}

/** Pick the primary QFU using bearing from ship to ARP when no circuit text available. */
private fun pickNearestQfu(
    shipLat: Double, shipLon: Double,
    arpLat: Double, arpLon: Double,
    circuit: String,
): Float {
    val headings = QfuParser.parse(circuit)
    if (headings.isEmpty()) return Float.NaN
    val toArp = com.airchecklists.app.ui.efis.gauges.map.NavMath.bearingDeg(shipLat, shipLon, arpLat, arpLon)
    return headings.minByOrNull { h -> abs(((h.toDouble() - toArp + 540.0) % 360.0) - 180.0) }!!.toFloat()
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawAircraftSilhouette(
    cx: Float, cy: Float, s: Float,
) {
    val c = GaugeColors.Mark
    val w = 5f
    drawLine(c, Offset(cx, cy - s), Offset(cx, cy + s * 0.8f), strokeWidth = w)
    drawLine(c, Offset(cx - s * 0.9f, cy), Offset(cx + s * 0.9f, cy), strokeWidth = w)
    drawLine(c, Offset(cx - s * 0.3f, cy + s * 0.7f), Offset(cx + s * 0.3f, cy + s * 0.7f), strokeWidth = w)
}
