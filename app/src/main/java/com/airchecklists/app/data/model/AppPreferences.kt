package com.airchecklists.app.data.model

import kotlinx.serialization.Serializable

/** How the app resolves its color scheme. */
@Serializable
enum class ThemeMode {
    LIGHT,
    DARK,
    AUTO,
}

/** Source of the EFIS heading tape. */
@Serializable
enum class EfisHeadingSource {
    MAGNETIC,
    GPS_TRACK,
}

/** Source of the EFIS variometer. */
@Serializable
enum class EfisVarioSource {
    GPS,
    BAROMETER,
}

/** An instrument that can be placed in an EFIS grid slot. Declaration order is
 *  the order shown in the settings picker. */
@Serializable
enum class EfisInstrument {
    NONE,
    HEADING,             // Conservateur de cap analogique
    HEADING_COMPACT,     // Conservateur de cap digital (1 ligne)
    AIRSPEED,            // Vitesse analogique
    AIRSPEED_COMPACT,    // Vitesse digitale (1 ligne)
    ALTIMETER,           // Altimètre analogique
    VARIOMETER,          // Variomètre analogique
    ALTVARIO_COMPACT,    // Altimètre + Vario digitaux (1 ligne)
    ATTITUDE,            // Horizon Artificiel analogique
    ATTITUDE_COMPACT,    // Horizon Artificiel digital (2 lignes)
    BALL,                // Bille analogique
    BALL_COMPACT,        // Bille digitale (1 ligne)
    EFIS_COMPACT,        // EFIS (3 lignes mini)
    MOVING_MAP,          // Moving Map (carte mobile)
    CHRONO,              // Chronomètre analogique (circulaire, 2 chronos)
    CHRONO_COMPACT,      // Chronomètre numérique (rectangulaire, 1 chrono)
    COUNTDOWN_ANALOG,    // Compte à rebours analogique (circulaire)
    COUNTDOWN_COMPACT,   // Compte à rebours numérique (rectangulaire, 1 rebours)
    HORAMETER,           // Horamètre analogique (2 relevés → durée de vol)
    HORAMETER_COMPACT,   // Horamètre numérique (rectangulaire)
    WEATHER_RADAR,       // Météo analogique (radar précipitations + vent FL20)
    WEATHER_RADAR_COMPACT, // Radar météo numérique (rectangulaire, double hauteur)
    TERRAINS,            // Accès rapide terrains analogique (triés par proximité)
    TERRAINS_COMPACT,    // Terrains numérique (rectangulaire, 3 côte à côte)
    WATCH,               // Montre analogique (aiguilles)
    WATCH_COMPACT,       // Montre numérique (rectangulaire)
    NAV_PLANNER,         // Prépa navigation (plein écran, 6 lignes)
    ANLFDR,              // Flight Recorder (enregistreur de vol, jauge ronde)
    NUMFDR,              // Flight Recorder numérique (rectangulaire, 100%-1L)
    NUMAPP,              // Aide à l'approche finale (highway-in-the-sky, 100%-3L)
    ;

    /** True for the rectangular "compact" variants (fill the whole cell). */
    val isCompact: Boolean
        get() = this in setOf(
            ATTITUDE_COMPACT, HEADING_COMPACT, BALL_COMPACT,
            AIRSPEED_COMPACT, ALTVARIO_COMPACT, EFIS_COMPACT, MOVING_MAP,
            CHRONO, CHRONO_COMPACT, COUNTDOWN_ANALOG, COUNTDOWN_COMPACT,
            HORAMETER, HORAMETER_COMPACT, WEATHER_RADAR, WEATHER_RADAR_COMPACT,
            TERRAINS, TERRAINS_COMPACT, WATCH, WATCH_COMPACT, NAV_PLANNER, NUMFDR,
            NUMAPP,
        )

    /** True for the round "analog" gauges (the only ones honouring the
     *  "show numeric values" preference). */
    val isAnalog: Boolean
        get() = this in setOf(ALTIMETER, VARIOMETER, ATTITUDE, HEADING, BALL, AIRSPEED, ANLFDR)

