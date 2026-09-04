package com.airchecklists.app.ui.efis.gauges.compact

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.airchecklists.app.di.ServiceLocator
import com.airchecklists.app.ui.efis.gauges.LocalGaugeBezel
import com.airchecklists.app.ui.efis.gauges.drawNumTitleBar
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val FGT_GREEN  = Color(0xFF32C832)
private val FGT_PURPLE = Color(0xFFAA00CC)
private val FGT_DIM    = Color(0xFF888888)
private val FGT_WHITE  = Color(0xFFDDDDDD)
private val FGT_BLACK  = Color(0xFF111111)

private val dateFmt = SimpleDateFormat("dd/MM/yyyy", Locale.FRANCE)
private val timeFmt = SimpleDateFormat("HH'h'mm", Locale.FRANCE)

/**
 * CMNFGT — Session de vol (rectangular, 100%-1L).
 * Layout: white title bar (32dp) + data row (52dp) + black bottom margin (remainder).
 * Total singleLineHeightDp = 120dp.
 */
@Composable
fun FlightSessionDigital(modifier: Modifier = Modifier) {
    val tm = rememberTextMeasurer()
    val bezel = LocalGaugeBezel.current
    val prefs by ServiceLocator.preferences.preferences.collectAsStateWithLifecycle()
    val fdrStatus by ServiceLocator.flightRecorder.status.collectAsStateWithLifecycle()
    val calibAlt by ServiceLocator.altCalibrationFt.collectAsStateWithLifecycle()

    val persist = prefs.instruments
    val aircraftName = ServiceLocator.currentAircraft()?.name?.takeIf { it.isNotBlank() } ?: "---"

    val engineStartMs = persist.engineStartMs
    val engineStopMs  = persist.engineStopMs

    val today       = dateFmt.format(Date())
    val startStr    = engineStartMs?.let { timeFmt.format(Date(it)) } ?: "--h--"
    val stopStr     = engineStopMs?.let  { timeFmt.format(Date(it)) } ?: "--h--"
    val durationStr = if (engineStartMs != null && engineStopMs != null) {
        val mins = ((engineStopMs - engineStartMs) / 60000L).coerceAtLeast(0L)
        "${mins / 60}h${(mins % 60).toString().padStart(2, '0')}"
    } else "---"
    val altiStr = calibAlt?.let { "$it ft" } ?: "---"

    Canvas(modifier = modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height

        // Title bar height driven by its text (like every other NUM instrument);
        // body is a fixed data row; the rest of h is a transparent margin below.
        val titleSp = 32f
        val titleSegs = listOf(
            "Session de vol du " to FGT_WHITE,
            today                to FGT_GREEN,
            " sur "              to FGT_WHITE,
            aircraftName         to FGT_GREEN,
        )
        val titleMeasured = titleSegs.map { (text, color) ->
            tm.measure(text, androidx.compose.ui.text.TextStyle(
                fontSize = titleSp.toSp(),
                color = color,
                fontWeight = if (color == FGT_GREEN) androidx.compose.ui.text.font.FontWeight.Bold
                             else androidx.compose.ui.text.font.FontWeight.Normal,
            ))
        }
        val titleTextH = titleMeasured.maxOf { it.size.height }.toFloat()

        val headerH  = titleTextH + 8.dp.toPx()
        // Fixed body height (not a fraction of h) so shrinking the cell only trims
        // the transparent bottom margin, never the data row.
        val bodyH    = 56.dp.toPx()
        val contentH = (headerH + bodyH).coerceAtMost(h)   // active area; rest = transparent margin

        // Paint the instrument background ONLY over the active area, so the bottom
        // margin stays transparent (shows the page background, separating this
        // instrument from the ones below).
        drawRect(CompactStyle.Bg, topLeft = Offset.Zero, size = Size(w, contentH))
        // Title bar background: same bezel style as all other NUM instruments.
        drawNumTitleBar(bezel, w, headerH)
        drawRect(Color(0xFF3A3A3A), topLeft = Offset.Zero, size = Size(w, contentH), style = Stroke(2f))
        val totalTW = titleMeasured.sumOf { it.size.width }.toFloat()
        var tx = ((w - totalTW) / 2f).coerceAtLeast(4.dp.toPx())
        val tcy = headerH / 2f
        titleMeasured.forEach { m ->
            drawText(m, topLeft = Offset(tx, tcy - m.size.height / 2f))
            tx += m.size.width
        }

        // --- Data row ---
        val bodyCy  = headerH + bodyH / 2f
        val labelSp = 12f
        val valueSp = 17f
        val sepW    = 1.5f
        val pad     = 8.dp.toPx()

        val colFdr  = w * 0.14f
        val colAlti = w * 0.22f
        val colTime = w * 0.155f
        val rightGroupW = colTime * 3f + sepW * 2f
        val rightStartX = w - rightGroupW - pad

        val sep1X = pad + colFdr
        val sep2X = sep1X + colAlti
        val sepTop = headerH + bodyH * 0.08f
        val sepBot = headerH + bodyH * 0.92f

        drawLine(Color(0xFF555555), Offset(sep1X, sepTop), Offset(sep1X, sepBot), strokeWidth = sepW)
        drawLine(Color(0xFF555555), Offset(sep2X, sepTop), Offset(sep2X, sepBot), strokeWidth = sepW)

        // FDR
        val fdrCx    = pad + colFdr / 2f
        val fdrColor = if (fdrStatus.recording) FGT_GREEN else FGT_DIM
        compactText(tm, "FDR", fdrCx, bodyCy - bodyH * 0.20f, sizeSp = labelSp, color = FGT_DIM)
        compactText(tm, if (fdrStatus.recording) "ON" else "OFF",
            fdrCx, bodyCy + bodyH * 0.20f, sizeSp = valueSp, bold = true, color = fdrColor)

        // ALTI
        val altiCx = sep1X + colAlti / 2f
        compactText(tm, "ALTI", altiCx, bodyCy - bodyH * 0.20f, sizeSp = labelSp, color = FGT_DIM)
        if (calibAlt != null) {
            val altiValueSp = 24f
            val pm = tm.measure(altiStr, androidx.compose.ui.text.TextStyle(
                fontSize = altiValueSp.toSp(), color = Color.White,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
            ))
            val pillW = pm.size.width + 34f
            val pillH = pm.size.height + 18f
            val pillY = bodyCy + bodyH * 0.22f
            drawRoundRect(FGT_PURPLE,
                topLeft = Offset(altiCx - pillW / 2f, pillY - pillH / 2f),
                size = Size(pillW, pillH),
                cornerRadius = CornerRadius(pillH / 2f))
            drawText(pm, topLeft = Offset(altiCx - pm.size.width / 2f, pillY - pm.size.height / 2f))
        } else {
            compactText(tm, "---", altiCx, bodyCy + bodyH * 0.20f, sizeSp = valueSp, bold = true, color = FGT_DIM)
        }

        // Right group: Départ | Arrivée | Durée
        val c1 = rightStartX + colTime / 2f
        val c2 = c1 + colTime + sepW
        val c3 = c2 + colTime + sepW
        val timeColor = FGT_GREEN

        drawLine(Color(0xFF555555), Offset(c1 + colTime / 2f, sepTop), Offset(c1 + colTime / 2f, sepBot), strokeWidth = sepW)
        drawLine(Color(0xFF555555), Offset(c2 + colTime / 2f, sepTop), Offset(c2 + colTime / 2f, sepBot), strokeWidth = sepW)

        compactText(tm, "Départ",  c1, bodyCy - bodyH * 0.20f, sizeSp = labelSp, color = FGT_DIM)
        compactText(tm, startStr,  c1, bodyCy + bodyH * 0.20f, sizeSp = valueSp, bold = true, color = timeColor)

        compactText(tm, "Arrivée", c2, bodyCy - bodyH * 0.20f, sizeSp = labelSp, color = FGT_DIM)
        compactText(tm, stopStr,   c2, bodyCy + bodyH * 0.20f, sizeSp = valueSp, bold = true, color = timeColor)

        compactText(tm, "Durée",     c3, bodyCy - bodyH * 0.20f, sizeSp = labelSp, color = FGT_DIM)
        compactText(tm, durationStr, c3, bodyCy + bodyH * 0.20f, sizeSp = valueSp, bold = true,
            color = if (durationStr == "---") FGT_DIM else timeColor)
    }
}

private fun Float.toSp() = androidx.compose.ui.unit.TextUnit(this, androidx.compose.ui.unit.TextUnitType.Sp)
