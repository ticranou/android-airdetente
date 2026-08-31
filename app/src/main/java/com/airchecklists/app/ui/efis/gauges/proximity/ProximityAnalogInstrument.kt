package com.airchecklists.app.ui.efis.gauges.proximity

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.airchecklists.app.data.terrain.SrtmProvider
import com.airchecklists.app.di.ServiceLocator
import com.airchecklists.app.ui.efis.gauges.GaugeColors
import com.airchecklists.app.ui.efis.gauges.GaugeLobe
import com.airchecklists.app.ui.efis.gauges.GaugeLobeCentre
import com.airchecklists.app.ui.efis.gauges.drawGaugeLobes
import com.airchecklists.app.ui.efis.gauges.compact.CompactStyle
import com.airchecklists.app.ui.efis.gauges.compact.compactText
import com.airchecklists.app.ui.efis.gauges.gaugeFace

// TAWS-like alert thresholds (AGL in feet)
private const val PULL_UP_FT    = 300   // immediate danger — red PULL UP
private const val CAUTION_FT    = 700   // caution — orange
private const val ADVISORY_FT   = 1500  // advisory — yellow

private enum class TawsLevel { UNKNOWN, CLEAR, ADVISORY, CAUTION, PULL_UP }

@Composable
fun ProximityAnalogInstrument(modifier: Modifier = Modifier) {
    val tm = rememberTextMeasurer()
    val state by ServiceLocator.efisProvider.state.collectAsStateWithLifecycle()
    val ctx = ServiceLocator.appContext
    val srtm = remember { SrtmProvider(ctx) }

    var terrainElevFt by remember { mutableIntStateOf(0) }
    var aglFt         by remember { mutableIntStateOf(0) }
    var tawsLevel     by remember { mutableStateOf(TawsLevel.UNKNOWN) }
    var downloading   by remember { mutableStateOf(false) }

    val hasPos = state.hasPosition
    val lat    = state.latitude
    val lon    = state.longitude
    val altFt  = state.gpsAltitudeFt.toInt()
    val vsFtMin = state.verticalSpeedFtMin.toInt()

    LaunchedEffect(hasPos, lat, lon) {
        if (!hasPos) { tawsLevel = TawsLevel.UNKNOWN; return@LaunchedEffect }
        while (true) {
            downloading = true
            val elevFt = srtm.elevationFt(lat, lon)
            downloading = false
            if (elevFt != null) {
                terrainElevFt = elevFt
                val agl = altFt - elevFt
                aglFt = agl
                tawsLevel = when {
                    agl < PULL_UP_FT  -> TawsLevel.PULL_UP
                    agl < CAUTION_FT  -> TawsLevel.CAUTION
                    agl < ADVISORY_FT -> TawsLevel.ADVISORY
                    else              -> TawsLevel.CLEAR
                }
            } else {
                tawsLevel = TawsLevel.UNKNOWN
            }
            kotlinx.coroutines.delay(3_000)
        }
    }

    Canvas(modifier = modifier.fillMaxSize()) {
        drawProximity(tm, tawsLevel, aglFt, terrainElevFt, altFt, vsFtMin, hasPos, downloading)
    }
}