    /** True for the numeric "single-line" instruments (label "…-1L"): they render
     *  at a fixed natural height, vertically centred, instead of stretching to fill
     *  an over-sized merged cell (the surplus becomes black margin). Excludes the
     *  multi-line compacts (ATTITUDE_COMPACT, EFIS_COMPACT, MOVING_MAP,
     *  WEATHER_RADAR_COMPACT) and every analog/round gauge. */
    val isSingleLine: Boolean
        get() = this in setOf(
            HEADING_COMPACT, AIRSPEED_COMPACT, ALTVARIO_COMPACT, BALL_COMPACT,
            CHRONO_COMPACT, COUNTDOWN_COMPACT, HORAMETER_COMPACT, TERRAINS_COMPACT,
            WATCH_COMPACT, NUMFDR,
        )

    /** Natural (capped) height in dp for a single-line instrument. Content-dense
     *  ones get more room: the horameter draws a label ABOVE each cell plus a
     *  two-line duration; terrains shows 3 ICAO cards (18sp); the FDR shows a badge
     *  over a chip row. The rest (single value cell or tape) fit the base height.
     *  Only meaningful when [isSingleLine]. */
    val singleLineHeightDp: Int
        get() = when (this) {
            HORAMETER_COMPACT -> 120
            TERRAINS_COMPACT -> 110
            NUMFDR -> 96
            else -> 88
        }

    /** Instruments that need a working orientation (pitch/roll/magnetic heading):
     *  attitude, heading and slip-ball variants, plus the combined EFIS block.
     *  On a device without gyroscope/magnetometer these show an "unavailable"
     *  overlay instead of frozen/wrong readings. */
    val requiresOrientation: Boolean
        get() = this in setOf(
            ATTITUDE, ATTITUDE_COMPACT, HEADING, HEADING_COMPACT,
            BALL, BALL_COMPACT, EFIS_COMPACT,
        )

    /** Legacy row-span default, used ONLY when migrating an old flat grid into the
     *  new cell-merge model so existing cockpits keep the same look. Live layouts
     *  size instruments via DashboardCell spans, not this. */
    val legacyRowSpan: Int
        get() = when (this) {
            MOVING_MAP -> AppPreferences.EFIS_MAX_ROWS
            EFIS_COMPACT -> 3
            NUMAPP -> 3
            ATTITUDE_COMPACT -> 2
            else -> 1
        }
}

/** Unit for the EFIS airspeed gauge. */
@Serializable
enum class EfisSpeedUnit {
    KMH,
    KNOTS,
}

/** Unit for all altitude / vertical-speed readouts. */
@Serializable
enum class AltitudeUnit {
    FEET,
    METERS,
}

/** Altitude / vertical-speed conversions + formatting for the selected unit. */
object AltitudeFormat {
    private const val FT_PER_M = 3.280839895

    /** Convert a value in feet to the display unit. */
    fun altValue(ft: Float, unit: AltitudeUnit): Float =
        if (unit == AltitudeUnit.METERS) (ft / FT_PER_M).toFloat() else ft

    /** Short altitude unit label ("ft" / "m"). */
    fun altLabel(unit: AltitudeUnit): String = if (unit == AltitudeUnit.METERS) "m" else "ft"

    /** Convert a vertical speed in ft/min to the display unit (ft/min or m/s). */
    fun vsValue(ftMin: Float, unit: AltitudeUnit): Float =
        if (unit == AltitudeUnit.METERS) (ftMin / FT_PER_M / 60.0).toFloat() else ftMin

    /** Vertical-speed unit label ("ft/min" / "m/s"). */
    fun vsLabel(unit: AltitudeUnit): String = if (unit == AltitudeUnit.METERS) "m/s" else "ft/min"
}

/** Visual style of the cockpit page marker (which dashboard is shown). */
@Serializable
enum class CockpitPagerStyle {
    DOTS,     // small filled circles
    BARS,     // short horizontal bars (active is wider/filled)
    NUMBERS,  // "2 / 4" text
}

/** Where the cockpit page marker sits on the page. */
@Serializable
enum class CockpitPagerPosition {
    TOP,
    BOTTOM,
}

