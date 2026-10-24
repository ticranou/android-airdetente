package com.airchecklists.app.ui.efis.gauges.circuit

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.airchecklists.app.data.repository.AerodromeDirectory
import com.airchecklists.app.data.sensors.EfisState
import com.airchecklists.app.di.ServiceLocator
import com.airchecklists.app.ui.efis.gauges.LocalGaugeBezel
import com.airchecklists.app.ui.efis.gauges.LocalHideBorder
import com.airchecklists.app.ui.efis.gauges.LocalHideTitle
import com.airchecklists.app.ui.efis.gauges.approach.ApproachGeometry
import com.airchecklists.app.ui.efis.gauges.approach.ApproachTargetDialog
import com.airchecklists.app.ui.efis.gauges.approach.ApproachTargetResolver
import com.airchecklists.app.ui.efis.gauges.compact.CompactStyle
import com.airchecklists.app.ui.efis.gauges.compact.compactText
import com.airchecklists.app.ui.efis.gauges.drawNumTitleBar
import kotlin.math.abs
import kotlin.math.roundToInt

private val ALT_OK   = Color(0xFF32C832)
private val ALT_WARN = Color(0xFFFFC107)
private val ALT_LOW  = Color(0xFFE53935)
private val RING_DIM = Color(0xFF2E2E2E)
private val ON  = CompactStyle.Climb
private val OFF = CompactStyle.Accent2

/**
 * NUMCCT — Circuit de piste v4 (220 dp).
 *
 * 3 zones horizontales :
 *  1) LOCALIZER  : pill terrain | localizer+piste rotative | pill QFU
 *  2) INFOS      : DISTANCE | AXE (coloré) | ANGLE
 *  3) FOOTER     : barre altimétrique horizontale avec curseur triangulaire coloré
 *
 * Arcs colorés (vert/orange/rouge) à gauche ET à droite, sur zones 1+2.
 * Long-press → ApproachTargetDialog partagée.
 */
