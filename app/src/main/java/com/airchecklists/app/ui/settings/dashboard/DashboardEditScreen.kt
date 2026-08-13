package com.airchecklists.app.ui.settings.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.airchecklists.app.R
import com.airchecklists.app.data.model.AppPreferences
import com.airchecklists.app.data.model.EfisInstrument
import com.airchecklists.app.di.ServiceLocator
import com.airchecklists.app.ui.components.EfisSlotSelector
import com.airchecklists.app.ui.repoViewModelFactory
import com.airchecklists.app.ui.settings.SettingsViewModel

/** Dedicated editor for one dashboard: name, Cockpit visibility, grid + slots. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardEditScreen(
    dashboardId: String,
    onBack: () -> Unit,
    viewModel: SettingsViewModel = viewModel(factory = repoViewModelFactory { SettingsViewModel(it) }),
) {
    val prefs by ServiceLocator.preferences.preferences.collectAsStateWithLifecycle()
    val dash = prefs.effectiveDashboards.firstOrNull { it.id == dashboardId }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.dashboard_edit_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    titleContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    navigationIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
            )
        },
    ) { inner ->
        if (dash == null) return@Scaffold
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(
                value = dash.name,
                onValueChange = { viewModel.setDashboardName(dash.id, it) },
                label = { Text(stringResource(R.string.dashboard_field_name)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = dash.showInCockpit,
                    onCheckedChange = { viewModel.setDashboardShowInCockpit(dash.id, it) },
                )
                Text(stringResource(R.string.settings_dashboard_in_cockpit))
            }

            Text(
                stringResource(R.string.dashboard_grid_size),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(stringResource(R.string.settings_efis_rows), style = MaterialTheme.typography.labelMedium)
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                val maxR = AppPreferences.EFIS_MAX_ROWS
                (1..maxR).forEachIndexed { i, r ->
                    SegmentedButton(
                        selected = dash.rows == r,
                        onClick = { viewModel.setDashboardRows(dash.id, r) },
                        shape = SegmentedButtonDefaults.itemShape(index = i, count = maxR),
                    ) { Text(r.toString()) }
                }
            }

            Text(
                stringResource(R.string.settings_efis_slots),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val cells = dash.normalizedCells
            // Visual 2×N grid: each master cell occupies its real merged footprint,
            // bordered — mirrors how the cockpit lays it out. Content is clipped to
            // its block so a cell's controls never overlap the next block's touch area.
            val cols = com.airchecklists.app.data.model.EFIS_COLS
            val rowHeight = 132.dp
            val gap = 8.dp
            BoxWithConstraints(modifier = Modifier.fillMaxWidth().height(rowHeight * dash.rows)) {
                val cellW = (maxWidth - gap) / cols
                cells.forEachIndexed { idx, cell ->
                    if (cell.covered) return@forEachIndexed
                    val rowIdx = idx / cols
                    val colIdx = idx % cols
                    val blockW = cellW * cell.colSpan + gap * (cell.colSpan - 1)
                    val blockH = rowHeight * cell.rowSpan - gap
                    Box(
                        modifier = Modifier
                            .offset(x = (cellW + gap) * colIdx, y = rowHeight * rowIdx)
                            .width(blockW)
                            .height(blockH)
                            .border(
                                width = 1.5.dp,
                                color = MaterialTheme.colorScheme.outline,
                                shape = RoundedCornerShape(12.dp),
                            )
                            .clip(RoundedCornerShape(12.dp))
                            .padding(8.dp),
                    ) {
                        DashboardCellEditor(
                            dash = dash,
                            index = idx,
                            rowIdx = rowIdx,
                            colIdx = colIdx,
                            cell = cell,
                            cells = cells,
                            viewModel = viewModel,
                        )
                    }
                }
            }
        }
    }
}

/** One editable master cell: instrument picker + merge/unmerge controls. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DashboardCellEditor(
    dash: com.airchecklists.app.data.model.Dashboard,
    index: Int,
    rowIdx: Int,
    colIdx: Int,
    cell: com.airchecklists.app.data.model.DashboardCell,
    cells: List<com.airchecklists.app.data.model.DashboardCell>,
    viewModel: SettingsViewModel,
    modifier: Modifier = Modifier,
) {
    val cols = com.airchecklists.app.data.model.EFIS_COLS

    fun isFree(i: Int): Boolean {
        val c = cells.getOrNull(i) ?: return false
        return !c.covered && c.colSpan == 1 && c.rowSpan == 1 &&
            c.instrument == EfisInstrument.NONE
    }
    // Can merge right when this is a left, single-column cell and the whole right
    // column across its rowSpan is free.
    val canMergeRight = colIdx == 0 && cell.colSpan == 1 &&
        (0 until cell.rowSpan).all { dr -> isFree((rowIdx + dr) * cols + 1) }
    // Can merge down when the next row band across colSpan is free.
    val nextRow = rowIdx + cell.rowSpan
    val canMergeDown = nextRow < dash.rows &&
        (0 until cell.colSpan).all { dc -> isFree(nextRow * cols + colIdx + dc) }
    val isMerged = cell.colSpan > 1 || cell.rowSpan > 1

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        EfisSlotSelector(
            instrument = cell.instrument,
            onSelect = { viewModel.setDashboardCellInstrument(dash.id, index, it) },
            modifier = Modifier.fillMaxWidth(),
        )
        // Single "Options" button → dialog with cell colour/style + merge/separate.
        // Works regardless of cell size (no inline buttons pushed out of view).
        var showOptions by remember { mutableStateOf(false) }
        TextButton(
            onClick = { showOptions = true },
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 6.dp, vertical = 2.dp),
        ) {
            Icon(Icons.Filled.Tune, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
            Text(stringResource(R.string.dashboard_cell_options), style = MaterialTheme.typography.labelSmall)
        }
        if (showOptions) {
            CellOptionsDialog(
                cell = cell,
                canMergeRight = canMergeRight,
                canMergeDown = canMergeDown,
                isMerged = isMerged,
                onColor = { viewModel.setDashboardCellAccent(dash.id, index, it) },
                onStyle = { viewModel.setDashboardCellBezelStyle(dash.id, index, it) },
                onMergeRight = { viewModel.mergeDashboardCell(dash.id, index, com.airchecklists.app.data.repository.MergeDir.RIGHT) },
                onMergeDown = { viewModel.mergeDashboardCell(dash.id, index, com.airchecklists.app.data.repository.MergeDir.DOWN) },
                onUnmerge = { viewModel.unmergeDashboardCell(dash.id, index) },
                onDismiss = { showOptions = false },
            )
        }
    }
}

/** Cell action rendered with the same chip style as the Carbone/Métal filter chips,
 *  so the "Cellule" section is visually homogeneous with the colour section. */