/** Bezel (outer ring) style of the round analog gauges. */
@Serializable
enum class GaugeBezelStyle {
    SOLID,    // flat colour (gaugeBezelColor)
    CARBON,   // procedural carbon-fibre weave
    BRUSHED,  // brushed-metal radial gradient
}

/** Which OpenAIP overlays are shown on the moving map (default: all visible). */
@Serializable
data class MapLayerPrefs(
    val aerodromes: Boolean = true,
    val airspaces: Boolean = true,
    val navaids: Boolean = true,
    val obstacles: Boolean = true,
    val reportingPoints: Boolean = true,
    val cities: Boolean = true,
    val aerodromeCodes: Boolean = true,
    /** Private / restricted airfields (OpenAIP type != 9), shown as a separate layer. */
    val privateAirfields: Boolean = true,
) {
    /** True if the layer whose GeoJSON base name is [base] should be visible. */
    fun visible(base: String): Boolean = when {
        base.startsWith("aero") -> aerodromes
        base.startsWith("airspace") -> airspaces
        base.startsWith("navaid") -> navaids
        base.startsWith("obstacle") -> obstacles
        base.startsWith("reporting") -> reportingPoints
        else -> true
    }
}

/** Columns are fixed at 2 in the cell-merge model. */
const val EFIS_COLS = 2

/**
 * One cell of a dashboard's 2×N grid. A "master" cell (covered == false) owns a
 * [colSpan]×[rowSpan] block; the cells it absorbs are marked [covered] and render
 * nothing. An empty master is instrument == NONE with 1×1 span.
 */
@Serializable
data class DashboardCell(
    val instrument: EfisInstrument = EfisInstrument.NONE,
    val colSpan: Int = 1,          // 1 or 2
    val rowSpan: Int = 1,          // 1..rows
    val covered: Boolean = false,  // absorbed by another master → not rendered/edited
    /** Per-instrument accent colour (ARGB) overriding the global bezel colour; null = inherit. */
    val accentColor: Long? = null,
    /** Per-instrument bezel style override (SOLID/CARBON/BRUSHED); null = inherit global. */
    val bezelStyle: GaugeBezelStyle? = null,
)

/** Dark accent colours offered for gauge bezels / NUM title bars. */
val DARK_ACCENTS: List<Long> = listOf(
    0xFF000000, // noir
    0xFF141414, // gris très foncé
    0xFF2A2A2A, // gris foncé
    0xFF0A1830, // bleu très foncé
    0xFF13294B, // bleu foncé
    0xFF1A0B2E, // violet très foncé
    0xFF2A1348, // violet foncé
    0xFF5A2E00, // orange foncé
    0xFF123A1E, // vert foncé
    0xFF4A3B00, // jaune foncé
)

/**
 * A saved instrument layout ("tableau de bord"): a **2-column × [rows]-row** grid of
 * [cells] (row-major, 2*rows entries), where cells can be merged into larger blocks.
 *
 * The legacy [cols]/[slots] fields are kept ONLY so older persisted data can be
 * migrated into [cells] (see [migratedCells]); live code reads [normalizedCells].
 */
@Serializable
data class Dashboard(
    val id: String,
    val name: String,
    val rows: Int = 2,
    val cells: List<DashboardCell> = emptyList(),
    val showInCockpit: Boolean = true,
    // ---- legacy (migration only) ----
    val cols: Int = 2,
    val slots: List<EfisInstrument> = emptyList(),
) {
    /** cells resized to exactly EFIS_COLS*rows (pad with empty / truncate), migrating
     *  from the legacy flat [slots] grid when [cells] is empty. */
    val normalizedCells: List<DashboardCell>
        get() {
            val source = cells.ifEmpty { migratedCells() }
            val n = (EFIS_COLS * rows).coerceAtLeast(1)
            return List(n) { source.getOrElse(it) { DashboardCell() } }
        }

    /** Build cells from the old flat slots grid, applying each instrument's
     *  legacyRowSpan as an initial vertical merge so migrated cockpits look the same. */
    private fun migratedCells(): List<DashboardCell> {
        if (slots.isEmpty()) return emptyList()
        val n = (EFIS_COLS * rows).coerceAtLeast(1)
        val out = MutableList(n) { DashboardCell() }
        // Old grid could be 1 or 2 cols; map row-major into the fixed 2-col grid.
        val oldCols = cols.coerceIn(1, EFIS_COLS)
        val covered = BooleanArray(n)
        for (i in slots.indices) {
            val oldRow = i / oldCols
            val oldCol = i % oldCols
            val idx = oldRow * EFIS_COLS + oldCol
            if (idx >= n || covered[idx]) continue
            val instr = slots[i]
            val span = instr.legacyRowSpan.coerceIn(1, rows - oldRow)
            out[idx] = DashboardCell(instrument = instr, colSpan = 1, rowSpan = span)
            for (extra in 1 until span) {
                val ci = idx + extra * EFIS_COLS
                if (ci < n) { out[ci] = DashboardCell(covered = true); covered[ci] = true }
            }
        }
        return out
    }
}