@Composable
fun CircuitDigital(
    state: EfisState,
    modifier: Modifier = Modifier,
) {
    val tm = rememberTextMeasurer()
    val bezel = LocalGaugeBezel.current
    val hideTitle = LocalHideTitle.current
    val hideCellBorder = LocalHideBorder.current
    val charts by ServiceLocator.vacRepository.charts.collectAsStateWithLifecycle()
    val lockedTarget by ServiceLocator.lockedApproachTarget.collectAsStateWithLifecycle()
    val prefs by ServiceLocator.preferences.preferences.collectAsStateWithLifecycle()
    var showDialog by remember { mutableStateOf(false) }

    val latKey = (state.latitude * 50).roundToInt()
    val lonKey = (state.longitude * 50).roundToInt()

    val auto by produceState(
        initialValue = null as com.airchecklists.app.ui.efis.gauges.approach.ApproachTarget?,
        charts, state.hasPosition, latKey, lonKey,
    ) {
        value = if (state.hasPosition)
            ApproachTargetResolver.auto(state.latitude, state.longitude, charts)
        else null
    }

    val target = lockedTarget ?: auto
    val tgt = target

    val circuitAltFt: Int? = tgt?.fieldElevFt?.let { it + 1000 }
    val aglFt: Float? = tgt?.fieldElevFt?.let { state.gpsAltitudeFt - it }

    val errors = ApproachGeometry.compute(
        state.latitude, state.longitude, state.gpsAltitudeFt.toDouble(),
        state.hasPosition, tgt,
    )

    val distanceM: Int? = if (state.hasPosition && tgt != null) {
        val mPerDegLat = 111_320.0
        val dlat = (tgt.lat - state.latitude) * mPerDegLat
        val dlon = (tgt.lon - state.longitude) * mPerDegLat *
            kotlin.math.cos(Math.toRadians(state.latitude))
        kotlin.math.sqrt(dlat * dlat + dlon * dlon).roundToInt()
    } else null

    val qfuDegVal: Float? = tgt?.qfuTrueDeg?.takeIf { !it.isNaN() }
    val angleDeg: Int? = qfuDegVal?.let { qfu ->
        val diff = ((state.headingDeg - qfu + 540f) % 360f) - 180f
        abs(diff).roundToInt()
    }

    Canvas(
        modifier = modifier.fillMaxSize().pointerInput(Unit) {
            detectTapGestures(onLongPress = { showDialog = true })
        },
    ) {
        val w = size.width
        val h = size.height
        drawRect(CompactStyle.Bg, size = size)

        val headerH = if (hideTitle) 0f else 22.dp.toPx().coerceAtMost(h * 0.12f)
        val bodyTop = headerH
        val bodyH   = h - headerH

        val greenFt  = prefs.approachGreenFt.toFloat()
        val orangeFt = prefs.approachOrangeFt.toFloat()
        val redFt    = prefs.approachRedFt.toFloat()
        val hasTarget = errors.hasTarget
        val aglVal   = aglFt ?: 0f
        val outerColor = if (aglFt != null && hasTarget && aglVal < greenFt)  ALT_OK   else RING_DIM
        val midColor   = if (aglFt != null && hasTarget && aglVal < orangeFt) ALT_WARN else RING_DIM
        val innerColor = if (aglFt != null && hasTarget && aglVal < redFt)    ALT_LOW  else RING_DIM

        // Zone heights : localizer 52% | infos 32% | footer 16%
        val footerH  = bodyH * 0.16f
        val infosH   = bodyH * 0.30f
        val locH     = bodyH - footerH - infosH

        val locTop    = bodyTop
        val infosTop  = locTop + locH
        val footerTop = infosTop + infosH

        // Arc zones — présentes sur les zones localizer + infos uniquement.
        val arcZoneW = w * 0.055f
        val arcH     = locH + infosH   // hauteur couverte par les arcs
        drawEdgeArcs(0f,          arcZoneW, locTop, arcH, outerColor, midColor, innerColor)
        drawEdgeArcs(w - arcZoneW, arcZoneW, locTop, arcH, outerColor, midColor, innerColor)

        // Contenu entre les arcs.
        val cx = arcZoneW
        val cw = w - 2f * arcZoneW

        // ── Zone 1 : Localizer ──────────────────────────────────────────────
        drawLocalizerZone(
            tm, cx, cw, locTop, locH,
            tgt?.icao, tgt?.runwayLengthM, qfuDegVal, circuitAltFt,
            errors, hasTarget, state.headingDeg,
        )

        // Séparateur horizontal entre localizer et infos.
        drawLine(Color(0xFF444444), Offset(cx, infosTop), Offset(cx + cw, infosTop), strokeWidth = 1f)

        // ── Zone 2 : Infos ──────────────────────────────────────────────────
        drawInfosZone(tm, cx, cw, infosTop, infosH, errors, hasTarget, angleDeg, distanceM)

        // ── Zone 3 : Footer altimétrique ────────────────────────────────────
        drawAltFooter(
            tm, footerTop, w, footerH,
            state.gpsAltitudeFt, circuitAltFt,
            greenFt, orangeFt, redFt, hasTarget,
        )

        if (!hideCellBorder) drawRect(Color(0xFF3A3A3A), size = size, style = Stroke(width = 2f))

        if (headerH > 0f) {
            drawNumTitleBar(bezel, w, headerH)
            compactText(tm, "CIRCUIT", w / 2f, headerH / 2f, sizeSp = 13f, color = CompactStyle.Dim)
        }
    }

    if (showDialog) {
        val terrains by produceState(initialValue = charts, charts, state.hasPosition, latKey, lonKey) {
            value = AerodromeDirectory.nearbyCharts(
                state.latitude, state.longitude, state.hasPosition, charts, 40,
            )
        }
        ApproachTargetDialog(
            terrains = terrains,
            onDismiss = { showDialog = false },
            onAuto = { ServiceLocator.lockedApproachTarget.value = null },
            onLock = { ServiceLocator.lockedApproachTarget.value = it },
        )
    }
}

// ── Zone 1 : Localizer ────────────────────────────────────────────────────────

