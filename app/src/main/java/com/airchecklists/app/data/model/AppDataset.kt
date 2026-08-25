package com.airchecklists.app.data.model

import kotlinx.serialization.Serializable

/**
 * A full, portable snapshot of the user-authored content of the app: aircraft
 * (with their characteristics & checklists), terrains (VAC charts) and the
 * "business" preferences (dashboards, units, EFIS sources, appearance…).
 *
 * This is the payload of the hidden "Data" tab export/import. It deliberately
 * EXCLUDES volatile/per-device state (running chronos, heading/altitude targets,
 * disclaimer acceptance, the installed offline-map tag, the seed version) so the
 * exported file is a clean, reusable dataset — suitable for shipping as the app's
 * default content.
 */
@Serializable
data class AppDataset(
    val version: Int = DATASET_VERSION,
    val aircraft: List<Aircraft> = emptyList(),
    val terrains: List<VacChart> = emptyList(),
    val preferences: DatasetPrefs = DatasetPrefs(),
) {
    companion object {
        const val DATASET_VERSION = 1
    }
}

/**
 * The subset of [AppPreferences] that is meaningful as shippable content. Mirrors
 * the "business" fields only; volatile instrument state and per-device flags are
 * intentionally omitted.
 */
@Serializable
data class DatasetPrefs(
    val themeMode: ThemeMode = ThemeMode.AUTO,
    val fontScale: Float = 1.0f,
    val vacAiracCycle: String = "eAIP_09_JUL_2026",
    val splashSeconds: Int = 2,
    val efisHeadingSource: EfisHeadingSource = EfisHeadingSource.MAGNETIC,
    val efisVarioSource: EfisVarioSource = EfisVarioSource.GPS,
    val efisSpeedUnit: EfisSpeedUnit = EfisSpeedUnit.KMH,
    val altitudeUnit: AltitudeUnit = AltitudeUnit.FEET,
    val efisResponsiveness: Float = 0.35f,
    val efisShowValues: Boolean = true,
    val keepScreenOn: Boolean = true,
    val dashboards: List<Dashboard> = emptyList(),
    val mapOrientation: MapOrientation = MapOrientation.NORTH_UP,
    val cockpitPagerStyle: CockpitPagerStyle = CockpitPagerStyle.DOTS,
    val cockpitPagerPosition: CockpitPagerPosition = CockpitPagerPosition.TOP,
    val gaugeBezelStyle: GaugeBezelStyle = GaugeBezelStyle.SOLID,
    val gaugeBezelColor: Long = 0xFF1C1C1C,
    val mapLayers: MapLayerPrefs = MapLayerPrefs(),
    val mapShowZoomButtons: Boolean = false,
    val wxLayers: WxLayerPrefs = WxLayerPrefs(),
) {
    /** Extract the shippable prefs from a full [AppPreferences]. */
    companion object {
        fun from(p: AppPreferences): DatasetPrefs = DatasetPrefs(
            themeMode = p.themeMode,
            fontScale = p.fontScale,
            vacAiracCycle = p.vacAiracCycle,
            splashSeconds = p.splashSeconds,
            efisHeadingSource = p.efisHeadingSource,
            efisVarioSource = p.efisVarioSource,
            efisSpeedUnit = p.efisSpeedUnit,
            altitudeUnit = p.altitudeUnit,
            efisResponsiveness = p.efisResponsiveness,
            efisShowValues = p.efisShowValues,
            keepScreenOn = p.keepScreenOn,
            dashboards = p.dashboards,
            mapOrientation = p.mapOrientation,
            cockpitPagerStyle = p.cockpitPagerStyle,
            cockpitPagerPosition = p.cockpitPagerPosition,
            gaugeBezelStyle = p.gaugeBezelStyle,
            gaugeBezelColor = p.gaugeBezelColor,
            mapLayers = p.mapLayers,
            mapShowZoomButtons = p.mapShowZoomButtons,
            wxLayers = p.wxLayers,
        )
    }

    /** Apply these prefs onto [current], preserving all volatile/per-device fields. */
    fun applyOnto(current: AppPreferences): AppPreferences = current.copy(
        themeMode = themeMode,
        fontScale = fontScale,
        vacAiracCycle = vacAiracCycle,
        splashSeconds = splashSeconds,
        efisHeadingSource = efisHeadingSource,
        efisVarioSource = efisVarioSource,
        efisSpeedUnit = efisSpeedUnit,
        altitudeUnit = altitudeUnit,
        efisResponsiveness = efisResponsiveness,
        efisShowValues = efisShowValues,
        keepScreenOn = keepScreenOn,
        dashboards = dashboards,
        mapOrientation = mapOrientation,
        cockpitPagerStyle = cockpitPagerStyle,
        cockpitPagerPosition = cockpitPagerPosition,
        gaugeBezelStyle = gaugeBezelStyle,
        gaugeBezelColor = gaugeBezelColor,
        mapLayers = mapLayers,
        mapShowZoomButtons = mapShowZoomButtons,
        wxLayers = wxLayers,
    )
}