private fun DrawScope.drawProximity(
    tm: TextMeasurer,
    level: TawsLevel,
    aglFt: Int,
    terrainElevFt: Int,
    altMslFt: Int,
    vsFtMin: Int,
    hasPos: Boolean,
    downloading: Boolean,
) {
    val (cx, cy, r) = gaugeFace()

    // ── TAWS label ──────────────────────────────────────────────────────────
    compactText(tm, "TAWS", cx, cy - r * 0.78f, sizeSp = 12f, color = CompactStyle.Dim)

    // ── Arc segments: terrain proximity arcs ────────────────────────────────
    drawTawsArcs(cx, cy, r, level)

    // ── Large AGL readout in the centre ─────────────────────────────────────
    val (alertColor, alertLabel) = alertInfo(level, downloading, hasPos)
    val aglStr = if (level != TawsLevel.UNKNOWN) "${aglFt}ft" else "---"
    compactText(tm, aglStr, cx, cy - r * 0.06f, sizeSp = 22f, bold = true, color = alertColor)
    compactText(tm, "AGL", cx, cy + r * 0.22f, sizeSp = 11f, color = CompactStyle.Dim)

    // ── Lobes ───────────────────────────────────────────────────────────────
    val vsStr = if (level != TawsLevel.UNKNOWN) {
        val sign = if (vsFtMin >= 0) "+" else ""
        "$sign$vsFtMin"
    } else "---"
    val vsColor = when {
        vsFtMin > 0  -> CompactStyle.Climb
        vsFtMin < 0  -> CompactStyle.Descent
        else         -> GaugeColors.Mark
    }

    val mslStr = if (level != TawsLevel.UNKNOWN) "${altMslFt}ft" else "---"

    drawGaugeLobes(
        tm, cx, cy, r,
        left   = GaugeLobe("V/S", vsStr, vsColor),
        centre = GaugeLobeCentre(alertLabel, mslStr, alertColor, GaugeColors.MarkDim),
        right  = GaugeLobe("MSL", "${terrainElevFt}ft", GaugeColors.Mark),
    )
}

private fun DrawScope.drawTawsArcs(cx: Float, cy: Float, r: Float, level: TawsLevel) {
    if (level == TawsLevel.UNKNOWN) return

    // Draw 3 concentric arc bands (think speed arc on altimeter):
    // outer arc (advisory = yellow), mid (caution = orange), inner (pull-up = red)
    // We use partial drawArc to fill the left/right half-rings

    val stroke = r * 0.055f
    val style = androidx.compose.ui.graphics.drawscope.Stroke(width = stroke)
    val clearColor = Color(0x44FFFFFF)
    val advisoryColor  = Color(0xFFFFCC00)
    val cautionColor   = Color(0xFFFF9500)
    val pullUpColor    = Color(0xFFFF3B30)

    // Three rings at 90%, 75%, 60% of r — thin dimmed arcs for clear
    val rPullUp   = r * 0.60f
    val rCaution  = r * 0.75f
    val rAdvisory = r * 0.90f

    fun arcColor(ring: TawsLevel): Color = when {
        level.ordinal >= ring.ordinal -> when (ring) {
            TawsLevel.ADVISORY -> advisoryColor
            TawsLevel.CAUTION  -> cautionColor
            TawsLevel.PULL_UP  -> pullUpColor
            else -> clearColor
        }
        else -> clearColor
    }

    fun drawRing(radius: Float, ring: TawsLevel) {
        val c = arcColor(ring)
        drawCircle(c, radius = radius, center = androidx.compose.ui.geometry.Offset(cx, cy), style = style)
    }

    drawRing(rAdvisory, TawsLevel.ADVISORY)
    drawRing(rCaution,  TawsLevel.CAUTION)
    drawRing(rPullUp,   TawsLevel.PULL_UP)

    // If PULL_UP, add a flashing triangle pointer at the top
    if (level == TawsLevel.PULL_UP) {
        val s = r * 0.12f
        val path = Path().apply {
            moveTo(cx, cy - r * 0.50f)
            lineTo(cx - s, cy - r * 0.50f + s * 1.5f)
            lineTo(cx + s, cy - r * 0.50f + s * 1.5f)
            close()
        }
        drawPath(path, pullUpColor)
    }
}

private fun alertInfo(level: TawsLevel, downloading: Boolean, hasPos: Boolean): Pair<Color, String> =
    when {
        !hasPos     -> Pair(GaugeColors.MarkDim, "NO GPS")
        downloading -> Pair(GaugeColors.MarkDim, "CHARG.")
        level == TawsLevel.UNKNOWN  -> Pair(GaugeColors.MarkDim, "---")
        level == TawsLevel.CLEAR    -> Pair(CompactStyle.Climb, "CLEAR")
        level == TawsLevel.ADVISORY -> Pair(Color(0xFFFFCC00), "CAUTION")
        level == TawsLevel.CAUTION  -> Pair(Color(0xFFFF9500), "WARNING")
        else                        -> Pair(Color(0xFFFF3B30), "PULL UP")
    }
