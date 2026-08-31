package com.airchecklists.app.ui.efis.gauges.traffic

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.airchecklists.app.di.ServiceLocator
import com.airchecklists.app.ui.efis.gauges.GaugeColors
import com.airchecklists.app.ui.efis.gauges.GaugeLobe
import com.airchecklists.app.ui.efis.gauges.GaugeLobeCentre
import com.airchecklists.app.ui.efis.gauges.drawGaugeLobes
import com.airchecklists.app.ui.efis.gauges.compact.CompactStyle
import com.airchecklists.app.ui.efis.gauges.compact.compactText
import com.airchecklists.app.ui.efis.gauges.gaugeFace
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sqrt

private val client = SafeskyClient()

private const val RANGE_NM = 5.0
private const val DANGER_ALT_FT = 500
private const val DANGER_DIST_NM = 1.0
private const val ALERT_ALT_FT = 1500
private const val ALERT_DIST_NM = 2.0

private enum class DangerLevel { NONE, OK, ALERT, DANGER }

@Composable
fun TrafficAnalogInstrument(modifier: Modifier = Modifier) {
    val tm = rememberTextMeasurer()
    val state by ServiceLocator.efisProvider.state.collectAsStateWithLifecycle()
    val apiKey = ServiceLocator.preferences.preferences.value.safeskyApiKey

    var beacons by remember { mutableStateOf<List<SafeskyBeacon>>(emptyList()) }
    var connected by remember { mutableStateOf<Boolean?>(null) }
    @Suppress("UNUSED_VARIABLE") var lastFetch by remember { mutableLongStateOf(0L) }

    val hasPos = state.hasPosition
    val lat = state.latitude
    val lon = state.longitude
    val ownAltFt = state.gpsAltitudeFt.toInt()

    LaunchedEffect(hasPos, apiKey) {
        if (apiKey.isNullOrBlank()) {
            connected = null
            beacons = emptyList()
            return@LaunchedEffect
        }
        while (true) {
            runCatching { client.fetchBeacons(lat, lon, RANGE_NM, apiKey) }
                .onSuccess { list ->
                    beacons = list
                    connected = true
                    lastFetch = System.currentTimeMillis()
                }
                .onFailure { connected = false }
            kotlinx.coroutines.delay(5_000)
        }
    }

    Canvas(modifier = modifier.fillMaxSize()) {
        drawTraffic(tm, beacons, connected, ownAltFt, lat, lon)
    }
}

private fun DrawScope.drawTraffic(
    tm: TextMeasurer,
    beacons: List<SafeskyBeacon>,
    connected: Boolean?,
    ownAltFt: Int,
    lat: Double,
    lon: Double,
) {
    val (cx, cy, r) = gaugeFace()

    compactText(tm, "TRAFIC", cx, cy - r * 0.78f, sizeSp = 12f, color = CompactStyle.Dim)

    val clipCircle = Path().apply { addOval(Rect(cx - r, cy - r, cx + r, cy + r)) }
    clipPath(clipCircle) {
        drawRadarContent(tm, beacons, ownAltFt, lat, lon, cx, cy, r)
    }

    drawOwnShip(cx, cy, r)

    val noKey = connected == null
    val connStr: String
    val connColor: Color
    when {
        noKey             -> { connStr = "---";  connColor = GaugeColors.MarkDim }
        connected == true -> { connStr = "OK";   connColor = CompactStyle.Climb }
        else              -> { connStr = "DÉCO"; connColor = CompactStyle.Descent }
    }

    val (dangerLevel, closestNm) = computeDanger(beacons, ownAltFt, lat, lon)

    val dangerColor: Color
    val dangerLabel: String
    val dangerPrimary: String
    val dangerSub: String

    if (noKey || connected == false) {
        dangerColor = GaugeColors.MarkDim; dangerLabel = "---"
        dangerPrimary = "---"; dangerSub = ""
    } else {
        when (dangerLevel) {
            DangerLevel.DANGER -> {
                dangerColor = Color(0xFFFF3B30); dangerLabel = "DANGER"
                dangerPrimary = "${beacons.size}"
                dangerSub = if (closestNm < 99.0) "${"%.1f".format(closestNm)}nm" else ""
            }
            DangerLevel.ALERT -> {
                dangerColor = Color(0xFFFF9500); dangerLabel = "ALERTE"
                dangerPrimary = "${beacons.size}"
                dangerSub = if (closestNm < 99.0) "${"%.1f".format(closestNm)}nm" else ""
            }
            DangerLevel.OK -> {
                dangerColor = CompactStyle.Climb; dangerLabel = "OK"
                dangerPrimary = "${beacons.size}"
                dangerSub = if (closestNm < 99.0) "${"%.1f".format(closestNm)}nm" else ""
            }
            DangerLevel.NONE -> {
                dangerColor = GaugeColors.MarkDim; dangerLabel = "---"
                dangerPrimary = "0"; dangerSub = ""
            }
        }
    }

    drawGaugeLobes(
        tm, cx, cy, r,
        left   = GaugeLobe("CONN",   connStr,    connColor),
        centre = GaugeLobeCentre(dangerPrimary, dangerSub, dangerColor, GaugeColors.MarkDim),
        right  = GaugeLobe("NIVEAU", dangerLabel, dangerColor),
    )
}

