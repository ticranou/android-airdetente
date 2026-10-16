package com.airchecklists.app.ui.efis.gauges.compact

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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.rememberTextMeasurer
import com.airchecklists.app.data.local.SunCalc
import com.airchecklists.app.data.sensors.EfisState
import com.airchecklists.app.ui.efis.gauges.GaugeBezel
import com.airchecklists.app.ui.efis.gauges.GaugeColors
import com.airchecklists.app.ui.efis.gauges.GaugeLobe
import com.airchecklists.app.ui.efis.gauges.GaugeLobeCentre
import com.airchecklists.app.ui.efis.gauges.LocalGaugeBezel
import com.airchecklists.app.ui.efis.gauges.drawGaugeLobes
import com.airchecklists.app.ui.efis.gauges.drawGaugeTitlePill
import com.airchecklists.app.ui.efis.gauges.gaugeFace
import kotlinx.coroutines.delay
import java.util.TimeZone
import kotlin.math.roundToInt

private val ANGT_GREEN  = Color(0xFF35C759)
private val ANGT_ORANGE = Color(0xFFE8843A)
private val ANGT_DIM    = Color(0xFF888888)
private val PILL_BG     = Color(0xE8101418)
private val PILL_BORDER = Color(0x99FFFFFF)

/**
 * ANLNGT — Nuit aéronautique, jauge ronde.
 *
 * Arc vert en haut proportionnel au temps de jour restant.
 * Deux pills "D HH:MM" / "F HH:MM" (Dawn / Fin de jour).
 * Lobe central : "TEMPS DISPO" + durée en vert (ou orange si < 1h).
 */
@Composable
fun NightAnalogInstrument(state: EfisState, modifier: Modifier = Modifier) {
    val tm    = rememberTextMeasurer()
    val bezel = LocalGaugeBezel.current

    var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000)
            nowMs = System.currentTimeMillis()
        }
    }

    data class SunKey(val lat: Int, val lon: Int, val min: Long)
    val sunKey = SunKey((state.latitude * 10).toInt(), (state.longitude * 10).toInt(), nowMs / 60_000)
    val sunTimes by remember(sunKey) {
        mutableStateOf(
            if (state.hasPosition) SunCalc.compute(state.latitude, state.longitude, SunCalc.epochMsToJulianDay(nowMs))
            else null,
        )
    }

    Canvas(modifier = modifier.fillMaxSize()) {
        val st            = sunTimes
        val utcOffsetMin  = TimeZone.getDefault().getOffset(nowMs) / 60_000
        val nowUtcMin     = ((nowMs / 60_000) % 1440).toInt()

        val dawn  = st?.dawnUtcMin
        val dusk  = st?.duskUtcMin
        val dayMin = if (dawn != null && dusk != null && dusk > dawn) dusk - dawn else null

        val availMin: Int? = if (dawn != null && dusk != null) when {
            nowUtcMin < dawn -> 0
            nowUtcMin > dusk -> 0
            else             -> dusk - nowUtcMin
        } else null

        val dayFrac: Float = if (dawn != null && dusk != null && dayMin != null && dayMin > 0) {
            ((nowUtcMin - dawn).coerceIn(0, dayMin).toFloat() / dayMin).coerceIn(0f, 1f)
        } else 0f

        val remainFrac = (1f - dayFrac).coerceIn(0f, 1f)

        fun fmtLocal(utcMin: Int?): String {
            if (utcMin == null) return "--:--"
            val local = ((utcMin + utcOffsetMin) % 1440 + 1440) % 1440
            return "%02d:%02d".format(local / 60, local % 60)
        }
        val dawnStr  = fmtLocal(dawn)
        val duskStr  = fmtLocal(dusk)

        val availStr = if (availMin == null || availMin == 0) "—" else {
            val h2 = availMin / 60; val m2 = availMin % 60
            "%dh%02d".format(h2, m2)
        }
        val availColor = when {
            availMin == null || availMin == 0 -> ANGT_DIM
            availMin < 60  -> ANGT_ORANGE
            else           -> ANGT_GREEN
        }

        drawNightGauge(tm, bezel, remainFrac, dawnStr, duskStr, availStr, availColor, st != null)
    }
}

