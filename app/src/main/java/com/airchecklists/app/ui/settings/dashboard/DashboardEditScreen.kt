package com.airchecklists.app.ui.settings.dashboard

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
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
import androidx.compose.material3.Slider
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.airchecklists.app.R
import com.airchecklists.app.data.model.AppPreferences
import com.airchecklists.app.data.model.DashboardBackgroundStyle
import com.airchecklists.app.data.model.EfisInstrument
import com.airchecklists.app.di.ServiceLocator
import com.airchecklists.app.ui.components.EfisSlotSelector
import com.airchecklists.app.ui.efis.drawDashboardBackground
import com.airchecklists.app.ui.repoViewModelFactory
import com.airchecklists.app.ui.settings.SettingsViewModel

private val ACTIVE_BACKGROUND_STYLES = listOf(
    DashboardBackgroundStyle.NONE,
    DashboardBackgroundStyle.CARBON,
    DashboardBackgroundStyle.BRUSHED_METAL,
    DashboardBackgroundStyle.RIVETED_METAL,
    DashboardBackgroundStyle.CARBON_RED,
    DashboardBackgroundStyle.CARBON_BLUE,
    DashboardBackgroundStyle.WOVEN_CARBON,
    DashboardBackgroundStyle.TOPOGRAPHIC,
    DashboardBackgroundStyle.TITANIUM,
    DashboardBackgroundStyle.ANODIZED_DARK,
    DashboardBackgroundStyle.DIAMOND_PLATE,
    DashboardBackgroundStyle.STARFIELD,
    DashboardBackgroundStyle.DEEP_OCEAN,
)

