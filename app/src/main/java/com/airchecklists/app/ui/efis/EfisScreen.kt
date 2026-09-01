package com.airchecklists.app.ui.efis

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.airchecklists.app.R
import com.airchecklists.app.data.model.Dashboard
import com.airchecklists.app.data.model.EfisInstrument
import com.airchecklists.app.data.sensors.EfisState
import com.airchecklists.app.di.ServiceLocator
import com.airchecklists.app.ui.efis.gauges.InstrumentSlot
import com.airchecklists.app.ui.simpleViewModelFactory

@Composable
fun EfisScreen(contentPadding: PaddingValues, onOpenMap: () -> Unit = {}) {
    val context = LocalContext.current
    val viewModel: EfisViewModel = viewModel(factory = simpleViewModelFactory { EfisViewModel(context) })
    val state by viewModel.state.collectAsStateWithLifecycle()
    val prefs by ServiceLocator.preferences.preferences.collectAsStateWithLifecycle()
    val trail by viewModel.trail.collectAsStateWithLifecycle()

    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> if (granted) viewModel.onLocationPermissionGranted() }

    LaunchedEffect(Unit) { permLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        viewModel.start()
        // Note: the flight recorder is NOT tied to this screen — it runs with the
        // foreground FlightService (app lifetime) so it keeps capturing in background.
        val obs = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    viewModel.start()
                }
                Lifecycle.Event.ON_PAUSE -> {
                    viewModel.stop()
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(obs)
            viewModel.stop()
        }
    }

    val cockpitDashboards = prefs.effectiveDashboards.filter { it.showInCockpit }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(
                top = contentPadding.calculateTopPadding(),
                bottom = contentPadding.calculateBottomPadding(),
            ),
    ) {
        if (cockpitDashboards.isEmpty()) {
            Text(
                text = "Aucun tableau de bord à afficher.\n\nDans Réglages ▸ Instruments, créez un tableau de bord " +
                    "et cochez « Afficher dans le Cockpit ».",
                color = Color(0xCCFFFFFF),
                fontSize = 15.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.align(Alignment.Center).padding(32.dp),
            )
            return@Box
        }

        val pagerState = rememberPagerState(pageCount = { cockpitDashboards.size })
        HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
            DashboardGrid(
                dashboard = cockpitDashboards[page],
                state = state,
                speedUnit = prefs.efisSpeedUnit,
                showValues = prefs.efisShowValues,
                speedArcs = viewModel.speedArcs,
                trail = trail,
                mapOrientation = prefs.mapOrientation,
                altUnit = prefs.altitudeUnit,
                focusDurationSec = prefs.focusDurationSec,
                onOpenMap = onOpenMap,
            )
        }

        // Page indicator (only when several dashboards). Style + position are
        // user-configurable in Réglages ▸ Cockpits. Aligned to the right so the
        // markers don't sit under the left-aligned cockpit title.
        if (cockpitDashboards.size > 1) {
            val padTop = prefs.cockpitPagerPosition == com.airchecklists.app.data.model.CockpitPagerPosition.TOP
            val alignment = if (padTop) Alignment.TopEnd else Alignment.BottomEnd
            CockpitPageIndicator(
                style = prefs.cockpitPagerStyle,
                count = cockpitDashboards.size,
                current = pagerState.currentPage,
                modifier = Modifier
                    .align(alignment)
                    // At the top, leave room for the full-screen button (also TopEnd).
                    .padding(top = if (padTop) 10.dp else 0.dp, bottom = if (padTop) 0.dp else 8.dp, end = if (padTop) 48.dp else 10.dp),
            )
        }

        if (!state.hasFix) {
            Text(
                text = "Instrument non certifié • sans fix GPS",
                color = Color(0xAAFFFFFF),
                fontSize = 10.sp,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 4.dp),
            )
        }

        // Full-screen toggle (hides header + tab bar + system bars).
        val fullscreen by ServiceLocator.cockpitFullscreen.collectAsStateWithLifecycle()
        IconButton(
            onClick = { ServiceLocator.cockpitFullscreen.value = !fullscreen },
            modifier = Modifier.align(Alignment.TopEnd).padding(4.dp),
        ) {
            Icon(
                imageVector = if (fullscreen) Icons.Filled.FullscreenExit else Icons.Filled.Fullscreen,
                contentDescription = if (fullscreen) "Quitter le plein écran" else "Plein écran",
                tint = Color(0xCCFFFFFF),
            )
        }
    }
}

