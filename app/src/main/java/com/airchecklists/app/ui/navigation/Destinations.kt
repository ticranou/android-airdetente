package com.airchecklists.app.ui.navigation

import kotlinx.serialization.Serializable

/** Type-safe navigation destinations (navigation-compose 2.8+). */
object Destinations {

    @Serializable
    data object Checks

    @Serializable
    data object Vac

    @Serializable
    data object Efis

    @Serializable
    data object Settings

    /** In-app help / documentation. */
    @Serializable
    data object Help

    /** Full-screen MapLibre moving map. */
    @Serializable
    data object MapView

    /** Edit one saved dashboard (instrument layout). */
    @Serializable
    data class DashboardEdit(val dashboardId: String)

    /** Read-only aircraft info sheet (characteristics + speeds). */
    @Serializable
    data class AircraftInfo(val aircraftId: String)

    @Serializable
    data class ChecklistExecution(val aircraftId: String, val checklistId: String)

    /** aircraftId == null => create a new aircraft. */
    @Serializable
    data class AircraftEdit(val aircraftId: String? = null)

    /** checklistId == null => create a new checklist. aircraftId may pre-select the parent. */
    @Serializable
    data class ChecklistEdit(
        val aircraftId: String? = null,
        val checklistId: String? = null,
        val isSection: Boolean = false,
    )

    /** vacId == null => create a new VAC chart. */
    @Serializable
    data class VacEdit(val vacId: String? = null)

    /** Terrain detail sheet: choose Weather / VAC / … */
    @Serializable
    data class TerrainDetail(val vacId: String)

    /** Weather (METAR/TAF) for a terrain. */
    @Serializable
    data class Weather(val vacId: String)
}
