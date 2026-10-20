package com.airchecklists.app.data.local

import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * AIRAC cycle helper.
 *
 * AIRAC cycles are published every 28 days on a fixed worldwide calendar.
 * The SIA eAIP paths used by [com.airchecklists.app.data.net.VacDownloader]
 * embed the cycle *effective date* in the form `DD_MON_YYYY`, where `MON` is
 * the English three-letter month abbreviation in uppercase (e.g. `09_JUL_2026`).
 * The fixed `eAIP_` prefix lives in the base URL — this value is only the
 * variable date segment. The month MUST stay English — the SIA media URLs
 * depend on it.
 *
 * The cycle dates are anchored on a known reference (cycle 2601 began on
 * 2026-01-22, per the official AIRAC calendar) and stepped by 28 days.
 */
object AiracCycle {

    /** A known AIRAC effective date (cycle 2601). */
    private val ANCHOR: LocalDate = LocalDate.of(2026, 1, 22)

    private const val PERIOD_DAYS = 28L

    /** English three-letter month abbreviations, uppercase, as used by the SIA. */
    private val MONTHS_EN = arrayOf(
        "JAN", "FEB", "MAR", "APR", "MAY", "JUN",
        "JUL", "AUG", "SEP", "OCT", "NOV", "DEC",
    )

    /** Returns the effective date of the AIRAC cycle in force on [date]. */
    fun effectiveDateFor(date: LocalDate): LocalDate {
        val daysSinceAnchor = ChronoUnit.DAYS.between(ANCHOR, date)
        val cyclesElapsed = Math.floorDiv(daysSinceAnchor, PERIOD_DAYS)
        return ANCHOR.plusDays(cyclesElapsed * PERIOD_DAYS)
    }

    /** Formats an effective date as the SIA cycle segment `DD_MON_YYYY`. */
    fun format(date: LocalDate): String {
        val day = "%02d".format(date.dayOfMonth)
        val mon = MONTHS_EN[date.monthValue - 1]
        return "${day}_${mon}_${date.year}"
    }

    /** Current AIRAC cycle segment for [today] (defaults to the system date). */
    fun current(today: LocalDate = LocalDate.now(ZoneId.systemDefault())): String =
        format(effectiveDateFor(today))

    /**
     * Normalizes a stored cycle value to the bare `DD_MON_YYYY` form, stripping
     * a legacy `eAIP_` prefix if present. Used to migrate values persisted
     * before the prefix was moved into the base URL.
     */
    fun normalize(cycle: String): String = cycle.removePrefix("eAIP_")
}

