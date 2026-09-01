package com.airchecklists.app.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.airchecklists.app.R
import com.airchecklists.app.data.model.Aircraft
import com.airchecklists.app.data.model.AppPreferences
import com.airchecklists.app.data.model.EfisHeadingSource
import com.airchecklists.app.data.model.EfisInstrument
import com.airchecklists.app.data.model.EfisSpeedUnit
import com.airchecklists.app.data.model.EfisVarioSource
import com.airchecklists.app.data.model.ThemeMode
import com.airchecklists.app.data.model.VacChart
import com.airchecklists.app.ui.components.ConfirmDeleteDialog
import com.airchecklists.app.ui.components.OrderableColumn
import com.airchecklists.app.ui.components.PrimaryTopBar
import com.airchecklists.app.ui.components.RowAction
import com.airchecklists.app.ui.components.RowActionsMenu
import com.airchecklists.app.ui.repoViewModelFactory
import com.airchecklists.app.ui.settings.vac.ReorderableVacList
import com.airchecklists.app.ui.theme.scaledByPrefs

private enum class SettingsSection { APPEARANCE, COCKPITS, AIRCRAFT, CHECKLISTS, VAC, HELP, DISCLAIMER, DATA }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    contentPadding: PaddingValues,
    onHome: () -> Unit,
    onAddAircraft: () -> Unit,
    onEditAircraft: (String) -> Unit,
    onAddChecklist: () -> Unit,
    onAddChecklistSection: () -> Unit,
    onEditChecklist: (aircraftId: String, checklistId: String) -> Unit,
    onAddVac: () -> Unit,
    onEditVac: (String) -> Unit,
    onHelp: () -> Unit,
    onEditDashboard: (String) -> Unit,
    viewModel: SettingsViewModel = viewModel(factory = repoViewModelFactory { SettingsViewModel(it) }),
) {
    val aircraft by viewModel.aircraft.collectAsStateWithLifecycle()
    val prefs by viewModel.preferences.collectAsStateWithLifecycle()
    val vacCharts by viewModel.vacCharts.collectAsStateWithLifecycle()
    val vacProgress by viewModel.vacProgress.collectAsStateWithLifecycle()
    val mapProgress by viewModel.mapProgress.collectAsStateWithLifecycle()
    val mapUpdateTag by viewModel.mapUpdateTag.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    // rememberSaveable so returning from an edit screen (dashboard / aircraft / vac /
    // checklist) restores the tab the user was on, instead of resetting to Affichage.
    var section by rememberSaveable { mutableStateOf(SettingsSection.APPEARANCE) }
    var aircraftToDelete by remember { mutableStateOf<Aircraft?>(null) }
    var checklistToDelete by remember { mutableStateOf<ChecklistRow?>(null) }
    var vacToDelete by remember { mutableStateOf<VacChart?>(null) }
    var dashboardToDelete by remember { mutableStateOf<com.airchecklists.app.data.model.Dashboard?>(null) }
    // Which aircraft a per-row export/import targets.
    var exportForId by remember { mutableStateOf<String?>(null) }
    var importForId by remember { mutableStateOf<String?>(null) }
    // Aircraft awaiting import-overwrite confirmation (has existing content).
    var importConfirmFor by remember { mutableStateOf<Aircraft?>(null) }

    // Hidden "Data" maintenance tab: unlocked by 5 taps on the Disclaimer title.
    // Deliberately NOT rememberSaveable → it vanishes when the app process dies.
    var dataUnlocked by remember { mutableStateOf(false) }
    var disclaimerTaps by remember { mutableIntStateOf(0) }
    // Mode of the SAF launchers when used for the whole-dataset export/import.
    var datasetExport by remember { mutableStateOf(false) }
    var datasetImport by remember { mutableStateOf(false) }
    val datasetExportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        if (uri != null) viewModel.exportDataset(uri)
        datasetExport = false
    }
    val datasetImportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) viewModel.importDataset(uri)
        datasetImport = false
    }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        val id = exportForId
        if (uri != null && id != null) viewModel.export(id, uri)
        else if (id != null) viewModel.notifyExportCancelled()
        exportForId = null
    }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        val id = importForId
        if (uri != null && id != null) viewModel.importInto(id, uri)
        importForId = null
    }

    LaunchedEffect(Unit) {
        viewModel.messages.collect { snackbarHostState.showSnackbar(it) }
    }
    val dataUnlockedMsg = stringResource(R.string.settings_data_unlocked)
    LaunchedEffect(dataUnlocked) {
        if (dataUnlocked) snackbarHostState.showSnackbar(dataUnlockedMsg)
    }

    Scaffold(
        topBar = {
            Column {
                PrimaryTopBar(
                    title = stringResource(R.string.settings_title),
                )
                // Scrollable section tabs, directly under the title bar (Material
                // standard placement; avoids overlapping the Android nav buttons).
                val order = buildList {
                    add(SettingsSection.APPEARANCE); add(SettingsSection.COCKPITS)
                    add(SettingsSection.AIRCRAFT); add(SettingsSection.CHECKLISTS); add(SettingsSection.VAC)
                    add(SettingsSection.HELP); add(SettingsSection.DISCLAIMER)
                    if (dataUnlocked) add(SettingsSection.DATA)
                }
                val selectedIndex = (order.indexOf(section) + 1).coerceAtLeast(0)
                androidx.compose.material3.ScrollableTabRow(
                    selectedTabIndex = selectedIndex,
                    edgePadding = 8.dp,
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                ) {
                    androidx.compose.material3.Tab(
                        selected = false,
                        onClick = onHome,
                        text = { Text(stringResource(R.string.action_back), maxLines = 1) },
                        icon = { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null) },
                    )
                    androidx.compose.material3.Tab(
                        selected = section == SettingsSection.APPEARANCE,
                        onClick = { section = SettingsSection.APPEARANCE },
                        text = { Text(stringResource(R.string.settings_section_appearance), maxLines = 1) },
                        icon = { Icon(Icons.Filled.Palette, contentDescription = null) },
                    )
                    androidx.compose.material3.Tab(
                        selected = section == SettingsSection.COCKPITS,
                        onClick = { section = SettingsSection.COCKPITS },
                        text = { Text(stringResource(R.string.settings_section_cockpits), maxLines = 1) },
                        icon = { Icon(Icons.Filled.Dashboard, contentDescription = null) },
                    )
                    androidx.compose.material3.Tab(
                        selected = section == SettingsSection.AIRCRAFT,
                        onClick = { section = SettingsSection.AIRCRAFT },
                        text = { Text(stringResource(R.string.settings_section_aircraft), maxLines = 1) },
                        icon = { Icon(Icons.Filled.Flight, contentDescription = null) },
                    )
                    androidx.compose.material3.Tab(
                        selected = section == SettingsSection.CHECKLISTS,
                        onClick = { section = SettingsSection.CHECKLISTS },
                        text = { Text(stringResource(R.string.settings_section_checklists), maxLines = 1) },
                        icon = { Icon(Icons.AutoMirrored.Filled.List, contentDescription = null) },
                    )
                    androidx.compose.material3.Tab(
                        selected = section == SettingsSection.VAC,
                        onClick = { section = SettingsSection.VAC },
                        text = { Text(stringResource(R.string.settings_section_vac), maxLines = 1) },
                        icon = { Icon(Icons.Filled.Map, contentDescription = null) },
                    )
                    androidx.compose.material3.Tab(
                        selected = section == SettingsSection.HELP,
                        onClick = { section = SettingsSection.HELP },
                        text = { Text(stringResource(R.string.help_title), maxLines = 1) },
                        icon = { Icon(Icons.AutoMirrored.Filled.HelpOutline, contentDescription = null) },
                    )
                    androidx.compose.material3.Tab(
                        selected = section == SettingsSection.DISCLAIMER,
                        onClick = { section = SettingsSection.DISCLAIMER },
                        text = { Text(stringResource(R.string.disclaimer_title), maxLines = 1) },
                        icon = { Icon(Icons.Filled.WarningAmber, contentDescription = null) },
                    )
                    if (dataUnlocked) {
                        androidx.compose.material3.Tab(
                            selected = section == SettingsSection.DATA,
                            onClick = { section = SettingsSection.DATA },
                            text = { Text(stringResource(R.string.settings_section_data), maxLines = 1) },
                            icon = { Icon(Icons.Filled.Storage, contentDescription = null) },
                        )
                    }
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { inner ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(
                    top = inner.calculateTopPadding() + 16.dp,
                    bottom = inner.calculateBottomPadding() + 16.dp,
                    start = 16.dp,
                    end = 16.dp,
                ),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            when (section) {
                SettingsSection.APPEARANCE -> AppearanceSection(
                    prefs = prefs,
                    onThemeMode = viewModel::setThemeMode,
                    onFontScale = viewModel::setFontScale,
                    onSplashSeconds = viewModel::setSplashSeconds,
                    onKeepScreenOn = viewModel::setKeepScreenOn,
                    onCockpitPagerStyle = viewModel::setCockpitPagerStyle,
                    onCockpitPagerPosition = viewModel::setCockpitPagerPosition,
                    onGaugeBezelStyle = viewModel::setGaugeBezelStyle,
                    onGaugeBezelColor = viewModel::setGaugeBezelColor,
                )
                SettingsSection.COCKPITS -> CockpitsSection(
                    prefs = prefs,
                    hasBarometer = viewModel.hasBarometer,
                    onHeadingSource = viewModel::setEfisHeadingSource,
                    onVarioSource = viewModel::setEfisVarioSource,
                    onEfisSpeedUnit = viewModel::setEfisSpeedUnit,
                    onAltitudeUnit = viewModel::setAltitudeUnit,
                    onEfisResponsiveness = viewModel::setEfisResponsiveness,
                    onEfisShowValues = viewModel::setEfisShowValues,
                    onFdrBufferMinutes = viewModel::setFdrBufferMinutes,
                    onFdrFlushMinutes = viewModel::setFdrFlushMinutes,
                    onSafeskyApiKey = viewModel::setSafeskyApiKey,
                    onAddDashboard = { onEditDashboard(viewModel.addDashboard("Nouveau tableau")) },
                    onEditDashboard = onEditDashboard,
                    onDuplicateDashboard = { srcId -> onEditDashboard(viewModel.duplicateDashboard(srcId, "(copie)")) },
                    onDeleteDashboard = { dashboardToDelete = it },
                    onReorderDashboards = viewModel::reorderDashboards,
                    onMapOrientation = viewModel::setMapOrientation,
                    onMapShowZoomButtons = viewModel::setMapShowZoomButtons,
                    mapProgress = mapProgress,
                    mapInstalledTag = viewModel.mapInstalledTag(),
                    mapUpdateTag = mapUpdateTag,
                    onDownloadMaps = viewModel::downloadMaps,
                    onCheckMapUpdate = viewModel::checkMapUpdate,
                )
                SettingsSection.AIRCRAFT -> AircraftSection(
                    aircraft = aircraft,
                    onAddAircraft = onAddAircraft,
                    onEditAircraft = onEditAircraft,
                    onDeleteAircraft = { aircraftToDelete = it },
                    onReordered = viewModel::reorderAircraft,
                    onExportAircraft = { a ->
                        exportForId = a.id
                        // Sanitize the suggested filename: some file pickers/providers
                        // reject dots or special chars in the base name (e.g. "F.JXSF").
                        val safe = a.name.replace(Regex("[^A-Za-z0-9 _-]"), "_").trim().ifBlank { "appareil" }
                        exportLauncher.launch("$safe.json")
                    },
                    onImportAircraft = { a ->
                        // Confirm before overwriting an aircraft that already has content.
                        val hasContent = a.characteristics.isNotEmpty() || a.checklists.isNotEmpty()
                        if (hasContent) {
                            importConfirmFor = a
                        } else {
                            importForId = a.id
                            importLauncher.launch(arrayOf("application/json"))
                        }
                    },
                )
                SettingsSection.CHECKLISTS -> ChecklistsSection(
                    aircraft = aircraft,
                    rowsFor = { viewModel.checklistRows(listOf(it)) },
                    onAddChecklist = onAddChecklist,
                    onAddChecklistSection = onAddChecklistSection,
                    onEditChecklist = onEditChecklist,
                    onDeleteChecklist = { checklistToDelete = it },
                    onReordered = viewModel::reorderChecklists,
                )
                SettingsSection.VAC -> VacSection(
                    charts = vacCharts,
                    airacCycle = prefs.vacAiracCycle,
                    downloadProgress = vacProgress,
                    onCycleChange = viewModel::setVacAiracCycle,
                    onAddVac = onAddVac,
                    onEditVac = onEditVac,
                    onDeleteVac = { vacToDelete = it },
                    onReordered = viewModel::reorderVac,
                    onDownloadVac = {
                        viewModel.downloadVacCharts(
                            msgDone = { ok -> "$ok carte(s) téléchargée(s)." },
                        )
                    },
                    onCheckVac = {
                        viewModel.checkVacUpdates(
                            msgDone = { n -> "$n mise(s) à jour disponible(s)." },
                            msgNone = "Toutes les cartes sont à jour.",
                        )
                    },
                )
                SettingsSection.HELP -> com.airchecklists.app.ui.help.HelpBody()
                SettingsSection.DISCLAIMER -> {
                    // 5 taps anywhere on the disclaimer content reveal the hidden Data tab
                    // for the lifetime of the app process.
                    com.airchecklists.app.ui.disclaimer.DisclaimerContent(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(
                                indication = null,
                                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                            ) {
                                if (!dataUnlocked) {
                                    disclaimerTaps++
                                    if (disclaimerTaps >= 5) {
                                        dataUnlocked = true
                                    }
                                }
                            },
                    )
                }
                SettingsSection.DATA -> DataSection(
                    onExport = {
                        datasetExport = true
                        datasetExportLauncher.launch("airdetente-dataset.json")
                    },
                    onImport = {
                        datasetImport = true
                        datasetImportLauncher.launch(arrayOf("application/json"))
                    },
                )
            }
        }
    }

    // Confirm before an import overwrites an aircraft that already has content.
    importConfirmFor?.let { a ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { importConfirmFor = null },
            title = { Text(stringResource(R.string.import_confirm_title)) },
            text = { Text(stringResource(R.string.import_confirm_message, a.name)) },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    importForId = a.id
                    importConfirmFor = null
                    importLauncher.launch(arrayOf("application/json"))
                }) { Text(stringResource(R.string.import_confirm_action)) }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { importConfirmFor = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }

    aircraftToDelete?.let { a ->
        ConfirmDeleteDialog(
            message = stringResource(R.string.confirm_delete_aircraft),
            onConfirm = {
                viewModel.deleteAircraft(a.id)
                aircraftToDelete = null
            },
            onDismiss = { aircraftToDelete = null },
        )
    }
    dashboardToDelete?.let { d ->
        ConfirmDeleteDialog(
            message = stringResource(R.string.confirm_delete_dashboard, d.name),
            onConfirm = {
                viewModel.deleteDashboard(d.id)
                dashboardToDelete = null
            },
            onDismiss = { dashboardToDelete = null },
        )
    }
    checklistToDelete?.let { row ->
        ConfirmDeleteDialog(
            message = stringResource(R.string.confirm_delete_checklist),
            onConfirm = {
                viewModel.deleteChecklist(row.aircraftId, row.checklistId)
                checklistToDelete = null
            },
            onDismiss = { checklistToDelete = null },
        )
    }
    vacToDelete?.let { chart ->
        ConfirmDeleteDialog(
            message = stringResource(R.string.confirm_delete_vac),
            onConfirm = {
                viewModel.deleteVac(chart.id)
                vacToDelete = null
            },
            onDismiss = { vacToDelete = null },
        )
    }
}

