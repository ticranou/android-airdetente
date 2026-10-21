package com.airchecklists.app.ui.efis.gauges.trammer

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.airchecklists.app.data.model.TrammerSnapshot
import com.airchecklists.app.di.ServiceLocator
import com.airchecklists.app.ui.efis.gauges.LocalGaugeBezel
import com.airchecklists.app.ui.efis.gauges.LocalHideBorder
import com.airchecklists.app.ui.efis.gauges.LocalHideTitle
import com.airchecklists.app.ui.efis.gauges.compact.CompactStyle
import com.airchecklists.app.ui.efis.gauges.compact.compactText
import com.airchecklists.app.ui.efis.gauges.drawNumTitleBar
import java.util.Calendar

/** The seven reporting-point checks, in order (letters T,R,A,M,M,E,R). */
private val TRAMMER_LETTERS = listOf("T", "R", "A", "M", "M", "E", "R")
private val TRAMMER_LABELS = listOf("Top", "Route", "Altitude", "Moteur", "Météo", "Essence", "Radio")

private val PILL_ON     = Color(0xFF5CB85C)   // green when validated
private val PILL_OFF_BG  = Color(0x22FFFFFF)  // faint translucent fill when not validated
private val SWITCH_ON   = Color(0xFF1E8B1E)
private val SWITCH_OFF  = Color(0xFF444444)
private val KNOB        = Color(0xFFEEEEEE)
private val TIME_BLUE   = Color(0xFF4FB0E8)
private val BADGE_BG    = Color(0xDD1A1A1A)   // same as the MAG/GPS source badge on NUMCAP

/**
 * NUMTRM — TRAMMER (V2). A reporting-point mnemonic.
 *
 * Layout:
 *  - LEFT: a dark badge (same style as the MAG/GPS source tag on NUMCAP) showing
 *    the last TRAMMER validation time (HHhMM) written vertically in blue.
 *  - CENTRE: the seven letters "T R A M M E R" as large pills — translucent by
 *    default, turning green once the matching check is validated.
 *  - RIGHT: a single large switch that drives the *current* check — the first
 *    letter not yet validated. Its label (Top/Route/Altitude/…) is shown above.
 *    DOUBLE-TAP the switch to validate the current letter and advance to the next.
 *
 * When the last letter is validated, all seven are green and the current wall-clock
 * time freezes on the left; 5 s later the whole instrument resets to its initial
 * state (all OFF, cursor back on Top). The pills themselves are not tappable.
 *
 * State is persisted per cell in [TrammerSnapshot], surviving dashboard switch and
 * process restart (same pattern as ANLACT).
 */