private fun DrawScope.drawLocalizerZone(
    tm: TextMeasurer,
    x: Float, zoneW: Float, top: Float, height: Float,
    icao: String?, runwayLengthM: Int?, qfuDeg: Float?, circuitAltFt: Int?,
    errors: ApproachGeometry.ApproachErrors, hasTarget: Boolean,
    headingDeg: Float,
) {
    val cy = top + height / 2f

    // Sous-zones : terrain 28% | localizer 44% | QFU 28%
    val terrainW = zoneW * 0.28f
    val locW     = zoneW * 0.44f
    val qfuW     = zoneW * 0.28f
    val terrainX = x
    val locX     = x + terrainW
    val locR     = locX + locW
    val locCx    = (locX + locR) / 2f
    val qfuX     = locR

    // — Pill ICAO.
    val pillH = height * 0.42f
    val pillW = terrainW * 0.82f
    val icaoCx = terrainX + terrainW / 2f
    drawPillWithText(tm, icaoCx, cy - height * 0.10f, pillW, pillH,
        icao ?: "—", 21f, if (icao != null) CompactStyle.Accent else CompactStyle.Dim)
    runwayLengthM?.let {
        compactText(tm, "$it m", icaoCx, cy + height * 0.26f, sizeSp = 13f, color = CompactStyle.Dim)
    }

    // — Pill QFU.
    val qfuNum = if (qfuDeg != null && !qfuDeg.isNaN()) {
        val tens = ((qfuDeg / 10f).roundToInt() % 36).let { v -> if (v <= 0) v + 36 else v }
        tens.toString().padStart(2, '0')
    } else "?"
    val qfuCx = qfuX + qfuW / 2f
    drawPillWithText(tm, qfuCx, cy - height * 0.10f, pillW, pillH,
        qfuNum, 24f, CompactStyle.Accent)
    circuitAltFt?.let {
        compactText(tm, "$it ft", qfuCx, cy + height * 0.26f, sizeSp = 13f, color = CompactStyle.Dim)
    }

    // — Localizer central.
    val full = locW * 0.32f
    val dot1 = full * 0.5f
    val latM = errors.lateralM.toFloat()
    val locDefl = (-latM / (2f * ApproachGeometry.LAT_TOL_M.toFloat()) * full).coerceIn(-full, full)
    val latOk  = ApproachGeometry.onAxis(errors.lateralM)
    val barColor = if (!hasTarget) CompactStyle.Dim else if (latOk) ON else OFF

    // Séparateurs pointillés.
    val dash = PathEffect.dashPathEffect(floatArrayOf(4f, 6f))
    val sepTop = top + height * 0.08f
    val sepBot = top + height * 0.92f
    drawLine(Color(0xFF888888), Offset(locX, sepTop), Offset(locX, sepBot),
        strokeWidth = 1.5f, pathEffect = dash)
    drawLine(Color(0xFF888888), Offset(locR, sepTop), Offset(locR, sepBot),
        strokeWidth = 1.5f, pathEffect = dash)

    // Piste rotative — clippée à la zone localizer.
    if (qfuDeg != null && !qfuDeg.isNaN()) {
        val rwyLen    = zoneW * 0.60f
        val rwyStroke = height * 0.14f
        clipRect(locX, top, locR, top + height) {
            rotate(degrees = qfuDeg - headingDeg, pivot = Offset(locCx, cy)) {
                drawLine(Color(0xFFCCCCCC),
                    start = Offset(locCx, cy - rwyLen / 2f),
                    end   = Offset(locCx, cy + rwyLen / 2f),
                    strokeWidth = rwyStroke, cap = StrokeCap.Round)
            }
        }
    }

    // Diamants.
    val diaR = height * 0.10f
    for (d in listOf(-full, -dot1, dot1, full)) {
        drawDiamond(Offset(locCx + d, cy), diaR, Color(0xFF8A8A8A))
    }

    // Barre localizer fine.
    val barReach = height * 0.42f
    val barW     = height * 0.025f
    if (hasTarget) {
        drawLine(barColor, Offset(locCx + locDefl, cy - barReach),
            Offset(locCx + locDefl, cy + barReach), strokeWidth = barW, cap = StrokeCap.Round)
    } else {
        drawLine(CompactStyle.Dim, Offset(locCx, cy - barReach),
            Offset(locCx, cy + barReach), strokeWidth = barW, cap = StrokeCap.Round)
        drawFlag(tm, "LOC", locCx, cy - height * 0.18f)
    }

    // Anneau central.
    drawCircle(CompactStyle.Bg, diaR * 0.90f, Offset(locCx, cy))
    drawCircle(CompactStyle.Mark, diaR * 0.90f, Offset(locCx, cy), style = Stroke(width = 2.2f))
}

// ── Zone 2 : Infos (DISTANCE | AXE | ANGLE) ──────────────────────────────────

