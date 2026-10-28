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
import com.airchecklists.app.ui.efis.gauges.CellBackground
import com.airchecklists.app.ui.efis.gauges.LocalCellBackground
import com.airchecklists.app.ui.efis.gauges.drawCellBackground
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
    val cellBg = LocalCellBackground.current

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

        drawCellBackground(cellBg)
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

        // Fraction restante : remainFrac = availMin / dayMin (0→1, gauche=plein, droite=vide)
        // La barre représente la journée entière. Le curseur est à availMin/dayMin depuis la droite.
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
        val barTop2  = bodyTop + bodyH * 0.52f
        val barH2    = bodyH * 0.20f

        // Les 3 zones sont fixes depuis la DROITE (fin de journée) :
        //   rouge  : les 30 dernières minutes (extrémité droite)
        //   orange : de 30min à 90min avant la fin
        //   gris   : tout le reste (plus de 90min restants)
        drawRect(Color(0xFF222222), topLeft = Offset(barLeft, barTop2), size = Size(barW, barH2))
        if (dayMin != null && dayMin > 0) {
            val grayEnd   = barRight - barW * (90f / dayMin).coerceIn(0f, 1f)
            val orangeEnd = barRight - barW * (30f / dayMin).coerceIn(0f, 1f)
            // Gris : début → grayEnd
            drawRect(NGT_GRAY,   topLeft = Offset(barLeft,    barTop2), size = Size(grayEnd   - barLeft,    barH2))
            // Orange : grayEnd → orangeEnd
            drawRect(NGT_ORANGE, topLeft = Offset(grayEnd,    barTop2), size = Size(orangeEnd - grayEnd,    barH2))
            // Rouge : orangeEnd → barRight
            drawRect(NGT_RED,    topLeft = Offset(orangeEnd,  barTop2), size = Size(barRight  - orangeEnd,  barH2))
        }

        // Masque sombre sur la partie vide (temps déjà écoulé — à gauche du curseur)
        // La partie remplie part de la droite : filledW = barW * remainFrac
        val filledW = barW * remainFrac
        if (remainFrac < 1f) {
            drawRect(
                Color(0xCC111111),
                topLeft = Offset(barLeft, barTop2),
                size    = Size(barW - filledW, barH2),
            )
        }

        // ── Horaires crépuscule civil (avec espace suffisant au-dessus de la barre) ──
        fun fmtLocal(utcMin: Int?): String {
            if (utcMin == null) return "---"
            val local = ((utcMin + utcOffsetMin) % 1440 + 1440) % 1440
            return "%02dh%02d".format(local / 60, local % 60)
        }
        val dawnStr = if (st != null) fmtLocal(dawn) else "---"
        val duskStr = if (st != null) fmtLocal(dusk) else "---"
        val timeY   = barTop2 - 10.dp.toPx()
        compactText(tm, dawnStr, barLeft, timeY, sizeSp = 11f, bold = true, color = NGT_BLUE, center = false)
        compactText(tm, duskStr, barRight, timeY, sizeSp = 11f, bold = true, color = NGT_BLUE, anchorRight = true)

        // ── Marqueur 12h au milieu de la barre ────────────────────────────────────
        val x12h = barLeft + barW * 0.5f
        val tickOver = barH2 * 0.6f
        drawLine(
            color = Color(0xFF888888),
            start = Offset(x12h, barTop2 - tickOver),
            end   = Offset(x12h, barTop2 + barH2),
            strokeWidth = 1.5f,
        )
        compactText(tm, "12h", x12h, barTop2 - tickOver - 6.dp.toPx(), sizeSp = 9f, color = NGT_DIM)

        // ── Curseur violet (triangle pointant vers le bas, sous la barre) ─────────
        // Position du curseur : depuis la droite, à remainFrac de la largeur totale
        if (st != null && dayMin != null && dayMin > 0) {
            val cursorX  = barLeft + (barW - filledW)
            val cursorH2 = barH2 * 0.9f    // plus haut
            val cursorHW = barH2 * 0.60f   // plus large
            val cursorPath = Path().apply {
                moveTo(cursorX, barTop2 + barH2)              // pointe en bas de barre
                lineTo(cursorX - cursorHW / 2f, barTop2 + barH2 + cursorH2)
                lineTo(cursorX + cursorHW / 2f, barTop2 + barH2 + cursorH2)
                close()
            }
            drawPath(cursorPath, Color(0xFFB94DD6))
        }
    }
}