@Composable
fun TrammerInstrument(cellIdx: Int, modifier: Modifier = Modifier) {
    val tm = rememberTextMeasurer()
    val bezel = LocalGaugeBezel.current
    val hideTitle = LocalHideTitle.current
    val hideBorder = LocalHideBorder.current

    val prefs by ServiceLocator.preferences.preferences.collectAsStateWithLifecycle()
    val snap = prefs.instruments.trammerSlots[cellIdx] ?: TrammerSnapshot()
    val checks = snap.checks
    val validatedMs = snap.validatedMs
    // Current check = first letter not yet validated (-1 when all validated).
    val cursor = checks.indexOfFirst { !it }

    fun setCheck(index: Int, value: Boolean) {
        ServiceLocator.updateInstruments { s ->
            val cur = s.trammerSlots[cellIdx] ?: TrammerSnapshot()
            val newChecks = cur.checks.toMutableList().also {
                if (index in it.indices) it[index] = value
            }
            val allOk = newChecks.all { it }
            // validatedMs = time of the LAST completed TRAMMER. Set it only when the
            // cycle completes; never clear it (it stays visible as the previous
            // reporting-point time through the reset and the next cycle).
            val newValidatedMs = if (allOk) System.currentTimeMillis() else cur.validatedMs
            s.copy(
                trammerSlots = s.trammerSlots + (cellIdx to cur.copy(
                    checks = newChecks, validatedMs = newValidatedMs,
                )),
            )
        }
    }

    fun reset() {
        // Clear the seven toggles but KEEP validatedMs (last TRAMMER time stays shown).
        ServiceLocator.updateInstruments { s ->
            val cur = s.trammerSlots[cellIdx] ?: TrammerSnapshot()
            s.copy(trammerSlots = s.trammerSlots + (cellIdx to TrammerSnapshot(validatedMs = cur.validatedMs)))
        }
    }

    // Once fully validated, wipe the toggles back to the initial state after 5 s
    // (the frozen time is preserved by reset()).
    val allValidated = checks.all { it }
    LaunchedEffect(allValidated) {
        if (allValidated) {
            kotlinx.coroutines.delay(5_000)
            reset()
        }
    }

    Canvas(
        modifier = modifier.fillMaxSize().pointerInput(Unit) {
            detectTapGestures(onDoubleTap = { offset ->
                val w = size.width.toFloat()
                val h = size.height.toFloat()
                val headerH = if (hideTitle) 0f else 20.dp.toPx().coerceAtMost(h * 0.4f)
                if (offset.y < headerH) return@detectTapGestures
                val switchZoneW = w * 0.26f
                val pillsRight = w - switchZoneW
                // Only the right-hand switch zone reacts, and only to a double-tap:
                // validate the current letter and advance.
                if (offset.x >= pillsRight) {
                    val snapNow = ServiceLocator.instrumentPersist.trammerSlots[cellIdx] ?: TrammerSnapshot()
                    val cur = snapNow.checks.indexOfFirst { !it }
                    if (cur in 0..6) setCheck(cur, true)
                }
            })
        },
    ) {
        val w = size.width
        val h = size.height
        drawRect(CompactStyle.Bg, size = size)

        val headerH = if (hideTitle) 0f else 20.dp.toPx().coerceAtMost(h * 0.4f)
        if (headerH > 0f) {
            drawNumTitleBar(bezel, w, headerH)
            compactText(tm, "TRAMMER", w / 2f, headerH / 2f, sizeSp = 12f, color = CompactStyle.Dim)
        }
        if (!hideBorder) drawRect(Color(0xFF3A3A3A), size = size, style = Stroke(width = 2f))

        val bodyTop = headerH
        val bodyH = h - headerH
        val bodyCy = bodyTop + bodyH / 2f

        // ── Left: dark badge with vertical validation time ───────────────────
        val badgeW = w * 0.07f
        drawRect(BADGE_BG, topLeft = Offset(0f, bodyTop), size = Size(badgeW, bodyH))
        drawLine(Color(0xFF3A3A3A), Offset(badgeW, bodyTop), Offset(badgeW, h), strokeWidth = 1f)
        if (validatedMs != null) {
            val cal = Calendar.getInstance().apply { timeInMillis = validatedMs }
            val txt = "%02dh%02d".format(cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE))
            val measured = tm.measure(
                txt,
                TextStyle(color = TIME_BLUE, fontSize = 14.sp, fontWeight = FontWeight.Bold),
            )
            val pivot = Offset(badgeW / 2f, bodyCy)
            withTransform({ rotate(-90f, pivot = pivot) }) {
                drawText(measured, topLeft = Offset(pivot.x - measured.size.width / 2f, pivot.y - measured.size.height / 2f))
            }
        }

        // ── Right: current-letter label + one large switch ───────────────────
        val switchZoneW = w * 0.26f
        val pillsLeft = badgeW + w * 0.03f   // gap between the time badge and the first pill
        val pillsRight = w - switchZoneW
        val switchCx = pillsRight + switchZoneW / 2f

        val switchOn = cursor < 0   // all validated → switch sits ON
        // Label above the switch = current letter's full word (or "OK" when complete).
        val labelTxt = if (cursor in 0..6) TRAMMER_LABELS[cursor] else "Terminé"
        compactText(tm, labelTxt, switchCx, bodyTop + bodyH * 0.30f, sizeSp = 15f, bold = true, color = CompactStyle.Mark)

        val trackW = switchZoneW * 0.62f
        val trackH = (bodyH * 0.30f).coerceAtMost(trackW * 0.52f)
        val trackLeft = switchCx - trackW / 2f
        val switchCy = bodyTop + bodyH * 0.64f
        val trackTop = switchCy - trackH / 2f
        drawRoundRect(
            color = if (switchOn) SWITCH_ON else SWITCH_OFF,
            topLeft = Offset(trackLeft, trackTop),
            size = Size(trackW, trackH),
            cornerRadius = CornerRadius(trackH / 2f),
        )
        val knobR = trackH * 0.40f
        val knobCx = if (switchOn) trackLeft + trackW - knobR - trackH * 0.12f
                     else trackLeft + knobR + trackH * 0.12f
        drawCircle(KNOB, radius = knobR, center = Offset(knobCx, switchCy))

        // ── Centre: seven large letter pills ─────────────────────────────────
        val colW = (pillsRight - pillsLeft) / 7f
        val pillH = bodyH * 0.72f
        val pillW = (colW * 0.82f).coerceAtMost(pillH)
        val corner = pillW * 0.28f
        for (i in 0..6) {
            val colCx = pillsLeft + (i + 0.5f) * colW
            val on = checks.getOrElse(i) { false }
            val pillLeft = colCx - pillW / 2f
            val pillTop = bodyCy - pillH / 2f
            if (on) {
                drawRoundRect(
                    color = PILL_ON,
                    topLeft = Offset(pillLeft, pillTop),
                    size = Size(pillW, pillH),
                    cornerRadius = CornerRadius(corner, corner),
                )
            } else {
                drawRoundRect(
                    color = PILL_OFF_BG,
                    topLeft = Offset(pillLeft, pillTop),
                    size = Size(pillW, pillH),
                    cornerRadius = CornerRadius(corner, corner),
                )
            }
            compactText(
                tm, TRAMMER_LETTERS[i], colCx, bodyCy,
                sizeSp = 30f, bold = true,
                color = if (on) Color.Black else CompactStyle.Dim,
            )
        }
    }
}
