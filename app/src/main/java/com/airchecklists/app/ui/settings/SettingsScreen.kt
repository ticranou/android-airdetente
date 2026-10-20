package com.airchecklists.app.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ExposedDropdownMenuDefaults
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.airchecklists.app.R
import com.airchecklists.app.data.model.Aircraft
import com.airchecklists.app.data.model.AppPreferences
import com.airchecklists.app.data.model.EfisHeadingSource
import com.airchecklists.app.data.model.EfisCalibrationWatch
import com.airchecklists.app.ui.components.CompassCalibrationDialog
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

internal enum class SettingsSection {
    SEARCH, APPLICATION, COCKPITS, INSTRUMENTS, AIRCRAFT, CHECKLISTS, AIRPORTS, DATA, HELP, DISCLAIMER
}

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
    // checklist) restores the tab the user was on, instead of resetting to Recherche.
    var section by rememberSaveable { mutableStateOf(SettingsSection.SEARCH) }
    var pendingAnchor by remember { mutableStateOf<SettingsAnchor?>(null) }
    val scrollState = rememberScrollState()
    val anchorOffsets = remember { androidx.compose.runtime.mutableStateMapOf<SettingsAnchor, Int>() }
    val coroutineScope = rememberCoroutineScope()
    val density = LocalDensity.current
    var aircraftToDelete by remember { mutableStateOf<Aircraft?>(null) }
    var checklistToDelete by remember { mutableStateOf<ChecklistRow?>(null) }
    var vacToDelete by remember { mutableStateOf<VacChart?>(null) }
    var dashboardToDelete by remember { mutableStateOf<com.airchecklists.app.data.model.Dashboard?>(null) }
    var exportForId by remember { mutableStateOf<String?>(null) }
    var importForId by remember { mutableStateOf<String?>(null) }
    var importConfirmFor by remember { mutableStateOf<Aircraft?>(null) }

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

    var showQrScan by remember { mutableStateOf(false) }
    var showJsonImportChoice by remember { mutableStateOf(false) }
    val cockpitImportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) viewModel.importCockpitFromUri(uri)
    }
    val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current

    LaunchedEffect(Unit) {
        viewModel.messages.collect { snackbarHostState.showSnackbar(it) }
    }

    LaunchedEffect(pendingAnchor) {
        val anchor = pendingAnchor ?: return@LaunchedEffect
        val topBarPx = with(density) { 130.dp.toPx() }.toInt()
        var attempts = 0
        while (anchorOffsets[anchor] == null && attempts < 30) {
            kotlinx.coroutines.delay(16); attempts++
        }
        anchorOffsets[anchor]?.let { y ->
            scrollState.animateScrollTo((y - topBarPx).coerceAtLeast(0))
        }
        pendingAnchor = null
    }

    if (showQrScan) {
        QrScanScreen(
            onResult = { raw ->
                showQrScan = false
                viewModel.importCockpitFromText(raw)
            },
            onBack = { showQrScan = false },
        )
        return
    }

    if (showJsonImportChoice) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showJsonImportChoice = false },
            title = { Text(stringResource(R.string.dashboard_import_json_title)) },
            text = { Text(stringResource(R.string.dashboard_import_json_message)) },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    showJsonImportChoice = false
                    cockpitImportLauncher.launch(arrayOf("application/json"))
                }) { Text(stringResource(R.string.dashboard_import_json_file)) }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = {
                    showJsonImportChoice = false
                    val text = clipboard.getText()?.text
                    if (text.isNullOrBlank()) viewModel.notifyEmptyClipboard()
                    else viewModel.importCockpitFromText(text)
                }) { Text(stringResource(R.string.dashboard_import_json_clipboard)) }
            },
        )
    }

    Scaffold(
        topBar = {
            Column {
                PrimaryTopBar(
                    title = stringResource(R.string.settings_title),
                )
                val order = listOf(
                    SettingsSection.SEARCH,
                    SettingsSection.APPLICATION,
                    SettingsSection.COCKPITS,
                    SettingsSection.INSTRUMENTS,
                    SettingsSection.AIRCRAFT,
                    SettingsSection.CHECKLISTS,
                    SettingsSection.AIRPORTS,
                    SettingsSection.DATA,
                    SettingsSection.HELP,
                    SettingsSection.DISCLAIMER,
                )
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
                        selected = section == SettingsSection.SEARCH,
                        onClick = { section = SettingsSection.SEARCH },
                        text = { Text("Recherche", maxLines = 1) },
                        icon = { Icon(Icons.Default.Search, contentDescription = null) },
                    )
                    androidx.compose.material3.Tab(
                        selected = section == SettingsSection.APPLICATION,
                        onClick = { section = SettingsSection.APPLICATION },
                        text = { Text(stringResource(R.string.settings_section_application), maxLines = 1) },
                        icon = { Icon(Icons.Filled.Settings, contentDescription = null) },
                    )
                    androidx.compose.material3.Tab(
                        selected = section == SettingsSection.COCKPITS,
                        onClick = { section = SettingsSection.COCKPITS },
                        text = { Text(stringResource(R.string.settings_section_cockpits), maxLines = 1) },
                        icon = { Icon(Icons.Filled.Dashboard, contentDescription = null) },
                    )
                    androidx.compose.material3.Tab(
                        selected = section == SettingsSection.INSTRUMENTS,
                        onClick = { section = SettingsSection.INSTRUMENTS },
                        text = { Text(stringResource(R.string.settings_section_instruments), maxLines = 1) },
                        icon = { Icon(Icons.Filled.Speed, contentDescription = null) },
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
                        selected = section == SettingsSection.AIRPORTS,
                        onClick = { section = SettingsSection.AIRPORTS },
                        text = { Text(stringResource(R.string.settings_section_airports), maxLines = 1) },
                        icon = { Icon(Icons.Filled.Map, contentDescription = null) },
                    )
                    androidx.compose.material3.Tab(
                        selected = section == SettingsSection.DATA,
                        onClick = { section = SettingsSection.DATA },
                        text = { Text(stringResource(R.string.settings_section_data), maxLines = 1) },
                        icon = { Icon(Icons.Filled.Storage, contentDescription = null) },
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
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { inner ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(
                    top = inner.calculateTopPadding() + 16.dp,
                    bottom = inner.calculateBottomPadding() + 16.dp,
                    start = 16.dp,
                    end = 16.dp,
                ),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            when (section) {
                SettingsSection.SEARCH -> SettingsSearchTab(onNavigateTo = { sec, anchor ->
                    section = sec
                    pendingAnchor = anchor
                })
                SettingsSection.APPLICATION -> ApplicationSection(
                    anchorOffsets = anchorOffsets,
                    prefs = prefs,
                    onThemeMode = viewModel::setThemeMode,
                    onSplashSeconds = viewModel::setSplashSeconds,
                    onKeepScreenOn = viewModel::setKeepScreenOn,
                )
                SettingsSection.COCKPITS -> CockpitsSection(
                    anchorOffsets = anchorOffsets,
                    prefs = prefs,
                    onCockpitPagerStyle = viewModel::setCockpitPagerStyle,
                    onCockpitPagerPosition = viewModel::setCockpitPagerPosition,
                    onAddDashboard = { onEditDashboard(viewModel.addDashboard("Nouveau tableau")) },
                    onEditDashboard = onEditDashboard,
                    onDuplicateDashboard = { srcId -> onEditDashboard(viewModel.duplicateDashboard(srcId, "(copie)")) },
                    onDeleteDashboard = { dashboardToDelete = it },
                    onImportCockpitFile = { showJsonImportChoice = true },
                    onImportCockpitQr = { showQrScan = true },
                    onReorderDashboards = viewModel::reorderDashboards,
                )
                SettingsSection.INSTRUMENTS -> InstrumentsSection(
                    anchorOffsets = anchorOffsets,
                    prefs = prefs,
                    hasBarometer = viewModel.hasBarometer,
                    onGaugeBezelStyle = viewModel::setGaugeBezelStyle,
                    onGaugeBezelColor = viewModel::setGaugeBezelColor,
                    onEfisShowValues = viewModel::setEfisShowValues,
                    onShowGestureHints = viewModel::setShowGestureHints,
                    onEfisResponsiveness = viewModel::setEfisResponsiveness,
                    onFocusDurationSec = viewModel::setFocusDurationSec,
                    onHeadingSource = viewModel::setEfisHeadingSource,
                    onCalibrationWatch = viewModel::setEfisCalibrationWatch,
                    onVarioSource = viewModel::setEfisVarioSource,
                    onEfisSpeedUnit = viewModel::setEfisSpeedUnit,
                    onAltitudeUnit = viewModel::setAltitudeUnit,
                    onGeoidRegion = viewModel::setGeoidRegion,
                    onGeoidCustomM = viewModel::setGeoidCustomM,
                    onFdrBufferMinutes = viewModel::setFdrBufferMinutes,
                    onFdrFlushMinutes = viewModel::setFdrFlushMinutes,
                    onMapOrientation = viewModel::setMapOrientation,
                    onMapShowZoomButtons = viewModel::setMapShowZoomButtons,
                    onSafeskyApiKey = viewModel::setSafeskyApiKey,
                )
                SettingsSection.AIRCRAFT -> AircraftSection(
                    aircraft = aircraft,
                    onAddAircraft = onAddAircraft,
                    onEditAircraft = onEditAircraft,
                    onDeleteAircraft = { aircraftToDelete = it },
                    onReordered = viewModel::reorderAircraft,
                    onExportAircraft = { a ->
                        exportForId = a.id
                        val safe = a.name.replace(Regex("[^A-Za-z0-9 _-]"), "_").trim().ifBlank { "appareil" }
                        exportLauncher.launch("$safe.json")
                    },
                    onImportAircraft = { a ->
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
                    anchorOffsets = anchorOffsets,
                    prefs = prefs,
                    aircraft = aircraft,
                    rowsFor = { viewModel.checklistRows(listOf(it)) },
                    onFontScale = viewModel::setFontScale,
                    onAddChecklist = onAddChecklist,
                    onAddChecklistSection = onAddChecklistSection,
                    onEditChecklist = onEditChecklist,
                    onDeleteChecklist = { checklistToDelete = it },
                    onReordered = viewModel::reorderChecklists,
                )
                SettingsSection.AIRPORTS -> AirportsSection(
                    charts = vacCharts,
                    airacCycle = prefs.vacAiracCycle,
                    onCycleChange = viewModel::setVacAiracCycle,
                    onAddVac = onAddVac,
                    onEditVac = onEditVac,
                    onDeleteVac = { vacToDelete = it },
                    onReordered = viewModel::reorderVac,
                )
                SettingsSection.DATA -> DataSection(
                    anchorOffsets = anchorOffsets,
                    charts = vacCharts,
                    airacCycle = prefs.vacAiracCycle,
                    vacProgress = vacProgress,
                    mapProgress = mapProgress,
                    mapInstalledTag = viewModel.mapInstalledTag(),
                    mapUpdateTag = mapUpdateTag,
                    onDownloadMaps = viewModel::downloadMaps,
                    onCheckMapUpdate = viewModel::checkMapUpdate,
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
                    onExport = {
                        datasetExport = true
                        datasetExportLauncher.launch("airdetente-dataset.json")
                    },
                    onImport = {
                        datasetImport = true
                        datasetImportLauncher.launch(arrayOf("application/json"))
                    },
                )
                SettingsSection.HELP -> com.airchecklists.app.ui.help.HelpBody()
                SettingsSection.DISCLAIMER -> {
                    com.airchecklists.app.ui.disclaimer.DisclaimerContent(
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }

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

/** APPLICATION tab: section "Démarrage" (splash + veille + thème) + section "Fermeture". */
@Composable
private fun ApplicationSection(
    anchorOffsets: androidx.compose.runtime.snapshots.SnapshotStateMap<SettingsAnchor, Int>,
    prefs: AppPreferences,
    onThemeMode: (ThemeMode) -> Unit,
    onSplashSeconds: (Int) -> Unit,
    onKeepScreenOn: (Boolean) -> Unit,
) {
    // ── Démarrage ─────────────────────────────────────────────────────────────
    AnchorBox(SettingsAnchor.APPEARANCE_SPLASH, anchorOffsets) {
    SectionHeader("Démarrage")

    SettingsTitre(stringResource(R.string.settings_splash))
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        listOf(0, 1, 2, 3).forEachIndexed { i, v ->
            SegmentedButton(
                selected = prefs.splashSeconds == v,
                onClick = { onSplashSeconds(v) },
                shape = SegmentedButtonDefaults.itemShape(index = i, count = 4),
            ) {
                Text(
                    if (v == 0) stringResource(R.string.settings_splash_off)
                    else stringResource(R.string.settings_splash_seconds, v),
                    maxLines = 1,
                )
            }
        }
    }
    } // AnchorBox APPEARANCE_SPLASH

    // Mise en veille de l'écran
    AnchorBox(SettingsAnchor.APPEARANCE_KEEP_SCREEN, anchorOffsets) {
    SettingsTitre("Mise en veille de l'écran")
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
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(start = 4.dp),
        )
    }
    } // AnchorBox APPEARANCE_KEEP_SCREEN

    // Thème de l'application
    AnchorBox(SettingsAnchor.APPEARANCE_THEME, anchorOffsets) {
    SettingsTitre("Thème de l'application")
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
    } // AnchorBox APPEARANCE_THEME

    // ── Fermeture ─────────────────────────────────────────────────────────────
    AnchorBox(SettingsAnchor.APPEARANCE_QUIT, anchorOffsets) {
    SectionHeader(stringResource(R.string.settings_quit_section))
    val quitCtx = androidx.compose.ui.platform.LocalContext.current
    var confirmQuit by remember { mutableStateOf(false) }
    OutlinedButton(onClick = { confirmQuit = true }, modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
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
    } // AnchorBox APPEARANCE_QUIT
}

/** COCKPITS tab: pager marker style/position + dashboards. */
@Composable
private fun CockpitsSection(
    anchorOffsets: androidx.compose.runtime.snapshots.SnapshotStateMap<SettingsAnchor, Int>,
    prefs: AppPreferences,
    onCockpitPagerStyle: (com.airchecklists.app.data.model.CockpitPagerStyle) -> Unit,
    onCockpitPagerPosition: (com.airchecklists.app.data.model.CockpitPagerPosition) -> Unit,
    onAddDashboard: () -> Unit,
    onEditDashboard: (String) -> Unit,
    onDuplicateDashboard: (String) -> Unit,
    onDeleteDashboard: (com.airchecklists.app.data.model.Dashboard) -> Unit,
    onImportCockpitFile: () -> Unit,
    onImportCockpitQr: () -> Unit,
    onReorderDashboards: (orderedIds: List<String>) -> Unit,
) {
    AnchorBox(SettingsAnchor.APPEARANCE_PAGER, anchorOffsets) {
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
    } // AnchorBox APPEARANCE_PAGER

    AnchorBox(SettingsAnchor.COCKPITS_DASHBOARDS, anchorOffsets) {
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
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedButton(onClick = onImportCockpitFile, modifier = Modifier.weight(1f)) {
            androidx.compose.material3.Icon(Icons.Filled.FileOpen, contentDescription = null)
            Text(stringResource(R.string.settings_dashboard_import_file), modifier = Modifier.padding(start = 8.dp))
        }
        OutlinedButton(onClick = onImportCockpitQr, modifier = Modifier.weight(1f)) {
            androidx.compose.material3.Icon(Icons.Filled.QrCodeScanner, contentDescription = null)
            Text(stringResource(R.string.settings_dashboard_import_qr), modifier = Modifier.padding(start = 8.dp))
        }
    }
    } // AnchorBox COCKPITS_DASHBOARDS
}

/** INSTRUMENTS tab: bezel, display options, sources, units, FDR, map orientation, safesky. */
@Composable
private fun InstrumentsSection(
    anchorOffsets: androidx.compose.runtime.snapshots.SnapshotStateMap<SettingsAnchor, Int>,
    prefs: AppPreferences,
    hasBarometer: Boolean,
    onGaugeBezelStyle: (com.airchecklists.app.data.model.GaugeBezelStyle) -> Unit,
    onGaugeBezelColor: (Long) -> Unit,
    onEfisShowValues: (Boolean) -> Unit,
    onShowGestureHints: (Boolean) -> Unit,
    onEfisResponsiveness: (Float) -> Unit,
    onFocusDurationSec: (Int) -> Unit,
    onHeadingSource: (EfisHeadingSource) -> Unit,
    onCalibrationWatch: (EfisCalibrationWatch) -> Unit,
    onVarioSource: (EfisVarioSource) -> Unit,
    onEfisSpeedUnit: (EfisSpeedUnit) -> Unit,
    onAltitudeUnit: (com.airchecklists.app.data.model.AltitudeUnit) -> Unit,
    onGeoidRegion: (com.airchecklists.app.data.model.GeoidRegion) -> Unit,
    onGeoidCustomM: (Float) -> Unit,
    onFdrBufferMinutes: (Int) -> Unit,
    onFdrFlushMinutes: (Int) -> Unit,
    onMapOrientation: (com.airchecklists.app.data.model.MapOrientation) -> Unit,
    onMapShowZoomButtons: (Boolean) -> Unit,
    onSafeskyApiKey: (String?) -> Unit,
) {
    // ── Paramètres d'affichage ────────────────────────────────────────────────
    AnchorBox(SettingsAnchor.INSTRUMENTS_DISPLAY, anchorOffsets) {
    SectionHeader(stringResource(R.string.settings_display_section))
    SettingsTitre(stringResource(R.string.settings_bezel))
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
    Text(
        stringResource(R.string.settings_bezel_color_hint),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 4.dp, bottom = 2.dp),
    )
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

    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

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
            Text(stringResource(R.string.settings_efis_show_values), style = MaterialTheme.typography.bodyLarge)
            Text(
                stringResource(R.string.settings_efis_show_values_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onShowGestureHints(!prefs.showGestureHints) },
    ) {
        androidx.compose.material3.Checkbox(
            checked = prefs.showGestureHints,
            onCheckedChange = { onShowGestureHints(it) },
        )
        Column(modifier = Modifier.padding(start = 4.dp)) {
            Text("Indicateurs de gestes", style = MaterialTheme.typography.bodyLarge)
            Text(
                "Affiche le trait (appui long) et les points (double tap) sur les instruments",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    Text(
        stringResource(R.string.settings_efis_sensitivity),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 8.dp),
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

    SettingsTitre(stringResource(R.string.settings_focus_section))
    Text(
        stringResource(R.string.settings_focus_duration),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text("${AppPreferences.FOCUS_DURATION_MIN_SEC}s", style = MaterialTheme.typography.labelSmall)
        Slider(
            value = prefs.focusDurationSec.toFloat(),
            onValueChange = { onFocusDurationSec(it.toInt()) },
            valueRange = AppPreferences.FOCUS_DURATION_MIN_SEC.toFloat()..AppPreferences.FOCUS_DURATION_MAX_SEC.toFloat(),
            steps = (AppPreferences.FOCUS_DURATION_MAX_SEC - AppPreferences.FOCUS_DURATION_MIN_SEC) / 5 - 1,
            modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
        )
        Text("${AppPreferences.FOCUS_DURATION_MAX_SEC}s", style = MaterialTheme.typography.labelSmall)
    }
    Text(
        stringResource(R.string.settings_focus_duration_value, prefs.focusDurationSec),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    } // AnchorBox INSTRUMENTS_DISPLAY

    // ── Sources de données ────────────────────────────────────────────────────
    AnchorBox(SettingsAnchor.INSTRUMENTS_SOURCES, anchorOffsets) {
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
    var showCalibrationDialog by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
    ) {
        Text(
            stringResource(R.string.settings_efis_calibration_watch),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedButton(onClick = { showCalibrationDialog = true }) {
            Text(stringResource(R.string.settings_efis_calibration_launch))
        }
    }
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        SegmentedButton(
            selected = prefs.efisCalibrationWatch == EfisCalibrationWatch.MAGNETIC_ONLY,
            onClick = { onCalibrationWatch(EfisCalibrationWatch.MAGNETIC_ONLY) },
            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
        ) { Text(stringResource(R.string.settings_efis_calibration_magnetic_only)) }
        SegmentedButton(
            selected = prefs.efisCalibrationWatch == EfisCalibrationWatch.ALWAYS,
            onClick = { onCalibrationWatch(EfisCalibrationWatch.ALWAYS) },
            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
        ) { Text(stringResource(R.string.settings_efis_calibration_always)) }
    }
    if (showCalibrationDialog) {
        CompassCalibrationDialog(
            driftDeg = 0f,
            onDismiss = { showCalibrationDialog = false },
            onCalibrated = { showCalibrationDialog = false },
        )
    }

    Text(
        stringResource(R.string.settings_efis_vario),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 8.dp),
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
    } // AnchorBox INSTRUMENTS_SOURCES

    // ── Unités ────────────────────────────────────────────────────────────────
    AnchorBox(SettingsAnchor.INSTRUMENTS_UNITS, anchorOffsets) {
    SectionHeader("Unités")
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
        modifier = Modifier.padding(top = 8.dp),
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
    Text(
        "Correction altitude GPS (géoïde)",
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 12.dp),
    )
    Text(
        "Corrige l'écart entre l'altitude WGS84 du GPS et l'altitude MSL des cartes aéronautiques",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    GeoidRegionDropdown(
        selected = prefs.geoidRegion,
        onSelect = onGeoidRegion,
    )
    if (prefs.geoidRegion == com.airchecklists.app.data.model.GeoidRegion.CUSTOM) {
        var customText by remember(prefs.geoidCustomM) {
            mutableStateOf(prefs.geoidCustomM.toInt().toString())
        }
        OutlinedTextField(
            value = customText,
            onValueChange = { v ->
                customText = v
                v.toFloatOrNull()?.let { onGeoidCustomM(it) }
            },
            label = { Text("Correction (mètres, négatif)") },
            singleLine = true,
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                keyboardType = androidx.compose.ui.text.input.KeyboardType.Number,
            ),
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        )
    }
    } // AnchorBox INSTRUMENTS_UNITS

    // ── Enregistreur de vol ───────────────────────────────────────────────────
    AnchorBox(SettingsAnchor.INSTRUMENTS_FDR, anchorOffsets) {
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
    } // AnchorBox INSTRUMENTS_FDR

    // ── Moving Map ────────────────────────────────────────────────────────────
    AnchorBox(SettingsAnchor.INSTRUMENTS_MAP, anchorOffsets) {
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
    } // AnchorBox INSTRUMENTS_MAP

    // ── Safesky ───────────────────────────────────────────────────────────────
    AnchorBox(SettingsAnchor.INSTRUMENTS_SAFESKY, anchorOffsets) {
    SectionHeader(stringResource(R.string.settings_safesky))
    Text(
        stringResource(R.string.settings_safesky_hint),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    SafeskyKeyField(value = prefs.safeskyApiKey ?: "", onChanged = { onSafeskyApiKey(it.ifBlank { null }) })
    } // AnchorBox INSTRUMENTS_SAFESKY
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

/** CHECKLISTS tab: font size + checklist list. */
@Composable
private fun ChecklistsSection(
    anchorOffsets: androidx.compose.runtime.snapshots.SnapshotStateMap<SettingsAnchor, Int>,
    prefs: AppPreferences,
    aircraft: List<Aircraft>,
    rowsFor: (Aircraft) -> List<ChecklistRow>,
    onFontScale: (Float) -> Unit,
    onAddChecklist: () -> Unit,
    onAddChecklistSection: () -> Unit,
    onEditChecklist: (aircraftId: String, checklistId: String) -> Unit,
    onDeleteChecklist: (ChecklistRow) -> Unit,
    onReordered: (aircraftId: String, orderedIds: List<String>) -> Unit,
) {
    AnchorBox(SettingsAnchor.APPEARANCE_FONT, anchorOffsets) {
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
    } // AnchorBox APPEARANCE_FONT

    AnchorBox(SettingsAnchor.CHECKLISTS_LIST, anchorOffsets) {
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
    } // AnchorBox CHECKLISTS_LIST
}

/** AIRPORTS tab: VAC list + add button, no download (downloads are in DATA). */
@Composable
private fun AirportsSection(
    charts: List<VacChart>,
    airacCycle: String,
    onCycleChange: (String) -> Unit,
    onAddVac: () -> Unit,
    onEditVac: (String) -> Unit,
    onDeleteVac: (VacChart) -> Unit,
    onReordered: (orderedIds: List<String>) -> Unit,
) {
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
}

/** DATA tab: map download, VAC download, sauvegarde/restauration. */
@Composable
private fun DataSection(
    anchorOffsets: androidx.compose.runtime.snapshots.SnapshotStateMap<SettingsAnchor, Int>,
    charts: List<VacChart>,
    airacCycle: String,
    vacProgress: VacDownloadProgress?,
    mapProgress: com.airchecklists.app.data.repository.MapDownloadProgress?,
    mapInstalledTag: String?,
    mapUpdateTag: String?,
    onDownloadMaps: () -> Unit,
    onCheckMapUpdate: () -> Unit,
    onDownloadVac: () -> Unit,
    onCheckVac: () -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit,
) {
    // ── Fond de Carte ─────────────────────────────────────────────────────────
    AnchorBox(SettingsAnchor.DATA_MAP, anchorOffsets) {
    SectionHeader(stringResource(R.string.settings_section_map_data))
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
    val downloadingMap = mapProgress != null
    OutlinedButton(onClick = onDownloadMaps, enabled = !downloadingMap, modifier = Modifier.fillMaxWidth().padding(top = 15.dp)) {
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
    OutlinedButton(onClick = onCheckMapUpdate, enabled = !downloadingMap, modifier = Modifier.fillMaxWidth()) {
        androidx.compose.material3.Icon(Icons.Filled.Refresh, contentDescription = null)
        Text(stringResource(R.string.settings_map_check_update), modifier = Modifier.padding(start = 8.dp))
    }
    } // AnchorBox DATA_MAP

    // ── Cartes VAC ────────────────────────────────────────────────────────────
    AnchorBox(SettingsAnchor.DATA_VAC, anchorOffsets) {
    SectionHeader(stringResource(R.string.settings_section_vac_data))
    val downloadingVac = vacProgress != null
    OutlinedButton(
        onClick = onDownloadVac,
        enabled = charts.isNotEmpty() && !downloadingVac,
        modifier = Modifier.fillMaxWidth().padding(top = 15.dp),
    ) {
        Icon(Icons.Filled.CloudDownload, null, Modifier.size(18.dp))
        Text(stringResource(R.string.settings_vac_download), Modifier.padding(start = 8.dp))
    }
    if (vacProgress != null) {
        val fraction = if (vacProgress.total == 0) 0f
        else vacProgress.done.toFloat() / vacProgress.total
        LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth())
        Text(
            text = stringResource(R.string.vac_download_progress, vacProgress.done, vacProgress.total),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    OutlinedButton(
        onClick = onCheckVac,
        enabled = charts.isNotEmpty() && !downloadingVac,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Icon(Icons.Filled.Refresh, null, Modifier.size(18.dp))
        Text(stringResource(R.string.settings_vac_check), Modifier.padding(start = 8.dp))
    }
    } // AnchorBox DATA_VAC

    // ── Sauvegarde / Restauration ─────────────────────────────────────────────
    AnchorBox(SettingsAnchor.DATA_BACKUP, anchorOffsets) {
    SectionHeader(stringResource(R.string.settings_section_backup))
    Text(
        stringResource(R.string.settings_data_hint),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    OutlinedButton(onClick = onExport, modifier = Modifier.fillMaxWidth().padding(top = 15.dp)) {
        Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(18.dp))
        Text(stringResource(R.string.settings_data_export), modifier = Modifier.padding(start = 8.dp))
    }
    OutlinedButton(onClick = onImport, modifier = Modifier.fillMaxWidth()) {
        Icon(Icons.Filled.Upload, contentDescription = null, modifier = Modifier.size(18.dp))
        Text(stringResource(R.string.settings_data_import), modifier = Modifier.padding(start = 8.dp))
    }
    } // AnchorBox DATA_BACKUP
}

@Composable
private fun AnchorBox(
    anchor: SettingsAnchor,
    offsets: androidx.compose.runtime.snapshots.SnapshotStateMap<SettingsAnchor, Int>,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier = Modifier.onGloballyPositioned { coords ->
        offsets[anchor] = coords.positionInRoot().y.toInt()
    }, content = content)
}

@Composable
private fun SectionHeader(text: String) {
    Column(modifier = Modifier.fillMaxWidth().padding(top = 24.dp, bottom = 5.dp)) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Text(
            text = text,
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold),
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(vertical = 8.dp),
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

@Composable
private fun SettingsTitre(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 10.dp, bottom = 4.dp),
    )
}

private fun Modifier.clickableRow(onClick: () -> Unit): Modifier =
    this.fillMaxWidth().clickable(onClick = onClick)

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

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun GeoidRegionDropdown(
    selected: com.airchecklists.app.data.model.GeoidRegion,
    onSelect: (com.airchecklists.app.data.model.GeoidRegion) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    androidx.compose.material3.ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
    ) {
        OutlinedTextField(
            value = selected.label,
            onValueChange = {},
            readOnly = true,
            trailingIcon = { androidx.compose.material3.ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.menuAnchor(androidx.compose.material3.MenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
        )
        if (expanded) {
            androidx.compose.material3.DropdownMenu(
                expanded = true,
                onDismissRequest = { expanded = false }
            ) {
                com.airchecklists.app.data.model.GeoidRegion.entries.forEach { region ->
                    DropdownMenuItem(
                        text = {
                            androidx.compose.foundation.layout.Row(
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(region.label)
                                if (region != com.airchecklists.app.data.model.GeoidRegion.CUSTOM && region != com.airchecklists.app.data.model.GeoidRegion.NONE) {
                                    Text(
                                        "${region.correctionM.toInt()} m",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        },
                        onClick = { onSelect(region); expanded = false },
                        trailingIcon = if (region == selected) {
                            { Icon(Icons.Filled.Check, null, tint = MaterialTheme.colorScheme.primary) }
                        } else null,
                    )
                }
            }
        }
    }
}
