package com.airchecklists.app.data.sensors

import android.annotation.SuppressLint
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import com.airchecklists.app.data.local.FlightRecorderStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** One recorded sample. [kind] tells how to read v1..v3 (see [FdrKind]). */
data class FdrSample(
    val tMs: Long,
    val kind: FdrKind,
    val v1: Double? = null,
    val v2: Double? = null,
    val v3: Double? = null,
)

/** Sample kinds + their v1..v3 meaning. */
enum class FdrKind {
    GPS,    // v1=lat, v2=lon, v3=speedKmh
    ALT,    // v1=altitudeFt
    ACCEL,  // v1=ax, v2=ay, v3=az (m/s²)
    GYRO,   // v1=gx, v2=gy, v3=gz (rad/s)
    BARO,   // v1=hPa
}

/** The recorder's live status, drives the instrument UI. */
data class FdrStatus(
    val recording: Boolean = false,   // actively capturing (started AND not paused)
    val paused: Boolean = false,
    val hasGps: Boolean = false,
    val hasAccel: Boolean = false,
    val hasGyro: Boolean = false,
    val hasBaro: Boolean = false,
    val sampleCount: Int = 0,
)

/**
 * Rolling flight recorder: captures raw GPS (1 Hz), accelerometer + gyroscope
 * (~20 Hz), pressure (~5 Hz) into bounded in-memory ring buffers holding the last
 * [bufferMinutes] minutes, and flushes them to disk every [flushMinutes] minutes.
 *
 * Independent from EfisSensorProvider (which only exposes fused scalars). Tied to
 * the cockpit screen lifecycle by the caller: [start]/[stop]. Not certified.
 */
