package com.airchecklists.app.data.model

/**
 * Characteristic-speed thresholds (km/h) used to draw the airspeed gauge arcs.
 * Built from a reference aircraft's speeds; null fields disable the related mark.
 *
 * Two arc rows on the EFIS:
 *  - Green row: green band [greenMin,greenMax] + Vno/Vne markers + Vs0/Vs1 red cursors.
 *  - White row: white band [whiteMin,whiteMax] (whiteMid splits full-flaps / 1-notch)
 *               + Vpl magenta cursor.
 */
data class SpeedArcs(
    val vs0: Int?,       // stall, full flaps  → red cursor
    val vs1: Int?,       // stall, clean       → red cursor
    val greenMin: Int?,  // green arc start
    val greenMax: Int?,  // green arc end
    val whiteMin: Int?,  // white arc start (full flaps)
    val whiteMid: Int?,  // white arc middle (full flaps ↔ 1 notch)
    val whiteMax: Int?,  // white arc end (1 notch)
    val vno: Int?,       // max structural cruise → marker
    val vne: Int?,       // never exceed          → marker
    val vpl: Int?,       // best glide            → magenta cursor
) {
    val hasAny: Boolean
        get() = listOf(vs0, vs1, greenMin, greenMax, whiteMin, whiteMid, whiteMax, vno, vne, vpl)
            .any { it != null && it > 0 }

    /** Same arcs with every threshold multiplied by [factor] (e.g. km/h → kt). */
    fun scaled(factor: Float): SpeedArcs {
        fun s(v: Int?): Int? = v?.let { kotlin.math.round(it * factor).toInt() }
        return SpeedArcs(s(vs0), s(vs1), s(greenMin), s(greenMax), s(whiteMin), s(whiteMid), s(whiteMax), s(vno), s(vne), s(vpl))
    }

    companion object {
        fun from(a: Aircraft): SpeedArcs = SpeedArcs(
            vs0 = a.vs0.takeIf { it > 0 },
            vs1 = a.vs1.takeIf { it > 0 },
            greenMin = a.greenMin.takeIf { it > 0 },
            greenMax = a.greenMax.takeIf { it > 0 },
            whiteMin = a.whiteMin.takeIf { it > 0 },
            whiteMid = a.whiteMid.takeIf { it > 0 },
            whiteMax = a.whiteMax.takeIf { it > 0 },
            vno = a.vno.takeIf { it > 0 },
            vne = a.vne.takeIf { it > 0 },
            vpl = a.vpl.takeIf { it > 0 },
        )
    }
}
