package com.airchecklists.app.ui.efis.gauges.flight

import com.airchecklists.app.data.local.FlightLogExporter
import com.airchecklists.app.data.local.buildFlightSummary
import com.airchecklists.app.data.local.captureComposableJpeg
import com.airchecklists.app.di.ServiceLocator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Builds and writes the full flight-session ZIP (trace.gpx + trace.kml + log.csv +
 * resume.jpg) to Downloads. The resume.jpg is a render of [FlightSummaryCard] for the
 * current session; if the capture can't be produced (no foreground Activity), the ZIP
 * is still written without it.
 *
 * [onResult] is invoked on the main thread with the ZIP file name, or null on failure.
 */
fun exportFlightSessionZip(scope: CoroutineScope, onResult: (String?) -> Unit) {
    scope.launch {
        val name = withContext(Dispatchers.Default) {
            val summary = buildFlightSummary()
            val activity = ServiceLocator.currentActivity
            val jpeg = if (activity != null) {
                captureComposableJpeg(activity) { FlightSummaryCard(summary) }
            } else null
            val track = ServiceLocator.flightRecorder.snapshotTrack()
            val samples = ServiceLocator.flightRecorder.snapshotSamples()
            FlightLogExporter.exportSessionZip(ServiceLocator.appContext, track, samples, jpeg)
        }
        onResult(name)
    }
}