/** Persisted snapshot of one count-up chronometer (wall-clock based). */
@Serializable
data class ChronoSnapshot(
    val running: Boolean = false,
    val accumMs: Long = 0L,
    val anchorEpochMs: Long? = null,
)

/** Persisted snapshot of one countdown timer (wall-clock based). */
@Serializable
data class CountdownSnapshot(
    val setMs: Long = 0L,
    val remainingMs: Long = 0L,
    val running: Boolean = false,
    val anchorEpochMs: Long? = null,
)

/** Persisted snapshot of one horameter reading pair (hundredths of an hour). */
@Serializable
data class HorameterSnapshot(
    val a: Int? = null,
    val b: Int? = null,
)

/**
 * Durable state for the stateful instruments, persisted to settings.json so it
 * survives a process kill/restart. Slots are keyed the same way as
 * ServiceLocator.instrumentState (analog top/bottom, numeric single, etc.).
 */
@Serializable
data class InstrumentPersistState(
    val chronoNum: ChronoSnapshot = ChronoSnapshot(),
    val chronoAnlTop: ChronoSnapshot = ChronoSnapshot(),
    val chronoAnlBot: ChronoSnapshot = ChronoSnapshot(),
    val countdownNum: CountdownSnapshot = CountdownSnapshot(),
    val countdownAnlTop: CountdownSnapshot = CountdownSnapshot(),
    val countdownAnlBot: CountdownSnapshot = CountdownSnapshot(),
    val horameterNum: HorameterSnapshot = HorameterSnapshot(),
    val horameterAnl: HorameterSnapshot = HorameterSnapshot(),
    val fdrPaused: Boolean = false,
    val targetHeading: Int? = null,
    val targetAltitude: Int? = null,
)

/** A navigation plan: an ordered list of terrain ICAO codes + free-text notes. */
@Serializable
data class NavPlan(
    val icaos: List<String> = emptyList(),
    val notes: String = "",
)

/**
 * User preferences, persisted as settings.json in internal storage.
 *
 * fontScale multiplies the base text size of list content (checklist items,
 * checklist lists, characteristics). 1.0 = default.
 */