// ---- Sections ----

/** −/value/+ stepper for a minutes setting (label left, controls right). */
@Composable
private fun MinutesStepper(label: String, value: Int, min: Int, max: Int, onChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        IconButton(onClick = { onChange(value - 1) }, enabled = value > min) {
            Icon(Icons.Filled.Remove, contentDescription = "-")
        }
        Text(
            stringResource(R.string.settings_fdr_minutes, value),
            style = MaterialTheme.typography.titleMedium,
        )
        IconButton(onClick = { onChange(value + 1) }, enabled = value < max) {
            Icon(Icons.Filled.Add, contentDescription = "+")
        }
    }
}

/** Hidden maintenance tab: export / import the full dataset. */
@Composable
private fun DataSection(onExport: () -> Unit, onImport: () -> Unit) {
    SectionHeader(stringResource(R.string.settings_section_data))
    Text(
        stringResource(R.string.settings_data_hint),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    OutlinedButton(onClick = onExport, modifier = Modifier.fillMaxWidth()) {
        Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(18.dp))
        Text(stringResource(R.string.settings_data_export), modifier = Modifier.padding(start = 8.dp))
    }
    OutlinedButton(onClick = onImport, modifier = Modifier.fillMaxWidth()) {
        Icon(Icons.Filled.Upload, contentDescription = null, modifier = Modifier.size(18.dp))
        Text(stringResource(R.string.settings_data_import), modifier = Modifier.padding(start = 8.dp))
    }
}

