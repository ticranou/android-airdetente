package com.airchecklists.app.data.model

import kotlinx.serialization.Serializable

/** How the moving map is oriented. */
@Serializable
enum class MapOrientation {
    NORTH_UP,
    TRACK_UP,
}