@Serializable
data class AppPreferences(
    val themeMode: ThemeMode = ThemeMode.AUTO,
    val fontScale: Float = 1.0f,
    /** Version of the bundled default dataset last seeded (0 = never). */
    val seedVersion: Int = 0,
    /** AIRAC cycle segment used to build SIA VAC PDF URLs. */
    val vacAiracCycle: String = "eAIP_09_JUL_2026",
    /** Splash screen duration in seconds (0 = no splash). */
    val splashSeconds: Int = 2,
    /** EFIS heading tape source. */
    val efisHeadingSource: EfisHeadingSource = EfisHeadingSource.MAGNETIC,
    /** EFIS variometer source. */
    val efisVarioSource: EfisVarioSource = EfisVarioSource.GPS,
    /** EFIS grid dimensions (cols 1..3, rows 1..2). */
    val efisCols: Int = 2,
    val efisRows: Int = 2,
    /** Instrument per grid slot, row-major. Normalized to efisCols*efisRows. */
    val efisSlots: List<EfisInstrument> = listOf(
        EfisInstrument.ATTITUDE, EfisInstrument.HEADING,
        EfisInstrument.ALTIMETER, EfisInstrument.VARIOMETER,
    ),
    /** Airspeed gauge unit. */
    val efisSpeedUnit: EfisSpeedUnit = EfisSpeedUnit.KMH,
    /** Unit for all altitude / vertical-speed readouts (default: feet). */
    val altitudeUnit: AltitudeUnit = AltitudeUnit.FEET,
    /** EFIS instrument responsiveness: 0 = very smooth, 1 = very reactive. */
    val efisResponsiveness: Float = 0.35f,
    /** Show a numeric value under altimeter/vario/heading/airspeed. */
    val efisShowValues: Boolean = true,
    /** Keep the screen on (prevent sleep). */
    val keepScreenOn: Boolean = true,
    /** Saved instrument layouts. Empty => migrate from the legacy efis* fields. */
    val dashboards: List<Dashboard> = emptyList(),
    /** Moving-map orientation. */
    val mapOrientation: MapOrientation = MapOrientation.NORTH_UP,
    /** Cockpit page-marker style + position. */
    val cockpitPagerStyle: CockpitPagerStyle = CockpitPagerStyle.DOTS,
    val cockpitPagerPosition: CockpitPagerPosition = CockpitPagerPosition.TOP,
    /** Analog gauge bezel style + solid colour (ARGB int, used when style = SOLID). */
    val gaugeBezelStyle: GaugeBezelStyle = GaugeBezelStyle.SOLID,
    val gaugeBezelColor: Long = 0xFF1C1C1C,
    /** Which OpenAIP overlays are shown on the moving map. */
    val mapLayers: MapLayerPrefs = MapLayerPrefs(),
    /** Which overlays are shown on the weather map (radar/METAR/TAF/SIGMET). */
    val wxLayers: WxLayerPrefs = WxLayerPrefs(),
    /** Show the on-map +/- zoom buttons (pinch-to-zoom always works). */
    val mapShowZoomButtons: Boolean = false,
    /** Tag of the currently installed offline map (null = none downloaded). */
    val installedMapTag: String? = null,
    /** Whether the user has accepted the liability disclaimer shown at first launch. */
    val disclaimerAccepted: Boolean = false,
    /** Whether the startup hardware-compatibility warning has been dismissed. */
    val compatWarningDismissed: Boolean = false,
    /** Flight recorder rolling-buffer length (minutes, min 5) and disk-flush period. */
    val fdrBufferMinutes: Int = 10,
    val fdrFlushMinutes: Int = 2,
    /** Durable state of the stateful instruments + heading/altitude targets. */
    val instruments: InstrumentPersistState = InstrumentPersistState(),
    /** Saved navigation plan (ordered terrain ICAOs + notes). */
    val navPlan: NavPlan = NavPlan(),
) {
    companion object {
        const val MIN_FONT_SCALE = 0.85f
        const val MAX_FONT_SCALE = 1.6f
        const val FONT_STEP = 0.15f
        const val MAX_SPLASH_SECONDS = 3
        const val EFIS_MAX_COLS = 2
        const val EFIS_MAX_ROWS = 6
        const val LEGACY_DASHBOARD_ID = "legacy-default"
        const val FDR_MIN_BUFFER_MIN = 5
        const val FDR_MAX_BUFFER_MIN = 60
        const val FDR_MIN_FLUSH_MIN = 1
        const val FDR_MAX_FLUSH_MIN = 10
    }

    /** efisSlots resized to exactly efisCols*efisRows (pad with NONE / truncate). */
    val normalizedSlots: List<EfisInstrument>
        get() {
            val n = (efisCols * efisRows).coerceAtLeast(1)
            return List(n) { efisSlots.getOrElse(it) { EfisInstrument.NONE } }
        }

    /**
     * The dashboards to use, migrating the legacy single grid into one default
     * dashboard when the list is empty (first run after the update).
     */
    val effectiveDashboards: List<Dashboard>
        get() = dashboards.ifEmpty {
            listOf(
                Dashboard(
                    id = LEGACY_DASHBOARD_ID,
                    name = "Tableau de bord",
                    cols = efisCols,
                    rows = efisRows,
                    slots = normalizedSlots,
                    showInCockpit = true,
                ),
            )
        }
}
