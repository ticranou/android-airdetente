package com.airchecklists.app.ui.terrain

/**
 * Extracts runway headings (QFU, in degrees) from a free-text "circuit" field.
 *
 * Handles our seed format "04/22 - QFU 041/221" (returns [41, 221]) and also
 * bare runway designators like "04/22" (→ 40°, 220°) as a fallback.
 */
object QfuParser {

    fun parse(circuit: String): List<Int> {
        if (circuit.isBlank()) return emptyList()

        // Prefer explicit QFU triplets after "QFU".
        val qfuPart = circuit.substringAfter("QFU", "").ifBlank { null }
        if (qfuPart != null) {
            val degrees = Regex("""\d{2,3}""").findAll(qfuPart)
                .mapNotNull { it.value.toIntOrNull() }
                .filter { it in 0..360 }
                .toList()
            if (degrees.isNotEmpty()) return degrees
        }

        // Fallback: runway designators like "04/22" (×10 => 40°, 220°).
        val runwayPart = circuit.substringBefore("-").substringBefore("QFU")
        return Regex("""\b(\d{2})\b""").findAll(runwayPart)
            .mapNotNull { it.groupValues[1].toIntOrNull() }
            .filter { it in 1..36 }
            .map { it * 10 }
            .toList()
    }

    /** The single heading to draw on the dial (first runway), or null. */
    fun primaryHeading(circuit: String): Int? = parse(circuit).firstOrNull()
}