private fun DrawScope.drawInfosZone(
    tm: TextMeasurer,
    x: Float, zoneW: Float, top: Float, height: Float,
    errors: ApproachGeometry.ApproachErrors, hasTarget: Boolean,
    angleDeg: Int?, distanceM: Int?,
) {
    val colW  = zoneW / 3f
    val cxD   = x + colW * 0.5f
    val cxAxe = x + colW * 1.5f
    val cxAng = x + colW * 2.5f
    val labelY = top + height * 0.28f
    val valueY = top + height * 0.58f

    // DISTANCE.
    val (distNum, distUnit) = when {
        distanceM == null  -> "—" to ""
        distanceM >= 1000  -> "${"%.1f".format(distanceM / 1000.0)}" to "km"
        else               -> "$distanceM" to "m"
    }
    val distColor = if (distanceM != null) CompactStyle.Accent else CompactStyle.Dim
    compactText(tm, "DISTANCE", cxD, labelY, sizeSp = 10f, color = CompactStyle.Dim)
    compactText(tm, "$distNum $distUnit".trim(), cxD, valueY, sizeSp = 22f, bold = true, color = distColor)

    // AXE.
    val lat = errors.lateralM
    val axeStr = if (!hasTarget) "—"
        else if (abs(lat) < 1.0) "0 m"
        else "${abs(lat).roundToInt().coerceAtMost(999)} m${if (lat > 0) "D" else "G"}"
    val axeColor = if (!hasTarget) CompactStyle.Dim else if (ApproachGeometry.onAxis(lat)) ON else OFF
    compactText(tm, "AXE", cxAxe, labelY, sizeSp = 10f, color = CompactStyle.Dim)
    compactText(tm, axeStr, cxAxe, valueY, sizeSp = 22f, bold = true, color = axeColor)

    // ANGLE.
    val angStr  = if (angleDeg != null) "$angleDeg°" else "—"
    val angColor = if (angleDeg != null) CompactStyle.Accent else CompactStyle.Dim
    compactText(tm, "ANGLE", cxAng, labelY, sizeSp = 10f, color = CompactStyle.Dim)
    compactText(tm, angStr, cxAng, valueY, sizeSp = 22f, bold = true, color = angColor)
}

// ── Zone 3 : Footer altimétrique horizontal ───────────────────────────────────

private fun DrawScope.drawAltFooter(
    tm: TextMeasurer,
    top: Float, w: Float, height: Float,
    altFt: Float, circuitAltFt: Int?,
    greenFt: Float, orangeFt: Float, redFt: Float,
    hasTarget: Boolean,
) {
    val pillW  = w * 0.11f
    val pillH  = height * 0.52f
    val pad    = pillW + w * 0.01f      // barre commence après le pill gauche
    val barTop = top + (height - pillH) / 2f
    val barH   = pillH
    val barL   = pad
    val barR   = w - pad
    val barW   = barR - barL

    val maxAlt = (circuitAltFt?.toFloat() ?: 2000f).coerceAtLeast(500f)

    // Axe inversé : 0 à droite, maxAlt à gauche.
    fun altToX(ft: Float): Float = barR - ((ft / maxAlt).coerceIn(0f, 1f)) * barW

    // Fond.
    drawRect(Color(0xFF1A1A1A), topLeft = Offset(barL, barTop), size = Size(barW, barH))

    // Segments colorés (axe inversé : rouge tout à droite, dim tout à gauche).
    if (hasTarget) {
        fun seg(xL: Float, xR: Float, color: Color) {
            val l = xL.coerceIn(barL, barR)
            val r = xR.coerceIn(barL, barR)
            if (r > l) drawRect(color, topLeft = Offset(l, barTop), size = Size(r - l, barH))
        }
        // De gauche (haute alt) à droite (sol) :
        seg(barL,              altToX(greenFt),  RING_DIM)
        seg(altToX(greenFt),   altToX(orangeFt), ALT_OK)
        seg(altToX(orangeFt),  altToX(redFt),    ALT_WARN)
        seg(altToX(redFt),     barR,              ALT_LOW)
    }

    // Curseur triangulaire ▼ au-dessus de la barre, couleur selon segment.
    val curX = altToX(altFt)
    val curColor = when {
        !hasTarget       -> CompactStyle.Dim
        altFt < redFt    -> ALT_LOW
        altFt < orangeFt -> ALT_WARN
        altFt < greenFt  -> ALT_OK
        else             -> RING_DIM
    }
    val triH   = barH * 0.70f
    val triW   = barH * 0.55f
    val triBot = barTop - height * 0.04f
    val tri = Path().apply {
        moveTo(curX, triBot)
        lineTo(curX - triW / 2f, triBot - triH)
        lineTo(curX + triW / 2f, triBot - triH)
        close()
    }
    drawPath(tri, curColor)

    // Pills aux extrémités.
    val pillCy  = top + height / 2f
    val pillRad = CornerRadius(pillH / 2f)

    // Pill gauche = altitude circuit (max).
    val leftLabel = circuitAltFt?.let { "$it ft" } ?: "— ft"
    drawRoundRect(Color(0xFF1E1E1E),
        topLeft = Offset(0f, pillCy - pillH / 2f), size = Size(pillW, pillH),
        cornerRadius = pillRad)
    drawRoundRect(Color(0xFF505050),
        topLeft = Offset(0f, pillCy - pillH / 2f), size = Size(pillW, pillH),
        cornerRadius = pillRad, style = Stroke(width = 1.4f))
    compactText(tm, leftLabel, pillW / 2f, pillCy, sizeSp = 9f, color = CompactStyle.Dim)

    // Pill droit = 0 ft (sol).
    drawRoundRect(Color(0xFF1E1E1E),
        topLeft = Offset(w - pillW, pillCy - pillH / 2f), size = Size(pillW, pillH),
        cornerRadius = pillRad)
    drawRoundRect(Color(0xFF505050),
        topLeft = Offset(w - pillW, pillCy - pillH / 2f), size = Size(pillW, pillH),
        cornerRadius = pillRad, style = Stroke(width = 1.4f))
    compactText(tm, "0 ft", w - pillW / 2f, pillCy, sizeSp = 9f, color = CompactStyle.Dim)
}

