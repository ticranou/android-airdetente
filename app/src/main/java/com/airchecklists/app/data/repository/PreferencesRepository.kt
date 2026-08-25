package com.airchecklists.app.data.repository

import com.airchecklists.app.data.local.SettingsStore
import com.airchecklists.app.data.model.AppPreferences
import com.airchecklists.app.data.model.DashboardCell
import com.airchecklists.app.data.model.EFIS_COLS
import com.airchecklists.app.data.model.EfisHeadingSource
import com.airchecklists.app.data.model.EfisInstrument
import com.airchecklists.app.data.model.EfisSpeedUnit
import com.airchecklists.app.data.model.EfisVarioSource
import com.airchecklists.app.data.model.ThemeMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext

/** Direction to merge a dashboard cell towards. */
enum class MergeDir { RIGHT, DOWN }

/** Source of truth for user preferences; persists every change to settings.json. */
class PreferencesRepository(private val store: SettingsStore) {

    private val _preferences = MutableStateFlow(store.read())
    val preferences: StateFlow<AppPreferences> = _preferences.asStateFlow()

    suspend fun setThemeMode(mode: ThemeMode) = persist { it.copy(themeMode = mode) }

    /** Apply the "business" prefs from an imported dataset, preserving volatile state. */
    suspend fun applyDataset(prefs: com.airchecklists.app.data.model.DatasetPrefs) =
        persist { prefs.applyOnto(it) }

    suspend fun setFontScale(scale: Float) {
        val clamped = scale.coerceIn(AppPreferences.MIN_FONT_SCALE, AppPreferences.MAX_FONT_SCALE)
        persist { it.copy(fontScale = clamped) }
    }

    suspend fun setSeedVersion(version: Int) = persist { it.copy(seedVersion = version) }

    suspend fun setVacAiracCycle(cycle: String) = persist { it.copy(vacAiracCycle = cycle.trim()) }

    suspend fun setSplashSeconds(seconds: Int) =
        persist { it.copy(splashSeconds = seconds.coerceIn(0, AppPreferences.MAX_SPLASH_SECONDS)) }

    suspend fun setEfisHeadingSource(source: EfisHeadingSource) =
        persist { it.copy(efisHeadingSource = source) }

    suspend fun setEfisVarioSource(source: EfisVarioSource) =
        persist { it.copy(efisVarioSource = source) }

    suspend fun setEfisGrid(cols: Int, rows: Int) = persist {
        val c = cols.coerceIn(1, AppPreferences.EFIS_MAX_COLS)
        val r = rows.coerceIn(1, AppPreferences.EFIS_MAX_ROWS)
        val n = c * r
        val slots = List(n) { i -> it.efisSlots.getOrElse(i) { EfisInstrument.NONE } }
        it.copy(efisCols = c, efisRows = r, efisSlots = slots)
    }

    suspend fun setEfisSlot(index: Int, instrument: EfisInstrument) = persist {
        val n = (it.efisCols * it.efisRows).coerceAtLeast(1)
        val slots = MutableList(n) { i -> it.efisSlots.getOrElse(i) { EfisInstrument.NONE } }
        if (index in slots.indices) slots[index] = instrument
        it.copy(efisSlots = slots)
    }

    suspend fun setEfisSpeedUnit(unit: EfisSpeedUnit) = persist { it.copy(efisSpeedUnit = unit) }

    suspend fun setAltitudeUnit(unit: com.airchecklists.app.data.model.AltitudeUnit) =
        persist { it.copy(altitudeUnit = unit) }

    suspend fun setEfisResponsiveness(value: Float) =
        persist { it.copy(efisResponsiveness = value.coerceIn(0.05f, 1f)) }

    suspend fun setEfisShowValues(show: Boolean) = persist { it.copy(efisShowValues = show) }

    suspend fun setKeepScreenOn(on: Boolean) = persist { it.copy(keepScreenOn = on) }

    suspend fun setMapOrientation(orientation: com.airchecklists.app.data.model.MapOrientation) =
        persist { it.copy(mapOrientation = orientation) }

    suspend fun setCockpitPagerStyle(style: com.airchecklists.app.data.model.CockpitPagerStyle) =
        persist { it.copy(cockpitPagerStyle = style) }

    suspend fun setCockpitPagerPosition(position: com.airchecklists.app.data.model.CockpitPagerPosition) =
        persist { it.copy(cockpitPagerPosition = position) }

    suspend fun setGaugeBezelStyle(style: com.airchecklists.app.data.model.GaugeBezelStyle) =
        persist { it.copy(gaugeBezelStyle = style) }

    suspend fun setGaugeBezelColor(color: Long) =
        persist { it.copy(gaugeBezelColor = color) }

    suspend fun setMapLayers(layers: com.airchecklists.app.data.model.MapLayerPrefs) =
        persist { it.copy(mapLayers = layers) }

    suspend fun setWxLayers(layers: com.airchecklists.app.data.model.WxLayerPrefs) =
        persist { it.copy(wxLayers = layers) }