private fun DrawScope.drawRadarContent(
    tm: TextMeasurer,
    beacons: List<SafeskyBeacon>,
    ownAltFt: Int,
    lat: Double,
    lon: Double,
    cx: Float,
    cy: Float,
    r: Float,
) {
    val ringPaint = Color(0x44FFFFFF)
    val ring1R = (r.toDouble() * 1.0 / RANGE_NM).toFloat()
    val ring3R = (r.toDouble() * 3.0 / RANGE_NM).toFloat()
    drawCircle(ringPaint, radius = ring1R, center = Offset(cx, cy), style = Stroke(width = 1f))
    drawCircle(ringPaint, radius = ring3R, center = Offset(cx, cy), style = Stroke(width = 1f))

    val mPerDegLat = 111_320.0
    val mPerDegLon = 111_320.0 * cos(Math.toRadians(lat))
    val rangeM = RANGE_NM * 1852.0

    beacons.forEach { b ->
        val northM = (b.latitude - lat) * mPerDegLat
        val eastM  = (b.longitude - lon) * mPerDegLon
        val distM  = sqrt(northM * northM + eastM * eastM)
        if (distM > rangeM * 1.1) return@forEach
        val bx = (cx + (eastM / rangeM * r.toDouble())).toFloat()
        val by = (cy - (northM / rangeM * r.toDouble())).toFloat()
        val color = beaconColor(b.beaconType)
        drawCircle(color, radius = 5f, center = Offset(bx, by))
        val altDelta = b.altitude - ownAltFt
        val sign = if (altDelta >= 0) "+" else ""
        compactText(tm, "$sign$altDelta", bx, by - 10f, sizeSp = 8f, color = color)
    }
}

private fun beaconColor(beaconType: String): Color = when (beaconType.lowercase()) {
    "drone"                           -> Color(0xFFFF3B30)
    "paraglider", "parachute", "para" -> Color(0xFFFFCC00)
    "glider"                          -> Color(0xFF64D2FF)
    "helicopter"                      -> Color(0xFFFF9F0A)
    else                              -> Color(0xFFFFFFFF)
}

private fun DrawScope.drawOwnShip(cx: Float, cy: Float, r: Float) {
    val s = r * 0.06f
    val path = Path().apply {
        moveTo(cx, cy - s * 2f)
        lineTo(cx + s, cy + s)
        lineTo(cx, cy + s * 0.5f)
        lineTo(cx - s, cy + s)
        close()
    }
    drawPath(path, Color(0xFFFFFFFF))
}

private fun computeDanger(
    beacons: List<SafeskyBeacon>,
    ownAltFt: Int,
    lat: Double,
    lon: Double,
): Pair<DangerLevel, Double> {
    if (beacons.isEmpty()) return Pair(DangerLevel.NONE, 99.0)
    val mPerDegLat = 111_320.0
    val mPerDegLon = 111_320.0 * cos(Math.toRadians(lat))
    var worst = DangerLevel.OK
    var closestNm = 99.0
    beacons.forEach { b ->
        val northM = (b.latitude - lat) * mPerDegLat
        val eastM  = (b.longitude - lon) * mPerDegLon
        val distNm = sqrt(northM * northM + eastM * eastM) / 1852.0
        val altDelta = abs(b.altitude - ownAltFt)
        if (distNm < closestNm) closestNm = distNm
        val level = when {
            altDelta < DANGER_ALT_FT && distNm < DANGER_DIST_NM -> DangerLevel.DANGER
            altDelta < ALERT_ALT_FT  && distNm < ALERT_DIST_NM  -> DangerLevel.ALERT
            else -> DangerLevel.OK
        }
        if (level.ordinal > worst.ordinal) worst = level
    }
    return Pair(worst, closestNm)
}
