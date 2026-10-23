package com.airchecklists.app

import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.airchecklists.app.di.ServiceLocator
import com.airchecklists.app.ui.efis.gauges.compact.LocalShowGestureHints
import com.airchecklists.app.ui.navigation.AirDetenteNavHost
import com.airchecklists.app.ui.select.AircraftSelectScreen
import com.airchecklists.app.ui.splash.SplashScreen
import com.airchecklists.app.ui.theme.AirDetenteTheme
import com.airchecklists.app.ui.theme.LocalChecklistFontScale
import kotlinx.coroutines.launch

private enum class AppPhase { SPLASH, DISCLAIMER, COMPAT, SELECT, APP }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // Keep-alive: start the always-on foreground service so the OS is very
        // unlikely to reclaim the app during a flight.
        com.airchecklists.app.data.service.FlightService.start(this)
        // Expose this Activity so instruments can host a transient off-screen
        // ComposeView for the flight-summary screenshot (resume.jpg).
        com.airchecklists.app.di.ServiceLocator.currentActivity = this
        // A "Quitter" tap (in-app or from the notification) routes here.
        if (intent?.action == ACTION_QUIT_APP) { quit(); return }
        setContent {
            val prefs by ServiceLocator.preferences.preferences.collectAsStateWithLifecycle()
            val aircraft by ServiceLocator.repository.aircraft.collectAsStateWithLifecycle()

            // Ask for notification permission (Android 13+) so the "vol en cours"
            // foreground-service notification is visible.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val notifLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
                    androidx.activity.result.contract.ActivityResultContracts.RequestPermission(),
                ) { /* granted or not — the service runs either way */ }
                androidx.compose.runtime.LaunchedEffect(Unit) {
                    notifLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                }
            }

            // Keep the screen on while the pref is enabled.
            androidx.compose.runtime.LaunchedEffect(prefs.keepScreenOn) {
                if (prefs.keepScreenOn) {
                    window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                } else {
                    window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                }
            }
            val splashEnabled = remember { ServiceLocator.preferences.preferences.value.splashSeconds > 0 }
            val disclaimerAccepted = remember { ServiceLocator.preferences.preferences.value.disclaimerAccepted }
            val caps = remember { ServiceLocator.capabilities }
            // Show the compat warning once, when a flight-relevant sensor is missing
            // and the user hasn't dismissed it for good.
            val compatWarningDismissed = remember { ServiceLocator.preferences.preferences.value.compatWarningDismissed }
            val needsCompatWarning = remember {
                !compatWarningDismissed && (!caps.hasOrientation || !caps.hasBarometer || !caps.hasGps)
            }
            // Phase reached after the disclaimer: compat warning (if needed) then select.
            val afterDisclaimer = if (needsCompatWarning) AppPhase.COMPAT else AppPhase.SELECT
            // First real screen after the (optional) splash: the disclaimer if it
            // hasn't been accepted yet, otherwise the compat warning / aircraft selection.
            val postSplashPhase = if (disclaimerAccepted) afterDisclaimer else AppPhase.DISCLAIMER
            var phase by remember {
                mutableStateOf(if (splashEnabled) AppPhase.SPLASH else postSplashPhase)
            }
            val scope = androidx.compose.runtime.rememberCoroutineScope()

            AirDetenteTheme(themeMode = prefs.themeMode) {
                CompositionLocalProvider(
                    LocalChecklistFontScale provides prefs.fontScale,
                    LocalShowGestureHints provides prefs.showGestureHints,
                ) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background,
                    ) {
                        when (phase) {
                            AppPhase.SPLASH -> SplashScreen(
                                durationSeconds = prefs.splashSeconds,
                                onFinished = { phase = postSplashPhase },
                            )
                            AppPhase.DISCLAIMER -> com.airchecklists.app.ui.disclaimer.DisclaimerScreen(
                                onAccept = { dontShowAgain ->
                                    if (dontShowAgain) {
                                        scope.launch { ServiceLocator.preferences.setDisclaimerAccepted(true) }
                                    }
                                    phase = afterDisclaimer
                                },
                            )
                            AppPhase.COMPAT -> com.airchecklists.app.ui.compat.CompatibilityScreen(
                                caps = caps,
                                onContinue = { dontShowAgain ->
                                    if (dontShowAgain) {
                                        scope.launch { ServiceLocator.preferences.setCompatWarningDismissed(true) }
                                    }
                                    phase = AppPhase.SELECT
                                },
                            )
                            AppPhase.SELECT -> {
                                // Auto-select when there's a single aircraft; otherwise let the user pick.
                                if (aircraft.size == 1) {
                                    ServiceLocator.currentAircraftId.value = aircraft.first().id
                                    phase = AppPhase.APP
                                } else if (aircraft.isEmpty()) {
                                    // No aircraft yet — enter the app anyway (Settings can create one).
                                    phase = AppPhase.APP
                                } else {
                                    AircraftSelectScreen(onSelected = {
                                        ServiceLocator.currentAircraftId.value = it
                                        phase = AppPhase.APP
                                    })
                                }
                            }
                            AppPhase.APP -> {
                                AirDetenteNavHost()
                                DataUpdateAlert()
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (com.airchecklists.app.di.ServiceLocator.currentActivity === this) {
            com.airchecklists.app.di.ServiceLocator.currentActivity = null
        }
        ServiceLocator.updateInstruments {
            it.copy(
                checklistChecked = emptyMap(),
                actionDone = emptyMap(),
                engineStartMs = null,
                engineStopMs = null,
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.action == ACTION_QUIT_APP) quit()
    }

    /** Stop the foreground service and close the app cleanly (leaves recents). */
    private fun quit() {
        com.airchecklists.app.data.service.FlightService.stop(this)
        finishAndRemoveTask()
    }

    companion object {
        const val ACTION_QUIT_APP = "com.airchecklists.app.QUIT_APP"
    }
}

/** One-shot alert shown when the startup check found newer data (map and/or VAC charts). */
@Composable
private fun DataUpdateAlert() {
    val mapTag by ServiceLocator.mapRepository.updateTag.collectAsStateWithLifecycle()
    val vacCount by ServiceLocator.vacOutdatedCount.collectAsStateWithLifecycle()
    var dismissed by remember { mutableStateOf(false) }
    val hasUpdate = mapTag != null || vacCount > 0
    if (hasUpdate && !dismissed) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { dismissed = true },
            title = { androidx.compose.material3.Text("Mises à jour disponibles") },
            text = {
                Column {
                    if (mapTag != null)
                        androidx.compose.material3.Text("• Fond de carte : version $mapTag disponible.")
                    if (vacCount > 0)
                        androidx.compose.material3.Text(
                            "• Cartes VAC : $vacCount carte${if (vacCount > 1) "s" else ""} obsolète${if (vacCount > 1) "s" else ""}.",
                        )
                    Spacer(modifier = Modifier.height(8.dp))
                    androidx.compose.material3.Text(
                        "Ouvrez Réglages ▸ Données pour télécharger les mises à jour.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = { dismissed = true }) {
                    androidx.compose.material3.Text("OK")
                }
            },
        )
    }
}
