package com.airchecklists.app.data.model

import kotlinx.serialization.Serializable

/**
 * Portable envelope for sharing a single cockpit (dashboard) between the companion
 * web builder (docs/cockpit-builder.html) and the app. Serialised as JSON, carried
 * either as a `.json` file or inside a QR code.
 *
 * On import the embedded [Dashboard.id] is ignored and a fresh UUID is assigned, so
 * importing never overwrites an existing cockpit. The [Dashboard.cells] grid is
 * re-normalised (repaired) on import, so a slightly malformed grid still loads.
 */
@Serializable
data class CockpitExport(
    val type: String = TYPE,
    val version: Int = VERSION,
    val dashboard: Dashboard,
) {
    companion object {
        const val TYPE = "airdetente.cockpit"
        const val VERSION = 1
    }
}