// ── Arcs de proximité ─────────────────────────────────────────────────────────

private fun DrawScope.drawEdgeArcs(
    x: Float, zoneW: Float, top: Float, height: Float,
    green: Color, orange: Color, red: Color,
) {
    val gap  = zoneW * 0.12f
    val barW = (zoneW - 4f * gap) / 3f
    val barTop = top + height * 0.04f
    val barH   = height * 0.92f
    listOf(green, orange, red).forEachIndexed { i, c ->
        val bx = x + gap + i * (barW + gap)
        drawRoundRect(c,
            topLeft = Offset(bx, barTop), size = Size(barW, barH),
            cornerRadius = CornerRadius(barW * 0.4f))
    }
}

// ── Pill helper ───────────────────────────────────────────────────────────────

private fun DrawScope.drawPill(
    cx: Float, cy: Float, pillW: Float, pillH: Float,
    text: String, textSp: Float, textColor: Color,
) {
    drawRoundRect(Color(0xFF1E1E1E),
        topLeft = Offset(cx - pillW / 2f, cy - pillH / 2f), size = Size(pillW, pillH),
        cornerRadius = CornerRadius(pillH / 2f))
    drawRoundRect(Color(0xFF707070),
        topLeft = Offset(cx - pillW / 2f, cy - pillH / 2f), size = Size(pillW, pillH),
        cornerRadius = CornerRadius(pillH / 2f), style = Stroke(width = 1.8f))
}

// La fonction drawPill ne peut pas appeler compactText directement (pas de TextMeasurer).
// On utilise une version avec tm séparée.
private fun DrawScope.drawPillWithText(
    tm: TextMeasurer,
    cx: Float, cy: Float, pillW: Float, pillH: Float,
    text: String, textSp: Float, textColor: Color,
) {
    drawPill(cx, cy, pillW, pillH, text, textSp, textColor)
    compactText(tm, text, cx, cy, sizeSp = textSp, bold = true, color = textColor)
}

// ── Helpers ───────────────────────────────────────────────────────────────────

private fun DrawScope.drawDiamond(c: Offset, rad: Float, color: Color) {
    val p = Path().apply {
        moveTo(c.x, c.y - rad); lineTo(c.x + rad, c.y)
        lineTo(c.x, c.y + rad); lineTo(c.x - rad, c.y); close()
    }
    drawPath(p, color, style = Stroke(width = 1.8f))
}

private fun DrawScope.drawFlag(tm: TextMeasurer, label: String, x: Float, y: Float) {
    val m = tm.measure(label)
    val fw = m.size.width + 10f
    val fh = m.size.height + 4f
    drawRoundRect(Color(0xFFFF5252),
        topLeft = Offset(x - fw / 2f, y - fh / 2f), size = Size(fw, fh),
        cornerRadius = CornerRadius(4f))
    drawText(m, topLeft = Offset(x - m.size.width / 2f, y - m.size.height / 2f))
}