    suspend fun setMapShowZoomButtons(show: Boolean) =
        persist { it.copy(mapShowZoomButtons = show) }

    suspend fun setNavPlan(plan: com.airchecklists.app.data.model.NavPlan) =
        persist { it.copy(navPlan = plan) }

    suspend fun setInstalledMapTag(tag: String?) = persist { it.copy(installedMapTag = tag) }

    suspend fun setDisclaimerAccepted(accepted: Boolean) = persist { it.copy(disclaimerAccepted = accepted) }

    suspend fun setCompatWarningDismissed(dismissed: Boolean) = persist { it.copy(compatWarningDismissed = dismissed) }

    suspend fun setInstrumentState(state: com.airchecklists.app.data.model.InstrumentPersistState) =
        persist { it.copy(instruments = state) }

    // ---- Dashboards ----

    /** Materialize the migrated dashboards into storage if none exist yet. */
    suspend fun ensureDashboardsMigrated() = persist {
        if (it.dashboards.isEmpty()) it.copy(dashboards = it.effectiveDashboards) else it
    }

    suspend fun upsertDashboard(dashboard: com.airchecklists.app.data.model.Dashboard) = persist { prefs ->
        val list = prefs.effectiveDashboards.toMutableList()
        val idx = list.indexOfFirst { it.id == dashboard.id }
        if (idx >= 0) list[idx] = dashboard else list.add(dashboard)
        prefs.copy(dashboards = list)
    }

    suspend fun deleteDashboard(id: String) = persist { prefs ->
        prefs.copy(dashboards = prefs.effectiveDashboards.filterNot { it.id == id })
    }

    /** Update one dashboard's row count (columns are fixed at 2), resizing cells. */
    suspend fun setDashboardRows(id: String, rows: Int) = persist { prefs ->
        val r = rows.coerceIn(1, AppPreferences.EFIS_MAX_ROWS)
        prefs.copy(dashboards = mapDashboard(prefs, id) { d ->
            val resized = List(EFIS_COLS * r) { i -> d.normalizedCells.getOrElse(i) { DashboardCell() } }
            d.copy(rows = r, cells = repairCells(resized, r), slots = emptyList())
        })
    }

    /** Set the instrument of the master cell at [index]. */
    suspend fun setDashboardCellInstrument(id: String, index: Int, instrument: EfisInstrument) = persist { prefs ->
        prefs.copy(dashboards = mapDashboard(prefs, id) { d ->
            val cells = d.normalizedCells.toMutableList()
            if (index in cells.indices && !cells[index].covered) {
                cells[index] = cells[index].copy(instrument = instrument)
            }
            d.copy(cells = repairCells(cells, d.rows), slots = emptyList())
        })
    }

    /** Set the per-cell accent colour ([color] = null → inherit the global bezel). */
    suspend fun setDashboardCellAccent(id: String, index: Int, color: Long?) = persist { prefs ->
        prefs.copy(dashboards = mapDashboard(prefs, id) { d ->
            val cells = d.normalizedCells.toMutableList()
            if (index in cells.indices && !cells[index].covered) {
                cells[index] = cells[index].copy(accentColor = color)
            }
            d.copy(cells = repairCells(cells, d.rows), slots = emptyList())
        })
    }

    /** Set the per-cell bezel style override ([style] = null → inherit the global). */
    suspend fun setDashboardCellBezelStyle(id: String, index: Int, style: com.airchecklists.app.data.model.GaugeBezelStyle?) = persist { prefs ->
        prefs.copy(dashboards = mapDashboard(prefs, id) { d ->
            val cells = d.normalizedCells.toMutableList()
            if (index in cells.indices && !cells[index].covered) {
                cells[index] = cells[index].copy(bezelStyle = style)
            }
            d.copy(cells = repairCells(cells, d.rows), slots = emptyList())
        })
    }

    /** Merge the master cell at [index] with its RIGHT or DOWN neighbour(s). */
    suspend fun mergeDashboardCell(id: String, index: Int, dir: MergeDir) = persist { prefs ->
        prefs.copy(dashboards = mapDashboard(prefs, id) { d ->
            val cells = d.normalizedCells.toMutableList()
            val rows = d.rows
            val row = index / EFIS_COLS
            val col = index % EFIS_COLS
            val master = cells.getOrNull(index)
            if (master != null && !master.covered) {
                when (dir) {
                    MergeDir.RIGHT -> {
                        // Absorb the whole right column across the master's rowSpan.
                        if (col == 0 && master.colSpan == 1) {
                            val ok = (0 until master.rowSpan).all { dr ->
                                val ri = (row + dr) * EFIS_COLS + 1
                                ri < cells.size && isFreeSingle(cells, ri)
                            }
                            if (ok) {
                                cells[index] = master.copy(colSpan = 2)
                                for (dr in 0 until master.rowSpan) {
                                    val ri = (row + dr) * EFIS_COLS + 1
                                    cells[ri] = DashboardCell(covered = true)
                                }
                            }
                        }
                    }
                    MergeDir.DOWN -> {
                        // Absorb the next row band across the master's colSpan.
                        val nextRow = row + master.rowSpan
                        if (nextRow < rows) {
                            val ok = (0 until master.colSpan).all { dc ->
                                val ci = nextRow * EFIS_COLS + col + dc
                                ci < cells.size && isFreeSingle(cells, ci)
                            }
                            if (ok) {
                                cells[index] = master.copy(rowSpan = master.rowSpan + 1)
                                for (dc in 0 until master.colSpan) {
                                    val ci = nextRow * EFIS_COLS + col + dc
                                    cells[ci] = DashboardCell(covered = true)
                                }
                            }
                        }
                    }
                }
            }
            d.copy(cells = repairCells(cells, d.rows), slots = emptyList())
        })
    }

