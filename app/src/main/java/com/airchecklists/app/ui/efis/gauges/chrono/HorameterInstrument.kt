package com.airchecklists.app.ui.efis.gauges.chrono

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.airchecklists.app.ui.efis.gauges.compact.CompactStyle
import com.airchecklists.app.ui.efis.gauges.compact.compactText
import com.airchecklists.app.ui.efis.gauges.compact.drawGestureHints
import com.airchecklists.app.ui.efis.gauges.drawGaugeTitlePill
import com.airchecklists.app.ui.efis.gauges.gaugeFace
import com.airchecklists.app.ui.efis.gauges.GaugeBezel
import com.airchecklists.app.ui.efis.gauges.GaugeLobe
import com.airchecklists.app.ui.efis.gauges.GaugeLobeCentre
import com.airchecklists.app.ui.efis.gauges.GaugeColors
import com.airchecklists.app.ui.efis.gauges.LocalGaugeBezel
import com.airchecklists.app.ui.efis.gauges.drawGaugeLobes

/** One hour-meter reading, stored as hundredths of an hour (e.g. 106.22 -> 10622),
 *  or null when not entered yet. */
private class HourReading {
    var hundredths by mutableStateOf<Int?>(null)
    /** "NNN.NN" for display, or a placeholder when empty. */
    fun display(): String = hundredths?.let { "%d.%02d".format(it / 100, it % 100) } ?: "---.--"
}

/**
 * Hour-meter ("Horamètre") instrument: round face with TWO cells, each holding a
 * reading like 106.22 (hundredths of an hour). Tap a cell to type its value on a
 * numeric keypad. Once both are set, the flight duration between them is shown
 * below (e.g. 106.22 → 106.34 = 0.12 h = 72' = 1h12).
 */
@Composable
fun HorameterInstrument(modifier: Modifier = Modifier) {
    val tm = rememberTextMeasurer()
    val top = remember { com.airchecklists.app.di.ServiceLocator.instrumentState("horameter.anl.top") { HourReading().also { it.hundredths = com.airchecklists.app.di.ServiceLocator.instrumentPersist.horameterAnl.a } } }
    val bottom = remember { com.airchecklists.app.di.ServiceLocator.instrumentState("horameter.anl.bot") { HourReading().also { it.hundredths = com.airchecklists.app.di.ServiceLocator.instrumentPersist.horameterAnl.b } } }
    var dialogFor by remember { mutableStateOf<HourReading?>(null) }
    val bezel = LocalGaugeBezel.current
    fun persist() {
        com.airchecklists.app.di.ServiceLocator.updateInstruments {
            it.copy(horameterAnl = com.airchecklists.app.data.model.HorameterSnapshot(a = top.hundredths, b = bottom.hundredths))
        }
    }

    Canvas(
        modifier = modifier.fillMaxSize().pointerInput(Unit) {
            detectTapGestures(
                onLongPress = { dialogFor = if (it.y < size.height / 2f) top else bottom },
            )
        },
    ) {
        drawHorameter(tm, top.display(), bottom.display(), durationLabel(top.hundredths, bottom.hundredths), bezel)
    }

    dialogFor?.let { reading ->
        HorameterInputDialog(
            initial = reading.hundredths,
            onDismiss = { dialogFor = null },
            onConfirm = { value -> reading.hundredths = value; persist(); dialogFor = null },
            onClear = { reading.hundredths = null; persist(); dialogFor = null },
        )
    }
}

/** "72' – 1h12" between two readings, or null while either is missing / invalid.
 *  A reading is HH.DD where each unit of the DD decimals counts a 6-minute step,
 *  so a raw difference of N (in hundredths) is N × 6 minutes:
 *  106.12 → 106.18 = 6 → 36 min ; 106.22 → 106.34 = 12 → 72 min = 1h12. */
private fun durationLabel(top: Int?, bottom: Int?): String? {
    if (top == null || bottom == null) return null
    val delta = bottom - top          // raw difference, in hundredth-units
    if (delta <= 0) return null
    val totalMin = delta * 6          // each unit = 6 minutes
    val h = totalMin / 60
    val m = totalMin % 60
    val hm = if (h > 0) "${h}h%02d".format(m) else "${m}min"
    return "$totalMin' – $hm"
}

private fun DrawScope.drawHorameter(tm: TextMeasurer, topText: String, botText: String, duration: String?, bezel: GaugeBezel) {
    val (cx, cy, r) = gaugeFace()
    drawGestureHints(cx - r * 0.98f, cy - r * 0.98f, hasLongPress = true, hasDoubleTap = false)

    val cellW = r * 1.25f
    val cellH = r * 0.34f
    fun cell(cyc: Float, text: String) {
        compactText(tm, text, cx, cyc, sizeSp = 30f, bold = true, mono = true, color = CompactStyle.Mark)
    }
    // Cells shifted up to leave room for the lobe.
    cell(cy - r * 0.32f, topText)
    cell(cy + r * 0.16f, botText)

    // Duration lobe at the bottom — split into minutes line + hhmm line.
    val parts = duration?.split(" – ")
    val minStr = parts?.getOrNull(0) ?: ""
    val hmStr  = parts?.getOrNull(1) ?: ""
    drawGaugeLobes(
        tm, cx, cy, r,
        left   = GaugeLobe("", "", GaugeColors.MarkDim),
        centre = GaugeLobeCentre(
            primary      = if (duration != null) minStr else "—",
            sub          = hmStr,
            primaryColor = CompactStyle.Accent2,
            subColor     = GaugeColors.Mark,
        ),
        right  = GaugeLobe("", "", GaugeColors.MarkDim),
    )
    drawGaugeTitlePill(tm, "HORAMETRE", cx, cy, r, bezel.hideTitle)
}