@Composable
private fun CellActionChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    androidx.compose.material3.FilterChip(
        selected = false,
        onClick = onClick,
        label = { Text(label) },
        leadingIcon = { Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp)) },
    )
}

/** A small selectable accent swatch; [color] = null renders a "Défaut" (inherit) dot. */
@Composable
private fun AccentDot(color: Long?, selected: Boolean, onClick: () -> Unit, size: androidx.compose.ui.unit.Dp = 28.dp) {
    val fill = if (color != null) androidx.compose.ui.graphics.Color(color.toInt()) else androidx.compose.ui.graphics.Color(0xFF444444)
    Box(
        modifier = Modifier
            .size(size)
            .clip(androidx.compose.foundation.shape.CircleShape)
            .background(fill)
            .border(
                width = if (selected) 3.dp else 1.dp,
                color = if (selected) MaterialTheme.colorScheme.primary else androidx.compose.ui.graphics.Color(0xFF777777),
                shape = androidx.compose.foundation.shape.CircleShape,
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (color == null) Text("D", color = androidx.compose.ui.graphics.Color.White, style = MaterialTheme.typography.labelSmall)
    }
}

/** Cell options dialog: bezel style (Défaut/Couleur/Carbone/Métal) + colour palette
 *  (when Couleur) + cell management (Large / Haut / Séparer). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CellOptionsDialog(
    cell: com.airchecklists.app.data.model.DashboardCell,
    canMergeRight: Boolean,
    canMergeDown: Boolean,
    isMerged: Boolean,
    onColor: (Long?) -> Unit,
    onStyle: (com.airchecklists.app.data.model.GaugeBezelStyle?) -> Unit,
    onMergeRight: () -> Unit,
    onMergeDown: () -> Unit,
    onUnmerge: () -> Unit,
    onDismiss: () -> Unit,
) {
    // Which "mode" is active: Défaut (both null), Couleur (color set), or a texture.
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.dashboard_cell_options)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.dashboard_cell_color), style = MaterialTheme.typography.labelLarge)
                // Style modes: "Défaut" on its own line, then Carbone + Métal on the next.
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    androidx.compose.material3.FilterChip(
                        selected = cell.accentColor == null && cell.bezelStyle == null,
                        onClick = { onColor(null); onStyle(null) },
                        label = { Text(stringResource(R.string.dashboard_cell_color_default)) },
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    androidx.compose.material3.FilterChip(
                        selected = cell.bezelStyle == com.airchecklists.app.data.model.GaugeBezelStyle.CARBON,
                        onClick = { onStyle(com.airchecklists.app.data.model.GaugeBezelStyle.CARBON); onColor(null) },
                        label = { Text(stringResource(R.string.settings_bezel_carbon)) },
                    )
                    androidx.compose.material3.FilterChip(
                        selected = cell.bezelStyle == com.airchecklists.app.data.model.GaugeBezelStyle.BRUSHED,
                        onClick = { onStyle(com.airchecklists.app.data.model.GaugeBezelStyle.BRUSHED); onColor(null) },
                        label = { Text(stringResource(R.string.settings_bezel_brushed)) },
                    )
                }
                // Colour palette (choosing a colour implies SOLID style override).
                FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    com.airchecklists.app.data.model.DARK_ACCENTS.forEach { col ->
                        AccentDot(
                            color = col,
                            selected = cell.accentColor == col && cell.bezelStyle == com.airchecklists.app.data.model.GaugeBezelStyle.SOLID,
                            onClick = { onColor(col); onStyle(com.airchecklists.app.data.model.GaugeBezelStyle.SOLID) },
                            size = 36.dp,
                        )
                    }
                }
                // Cell management. Same chip style as the colour section for consistency.
                if (canMergeRight || canMergeDown || isMerged) {
                    androidx.compose.material3.HorizontalDivider()
                    Text(stringResource(R.string.dashboard_cell_manage), style = MaterialTheme.typography.labelLarge)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (canMergeRight) CellActionChip(Icons.AutoMirrored.Filled.ArrowForward, stringResource(R.string.dashboard_merge_right)) { onMergeRight(); onDismiss() }
                        if (canMergeDown) CellActionChip(Icons.Filled.ArrowDownward, stringResource(R.string.dashboard_merge_down)) { onMergeDown(); onDismiss() }
                        if (isMerged) CellActionChip(Icons.Filled.Close, stringResource(R.string.dashboard_unmerge)) { onUnmerge(); onDismiss() }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_save)) } },
    )
}
