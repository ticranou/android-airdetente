package com.airchecklists.app.ui.efis

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.airchecklists.app.data.sensors.EfisSensorProvider
import com.airchecklists.app.data.sensors.EfisState
import com.airchecklists.app.di.ServiceLocator
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

class EfisViewModel(context: Context) : ViewModel() {

    private val prefsRepo = ServiceLocator.preferences
    private val provider = ServiceLocator.efisProvider

    val state: StateFlow<EfisState> = provider.state

    /** GPS trail points [lat, lon] for the moving map (session only). */
    val trail: StateFlow<List<DoubleArray>> = provider.trail

    init {
        // Keep the provider's sources + responsiveness in sync with preferences.
        prefsRepo.preferences
            .onEach {
                provider.updateSources(it.efisHeadingSource, it.efisVarioSource)
                provider.updateResponsiveness(it.efisResponsiveness)
            }
            .launchIn(viewModelScope)
    }

    fun start() = provider.start()
    fun stop() = provider.stop()

    fun calibrateHorizon() = provider.calibrate()
    fun resetHorizon() = provider.resetCalibration()

    /** Speed arcs from the current aircraft (null if none/unset). */
    val speedArcs: com.airchecklists.app.data.model.SpeedArcs?
        get() {
            val aircraft = ServiceLocator.currentAircraft() ?: return null
            return com.airchecklists.app.data.model.SpeedArcs.from(aircraft).takeIf { it.hasAny }
        }

    /** Called after the location permission is granted. */
    fun onLocationPermissionGranted() = provider.startLocation()

    override fun onCleared() {
        provider.stop()
    }
}
