package com.airchecklists.app.data.model

import kotlinx.serialization.Serializable

/**
 * Built-in icon set the user picks from when creating an aircraft.
 * Serialized by enum name so the JSON stays stable across releases.
 * Vector resolution lives in the UI layer (AircraftIcon.imageVector()).
 */
@Serializable
enum class AircraftIcon {
    PLANE,
    JET,
    HELICOPTER,
    GLIDER,
    PARAGLIDER,
    ULM,
    DRONE,
    BALLOON,
}
