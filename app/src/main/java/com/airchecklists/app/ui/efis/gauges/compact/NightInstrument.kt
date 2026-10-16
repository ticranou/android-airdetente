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
import kotlin.math.roundToInt

private val NGT_BLUE    = Color(0xFF5588FF)   // horaires crépuscule (bleu clair)
private val NGT_GREEN   = Color(0xFF35C759)   // temps dispo (vert)
private val NGT_ORANGE  = Color(0xFFE8843A)   // curseur heure actuelle (orange)
private val NGT_RED     = Color(0xFFFF5252)   // temps dispo critique (<30min)
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

        // ── Calcul des valeurs ─────────────────────────────────────────────────────
        val st    = sunTimes
        val dawn  = st?.dawnUtcMin
        val dusk  = st?.duskUtcMin

        // Temps disponible avant la prochaine nuit (en minutes)
        val availMin: Int? = if (dawn != null && dusk != null) when {
            nowUtcMin < dawn  -> 0                       // nuit, en attente de l'aube
            nowUtcMin > dusk  -> 0                       // nuit
            else              -> dusk - nowUtcMin        // minutes restantes de jour
        } else null

        val availStr: String = if (availMin == null || availMin == 0) {
            "—"
        } else {
            val h2 = availMin / 60
            val m2 = availMin % 60
            "%dh%02d".format(h2, m2)
        }
        val availColor = when {
            availMin == null              -> NGT_DIM
            availMin == 0                 -> NGT_DIM
            availMin < 30                 -> NGT_RED
            availMin < 90                 -> NGT_ORANGE
            else                          -> NGT_GREEN
        }

        // ── Mise en page : barre à gauche, panneau droit ──────────────────────────
        val rightPanelW = w * 0.30f
        val divX        = w - rightPanelW
        val barLeft     = 0f
        val barRight    = divX - 1f

        // Divider vertical
        drawLine(NGT_DIVIDER, Offset(divX, bodyTop + 4f), Offset(divX, h - 4f), strokeWidth = 1f)

        // ── Panneau droit : TEMPS DISPO + valeur ──────────────────────────────────
        val rpCx   = divX + rightPanelW / 2f
        val rpLblY = bodyTop + bodyH * 0.32f
        val rpValY = bodyTop + bodyH * 0.68f
        compactText(tm, "TEMPS DISPO", rpCx, rpLblY, sizeSp = 8f, color = NGT_DIM)
        compactText(tm, availStr, rpCx, rpValY, sizeSp = 16f, bold = true, color = availColor)

        // ── Barre centrale (axe temporel UTC 0→1440) ──────────────────────────────
        val barMargin = w * 0.04f     // marge gauche/droite
        val barW     = barRight - barLeft - barMargin * 2
        val barTop2  = bodyTop + bodyH * 0.50f
        val barH2    = bodyH * 0.30f
        val barLeft2 = barLeft + barMargin

        // Gradient de base : gris foncé (nuit) aux extrémités, blanc au centre (midi solaire)
        val gradientBrush = if (dawn != null && dusk != null) {
            val pxPerMinPre = barW / 1440f
            val xDawnFrac   = (dawn  / 1440f).coerceIn(0f, 1f)
            val xDuskFrac   = (dusk  / 1440f).coerceIn(0f, 1f)
            val xNoonFrac   = ((dawn + dusk) / 2 / 1440f).coerceIn(0f, 1f)
            Brush.horizontalGradient(
                colorStops = arrayOf(
                    0f           to Color(0xFF0D0D0D),
                    xDawnFrac    to Color(0xFF222222),
                    xNoonFrac    to Color(0xFFFFFFFF),
                    xDuskFrac    to Color(0xFF222222),
                    1f           to Color(0xFF0D0D0D),
                ),
                startX = barLeft2,
                endX   = barLeft2 + barW,
            )
        } else {
            Brush.horizontalGradient(
                colors = listOf(Color(0xFF1A1A1A), Color(0xFF1A1A1A)),
                startX = barLeft2, endX = barLeft2 + barW,
            )
        }
        drawRoundRect(
            brush        = gradientBrush,
            topLeft      = Offset(barLeft2, barTop2),
            size         = Size(barW, barH2),
            cornerRadius = CornerRadius(barH2 * 0.25f),
        )

        val pxPerMin = barW / 1440f

        // ── Horaires crépuscule civil (heure locale) — espacés du gradient ────────
        fun fmtLocal(utcMin: Int?): String {
            if (utcMin == null) return "---"
            val local = ((utcMin + utcOffsetMin) % 1440 + 1440) % 1440
            return "%02dh%02d".format(local / 60, local % 60)
        }
        val dawnStr = if (st != null) fmtLocal(dawn) else "---"
        val duskStr = if (st != null) fmtLocal(dusk) else "---"
        val timeY   = barTop2 - 14.dp.toPx()          // espacé au-dessus du gradient
        compactText(tm, dawnStr, barLeft2 + 4f, timeY, sizeSp = 11f, bold = true, color = NGT_BLUE, center = false)
        compactText(tm, duskStr, barRight - barMargin, timeY, sizeSp = 11f, bold = true, color = NGT_BLUE, anchorRight = true)

        // ── Trait 12h : dépasse au-dessus du gradient, label encore plus haut ─────
        val x12h      = barLeft2 + 720 * pxPerMin
        val tickOver  = barH2 * 0.5f                   // dépasse de moitié la hauteur de barre
        drawLine(
            color = Color(0xFF666666),
            start = Offset(x12h, barTop2 - tickOver),
            end   = Offset(x12h, barTop2 + barH2),
            strokeWidth = 1.5f,
        )
        compactText(tm, "12h", x12h, barTop2 - tickOver - 8.dp.toPx(),
            sizeSp = 9f, color = NGT_DIM)

        // ── Curseur heure courante : cercle orange avec bordure noire ─────────────
        val nowX      = barLeft2 + nowUtcMin * pxPerMin
        val cursorR   = barH2 * 0.45f                  // gros curseur
        drawCircle(Color.Black,   radius = cursorR + 3f, center = Offset(nowX, barTop2 + barH2 / 2f))
        drawCircle(NGT_ORANGE,    radius = cursorR,      center = Offset(nowX, barTop2 + barH2 / 2f))
    }
}