@Composable
private fun AppearanceSection(
    prefs: AppPreferences,
    onThemeMode: (ThemeMode) -> Unit,
    onFontScale: (Float) -> Unit,
    onSplashSeconds: (Int) -> Unit,
    onKeepScreenOn: (Boolean) -> Unit,
    onCockpitPagerStyle: (com.airchecklists.app.data.model.CockpitPagerStyle) -> Unit,
    onCockpitPagerPosition: (com.airchecklists.app.data.model.CockpitPagerPosition) -> Unit,
    onGaugeBezelStyle: (com.airchecklists.app.data.model.GaugeBezelStyle) -> Unit,
    onGaugeBezelColor: (Long) -> Unit,
) {
    SectionHeader(stringResource(R.string.settings_section_appearance))
    Text(
        stringResource(R.string.settings_theme),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        SegmentedButton(
            selected = prefs.themeMode == ThemeMode.DARK,
            onClick = { onThemeMode(ThemeMode.DARK) },
            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
        ) { Text(stringResource(R.string.settings_theme_dark)) }
        SegmentedButton(
            selected = prefs.themeMode == ThemeMode.AUTO,
            onClick = { onThemeMode(ThemeMode.AUTO) },
            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
        ) { Text(stringResource(R.string.settings_theme_auto)) }
    }

    // Font size: everything on one line (label preview kept short).
    SectionHeader(stringResource(R.string.settings_font_size))
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        IconButton(
            onClick = { onFontScale(prefs.fontScale - AppPreferences.FONT_STEP) },
            enabled = prefs.fontScale > AppPreferences.MIN_FONT_SCALE + 0.001f,
        ) {
            Icon(Icons.Filled.Remove, stringResource(R.string.settings_font_decrease))
        }
        Text("${(prefs.fontScale * 100).toInt()} %", style = MaterialTheme.typography.titleMedium)
        IconButton(
            onClick = { onFontScale(prefs.fontScale + AppPreferences.FONT_STEP) },
            enabled = prefs.fontScale < AppPreferences.MAX_FONT_SCALE - 0.001f,
        ) {
            Icon(Icons.Filled.Add, stringResource(R.string.settings_font_increase))
        }
        Text(
            text = stringResource(R.string.settings_font_preview),
            style = MaterialTheme.typography.bodyLarge.scaledByPrefs(),
            maxLines = 1,
            modifier = Modifier.padding(start = 12.dp),
        )
    }

    // Splash screen duration (0 = disabled).
    SectionHeader(stringResource(R.string.settings_splash))
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        IconButton(
            onClick = { onSplashSeconds(prefs.splashSeconds - 1) },
            enabled = prefs.splashSeconds > 0,
        ) {
            Icon(Icons.Filled.Remove, stringResource(R.string.settings_splash_decrease))
        }
        Text(
            text = if (prefs.splashSeconds == 0) stringResource(R.string.settings_splash_off)
            else stringResource(R.string.settings_splash_seconds, prefs.splashSeconds),
            style = MaterialTheme.typography.titleMedium,
        )
        IconButton(
            onClick = { onSplashSeconds(prefs.splashSeconds + 1) },
            enabled = prefs.splashSeconds < AppPreferences.MAX_SPLASH_SECONDS,
        ) {
            Icon(Icons.Filled.Add, stringResource(R.string.settings_splash_increase))
        }
    }

    // Keep screen on toggle (general display setting → stays in Affichage).
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onKeepScreenOn(!prefs.keepScreenOn) },
    ) {
        androidx.compose.material3.Checkbox(
            checked = prefs.keepScreenOn,
            onCheckedChange = { onKeepScreenOn(it) },
        )
        Text(
            stringResource(R.string.settings_keep_screen_on),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(start = 4.dp),
        )
    }

    // --- Cockpit page marker (style + position) ---
    SectionHeader(stringResource(R.string.settings_pager_style))
    Text(
        stringResource(R.string.settings_pager_style_hint),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        val styles = com.airchecklists.app.data.model.CockpitPagerStyle.entries
        styles.forEachIndexed { i, s ->
            SegmentedButton(
                selected = prefs.cockpitPagerStyle == s,
                onClick = { onCockpitPagerStyle(s) },
                shape = SegmentedButtonDefaults.itemShape(index = i, count = styles.size),
            ) {
                Text(
                    when (s) {
                        com.airchecklists.app.data.model.CockpitPagerStyle.DOTS -> stringResource(R.string.settings_pager_style_dots)
                        com.airchecklists.app.data.model.CockpitPagerStyle.BARS -> stringResource(R.string.settings_pager_style_bars)
                        com.airchecklists.app.data.model.CockpitPagerStyle.NUMBERS -> stringResource(R.string.settings_pager_style_numbers)
                    },
                )
            }
        }
    }
    Text(
        stringResource(R.string.settings_pager_position),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 4.dp),
    )
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        SegmentedButton(
            selected = prefs.cockpitPagerPosition == com.airchecklists.app.data.model.CockpitPagerPosition.TOP,
            onClick = { onCockpitPagerPosition(com.airchecklists.app.data.model.CockpitPagerPosition.TOP) },
            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
        ) { Text(stringResource(R.string.settings_pager_position_top)) }
        SegmentedButton(
            selected = prefs.cockpitPagerPosition == com.airchecklists.app.data.model.CockpitPagerPosition.BOTTOM,
            onClick = { onCockpitPagerPosition(com.airchecklists.app.data.model.CockpitPagerPosition.BOTTOM) },
            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
        ) { Text(stringResource(R.string.settings_pager_position_bottom)) }
    }

    // --- Analog gauge bezel (contour) style + colour ---
    SectionHeader(stringResource(R.string.settings_bezel))
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        val styles = com.airchecklists.app.data.model.GaugeBezelStyle.entries
        styles.forEachIndexed { i, s ->
            SegmentedButton(
                selected = prefs.gaugeBezelStyle == s,
                onClick = { onGaugeBezelStyle(s) },
                shape = SegmentedButtonDefaults.itemShape(index = i, count = styles.size),
            ) {
                Text(
                    when (s) {
                        com.airchecklists.app.data.model.GaugeBezelStyle.SOLID -> stringResource(R.string.settings_bezel_solid)
                        com.airchecklists.app.data.model.GaugeBezelStyle.CARBON -> stringResource(R.string.settings_bezel_carbon)
                        com.airchecklists.app.data.model.GaugeBezelStyle.BRUSHED -> stringResource(R.string.settings_bezel_brushed)
                    },
                    maxLines = 1,
                )
            }
        }
    }
    // Explanation shown regardless of style: this is the default, overridable per instrument.
    Text(
        stringResource(R.string.settings_bezel_color_hint),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 4.dp, bottom = 2.dp),
    )
    // Colour palette (only relevant when the style is SOLID).
    if (prefs.gaugeBezelStyle == com.airchecklists.app.data.model.GaugeBezelStyle.SOLID) {
        Text(
            stringResource(R.string.settings_bezel_color),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
        val palette = com.airchecklists.app.data.model.DARK_ACCENTS
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
            palette.forEach { col ->
                val selected = prefs.gaugeBezelColor == col
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(Color(col))
                        .border(
                            width = if (selected) 3.dp else 1.dp,
                            color = if (selected) MaterialTheme.colorScheme.primary else Color(0xFF666666),
                            shape = CircleShape,
                        )
                        .clickable { onGaugeBezelColor(col) },
                )
            }
        }
    }

    // --- Quitter l'application (arrête le service et ferme proprement) ---
    SectionHeader(stringResource(R.string.settings_quit_section))
    val quitCtx = androidx.compose.ui.platform.LocalContext.current
    var confirmQuit by remember { mutableStateOf(false) }
    OutlinedButton(onClick = { confirmQuit = true }, modifier = Modifier.fillMaxWidth()) {
        androidx.compose.material3.Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = null)
        Text(stringResource(R.string.settings_quit_action), modifier = Modifier.padding(start = 8.dp))
    }
    if (confirmQuit) {
        AlertDialog(
            onDismissRequest = { confirmQuit = false },
            title = { Text(stringResource(R.string.settings_quit_action)) },
            text = { Text(stringResource(R.string.settings_quit_message)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmQuit = false
                    quitCtx.startActivity(
                        android.content.Intent(quitCtx, com.airchecklists.app.MainActivity::class.java).apply {
                            action = com.airchecklists.app.MainActivity.ACTION_QUIT_APP
                            flags = android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP or android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP
                        },
                    )
                }) { Text(stringResource(R.string.settings_quit_action)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmQuit = false }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
}

/** Cockpit-related settings: EFIS sources/units/behaviour, saved dashboards, map. */
@Composable
private fun CockpitsSection(
    prefs: AppPreferences,
    hasBarometer: Boolean,
    onHeadingSource: (EfisHeadingSource) -> Unit,
    onVarioSource: (EfisVarioSource) -> Unit,
    onEfisSpeedUnit: (EfisSpeedUnit) -> Unit,
    onAltitudeUnit: (com.airchecklists.app.data.model.AltitudeUnit) -> Unit,
    onEfisResponsiveness: (Float) -> Unit,
    onEfisShowValues: (Boolean) -> Unit,
    onFdrBufferMinutes: (Int) -> Unit,
    onFdrFlushMinutes: (Int) -> Unit,
    onSafeskyApiKey: (String?) -> Unit,
    onAddDashboard: () -> Unit,
    onEditDashboard: (String) -> Unit,
    onDuplicateDashboard: (String) -> Unit,
    onDeleteDashboard: (com.airchecklists.app.data.model.Dashboard) -> Unit,
    onReorderDashboards: (orderedIds: List<String>) -> Unit,
    onMapOrientation: (com.airchecklists.app.data.model.MapOrientation) -> Unit,
    onMapShowZoomButtons: (Boolean) -> Unit,
    mapProgress: com.airchecklists.app.data.repository.MapDownloadProgress?,
    mapInstalledTag: String?,
    mapUpdateTag: String?,
    onDownloadMaps: () -> Unit,
    onCheckMapUpdate: () -> Unit,
) {
    // EFIS options.
    SectionHeader(stringResource(R.string.settings_efis))
    Text(
        stringResource(R.string.settings_efis_heading),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        SegmentedButton(
            selected = prefs.efisHeadingSource == EfisHeadingSource.MAGNETIC,
            onClick = { onHeadingSource(EfisHeadingSource.MAGNETIC) },
            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
        ) { Text(stringResource(R.string.settings_efis_heading_magnetic)) }
        SegmentedButton(
            selected = prefs.efisHeadingSource == EfisHeadingSource.GPS_TRACK,
            onClick = { onHeadingSource(EfisHeadingSource.GPS_TRACK) },
            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
        ) { Text(stringResource(R.string.settings_efis_heading_gps)) }
    }
    Text(
        stringResource(R.string.settings_efis_vario),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        SegmentedButton(
            selected = prefs.efisVarioSource == EfisVarioSource.GPS,
            onClick = { onVarioSource(EfisVarioSource.GPS) },
            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
        ) { Text(stringResource(R.string.settings_efis_vario_gps)) }
        SegmentedButton(
            selected = prefs.efisVarioSource == EfisVarioSource.BAROMETER,
            onClick = { if (hasBarometer) onVarioSource(EfisVarioSource.BAROMETER) },
            enabled = hasBarometer,
            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
        ) { Text(stringResource(R.string.settings_efis_vario_baro)) }
    }
    if (!hasBarometer) {
        Text(
            stringResource(R.string.settings_efis_no_baro),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    // Airspeed unit.
    Text(
        stringResource(R.string.settings_efis_speed_unit),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        SegmentedButton(
            selected = prefs.efisSpeedUnit == EfisSpeedUnit.KMH,
            onClick = { onEfisSpeedUnit(EfisSpeedUnit.KMH) },
            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
        ) { Text(stringResource(R.string.settings_efis_speed_kmh)) }
        SegmentedButton(
            selected = prefs.efisSpeedUnit == EfisSpeedUnit.KNOTS,
            onClick = { onEfisSpeedUnit(EfisSpeedUnit.KNOTS) },
            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
        ) { Text(stringResource(R.string.settings_efis_speed_knots)) }
    }
    Text(
        stringResource(R.string.settings_alt_unit),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        SegmentedButton(
            selected = prefs.altitudeUnit == com.airchecklists.app.data.model.AltitudeUnit.FEET,
            onClick = { onAltitudeUnit(com.airchecklists.app.data.model.AltitudeUnit.FEET) },
            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
        ) { Text(stringResource(R.string.settings_alt_unit_ft)) }
        SegmentedButton(
            selected = prefs.altitudeUnit == com.airchecklists.app.data.model.AltitudeUnit.METERS,
            onClick = { onAltitudeUnit(com.airchecklists.app.data.model.AltitudeUnit.METERS) },
            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
        ) { Text(stringResource(R.string.settings_alt_unit_m)) }
    }

    // Instrument responsiveness (smoothing).
    Text(
        stringResource(R.string.settings_efis_sensitivity),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.settings_efis_sensitivity_smooth), style = MaterialTheme.typography.labelSmall)
        Slider(
            value = prefs.efisResponsiveness,
            onValueChange = onEfisResponsiveness,
            valueRange = 0.05f..1f,
            modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
        )
        Text(stringResource(R.string.settings_efis_sensitivity_reactive), style = MaterialTheme.typography.labelSmall)
    }

    // Show numeric values toggle.
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onEfisShowValues(!prefs.efisShowValues) },
    ) {
        androidx.compose.material3.Checkbox(
            checked = prefs.efisShowValues,
            onCheckedChange = { onEfisShowValues(it) },
        )
        Column(modifier = Modifier.padding(start = 4.dp)) {
            Text(
                stringResource(R.string.settings_efis_show_values),
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                stringResource(R.string.settings_efis_show_values_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    // Flight recorder (ANLFDR): rolling buffer length + disk-flush period.
    SectionHeader(stringResource(R.string.settings_fdr))
    Text(
        stringResource(R.string.settings_fdr_hint),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    MinutesStepper(
        label = stringResource(R.string.settings_fdr_buffer),
        value = prefs.fdrBufferMinutes,
        min = AppPreferences.FDR_MIN_BUFFER_MIN,
        max = AppPreferences.FDR_MAX_BUFFER_MIN,
        onChange = onFdrBufferMinutes,
    )
    MinutesStepper(
        label = stringResource(R.string.settings_fdr_flush),
        value = prefs.fdrFlushMinutes,
        min = AppPreferences.FDR_MIN_FLUSH_MIN,
        max = AppPreferences.FDR_MAX_FLUSH_MIN,
        onChange = onFdrFlushMinutes,
    )

    // Safesky API key (ANLTRF traffic radar).
    SectionHeader(stringResource(R.string.settings_safesky))
    Text(
        stringResource(R.string.settings_safesky_hint),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    SafeskyKeyField(value = prefs.safeskyApiKey ?: "", onChanged = { onSafeskyApiKey(it.ifBlank { null }) })

    // Dashboards (saved instrument layouts).
    SectionHeader(stringResource(R.string.settings_dashboards))
    Text(
        stringResource(R.string.settings_dashboards_hint),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    OrderableColumn(
        items = prefs.effectiveDashboards,
        keyOf = { it.id },
        onReordered = onReorderDashboards,
    ) { dash ->
        Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
            Text(dash.name, style = MaterialTheme.typography.bodyLarge)
        }
        RowActionsMenu(
            actions = listOf(
                RowAction(stringResource(R.string.action_edit), Icons.Filled.Edit) { onEditDashboard(dash.id) },
                RowAction(stringResource(R.string.action_duplicate), Icons.Filled.ContentCopy) { onDuplicateDashboard(dash.id) },
                RowAction(
                    stringResource(R.string.action_delete),
                    Icons.Filled.Delete,
                    tint = MaterialTheme.colorScheme.error,
                ) { onDeleteDashboard(dash) },
            ),
        )
    }
    OutlinedButton(onClick = onAddDashboard, modifier = Modifier.fillMaxWidth()) {
        androidx.compose.material3.Icon(Icons.Filled.Add, contentDescription = null)
        Text(stringResource(R.string.settings_dashboard_add), modifier = Modifier.padding(start = 8.dp))
    }

    // Maps: download/update the offline VFR map + orientation.
    SectionHeader(stringResource(R.string.settings_map))
    Text(
        stringResource(R.string.settings_map_orientation),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        SegmentedButton(
            selected = prefs.mapOrientation == com.airchecklists.app.data.model.MapOrientation.NORTH_UP,
            onClick = { onMapOrientation(com.airchecklists.app.data.model.MapOrientation.NORTH_UP) },
            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
        ) { Text(stringResource(R.string.settings_map_north_up)) }
        SegmentedButton(
            selected = prefs.mapOrientation == com.airchecklists.app.data.model.MapOrientation.TRACK_UP,
            onClick = { onMapOrientation(com.airchecklists.app.data.model.MapOrientation.TRACK_UP) },
            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
        ) { Text(stringResource(R.string.settings_map_track_up)) }
    }
    // Show / hide the on-map zoom buttons (pinch-to-zoom always works).
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().clickable { onMapShowZoomButtons(!prefs.mapShowZoomButtons) },
    ) {
        androidx.compose.material3.Checkbox(
            checked = prefs.mapShowZoomButtons,
            onCheckedChange = { onMapShowZoomButtons(it) },
        )
        Text(
            stringResource(R.string.settings_map_zoom_buttons),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(start = 4.dp),
        )
    }

    Text(
        if (mapInstalledTag != null)
            stringResource(R.string.settings_map_installed, mapInstalledTag)
        else stringResource(R.string.settings_map_none),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    if (mapUpdateTag != null) {
        Text(
            stringResource(R.string.settings_map_update_available, mapUpdateTag),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
        )
    }

    val downloading = mapProgress != null
    OutlinedButton(onClick = onDownloadMaps, enabled = !downloading, modifier = Modifier.fillMaxWidth()) {
        androidx.compose.material3.Icon(Icons.Filled.CloudDownload, contentDescription = null)
        Text(stringResource(R.string.settings_map_download), modifier = Modifier.padding(start = 8.dp))
    }
    if (mapProgress != null) {
        val p = mapProgress
        val frac = if (p.bytesTotal > 0) (p.bytesDone.toFloat() / p.bytesTotal).coerceIn(0f, 1f) else 0f
        if (p.bytesTotal > 0) {
            LinearProgressIndicator(progress = { frac }, modifier = Modifier.fillMaxWidth())
        } else {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
        val mb = { b: Long -> "%.0f".format(b / 1_048_576.0) }
        Text(
            "${p.phase} — ${mb(p.bytesDone)} / ${if (p.bytesTotal > 0) mb(p.bytesTotal) + " Mo" else "?"}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    OutlinedButton(onClick = onCheckMapUpdate, enabled = !downloading, modifier = Modifier.fillMaxWidth()) {
        androidx.compose.material3.Icon(Icons.Filled.Refresh, contentDescription = null)
        Text(stringResource(R.string.settings_map_check_update), modifier = Modifier.padding(start = 8.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AircraftSection(
    aircraft: List<Aircraft>,
    onAddAircraft: () -> Unit,
    onEditAircraft: (String) -> Unit,
    onDeleteAircraft: (Aircraft) -> Unit,
    onReordered: (orderedIds: List<String>) -> Unit,
    onExportAircraft: (Aircraft) -> Unit,
    onImportAircraft: (Aircraft) -> Unit,
) {
    SectionHeader(stringResource(R.string.settings_section_aircraft))
    if (aircraft.isNotEmpty()) {
        Text(
            stringResource(R.string.settings_reorder_aircraft_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OrderableColumn(
            items = aircraft,
            keyOf = { it.id },
            onReordered = onReordered,
        ) { a ->
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 12.dp),
            ) {
                Text(a.name, style = MaterialTheme.typography.bodyLarge)
                if (a.subtitle.isNotBlank()) {
                    Text(
                        a.subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            RowActionsMenu(
                actions = listOf(
                    RowAction(stringResource(R.string.action_edit), Icons.Filled.Edit) { onEditAircraft(a.id) },
                    RowAction(
                        stringResource(R.string.action_delete),
                        Icons.Filled.Delete,
                        tint = MaterialTheme.colorScheme.error,
                    ) { onDeleteAircraft(a) },
                    RowAction(stringResource(R.string.settings_import), Icons.Filled.Upload) { onImportAircraft(a) },
                    RowAction(stringResource(R.string.settings_export), Icons.Filled.Download) { onExportAircraft(a) },
                ),
            )
        }
    }
    OutlinedButton(onClick = onAddAircraft, modifier = Modifier.fillMaxWidth()) {
        Icon(Icons.Filled.Add, null, Modifier.size(18.dp))
        Text(stringResource(R.string.settings_add_aircraft), Modifier.padding(start = 8.dp))
    }
}

@Composable
private fun ChecklistsSection(
    aircraft: List<Aircraft>,
    rowsFor: (Aircraft) -> List<ChecklistRow>,
    onAddChecklist: () -> Unit,
    onAddChecklistSection: () -> Unit,
    onEditChecklist: (aircraftId: String, checklistId: String) -> Unit,
    onDeleteChecklist: (ChecklistRow) -> Unit,
    onReordered: (aircraftId: String, orderedIds: List<String>) -> Unit,
) {
    SectionHeader(stringResource(R.string.settings_section_checklists))
    Text(
        stringResource(R.string.settings_reorder_hint),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    aircraft.forEach { a ->
        val rows = rowsFor(a)
        if (rows.isNotEmpty()) {
            ReorderableChecklistGroup(
                aircraftName = a.name,
                rows = rows,
                onEdit = { onEditChecklist(it.aircraftId, it.checklistId) },
                onDelete = onDeleteChecklist,
                onReordered = onReordered,
            )
        }
    }
    OutlinedButton(onClick = onAddChecklist, modifier = Modifier.fillMaxWidth()) {
        Icon(Icons.Filled.Add, null, Modifier.size(18.dp))
        Text(stringResource(R.string.settings_add_checklist), Modifier.padding(start = 8.dp))
    }
    OutlinedButton(onClick = onAddChecklistSection, modifier = Modifier.fillMaxWidth()) {
        Icon(Icons.Filled.Add, null, Modifier.size(18.dp))
        Text(stringResource(R.string.settings_add_checklist_section), Modifier.padding(start = 8.dp))
    }
}

@Composable
private fun VacSection(
    charts: List<VacChart>,
    airacCycle: String,
    downloadProgress: VacDownloadProgress?,
    onCycleChange: (String) -> Unit,
    onAddVac: () -> Unit,
    onEditVac: (String) -> Unit,
    onDeleteVac: (VacChart) -> Unit,
    onReordered: (orderedIds: List<String>) -> Unit,
    onDownloadVac: () -> Unit,
    onCheckVac: () -> Unit,
) {
    SectionHeader(stringResource(R.string.settings_section_vac))
    OutlinedTextField(
        value = airacCycle,
        onValueChange = onCycleChange,
        label = { Text(stringResource(R.string.settings_vac_cycle)) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    if (charts.isNotEmpty()) {
        Text(
            stringResource(R.string.settings_vac_reorder_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        ReorderableVacList(
            charts = charts,
            onEdit = { onEditVac(it.id) },
            onDelete = onDeleteVac,
            onReordered = onReordered,
        )
    }
    OutlinedButton(onClick = onAddVac, modifier = Modifier.fillMaxWidth()) {
        Icon(Icons.Filled.Add, null, Modifier.size(18.dp))
        Text(stringResource(R.string.settings_add_vac), Modifier.padding(start = 8.dp))
    }

    val downloading = downloadProgress != null
    OutlinedButton(
        onClick = onDownloadVac,
        enabled = charts.isNotEmpty() && !downloading,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Icon(Icons.Filled.CloudDownload, null, Modifier.size(18.dp))
        Text(stringResource(R.string.settings_vac_download), Modifier.padding(start = 8.dp))
    }
    // Progress bar while downloading.
    if (downloadProgress != null) {
        val fraction = if (downloadProgress.total == 0) 0f
        else downloadProgress.done.toFloat() / downloadProgress.total
        LinearProgressIndicator(
            progress = { fraction },
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            text = stringResource(
                R.string.vac_download_progress,
                downloadProgress.done,
                downloadProgress.total,
            ),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    OutlinedButton(
        onClick = onCheckVac,
        enabled = charts.isNotEmpty() && !downloading,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Icon(Icons.Filled.Refresh, null, Modifier.size(18.dp))
        Text(stringResource(R.string.settings_vac_check), Modifier.padding(start = 8.dp))
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
    )
}

/** Small helper: make a whole ListItem row clickable. */
private fun Modifier.clickableRow(onClick: () -> Unit): Modifier =
    this.fillMaxWidth().clickable(onClick = onClick)

/** Text field for the Safesky API key, masked like a password with a show/hide toggle. */
@Composable
private fun SafeskyKeyField(value: String, onChanged: (String) -> Unit) {
    var text by remember(value) { mutableStateOf(value) }
    var visible by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = text,
        onValueChange = { text = it; onChanged(it) },
        label = { Text(stringResource(R.string.settings_safesky_key_label)) },
        singleLine = true,
        visualTransformation = if (visible) androidx.compose.ui.text.input.VisualTransformation.None
                               else androidx.compose.ui.text.input.PasswordVisualTransformation(),
        trailingIcon = {
            TextButton(onClick = { visible = !visible }) {
                Text(if (visible) "Masquer" else "Afficher", style = MaterialTheme.typography.labelSmall)
            }
        },
        modifier = Modifier.fillMaxWidth(),
    )
}