private fun DrawScope.drawNightGauge(
    tm: TextMeasurer,
    bezel: GaugeBezel,
    remainFrac: Float,
    dawnStr: String,
    duskStr: String,
    availStr: String,
    availColor: Color,
    hasGps: Boolean,
) {
    val (cx, cy, r) = gaugeFace(bezel)

    // ── Arc de temps disponible (style carburant : 240° de 150° à 30°) ────────
    val arcStart  = 150f
    val arcSweep  = 240f
    val arcRadius = r * 0.72f
    val strokeW   = r * 0.10f

    // Arc de fond gris
    drawArc(
        color      = Color(0xFF2A2A2A),
        startAngle = arcStart,
        sweepAngle = arcSweep,
        useCenter  = false,
        topLeft    = Offset(cx - arcRadius, cy - arcRadius),
        size       = Size(arcRadius * 2, arcRadius * 2),
        style      = Stroke(width = strokeW * 1.1f),
    )
    // Arc vert proportionnel au temps restant
    if (hasGps && remainFrac > 0f) {
        drawArc(
            color      = ANGT_GREEN,
            startAngle = arcStart,
            sweepAngle = arcSweep * remainFrac,
            useCenter  = false,
            topLeft    = Offset(cx - arcRadius, cy - arcRadius),
            size       = Size(arcRadius * 2, arcRadius * 2),
            style      = Stroke(width = strokeW),
        )
    }

    // ── Pills "D HH:MM" et "F HH:MM" ─────────────────────────────────────────
    val pillW    = r * 1.20f
    val pillH    = r * 0.34f
    val pillCorr = CornerRadius(pillH / 2f)
    val gap      = r * 0.06f

    // Pill supérieure : D (Dawn)
    val pill1Y = cy - r * 0.26f
    drawRoundRect(PILL_BG, Offset(cx - pillW / 2f, pill1Y - pillH / 2f), Size(pillW, pillH), pillCorr)
    drawRoundRect(PILL_BORDER, Offset(cx - pillW / 2f, pill1Y - pillH / 2f), Size(pillW, pillH), pillCorr, style = Stroke(1.5f))
    val dLabelX = cx - pillW / 2f + pillH * 0.55f
    compactText(tm, "D", dLabelX, pill1Y, sizeSp = 13f, color = ANGT_DIM)
    compactText(tm, if (hasGps) dawnStr else "--:--", cx + pillW * 0.06f, pill1Y, sizeSp = 18f, bold = true, color = GaugeColors.Mark)

    // Pill inférieure : F (Fin / Dusk)
    val pill2Y = pill1Y + pillH + gap
    drawRoundRect(PILL_BG, Offset(cx - pillW / 2f, pill2Y - pillH / 2f), Size(pillW, pillH), pillCorr)
    drawRoundRect(PILL_BORDER, Offset(cx - pillW / 2f, pill2Y - pillH / 2f), Size(pillW, pillH), pillCorr, style = Stroke(1.5f))
    compactText(tm, "F", dLabelX, pill2Y, sizeSp = 13f, color = ANGT_DIM)
    compactText(tm, if (hasGps) duskStr else "--:--", cx + pillW * 0.06f, pill2Y, sizeSp = 18f, bold = true, color = GaugeColors.Mark)

    // ── Lobe central : TEMPS DISPO ────────────────────────────────────────────
    drawGaugeLobes(
        tm, cx, cy, r,
        left   = GaugeLobe("", "", GaugeColors.MarkDim),
        centre = GaugeLobeCentre(
            primary      = availStr,
            sub          = "TEMPS DISPO",
            primaryColor = availColor,
            subColor     = ANGT_DIM,
        ),
        right  = GaugeLobe("", "", GaugeColors.MarkDim),
    )

    drawGaugeTitlePill(tm, "NUIT AERO", cx, cy, r, bezel.hideTitle)
}
