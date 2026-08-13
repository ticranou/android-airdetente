package com.airchecklists.app.data.model

import kotlinx.serialization.Serializable

/**
 * A single technical characteristic of an aircraft, e.g.
 * label = "Vitesse de décrochage (Vs)", value = "65", unit = "km/h".
 */
@Serializable
data class Characteristic(
    val id: String,
    val label: String,
    val value: String,
    val unit: String = "",
)

/**
 * Full data for one aircraft ("appareil"). This is the unit of persistence:
 * one Aircraft == one JSON file, holding its characteristics AND all its
 * checklists (with their items). Keeping everything in one file makes offline
 * editing and JSON import/export straightforward.
 */
@Serializable
data class Aircraft(
    val id: String,
    val schemaVersion: Int = 1,
    val name: String,
    val subtitle: String = "",
    val icon: AircraftIcon = AircraftIcon.PLANE,
    val characteristics: List<Characteristic> = emptyList(),
    val checklists: List<Checklist> = emptyList(),
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
    /** Display order in the aircraft list. Lower comes first; ties break by name. */
    val sortIndex: Int = 0,
    /** Characteristic speeds (km/h) for the EFIS airspeed arcs (0 = unset). */
    val vs0: Int = 0,        // stall, full flaps  → red cursor
    val vs1: Int = 0,        // stall, clean       → red cursor
    val greenMin: Int = 0,   // green arc start (normal operating range)
    val greenMax: Int = 0,   // green arc end
    val whiteMin: Int = 0,   // white arc start (flaps range, full flaps)
    val whiteMid: Int = 0,   // white arc middle (full flaps ↔ 1 notch boundary)
    val whiteMax: Int = 0,   // white arc end (1 notch)
    val vno: Int = 0,        // max structural cruising → marker on green row
    val vne: Int = 0,        // never exceed            → marker on green row
    val vpl: Int = 0,        // best glide speed        → magenta cursor on white row
)