    /** Reset the master cell at [index] to 1×1, freeing the cells it covered. */
    suspend fun unmergeDashboardCell(id: String, index: Int) = persist { prefs ->
        prefs.copy(dashboards = mapDashboard(prefs, id) { d ->
            val cells = d.normalizedCells.toMutableList()
            val master = cells.getOrNull(index)
            if (master != null && !master.covered && (master.colSpan > 1 || master.rowSpan > 1)) {
                val row = index / EFIS_COLS
                val col = index % EFIS_COLS
                for (dr in 0 until master.rowSpan) for (dc in 0 until master.colSpan) {
                    val ci = (row + dr) * EFIS_COLS + col + dc
                    if (ci in cells.indices && ci != index) cells[ci] = DashboardCell()
                }
                cells[index] = master.copy(colSpan = 1, rowSpan = 1)
            }
            d.copy(cells = repairCells(cells, d.rows), slots = emptyList())
        })
    }

    private fun mapDashboard(
        prefs: AppPreferences,
        id: String,
        transform: (com.airchecklists.app.data.model.Dashboard) -> com.airchecklists.app.data.model.Dashboard,
    ): List<com.airchecklists.app.data.model.Dashboard> =
        prefs.effectiveDashboards.map { if (it.id == id) transform(it) else it }

    private fun isFreeSingle(cells: List<DashboardCell>, i: Int): Boolean {
        val c = cells.getOrNull(i) ?: return false
        return !c.covered && c.colSpan == 1 && c.rowSpan == 1 && c.instrument == EfisInstrument.NONE
    }

    /**
     * Normalises a cell list to a consistent 2×[rows] grid: clamps spans to the grid,
     * recomputes which cells are covered from the masters, and drops overlaps
     * (last master wins). Always call after a mutation so persisted state is valid.
     */
    private fun repairCells(cells: List<DashboardCell>, rows: Int): List<DashboardCell> {
        val n = EFIS_COLS * rows
        val out = MutableList(n) { i -> cells.getOrElse(i) { DashboardCell() }.copy(covered = false) }
        val owned = BooleanArray(n)
        for (i in 0 until n) {
            if (owned[i]) { out[i] = DashboardCell(covered = true); continue }
            val cell = out[i]
            if (cell.colSpan <= 1 && cell.rowSpan <= 1) continue
            val row = i / EFIS_COLS
            val col = i % EFIS_COLS
            val cs = cell.colSpan.coerceIn(1, EFIS_COLS - col)
            val rs = cell.rowSpan.coerceIn(1, rows - row)
            out[i] = cell.copy(colSpan = cs, rowSpan = rs, covered = false)
            for (dr in 0 until rs) for (dc in 0 until cs) {
                if (dr == 0 && dc == 0) continue
                val ci = (row + dr) * EFIS_COLS + col + dc
                if (ci in 0 until n) { owned[ci] = true }
            }
        }
        // Second pass: stamp covered flags for owned cells not already handled.
        for (i in 0 until n) if (owned[i]) out[i] = DashboardCell(covered = true)
        return out
    }

    suspend fun setDashboardName(id: String, name: String) = persist { prefs ->
        prefs.copy(dashboards = prefs.effectiveDashboards.map { if (it.id == id) it.copy(name = name) else it })
    }

    suspend fun setDashboardShowInCockpit(id: String, show: Boolean) = persist { prefs ->
        prefs.copy(dashboards = prefs.effectiveDashboards.map { if (it.id == id) it.copy(showInCockpit = show) else it })
    }

    /** Reorder dashboards to match the given id order (unknown ids keep their tail order). */
    suspend fun reorderDashboards(orderedIds: List<String>) = persist { prefs ->
        val byId = prefs.effectiveDashboards.associateBy { it.id }
        val ordered = orderedIds.mapNotNull { byId[it] }
        val rest = prefs.effectiveDashboards.filter { it.id !in orderedIds }
        prefs.copy(dashboards = ordered + rest)
    }

    private suspend fun persist(transform: (AppPreferences) -> AppPreferences) {
        val updated = transform(_preferences.value)
        _preferences.update { updated }
        withContext(Dispatchers.IO) { store.write(updated) }
    }
}