/** Cockpit page marker: dots, bars, or "n / total" text, per user preference. */
@Composable
private fun CockpitPageIndicator(
    style: com.airchecklists.app.data.model.CockpitPagerStyle,
    count: Int,
    current: Int,
    modifier: Modifier = Modifier,
) {
    val active = Color.White
    val inactive = Color(0x66FFFFFF)
    when (style) {
        com.airchecklists.app.data.model.CockpitPagerStyle.DOTS ->
            Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                repeat(count) { i ->
                    val on = i == current
                    Box(
                        modifier = Modifier
                            .size(if (on) 9.dp else 7.dp)
                            .clip(CircleShape)
                            .background(if (on) active else inactive),
                    )
                }
            }
        com.airchecklists.app.data.model.CockpitPagerStyle.BARS ->
            Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                repeat(count) { i ->
                    val on = i == current
                    Box(
                        modifier = Modifier
                            .height(4.dp)
                            .width(if (on) 22.dp else 12.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(if (on) active else inactive),
                    )
                }
            }
        com.airchecklists.app.data.model.CockpitPagerStyle.NUMBERS ->
            Box(
                modifier = modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0x66000000))
                    .padding(horizontal = 10.dp, vertical = 3.dp),
            ) {
                Text(
                    text = "${current + 1} / $count",
                    color = active,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
    }
}

/** Renders one dashboard's 2×N grid, placing each master cell as a block that
 *  spans its colSpan×rowSpan; covered/empty cells render nothing. */
