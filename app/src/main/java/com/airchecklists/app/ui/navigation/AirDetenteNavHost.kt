package com.airchecklists.app.ui.navigation

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.airchecklists.app.R
import com.airchecklists.app.di.ServiceLocator
import com.airchecklists.app.ui.aircraft.AircraftInfoScreen
import com.airchecklists.app.ui.checks.ChecksScreen
import com.airchecklists.app.ui.efis.EfisScreen
import com.airchecklists.app.ui.execution.ChecklistExecutionScreen
import com.airchecklists.app.ui.help.HelpScreen
import com.airchecklists.app.ui.settings.SettingsScreen
import com.airchecklists.app.ui.settings.aircraft.AircraftEditScreen
import com.airchecklists.app.ui.settings.checklist.ChecklistEditScreen
import com.airchecklists.app.ui.settings.vac.VacEditScreen
import com.airchecklists.app.ui.terrain.TerrainDetailScreen
import com.airchecklists.app.ui.terrain.weather.WeatherScreen
import com.airchecklists.app.ui.vac.VacScreen

private data class TabItem(
    val destination: Any,
    val labelRes: Int,
    val icon: ImageVector,
    val isCurrent: (androidx.navigation.NavDestination?) -> Boolean,
)

/** Hides or shows the system status/navigation bars (immersive mode). Restores
 *  them when this composable leaves the composition. */
@Composable
private fun ImmersiveSystemBars(hidden: Boolean) {
    val view = androidx.compose.ui.platform.LocalView.current
    androidx.compose.runtime.DisposableEffect(hidden) {
        val window = (view.context as? android.app.Activity)?.window
        val controller = window?.let { androidx.core.view.WindowCompat.getInsetsController(it, view) }
        controller?.let {
            it.systemBarsBehavior =
                androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            if (hidden) it.hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            else it.show(androidx.core.view.WindowInsetsCompat.Type.systemBars())
        }
        onDispose {
            controller?.show(androidx.core.view.WindowInsetsCompat.Type.systemBars())
        }
    }
}