class FlightRecorder(
    context: Context,
    private val store: FlightRecorderStore,
    private val caps: DeviceCapabilities,
    // Position source: the app's fused EfisState (real GPS in flight, scripted in
    // demo mode). Used only while demo is active OR as a fallback before our own
    // GPS listener has produced a fix — see positionJob.
    private val positionProvider: () -> EfisState,
    // True when the EFIS demo is running. In demo we take the scripted EfisState so
    // the recorded track matches the moving map; otherwise we use our own GPS below.
    private val isDemo: () -> Boolean,
) {
    private val appContext = context.applicationContext
    private val sensorManager = appContext.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val locationManager = appContext.getSystemService(Context.LOCATION_SERVICE) as LocationManager

    private val accel = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val gyro = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
    private val pressure = sensorManager.getDefaultSensor(Sensor.TYPE_PRESSURE)

    // Rolling buffer (single deque, time-ordered). Trimmed by age on each insert.
    private val buffer = ArrayDeque<FdrSample>()
    // Session-long GPS + ~1 Hz altitude track (never purged) → full-flight GPX.
    private val trackBuffer = ArrayDeque<FdrSample>()
    private var lastTrackAltMs = 0L
    private val lock = Any()

    @Volatile private var started = false
    @Volatile private var paused = false
    @Volatile var bufferMinutes: Int = 10
    @Volatile var flushMinutes: Int = 2

    // Our own GPS fix (independent of the screen-bound EfisSensorProvider), so the
    // recorder keeps a live track when the cockpit screen is backgrounded.
    @Volatile private var lastGpsLat = 0.0
    @Volatile private var lastGpsLon = 0.0
    @Volatile private var lastGpsAltFt = 0.0
    @Volatile private var lastGpsSpeedKmh = 0.0
    @Volatile private var lastGpsMs = 0L

    private var flushJob: Job? = null
    private var positionJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Default + Job())

    private val _status = MutableStateFlow(
        FdrStatus(
            hasGps = caps.hasGps,
            hasAccel = caps.hasAccelerometer,
            hasGyro = caps.hasGyroscope,
            hasBaro = caps.hasBarometer,
        ),
    )
    val status: StateFlow<FdrStatus> = _status.asStateFlow()

    // Flush-cycle timing (for the UI progress ring): when the current cycle started
    // and how long it lasts. flushProgress() = elapsed / interval, clamped 0..1.
    @Volatile private var flushCycleStartMs = 0L
    private fun flushIntervalMs() = flushMinutes.coerceAtLeast(1) * 60_000L

    /** 0f right after a disk write → 1f just before the next one (0 when stopped). */
    fun flushProgress(): Float {
        if (!started || flushCycleStartMs == 0L) return 0f
        val elapsed = System.currentTimeMillis() - flushCycleStartMs
        return (elapsed.toFloat() / flushIntervalMs()).coerceIn(0f, 1f)
    }

    fun setPausedRestored(p: Boolean) { paused = p; publish() }

    fun start() {
        if (started) return
        started = true
        flushCycleStartMs = System.currentTimeMillis()
        accel?.let { sensorManager.registerListener(sensorListener, it, SensorManager.SENSOR_DELAY_GAME) }
        gyro?.let { sensorManager.registerListener(sensorListener, it, SensorManager.SENSOR_DELAY_GAME) }
        pressure?.let { sensorManager.registerListener(sensorListener, it, SensorManager.SENSOR_DELAY_NORMAL) }
        startLocation()
        // Position at ~1 Hz. In demo we take the fused EfisState (scripted track); in
        // real flight we take our own GPS listener (keeps recording when the cockpit
        // screen is backgrounded), falling back to EfisState if no fix yet.
        positionJob = scope.launch {
            while (started) {
                val fix = readPosition()
                if (fix != null) {
                    add(FdrKind.GPS, fix.lat, fix.lon, fix.speedKmh)
                    add(FdrKind.ALT, fix.altFt)
                }
                delay(1000L)
            }
        }
        flushJob = scope.launch {
            while (started) {
                delay(flushIntervalMs())
                flush()
                flushCycleStartMs = System.currentTimeMillis()
            }
        }
        publish()
    }

    /** A single position sample, from whichever source is authoritative right now. */
    private data class Fix(val lat: Double, val lon: Double, val altFt: Double, val speedKmh: Double)

    /** Pick the position source: scripted EfisState in demo, own GPS otherwise. */
    private fun readPosition(): Fix? {
        if (isDemo()) {
            val s = positionProvider()
            return if (s.hasPosition) {
                Fix(s.latitude, s.longitude, s.gpsAltitudeFt.toDouble(), s.gpsSpeedKmh.toDouble())
            } else null
        }
        // Real flight: prefer our own independent GPS fix.
        if (lastGpsMs != 0L) {
            return Fix(lastGpsLat, lastGpsLon, lastGpsAltFt, lastGpsSpeedKmh)
        }
        // Fallback before our listener has delivered a fix (e.g. screen active, GPS warming up).
        val s = positionProvider()
        return if (s.hasPosition) {
            Fix(s.latitude, s.longitude, s.gpsAltitudeFt.toDouble(), s.gpsSpeedKmh.toDouble())
        } else null
    }

    /** Register our own GPS updates (independent of EfisSensorProvider). */
    @SuppressLint("MissingPermission")
    private fun startLocation() {
        val hasPerm = appContext.checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED ||
            appContext.checkSelfPermission(android.Manifest.permission.ACCESS_COARSE_LOCATION) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED
        if (!hasPerm) return
        runCatching {
            val provider = when {
                locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
                locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
                else -> null
            }
            if (provider != null) {
                locationManager.requestLocationUpdates(provider, 1000L, 0f, locationListener)
            }
        }
    }

    private val locationListener = LocationListener { loc: Location ->
        lastGpsLat = loc.latitude
        lastGpsLon = loc.longitude
        if (loc.hasAltitude()) lastGpsAltFt = loc.altitude * 3.28084
        lastGpsSpeedKmh = if (loc.hasSpeed()) loc.speed * 3.6 else lastGpsSpeedKmh
        lastGpsMs = System.currentTimeMillis()
    }

    fun stop() {
        if (!started) return
        started = false
        flushCycleStartMs = 0L
        sensorManager.unregisterListener(sensorListener)
        runCatching { locationManager.removeUpdates(locationListener) }
        lastGpsMs = 0L
        positionJob?.cancel(); positionJob = null
        flushJob?.cancel(); flushJob = null
        flush()   // persist whatever we have
        publish()
    }

    fun pause() { paused = true; publish() }
    fun resume() { paused = false; publish() }
    fun togglePause() { paused = !paused; publish() }

    private fun add(kind: FdrKind, v1: Double? = null, v2: Double? = null, v3: Double? = null) {
        if (!started || paused) return
        val now = System.currentTimeMillis()
        val cutoff = now - bufferMinutes.coerceAtLeast(5) * 60_000L
        val sample = FdrSample(now, kind, v1, v2, v3)
        synchronized(lock) {
            // High-frequency ring buffer (all kinds), bounded to bufferMinutes → raw log.
            buffer.addLast(sample)
            while (buffer.isNotEmpty() && buffer.first().tMs < cutoff) buffer.removeFirst()
            // Session-long track (GPS + ~1 Hz altitude only), never purged → full GPX.
            when (kind) {
                FdrKind.GPS -> trackBuffer.addLast(sample)
                FdrKind.ALT -> if (now - lastTrackAltMs >= 950L) { lastTrackAltMs = now; trackBuffer.addLast(sample) }
                else -> {}
            }
        }
        // Publish count occasionally (every ~64 samples) to avoid UI churn.
        if ((buffer.size and 0x3F) == 0) publish()
    }

    private fun flush() {
        val snapshot = synchronized(lock) { buffer.toList() }
        runCatching { store.writeSession(snapshot) }
    }

    /** A time-ordered copy of the recent high-frequency buffer (for the raw log). */
    fun snapshotSamples(): List<FdrSample> = synchronized(lock) { buffer.toList() }

    /** A time-ordered copy of the whole-session GPS+altitude track (for the GPX). */
    fun snapshotTrack(): List<FdrSample> = synchronized(lock) { trackBuffer.toList() }

    private fun publish() {
        _status.value = _status.value.copy(
            recording = started && !paused,
            paused = paused,
            sampleCount = buffer.size,
        )
    }

    private val sensorListener = object : SensorEventListener {
        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        override fun onSensorChanged(event: SensorEvent) {
            when (event.sensor.type) {
                Sensor.TYPE_ACCELEROMETER ->
                    add(FdrKind.ACCEL, event.values[0].toDouble(), event.values[1].toDouble(), event.values[2].toDouble())
                Sensor.TYPE_GYROSCOPE ->
                    add(FdrKind.GYRO, event.values[0].toDouble(), event.values[1].toDouble(), event.values[2].toDouble())
                // Raw pressure only; altitude comes from the fused position poll so it
                // stays consistent with the moving map (and works in demo mode).
                Sensor.TYPE_PRESSURE -> add(FdrKind.BARO, event.values[0].toDouble())
            }
        }
    }
}
