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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
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
private val ANGT_RED    = Color(0xFFFF5252)
private val ANGT_DIM    = Color(0xFF888888)

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

        drawNightGauge(tm, bezel, remainFrac, dawnStr, duskStr, availStr, availMin, st != null)
    }
}

private fun DrawScope.drawNightGauge(
    tm: TextMeasurer,
    bezel: GaugeBezel,
    remainFrac: Float,
    dawnStr: String,
    duskStr: String,
    availStr: String,
    availMin: Int?,
    hasGps: Boolean,
) {
    val (cx, cy, r) = gaugeFace(bezel)

    // ── Gradient horizontal jour/nuit ─────────────────────────────────────────
    val gradMargin = r * 0.12f
    val gradLeft   = cx - r + gradMargin
    val gradRight  = cx + r - gradMargin
    val gradW      = gradRight - gradLeft
    val gradH      = r * 0.28f
    val gradTop    = cy - r * 0.22f

    val gradBrush = Brush.horizontalGradient(
        colors = listOf(
            Color(0xFF111318),
            Color(0xFF444444),
            Color(0xFFCCCCCC),
            Color(0xFFFFFFFF),
            Color(0xFFCCCCCC),
            Color(0xFF444444),
            Color(0xFF111318),
        ),
        startX = gradLeft,
        endX   = gradRight,
    )
    drawRoundRect(
        brush        = gradBrush,
        topLeft      = Offset(gradLeft, gradTop),
        size         = Size(gradW, gradH),
        cornerRadius = CornerRadius(gradH * 0.3f),
    )

    // ── Trait + label 12h ─────────────────────────────────────────────────────
    val noonX    = cx
    val tickOver = gradH * 0.5f
    drawLine(
        color       = Color(0xFF888888),
        start       = Offset(noonX, gradTop - tickOver),
        end         = Offset(noonX, gradTop + gradH),
        strokeWidth = 1.5f,
    )
    compactText(tm, "12h", noonX, gradTop - tickOver - r * 0.08f,
        sizeSp = 9f, color = Color(0xFF888888))

    // ── Curseur heure courante ────────────────────────────────────────────────
    if (hasGps) {
        val dayFrac2  = 1f - remainFrac
        val dawnFrac  = 0.20f
        val duskFrac  = 0.80f
        val nowFrac   = dawnFrac + dayFrac2 * (duskFrac - dawnFrac)
        val nowX      = gradLeft + gradW * nowFrac.coerceIn(0f, 1f)
        val cursorR   = r * 0.055f
        drawCircle(Color.Black,      radius = cursorR + 3f, center = Offset(nowX, gradTop + gradH / 2f))
        drawCircle(Color(0xFFFF9500), radius = cursorR,      center = Offset(nowX, gradTop + gradH / 2f))
    }

    // ── Heures bleues espacées du gradient ────────────────────────────────────
    val hoursY = gradTop + gradH + r * 0.18f
    compactText(tm, if (hasGps) dawnStr else "--:--",
        gradLeft + gradW * 0.18f, hoursY,
        sizeSp = 14f, bold = true, color = Color(0xFF5BA4E8))
    compactText(tm, if (hasGps) duskStr else "--:--",
        gradLeft + gradW * 0.82f, hoursY,
        sizeSp = 14f, bold = true, color = Color(0xFF5BA4E8))

    // ── Couleur temps disponible : vert ≥90min, orange 30–90min, rouge <30min ─
    val availColor = when {
        availMin == null || availMin == 0 -> ANGT_DIM
        availMin < 30  -> ANGT_RED
        availMin < 90  -> ANGT_ORANGE
        else           -> ANGT_GREEN
    }

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