private val BG_STYLE_LABELS = mapOf(
    DashboardBackgroundStyle.NONE to "Aucun",
    DashboardBackgroundStyle.CARBON to "Carbone",
    DashboardBackgroundStyle.BRUSHED_METAL to "Métal brossé",
    DashboardBackgroundStyle.RIVETED_METAL to "Rivets",
    DashboardBackgroundStyle.CARBON_RED to "Carbone rouge",
    DashboardBackgroundStyle.CARBON_BLUE to "Carbone bleu",
    DashboardBackgroundStyle.WOVEN_CARBON to "Carbone tissé",
    DashboardBackgroundStyle.TOPOGRAPHIC to "Topographie",
    DashboardBackgroundStyle.TITANIUM to "Titane",
    DashboardBackgroundStyle.ANODIZED_DARK to "Anodisé",
    DashboardBackgroundStyle.DIAMOND_PLATE to "Larmé",
    DashboardBackgroundStyle.STARFIELD to "Étoiles",
    DashboardBackgroundStyle.DEEP_OCEAN to "Océan",
)

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

            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = dash.showTitle,
                    onCheckedChange = { viewModel.setDashboardShowTitle(dash.id, it) },
                )
                Text(stringResource(R.string.settings_dashboard_show_title))
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = dash.statusBannerTop,
                    onCheckedChange = { viewModel.setDashboardStatusBannerTop(dash.id, it) },
                )
                Text(stringResource(R.string.settings_dashboard_status_banner_top))
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = dash.statusBannerBottom,
                    onCheckedChange = { viewModel.setDashboardStatusBannerBottom(dash.id, it) },
                )
                Text(stringResource(R.string.settings_dashboard_status_banner_bottom))
            }

            Text(
                "Fond du cockpit",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth().heightIn(max = 440.dp),
                userScrollEnabled = false,
            ) {
                items(ACTIVE_BACKGROUND_STYLES) { style ->
                    val selected = dash.backgroundStyle == style
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .border(
                                width = if (selected) 2.dp else 0.5.dp,
                                color = if (selected) MaterialTheme.colorScheme.primary else Color(0xFF3A3A3A),
                                shape = RoundedCornerShape(8.dp),
                            )
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { viewModel.setDashboardBackgroundStyle(dash.id, style) }
                            .padding(4.dp),
                    ) {
                        Canvas(
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(16f / 9f),
                        ) {
                            if (style == DashboardBackgroundStyle.NONE) {
                                drawRect(androidx.compose.ui.graphics.Color(0xFF1A1A1A))
                            } else {
                                drawDashboardBackground(style)
                            }
                        }
                        Text(
                            BG_STYLE_LABELS[style] ?: style.name,
                            style = MaterialTheme.typography.labelSmall,
                            textAlign = TextAlign.Center,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 4.dp, bottom = 2.dp),
                        )
                    }
                }
            }
            if (dash.backgroundStyle != DashboardBackgroundStyle.NONE) {
                Text(
                    "Opacité : ${(dash.backgroundOpacity * 100).toInt()} %",
                    style = MaterialTheme.typography.labelMedium,
                )
                Slider(
                    value = dash.backgroundOpacity,
                    onValueChange = { viewModel.setDashboardBackgroundOpacity(dash.id, it) },
                    valueRange = 0.05f..1f,
                    modifier = Modifier.fillMaxWidth(),
                )
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
        // Build the ordered list of block-start rows (same logic as the repository).
        val blockStarts = run {
            val list = mutableListOf<Int>()
            var r = 0
            while (r < dash.rows) {
                list.add(r)
                val span = (0 until cols)
                    .mapNotNull { dc -> cells.getOrNull(r * cols + dc)?.takeIf { !it.covered } }
                    .maxOfOrNull { it.rowSpan } ?: 1
                r += span.coerceAtLeast(1)
            }
            list
        }
        val blockIdx = blockStarts.indexOf(rowIdx)
        val canMoveUp   = blockIdx > 0
        val canMoveDown = blockIdx >= 0 && blockIdx < blockStarts.size - 1
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(
                onClick = { showOptions = true },
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 6.dp, vertical = 2.dp),
            ) {
                Icon(Icons.Filled.Tune, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text(stringResource(R.string.dashboard_cell_options), style = MaterialTheme.typography.labelSmall)
            }
            IconButton(
                onClick = { viewModel.moveDashboardRowUp(dash.id, rowIdx) },
                enabled = canMoveUp,
                modifier = Modifier.size(32.dp),
            ) {
                Icon(Icons.Filled.ArrowUpward, contentDescription = null, modifier = Modifier.size(18.dp))
            }
            IconButton(
                onClick = { viewModel.moveDashboardRowDown(dash.id, rowIdx) },
                enabled = canMoveDown,
                modifier = Modifier.size(32.dp),
            ) {
                Icon(Icons.Filled.ArrowDownward, contentDescription = null, modifier = Modifier.size(18.dp))
            }
        }
        if (showOptions) {
            CellOptionsDialog(
                cell = cell,
                canMergeRight = canMergeRight,
                canMergeDown = canMergeDown,
                isMerged = isMerged,
                onColor = { viewModel.setDashboardCellAccent(dash.id, index, it) },
                onStyle = { viewModel.setDashboardCellBezelStyle(dash.id, index, it) },
                onHideTitle = { viewModel.setDashboardCellHideTitle(dash.id, index, it) },
                onHideBorder = { viewModel.setDashboardCellHideBorder(dash.id, index, it) },
                onTitleTexture = { viewModel.setDashboardCellTitleTexture(dash.id, index, it) },
                onCellBgColor = { viewModel.setDashboardCellBgColor(dash.id, index, it) },
                onCellBgTexture = { viewModel.setDashboardCellBgTexture(dash.id, index, it) },
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

/** Cell options dialog: cell management + title style + instrument background + visibility. */
@OptIn(ExperimentalLayoutApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun CellOptionsDialog(
    cell: com.airchecklists.app.data.model.DashboardCell,
    canMergeRight: Boolean,
    canMergeDown: Boolean,
    isMerged: Boolean,
    onColor: (Long?) -> Unit,
    onStyle: (com.airchecklists.app.data.model.GaugeBezelStyle?) -> Unit,
    onHideTitle: (Boolean) -> Unit,
    onHideBorder: (Boolean) -> Unit,
    onTitleTexture: (DashboardBackgroundStyle?) -> Unit,
    onCellBgColor: (Long?) -> Unit,
    onCellBgTexture: (DashboardBackgroundStyle?) -> Unit,
    onMergeRight: () -> Unit,
    onMergeDown: () -> Unit,
    onUnmerge: () -> Unit,
    onDismiss: () -> Unit,
) {
    // Which title "mode" is active:
    //   Défaut = accentColor==null && bezelStyle==null && titleBackgroundTexture==null
    //   Couleur = bezelStyle==SOLID && accentColor!=null
    //   Texture = titleBackgroundTexture!=null
    val titleMode = when {
        cell.titleBackgroundTexture != null -> 2
        cell.accentColor != null && cell.bezelStyle == com.airchecklists.app.data.model.GaugeBezelStyle.SOLID -> 1
        else -> 0
    }
    // Which cell-bg "mode" is active:
    //   Défaut = both null, Couleur = color!=null, Texture = texture!=null
    val bgMode = when {
        cell.cellBackgroundTexture != null -> 2
        cell.cellBackgroundColor != null -> 1
        else -> 0
    }
    var showTitleTexturePicker by remember { mutableStateOf(false) }
    var showBgTexturePicker by remember { mutableStateOf(false) }

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.dashboard_cell_options)) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.verticalScroll(rememberScrollState()),
            ) {
                // ── Section 1: Cellule (merge / unmerge) ─────────────────────────
                if (canMergeRight || canMergeDown || isMerged) {
                    Text(stringResource(R.string.dashboard_cell_manage), style = MaterialTheme.typography.labelLarge)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (canMergeRight) CellActionChip(Icons.AutoMirrored.Filled.ArrowForward, stringResource(R.string.dashboard_merge_right)) { onMergeRight(); onDismiss() }
                        if (canMergeDown) CellActionChip(Icons.Filled.ArrowDownward, stringResource(R.string.dashboard_merge_down)) { onMergeDown(); onDismiss() }
                        if (isMerged) CellActionChip(Icons.Filled.Close, stringResource(R.string.dashboard_unmerge)) { onUnmerge(); onDismiss() }
                    }
                    androidx.compose.material3.HorizontalDivider()
                }

                // ── Section 2: Aspect du titre ────────────────────────────────────
                Text(stringResource(R.string.dashboard_cell_title_aspect), style = MaterialTheme.typography.labelLarge)
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    SegmentedButton(
                        selected = titleMode == 0,
                        onClick = { onColor(null); onStyle(null); onTitleTexture(null) },
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 3),
                    ) { Text(stringResource(R.string.dashboard_cell_mode_default)) }
                    SegmentedButton(
                        selected = titleMode == 1,
                        onClick = {
                            onTitleTexture(null)
                            if (cell.accentColor == null) onColor(com.airchecklists.app.data.model.DARK_ACCENTS.first())
                            onStyle(com.airchecklists.app.data.model.GaugeBezelStyle.SOLID)
                        },
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 3),
                    ) { Text(stringResource(R.string.dashboard_cell_mode_color)) }
                    SegmentedButton(
                        selected = titleMode == 2,
                        onClick = {
                            onColor(null); onStyle(null)
                            if (cell.titleBackgroundTexture == null) onTitleTexture(DashboardBackgroundStyle.CARBON)
                        },
                        shape = SegmentedButtonDefaults.itemShape(index = 2, count = 3),
                    ) { Text(stringResource(R.string.dashboard_cell_mode_texture)) }
                }
                when (titleMode) {
                    1 -> {
                        // Colour palette
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            com.airchecklists.app.data.model.DARK_ACCENTS.forEach { col ->
                                AccentDot(
                                    color = col,
                                    selected = cell.accentColor == col,
                                    onClick = { onColor(col); onStyle(com.airchecklists.app.data.model.GaugeBezelStyle.SOLID) },
                                    size = 36.dp,
                                )
                            }
                        }
                    }
                    2 -> {
                        // Texture thumbnail + pick button
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextureThumbnail(cell.titleBackgroundTexture, 48.dp)
                            TextButton(onClick = { showTitleTexturePicker = true }) {
                                Text(stringResource(R.string.dashboard_cell_pick_texture))
                            }
                        }
                    }
                }

                androidx.compose.material3.HorizontalDivider()

                // ── Section 3: Fond de l'instrument ──────────────────────────────
                Text(stringResource(R.string.dashboard_cell_bg), style = MaterialTheme.typography.labelLarge)
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    SegmentedButton(
                        selected = bgMode == 0,
                        onClick = { onCellBgColor(null); onCellBgTexture(null) },
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 3),
                    ) { Text(stringResource(R.string.dashboard_cell_mode_default)) }
                    SegmentedButton(
                        selected = bgMode == 1,
                        onClick = {
                            onCellBgTexture(null)
                            if (cell.cellBackgroundColor == null) onCellBgColor(0x88000000L)
                        },
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 3),
                    ) { Text(stringResource(R.string.dashboard_cell_mode_color)) }
                    SegmentedButton(
                        selected = bgMode == 2,
                        onClick = {
                            onCellBgColor(null)
                            if (cell.cellBackgroundTexture == null) onCellBgTexture(DashboardBackgroundStyle.CARBON)
                        },
                        shape = SegmentedButtonDefaults.itemShape(index = 2, count = 3),
                    ) { Text(stringResource(R.string.dashboard_cell_mode_texture)) }
                }
                when (bgMode) {
                    1 -> {
                        // Semi-transparent colour palette
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            BG_COLOR_PALETTE.forEach { col ->
                                AccentDot(
                                    color = col,
                                    selected = cell.cellBackgroundColor == col,
                                    onClick = { onCellBgColor(col) },
                                    size = 36.dp,
                                )
                            }
                        }
                    }
                    2 -> {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextureThumbnail(cell.cellBackgroundTexture, 48.dp)
                            TextButton(onClick = { showBgTexturePicker = true }) {
                                Text(stringResource(R.string.dashboard_cell_pick_texture))
                            }
                        }
                    }
                }

                androidx.compose.material3.HorizontalDivider()

                // ── Title / border visibility ─────────────────────────────────────
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().clickable { onHideTitle(!cell.hideTitle) }.padding(vertical = 4.dp),
                ) {
                    androidx.compose.material3.Checkbox(checked = cell.hideTitle, onCheckedChange = { onHideTitle(it) })
                    Text(stringResource(R.string.dashboard_cell_hide_title), style = MaterialTheme.typography.bodyMedium)
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().clickable { onHideBorder(!cell.hideBorder) }.padding(vertical = 4.dp),
                ) {
                    androidx.compose.material3.Checkbox(checked = cell.hideBorder, onCheckedChange = { onHideBorder(it) })
                    Text(stringResource(R.string.dashboard_cell_hide_border), style = MaterialTheme.typography.bodyMedium)
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_save)) } },
    )

    if (showTitleTexturePicker) {
        TexturePickerDialog(
            title = stringResource(R.string.dashboard_cell_title_aspect),
            selected = cell.titleBackgroundTexture,
            onSelect = { onTitleTexture(it); showTitleTexturePicker = false },
            onDismiss = { showTitleTexturePicker = false },
        )
    }
    if (showBgTexturePicker) {
        TexturePickerDialog(
            title = stringResource(R.string.dashboard_cell_bg),
            selected = cell.cellBackgroundTexture,
            onSelect = { onCellBgTexture(it); showBgTexturePicker = false },
            onDismiss = { showBgTexturePicker = false },
        )
    }
}