@Composable
private fun DashboardGrid(
    dashboard: Dashboard,
    state: EfisState,
    speedUnit: com.airchecklists.app.data.model.EfisSpeedUnit,
    showValues: Boolean,
    speedArcs: com.airchecklists.app.data.model.SpeedArcs?,
    trail: List<DoubleArray>,
    mapOrientation: com.airchecklists.app.data.model.MapOrientation,
    altUnit: com.airchecklists.app.data.model.AltitudeUnit,
    focusDurationSec: Int,
    onOpenMap: () -> Unit,
) {
    val cols = com.airchecklists.app.data.model.EFIS_COLS
    val rows = dashboard.rows
    val cells = dashboard.normalizedCells
    val title = dashboard.name.trim()

    // Focus mode state: index of the focused cell (-1 = none).
    var focusCellIdx by remember { mutableIntStateOf(-1) }

    Column(modifier = Modifier.fillMaxSize()) {
        // Reserved title band at the top of the cockpit (does not overlap instruments).
        // Left-aligned; the page markers are drawn on the right by the caller. Shown only
        // when enabled per-cockpit in Réglages ▸ Cockpits.
        if (dashboard.showTitle && title.isNotEmpty()) {
            Text(
                text = title,
                color = Color(0xE6FFFFFF),
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF0A0A0A))
                    // Leave room on the right for the full-screen button + page markers.
                    .padding(start = 12.dp, end = 96.dp, top = 5.dp, bottom = 5.dp),
            )
        }
        BoxWithConstraints(modifier = Modifier.fillMaxWidth().weight(1f)) {
            val cellW = maxWidth / cols
            val cellH = maxHeight / rows
            cells.forEachIndexed { i, cell ->
                if (cell.covered || cell.instrument == EfisInstrument.NONE) return@forEachIndexed
                val row = i / cols
                val col = i % cols
                val canFocus = cell.instrument != EfisInstrument.NAV_PLANNER
                Box(
                    modifier = Modifier
                        .offset(x = cellW * col, y = cellH * row)
                        .width(cellW * cell.colSpan)
                        .height(cellH * cell.rowSpan)
                        .then(
                            if (canFocus) Modifier.pointerInput(Unit) {
                                // Detect a pinch (2 fingers moving apart) without consuming
                                // single-finger events so the HorizontalPager can still scroll.
                                awaitPointerEventScope {
                                    while (true) {
                                        // Wait for any down event (Initial pass = before children).
                                        val event = awaitPointerEvent(PointerEventPass.Initial)
                                        val pressed = event.changes.filter { it.pressed }
                                        if (pressed.size < 2) continue
                                        // Two fingers down: measure initial distance.
                                        val p1 = pressed[0].position
                                        val p2 = pressed[1].position
                                        val initDist = (p2 - p1).getDistance().coerceAtLeast(1f)
                                        // Track until both fingers lift or scale threshold reached.
                                        var triggered = false
                                        loop@ while (true) {
                                            val ev = awaitPointerEvent(PointerEventPass.Initial)
                                            val cur = ev.changes.filter { it.pressed }
                                            if (cur.size < 2) break@loop
                                            val c1 = cur[0].position
                                            val c2 = cur[1].position
                                            val dist = (c2 - c1).getDistance()
                                            if (dist / initDist > 1.3f) {
                                                triggered = true
                                                // Drain until all fingers lift.
                                                while (true) {
                                                    val drain = awaitPointerEvent(PointerEventPass.Initial)
                                                    if (drain.changes.all { !it.pressed }) break
                                                }
                                                break@loop
                                            }
                                        }
                                        if (triggered) focusCellIdx = i
                                    }
                                }
                            } else Modifier,
                        ),
                ) {
                    InstrumentSlot(
                        instrument = cell.instrument,
                        state = state,
                        speedUnit = speedUnit,
                        showValues = showValues,
                        speedArcs = speedArcs,
                        altUnit = altUnit,
                        trail = trail,
                        mapOrientation = mapOrientation,
                        accentColor = cell.accentColor,
                        bezelStyleOverride = cell.bezelStyle,
                        onOpenMap = onOpenMap,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }

    // Focus mode dialog.
    if (focusCellIdx in cells.indices) {
        val focusCell = cells[focusCellIdx]
        if (!focusCell.covered && focusCell.instrument != EfisInstrument.NONE) {
            FocusDialog(
                instrument = focusCell.instrument,
                accentColor = focusCell.accentColor,
                bezelStyleOverride = focusCell.bezelStyle,
                state = state,
                speedUnit = speedUnit,
                showValues = showValues,
                speedArcs = speedArcs,
                altUnit = altUnit,
                trail = trail,
                mapOrientation = mapOrientation,
                focusDurationSec = focusDurationSec,
                onDismiss = { focusCellIdx = -1 },
            )
        }
    }
}

/** Full-screen focus dialog for a single instrument with a countdown auto-close button. */
@Composable
private fun FocusDialog(
    instrument: EfisInstrument,
    accentColor: Long?,
    bezelStyleOverride: com.airchecklists.app.data.model.GaugeBezelStyle?,
    state: EfisState,
    speedUnit: com.airchecklists.app.data.model.EfisSpeedUnit,
    showValues: Boolean,
    speedArcs: com.airchecklists.app.data.model.SpeedArcs?,
    altUnit: com.airchecklists.app.data.model.AltitudeUnit,
    trail: List<DoubleArray>,
    mapOrientation: com.airchecklists.app.data.model.MapOrientation,
    focusDurationSec: Int,
    onDismiss: () -> Unit,
) {
    var remaining by remember(focusDurationSec) { mutableIntStateOf(focusDurationSec) }

    // Countdown ticker.
    LaunchedEffect(focusDurationSec) {
        while (remaining > 0) {
            kotlinx.coroutines.delay(1000L)
            remaining--
        }
        onDismiss()
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnClickOutside = true,
            decorFitsSystemWindows = false,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            InstrumentSlot(
                instrument = instrument,
                state = state,
                speedUnit = speedUnit,
                showValues = showValues,
                speedArcs = speedArcs,
                altUnit = altUnit,
                trail = trail,
                mapOrientation = mapOrientation,
                accentColor = accentColor,
                bezelStyleOverride = bezelStyleOverride,
                modifier = Modifier.fillMaxWidth().weight(1f),
            )
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF222222)),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = stringResource(R.string.settings_focus_close, remaining),
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}