package com.airchecklists.app

import android.app.Application
import com.airchecklists.app.di.ServiceLocator
import org.maplibre.android.MapLibre

class AirDetenteApp : Application() {
    override fun onCreate() {
        super.onCreate()
        ServiceLocator.init(this)
        // Initialize MapLibre once (no API key needed for local/self-hosted tiles).
        MapLibre.getInstance(this)
    }
}
