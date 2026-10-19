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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.airchecklists.app.data.local.SunCalc
import com.airchecklists.app.data.sensors.EfisState
import com.airchecklists.app.ui.efis.gauges.LocalGaugeBezel
import com.airchecklists.app.ui.efis.gauges.LocalHideBorder
import com.airchecklists.app.ui.efis.gauges.LocalHideTitle
import com.airchecklists.app.ui.efis.gauges.drawNumTitleBar
import kotlinx.coroutines.delay
import java.util.TimeZone

private val NGT_BLUE    = Color(0xFF5588FF)
private val NGT_GREEN   = Color(0xFF1E8B1E)
private val NGT_ORANGE  = Color(0xFFE8843A)
private val NGT_RED     = Color(0xFFCC2222)
private val NGT_GRAY    = Color(0xFF555555)
private val NGT_DIM     = Color(0xFF888888)
private val NGT_DIVIDER = Color(0xFF404040)

/**
 * NUMNGT — Nuit aéronautique (crépuscule civil, 50%-1L).
 *
 * Barre de dégradé blanc→gris→noir représentant le jour (blanc au centre solaire,
 * nuit noire aux extrémités). Flèche orange sur la position actuelle. Horaires
 * crépuscule civil en bleu. Panneau droit : "TEMPS DISPO" + durée avant la nuit.
 */