/** Semi-transparent background colour palette. */
private val BG_COLOR_PALETTE: List<Long> = listOf(
    0x88000000L, // noir semi-transparent
    0xAA000000L, // noir opaque-ish
    0x550A1830L, // bleu foncé semi-transparent
    0x55141414L, // gris foncé semi-transparent
    0x55123A1EL, // vert foncé semi-transparent
    0x551A0B2EL, // violet semi-transparent
)

/** Reusable texture picker dialog (full-screen grid of 16:9 texture vignettes). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TexturePickerDialog(
    title: String,
    selected: DashboardBackgroundStyle?,
    onSelect: (DashboardBackgroundStyle?) -> Unit,
    onDismiss: () -> Unit,
) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp),
            ) {
                items(ACTIVE_BACKGROUND_STYLES) { style ->
                    val isSelected = selected == style
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .border(
                                width = if (isSelected) 2.dp else 0.5.dp,
                                color = if (isSelected) MaterialTheme.colorScheme.primary else Color(0xFF3A3A3A),
                                shape = RoundedCornerShape(8.dp),
                            )
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onSelect(style) }
                            .padding(4.dp),
                    ) {
                        Canvas(modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f)) {
                            if (style == DashboardBackgroundStyle.NONE) drawRect(androidx.compose.ui.graphics.Color(0xFF1A1A1A))
                            else drawDashboardBackground(style)
                        }
                        Text(
                            BG_STYLE_LABELS[style] ?: style.name,
                            style = MaterialTheme.typography.labelSmall,
                            textAlign = TextAlign.Center,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 4.dp, bottom = 2.dp),
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_save)) } },
    )
}

/** Small texture preview thumbnail. */
@Composable
private fun TextureThumbnail(style: DashboardBackgroundStyle?, size: androidx.compose.ui.unit.Dp) {
    Canvas(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(4.dp))
            .border(0.5.dp, Color(0xFF3A3A3A), RoundedCornerShape(4.dp)),
    ) {
        if (style == null || style == DashboardBackgroundStyle.NONE) {
            drawRect(androidx.compose.ui.graphics.Color(0xFF1A1A1A))
        } else {
            drawDashboardBackground(style)
        }
    }
}