@Composable
fun AirDetenteNavHost() {
    val navController = rememberNavController()

    val tabs = listOf(
        TabItem(Destinations.Checks, R.string.nav_checks, Icons.AutoMirrored.Filled.List) { it?.hasRoute(Destinations.Checks::class) == true },
        TabItem(Destinations.Efis, R.string.nav_efis, Icons.Filled.Dashboard) { it?.hasRoute(Destinations.Efis::class) == true },
        TabItem(Destinations.Vac, R.string.nav_vac, Icons.Filled.Map) { it?.hasRoute(Destinations.Vac::class) == true },
        TabItem(Destinations.Settings, R.string.nav_settings, Icons.Filled.Settings) { it?.hasRoute(Destinations.Settings::class) == true },
    )

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    val onChecks = currentDestination?.hasRoute(Destinations.Checks::class) == true
    val onVac = currentDestination?.hasRoute(Destinations.Vac::class) == true
    val onEfis = currentDestination?.hasRoute(Destinations.Efis::class) == true
    // The global bar + aircraft banner show on Checks, VAC and EFIS. Settings owns its own section bar.
    val showBottomBar = onChecks || onVac || onEfis
    val showBanner = onChecks || onVac || onEfis

    val aircraft by ServiceLocator.repository.aircraft.collectAsStateWithLifecycle()
    val currentId by ServiceLocator.currentAircraftId.collectAsStateWithLifecycle()
    val currentName = aircraft.firstOrNull { it.id == currentId }?.name
    val demoActive by ServiceLocator.efisProvider.demoActive.collectAsStateWithLifecycle()

    // Full-screen cockpit: hide header + tab bar (and the system bars). Only ever
    // active on the EFIS route; force it off elsewhere so other screens are normal.
    val fullscreen by ServiceLocator.cockpitFullscreen.collectAsStateWithLifecycle()
    androidx.compose.runtime.LaunchedEffect(onEfis) {
        if (!onEfis) ServiceLocator.cockpitFullscreen.value = false
    }
    val cockpitFullscreen = onEfis && fullscreen
    ImmersiveSystemBars(hidden = cockpitFullscreen)

    Scaffold(
        topBar = {
            if (showBanner && currentId != null && !cockpitFullscreen) {
                AircraftBanner(
                    name = currentName ?: "",
                    showCalibrate = onEfis,
                    demoActive = demoActive,
                    onInfo = { navController.navigate(Destinations.AircraftInfo(currentId!!)) },
                    onCalibrate = { ServiceLocator.efisProvider.calibrate() },
                    onResetCalibration = { ServiceLocator.efisProvider.resetCalibration() },
                    onToggleDemo = { ServiceLocator.efisProvider.toggleDemo() },
                )
            }
        },
        bottomBar = {
            if (showBottomBar && !cockpitFullscreen) {
                NavigationBar {
                    tabs.forEach { tab ->
                        val selected = tab.isCurrent(currentDestination)
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                navController.navigate(tab.destination) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(tab.icon, contentDescription = null) },
                            label = { Text(stringResource(tab.labelRes)) },
                        )
                    }
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Destinations.Checks,
        ) {
            composable<Destinations.Checks> {
                ChecksScreen(
                    contentPadding = innerPadding,
                    onOpenChecklist = { aircraftId, checklistId ->
                        navController.navigate(Destinations.ChecklistExecution(aircraftId, checklistId))
                    },
                )
            }
            composable<Destinations.Vac> {
                VacScreen(
                    contentPadding = innerPadding,
                    onOpenTerrain = { navController.navigate(Destinations.TerrainDetail(it)) },
                )
            }
            composable<Destinations.Efis> {
                EfisScreen(
                    contentPadding = innerPadding,
                    onOpenMap = { navController.navigate(Destinations.MapView) },
                )
            }
            composable<Destinations.MapView> {
                com.airchecklists.app.ui.map.MapScreen(
                    onBack = { navController.popBackStack() },
                    onOpenSettings = {
                        navController.navigate(Destinations.Settings) {
                            popUpTo(navController.graph.findStartDestination().id) { inclusive = false }
                            launchSingleTop = true
                        }
                    },
                )
            }
            composable<Destinations.AircraftInfo> { entry ->
                val route = entry.toRoute<Destinations.AircraftInfo>()
                AircraftInfoScreen(aircraftId = route.aircraftId, onBack = { navController.popBackStack() })
            }
            composable<Destinations.Settings> {
                SettingsScreen(
                    contentPadding = innerPadding,
                    onHome = {
                        navController.navigate(Destinations.Checks) {
                            popUpTo(navController.graph.findStartDestination().id) { inclusive = false }
                            launchSingleTop = true
                        }
                    },
                    onAddAircraft = { navController.navigate(Destinations.AircraftEdit()) },
                    onEditAircraft = { navController.navigate(Destinations.AircraftEdit(it)) },
                    onAddChecklist = { navController.navigate(Destinations.ChecklistEdit()) },
                    onAddChecklistSection = {
                        navController.navigate(Destinations.ChecklistEdit(isSection = true))
                    },
                    onEditChecklist = { aircraftId, checklistId ->
                        navController.navigate(Destinations.ChecklistEdit(aircraftId, checklistId))
                    },
                    onAddVac = { navController.navigate(Destinations.VacEdit()) },
                    onEditVac = { navController.navigate(Destinations.VacEdit(it)) },
                    onHelp = { navController.navigate(Destinations.Help) },
                    onEditDashboard = { navController.navigate(Destinations.DashboardEdit(it)) },
                )
            }
            composable<Destinations.Help> {
                HelpScreen(onBack = { navController.popBackStack() })
            }
            composable<Destinations.DashboardEdit> { entry ->
                val route = entry.toRoute<Destinations.DashboardEdit>()
                com.airchecklists.app.ui.settings.dashboard.DashboardEditScreen(
                    dashboardId = route.dashboardId,
                    onBack = { navController.popBackStack() },
                )
            }
            composable<Destinations.ChecklistExecution> { entry ->
                val route = entry.toRoute<Destinations.ChecklistExecution>()
                ChecklistExecutionScreen(
                    aircraftId = route.aircraftId,
                    checklistId = route.checklistId,
                    onHome = {
                        navController.navigate(Destinations.Checks) {
                            popUpTo(navController.graph.findStartDestination().id) { inclusive = false }
                            launchSingleTop = true
                        }
                    },
                    onBackToChecklists = {
                        navController.popBackStack(Destinations.Checks, inclusive = false)
                    },
                    onOpenNext = { nextChecklistId ->
                        navController.navigate(
                            Destinations.ChecklistExecution(route.aircraftId, nextChecklistId)
                        ) {
                            popUpTo(Destinations.ChecklistExecution(route.aircraftId, route.checklistId)) {
                                inclusive = true
                            }
                        }
                    },
                )
            }
            composable<Destinations.AircraftEdit> { entry ->
                val route = entry.toRoute<Destinations.AircraftEdit>()
                AircraftEditScreen(
                    aircraftId = route.aircraftId,
                    onDone = { navController.popBackStack() },
                )
            }
            composable<Destinations.ChecklistEdit> { entry ->
                val route = entry.toRoute<Destinations.ChecklistEdit>()
                ChecklistEditScreen(
                    aircraftId = route.aircraftId,
                    checklistId = route.checklistId,
                    isSection = route.isSection,
                    onDone = { navController.popBackStack() },
                )
            }
            composable<Destinations.VacEdit> { entry ->
                val route = entry.toRoute<Destinations.VacEdit>()
                VacEditScreen(
                    vacId = route.vacId,
                    onDone = { navController.popBackStack() },
                )
            }
            composable<Destinations.TerrainDetail> { entry ->
                val route = entry.toRoute<Destinations.TerrainDetail>()
                TerrainDetailScreen(
                    vacId = route.vacId,
                    onBack = { navController.popBackStack() },
                    onOpenWeather = { navController.navigate(Destinations.Weather(route.vacId)) },
                )
            }
            composable<Destinations.Weather> { entry ->
                val route = entry.toRoute<Destinations.Weather>()
                WeatherScreen(
                    vacId = route.vacId,
                    onBack = { navController.popBackStack() },
                )
            }
        }
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun AircraftBanner(
    name: String,
    showCalibrate: Boolean,
    demoActive: Boolean,
    onInfo: () -> Unit,
    onCalibrate: () -> Unit,
    onResetCalibration: () -> Unit,
    onToggleDemo: () -> Unit,
) {
    var confirmCalibrate by remember { mutableStateOf(false) }
    androidx.compose.material3.TopAppBar(
        title = {
            // Long-press the aircraft name to toggle the EFIS demo flight.
            Box(
                modifier = Modifier.pointerInput(Unit) {
                    detectTapGestures(onLongPress = { onToggleDemo() })
                },
            ) {
                Text(if (demoActive) "$name • DÉMO" else name)
            }
        },
        actions = {
            androidx.compose.material3.IconButton(onClick = onInfo) {
                Icon(Icons.Filled.Info, contentDescription = stringResource(R.string.aircraft_info))
            }
            if (showCalibrate) {
                // Long-press = calibrate horizon (deliberate, avoids accidental
                // calibration in flight). A short tap does nothing.
                Box(
                    modifier = Modifier
                        .padding(end = 4.dp)
                        .size(40.dp)
                        .pointerInput(Unit) {
                            detectTapGestures(onLongPress = { confirmCalibrate = true })
                        },
                    contentAlignment = androidx.compose.ui.Alignment.Center,
                ) {
                    Icon(
                        Icons.Filled.Explore,
                        contentDescription = stringResource(R.string.efis_calibrate),
                        tint = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        colors = androidx.compose.material3.TopAppBarDefaults.topAppBarColors(
            // Match the bottom NavigationBar's background (Material surfaceContainer)
            // so the header and footer share the same colour.
            containerColor = androidx.compose.material3.MaterialTheme.colorScheme.surfaceContainer,
            titleContentColor = androidx.compose.material3.MaterialTheme.colorScheme.onSurface,
            actionIconContentColor = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
        ),
    )

    if (confirmCalibrate) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { confirmCalibrate = false },
            title = { Text(stringResource(R.string.efis_calibrate_confirm_title)) },
            text = { Text(stringResource(R.string.efis_calibrate_confirm_message)) },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    confirmCalibrate = false
                    onCalibrate()
                }) { Text(stringResource(R.string.efis_calibrate_confirm_ok)) }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { confirmCalibrate = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}