@Composable
fun NightInstrument(state: EfisState, modifier: Modifier = Modifier) {
    val tm = rememberTextMeasurer()
    val bezel = LocalGaugeBezel.current
    val hideTitle = LocalHideTitle.current
    val hideCellBorder = LocalHideBorder.current

    var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000)
            nowMs = System.currentTimeMillis()
        }
    }

    // Calcul solaire mémorisé à la minute.
    data class SunKey(val lat: Int, val lon: Int, val min: Long)
    val sunKey = SunKey((state.latitude * 10).toInt(), (state.longitude * 10).toInt(), nowMs / 60_000)
    val sunTimes by remember(sunKey) {
        mutableStateOf(
            if (state.hasPosition) SunCalc.compute(state.latitude, state.longitude, SunCalc.epochMsToJulianDay(nowMs))
            else null,
        )
    }

    Canvas(modifier = modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val pad = w * 0.02f

        val headerH = if (hideTitle) 0f else 20.dp.toPx().coerceAtMost(h * 0.4f)

        drawRect(CompactStyle.Bg, size = size)
        if (!hideCellBorder) drawRect(Color(0xFF3A3A3A), size = size, style = Stroke(width = 2f))
        if (headerH > 0f) {
            drawNumTitleBar(bezel, w, headerH)
            compactText(tm, "NUIT AERO", w / 2f, headerH / 2f, sizeSp = 12f, color = CompactStyle.Dim)
        }

        val bodyTop = headerH
        val bodyH   = h - headerH

        val utcOffsetMin = TimeZone.getDefault().getOffset(nowMs) / 60_000
        val nowUtcMin    = ((nowMs / 60_000) % 1440).toInt()

        val st    = sunTimes
        val dawn  = st?.dawnUtcMin
        val dusk  = st?.duskUtcMin
        val dayMin = if (dawn != null && dusk != null && dusk > dawn) dusk - dawn else null

        val availMin: Int? = if (dawn != null && dusk != null) when {
            nowUtcMin < dawn  -> 0
            nowUtcMin > dusk  -> 0
            else              -> dusk - nowUtcMin
        } else null

        val availStr: String = if (availMin == null || availMin == 0) {
            "—"
        } else {
            val h2 = availMin / 60; val m2 = availMin % 60
            "%dh%02d".format(h2, m2)
        }
        val availColor = when {
            availMin == null || availMin == 0 -> NGT_DIM
            availMin < 30  -> NGT_RED
            availMin < 90  -> NGT_ORANGE
            else           -> NGT_GREEN
        }

        // Fraction restante : remainFrac = availMin / dayMin (0→1 de dusk→dawn)
        val remainFrac: Float = if (availMin != null && dayMin != null && dayMin > 0 && availMin > 0)
            (availMin.toFloat() / dayMin).coerceIn(0f, 1f) else 0f

        // ── Mise en page : barre à gauche, panneau droit ──────────────────────────
        val rightPanelW = w * 0.30f
        val divX        = w - rightPanelW

        drawLine(NGT_DIVIDER, Offset(divX, bodyTop + 4f), Offset(divX, h - 4f), strokeWidth = 1f)

        // ── Panneau droit : TEMPS DISPO + valeur ──────────────────────────────────
        val rpCx   = divX + rightPanelW / 2f
        val rpLblY = bodyTop + bodyH * 0.32f
        val rpValY = bodyTop + bodyH * 0.68f
        compactText(tm, "TEMPS DISPO", rpCx, rpLblY, sizeSp = 8f, color = NGT_DIM)
        compactText(tm, availStr, rpCx, rpValY, sizeSp = 16f, bold = true, color = availColor)

        // ── Barre de progression style NUMCRB ─────────────────────────────────────
        val barLeft  = pad
        val barRight = divX - pad
        val barW     = barRight - barLeft
        val barTop2  = bodyTop + bodyH * 0.38f
        val barH2    = bodyH * 0.22f

        // Fond + 3 zones colorées fixes (rouge 0→30min, orange 30→90min, gris 90→dayMin)
        drawRect(Color(0xFF222222), topLeft = Offset(barLeft, barTop2), size = Size(barW, barH2))
        if (dayMin != null && dayMin > 0) {
            val redEnd2   = barLeft + barW * (30f / dayMin).coerceIn(0f, 1f)
            val orangeEnd2 = barLeft + barW * (90f / dayMin).coerceIn(0f, 1f)
            drawRect(NGT_RED,    topLeft = Offset(barLeft,     barTop2), size = Size(redEnd2    - barLeft,     barH2))
            drawRect(NGT_ORANGE, topLeft = Offset(redEnd2,     barTop2), size = Size(orangeEnd2 - redEnd2,     barH2))
            drawRect(NGT_GRAY,   topLeft = Offset(orangeEnd2,  barTop2), size = Size(barRight   - orangeEnd2,  barH2))
        }

        // Masque sombre sur la partie vide (temps déjà écoulé — à droite du niveau actuel)
        val filledW = barW * remainFrac
        if (remainFrac < 1f) {
            drawRect(
                Color(0xCC111111),
                topLeft = Offset(barLeft + filledW, barTop2),
                size    = Size(barW - filledW, barH2),
            )
        }

        // ── Horaires crépuscule civil ──────────────────────────────────────────────
        fun fmtLocal(utcMin: Int?): String {
            if (utcMin == null) return "---"
            val local = ((utcMin + utcOffsetMin) % 1440 + 1440) % 1440
            return "%02dh%02d".format(local / 60, local % 60)
        }
        val dawnStr = if (st != null) fmtLocal(dawn) else "---"
        val duskStr = if (st != null) fmtLocal(dusk) else "---"
        val timeY   = barTop2 - 5.dp.toPx()
        compactText(tm, dawnStr, barLeft, timeY, sizeSp = 11f, bold = true, color = NGT_BLUE, center = false)
        compactText(tm, duskStr, barRight, timeY, sizeSp = 11f, bold = true, color = NGT_BLUE, anchorRight = true)

        // ── Curseur violet (triangle pointant vers le haut, style NUMCRB/NUMSPD) ──
        if (st != null && dayMin != null && dayMin > 0) {
            val cursorX = barLeft + filledW
            val cursorH2 = barH2 * 0.6f
            val cursorHW = barH2 * 0.45f
            val cursorPath = Path().apply {
                moveTo(cursorX, barTop2 + barH2)
                lineTo(cursorX - cursorHW / 2f, barTop2 + barH2 + cursorH2)
                lineTo(cursorX + cursorHW / 2f, barTop2 + barH2 + cursorH2)
                close()
            }
            drawPath(cursorPath, Color(0xFFB94DD6))
        }
    }
}
