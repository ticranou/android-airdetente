package com.airchecklists.app.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AirplanemodeActive
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.filled.Paragliding
import androidx.compose.material.icons.outlined.Air
import androidx.compose.material.icons.outlined.FlightTakeoff
import androidx.compose.material.icons.outlined.Sailing
import androidx.compose.material.icons.outlined.Toys
import androidx.compose.ui.graphics.vector.ImageVector
import com.airchecklists.app.data.model.AircraftIcon

/** Resolves a stored AircraftIcon enum to a Material ImageVector for display. */
fun AircraftIcon.imageVector(): ImageVector = when (this) {
    AircraftIcon.PLANE -> Icons.Filled.Flight
    AircraftIcon.JET -> Icons.Outlined.FlightTakeoff
    AircraftIcon.HELICOPTER -> Icons.Outlined.Toys
    AircraftIcon.GLIDER -> Icons.Outlined.Air
    AircraftIcon.PARAGLIDER -> Icons.Filled.Paragliding
    AircraftIcon.ULM -> Icons.Filled.AirplanemodeActive
    AircraftIcon.DRONE -> Icons.Outlined.Toys
    AircraftIcon.BALLOON -> Icons.Outlined.Sailing
}

/** Short human label for each icon, shown in the picker. */
fun AircraftIcon.label(): String = when (this) {
    AircraftIcon.PLANE -> "Avion"
    AircraftIcon.JET -> "Jet"
    AircraftIcon.HELICOPTER -> "Hélico"
    AircraftIcon.GLIDER -> "Planeur"
    AircraftIcon.PARAGLIDER -> "Parapente"
    AircraftIcon.ULM -> "ULM"
    AircraftIcon.DRONE -> "Drone"
    AircraftIcon.BALLOON -> "Ballon"
}
