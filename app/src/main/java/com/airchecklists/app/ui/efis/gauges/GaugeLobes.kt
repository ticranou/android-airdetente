package com.airchecklists.app.ui.efis.gauges

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Three-lobe bottom readout for round analog gauges.
 *
 * Layout (matches mockup):
 *
 *  Centre lobe  — ellipse, width = 2r, top edge tangent to bottom of face circle (cy+r),
 *                 so its centre Y = cy + r + cH/2.
 *
 *  Side lobes   — smaller ellipses, outer edge flush with circle edge (cx±r),
 *                 bottom edge at the vertical midpoint of the centre lobe (cy + r + cH/2).
 *                 So side centre Y = cy + r + cH/2 - sH/2,
 *                    side centre X = cx ∓ r + sW/2  (left) / cx + r - sW/2 (right).
 */

private const val CENTRE_RATIO = 2.5f   // centre width / side width (aspect hint only)

data class GaugeLobe(
    val label: String,
    val value: String,
    val valueColor: Color,
)

data class GaugeLobeCentre(
    val primary: String,
    val sub: String = "",
    val primaryColor: Color = GaugeColors.Mark,
    val subColor: Color = GaugeColors.MarkDim,
)

private val CHIP_BG     = Color(0xE8101418)
private val CHIP_BORDER = Color(0x99FFFFFF)

fun DrawScope.drawGaugeLobes(
    tm: TextMeasurer,
    cx: Float,
    cy: Float,
    r: Float,
    left: GaugeLobe,
    centre: GaugeLobeCentre,
    right: GaugeLobe,
) {
    // ── Font sizes scaled from r ──────────────────────────────────────────────────────
    val labelSp    = (r * 0.075f).coerceIn(7f, 11f)
    val valueSp    = (r * 0.100f).coerceIn(9f, 14f)
    val centPrimSp = (r * 0.145f).coerceIn(13f, 20f)
    val centSubSp  = (r * 0.098f).coerceIn(9f,  14f)

    val lLbl = tm.measure(left.label,     TextStyle(color = GaugeColors.MarkDim,  fontSize = labelSp.sp))
    val lVal = tm.measure(left.value,     TextStyle(color = left.valueColor,       fontSize = valueSp.sp,    fontWeight = FontWeight.Bold))
    val rLbl = tm.measure(right.label,    TextStyle(color = GaugeColors.MarkDim,  fontSize = labelSp.sp))
    val rVal = tm.measure(right.value,    TextStyle(color = right.valueColor,      fontSize = valueSp.sp,    fontWeight = FontWeight.Bold))
    val cPri = tm.measure(centre.primary, TextStyle(color = centre.primaryColor,  fontSize = centPrimSp.sp, fontWeight = FontWeight.Bold))
    val cSub = if (centre.sub.isNotEmpty())
        tm.measure(centre.sub, TextStyle(color = centre.subColor, fontSize = centSubSp.sp, fontWeight = FontWeight.Bold))
    else null

    val padH = r * 0.08f
    val padV = r * 0.05f
    val gap  = r * 0.02f

    // ── Centre lobe dimensions ────────────────────────────────────────────────────────
    // Width = 2r (spans the full circle diameter).  Height driven by content.
    val cW = 1.6f * r
    val cContentH = cPri.size.height + (if (cSub != null) gap + cSub.size.height else 0f)
    val cH = (cContentH + padV * 2f).coerceAtLeast(r * 0.28f)

    // Centre lobe: bottom edge slightly below cy+r (clears the carbon bezel ring)
    val cCentreY = cy + r + r * 0.06f - cH / 2f
    val cCentreX = cx

    // ── Side lobe dimensions ──────────────────────────────────────────────────────────
    // Width: content + padding, must fit between circle edge and centre lobe edge.
    // Max side width = (cW - content_gap) / 2, but we keep them narrower than centre.
    val sWContent = maxOf(
        lLbl.size.width, lVal.size.width,
        rLbl.size.width, rVal.size.width,
    ).toFloat()
    val sWMax = r * 0.85f   // wider side lobes
    val sW = (sWContent + padH * 2f).coerceIn(r * 0.52f, sWMax)
    val sH = (lLbl.size.height + lVal.size.height + gap + padV * 2f).coerceAtLeast(r * 0.22f)

    // Side lobes: bottom edge = cCentreY (vertical midpoint of centre lobe)
    // so side centreY = cCentreY - sH/2
    val sCentreY = cCentreY - sH / 2f

    // Outer edge of side lobe flush with circle: leftX outer = cx-r, rightX outer = cx+r
    val lCentreX = cx - r + sW / 2f
    val rCentreX = cx + r - sW / 2f

    // ── Ellipse draw helper ───────────────────────────────────────────────────────────
    // drawOval takes topLeft + size.
    fun ellipse(centreX: Float, centreY: Float, w: Float, h: Float) {
        val tl = Offset(centreX - w / 2f, centreY - h / 2f)
        val sz = Size(w, h)
        drawOval(CHIP_BG,     topLeft = tl, size = sz)
        drawOval(CHIP_BORDER, topLeft = tl, size = sz,
            style = Stroke(width = (r * 0.013f).coerceAtLeast(1.2f)))
    }

    // Draw centre first (underneath side lobes in z-order)
    ellipse(cCentreX, cCentreY, cW, cH)

    // Draw side lobes only when they carry content (label or value non-empty)
    val showSides = left.label.isNotEmpty() || left.value.isNotEmpty() ||
                    right.label.isNotEmpty() || right.value.isNotEmpty()
    if (showSides) {
        ellipse(lCentreX, sCentreY, sW, sH)
        ellipse(rCentreX, sCentreY, sW, sH)
    }

    // ── Centre lobe text ──────────────────────────────────────────────────────────────
    run {
        val top = cCentreY - cH / 2f + padV
        drawText(cPri, topLeft = Offset(cCentreX - cPri.size.width / 2f, top))
        if (cSub != null) {
            drawText(cSub, topLeft = Offset(cCentreX - cSub.size.width / 2f,
                top + cPri.size.height + gap))
        }
    }

    // ── Left lobe text ────────────────────────────────────────────────────────────────
    if (showSides) run {
        val top = sCentreY - sH / 2f + padV
        drawText(lLbl, topLeft = Offset(lCentreX - lLbl.size.width / 2f, top))
        drawText(lVal, topLeft = Offset(lCentreX - lVal.size.width / 2f,
            top + lLbl.size.height + gap))
    }

    // ── Right lobe text ───────────────────────────────────────────────────────────────
    if (showSides) run {
        val top = sCentreY - sH / 2f + padV
        drawText(rLbl, topLeft = Offset(rCentreX - rLbl.size.width / 2f, top))
        drawText(rVal, topLeft = Offset(rCentreX - rVal.size.width / 2f,
            top + rLbl.size.height + gap))
    }
}
