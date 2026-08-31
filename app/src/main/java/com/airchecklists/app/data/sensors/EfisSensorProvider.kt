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
import com.airchecklists.app.data.model.EfisHeadingSource
import com.airchecklists.app.data.model.EfisVarioSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.abs

/** Live EFIS data snapshot. */
data class EfisState(
    val pitchDeg: Float = 0f,
    val rollDeg: Float = 0f,
    val slip: Float = 0f,             // -1..1 lateral (ball)
    val headingDeg: Float = 0f,
    val gpsSpeedKmh: Float = 0f,
    val gpsAltitudeFt: Float = 0f,
    val verticalSpeedFtMin: Float = 0f,
    val hasFix: Boolean = false,
    val hasBarometer: Boolean = false,
    // Position for the moving map.
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val gpsTrackDeg: Float = 0f,      // course over ground (GPS bearing)
    val hasPosition: Boolean = false,
)

/**
 * Wraps device sensors + GPS and exposes an [EfisState] flow. Not a certified
 * instrument — indicative use only. Call [start]/[stop] around the lifecycle.
 */
class EfisSensorProvider(
    context: Context,
    private var headingSource: EfisHeadingSource,
    private var varioSource: EfisVarioSource,
    private var responsiveness: Float = 0.35f,
) {
    private val appContext = context.applicationContext
    private val sensorManager = appContext.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val locationManager = appContext.getSystemService(Context.LOCATION_SERVICE) as LocationManager

    private val rotationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
    private val accelSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val pressureSensor = sensorManager.getDefaultSensor(Sensor.TYPE_PRESSURE)

    private val _state = MutableStateFlow(EfisState(hasBarometer = pressureSensor != null))
    val state: StateFlow<EfisState> = _state.asStateFlow()

    /** GPS trail (session only): list of [lat, lon] points, oldest first. */
    private val trailBuffer = ArrayDeque<DoubleArray>()
    private val _trail = MutableStateFlow<List<DoubleArray>>(emptyList())
    val trail: StateFlow<List<DoubleArray>> = _trail.asStateFlow()

    /** Append a position to the trail, keeping it bounded and skipping tiny hops. */
    private fun pushTrail(lat: Double, lon: Double) {
        val last = trailBuffer.lastOrNull()
        if (last != null) {
            // ~ metres per degree lat; skip points closer than ~5 m to avoid saturation.
            val dLat = (lat - last[0]) * 111_320.0
            val dLon = (lon - last[1]) * 111_320.0 * kotlin.math.cos(Math.toRadians(lat))
            if (dLat * dLat + dLon * dLon < 25.0) return
        }
        trailBuffer.addLast(doubleArrayOf(lat, lon))
        while (trailBuffer.size > MAX_TRAIL) trailBuffer.removeFirst()
        _trail.value = trailBuffer.toList()
    }

    private fun clearTrail() {
        trailBuffer.clear()
        _trail.value = emptyList()
    }

    /** Demo mode: scripted flight that ignores real sensors while active. */
    private val _demoActive = MutableStateFlow(false)
    val demoActive: StateFlow<Boolean> = _demoActive.asStateFlow()
    /** Which scripted demo runs: 0 = manoeuvres tour, 1 = final approach. */
    private val _demoVariant = MutableStateFlow(0)
    val demoVariant: StateFlow<Int> = _demoVariant.asStateFlow()
    private val demoScope = CoroutineScope(Dispatchers.Default)
    private var demoJob: Job? = null

    // Working values.
    private var magneticHeading = 0f
    private var gpsTrack = 0f

    // Attitude calibration: reference captured at the mount's neutral position.
    private var pitchOffset = 0f
    private var rollOffset = 0f
    private var lastRawPitch = 0f
    private var lastRawRoll = 0f

    // Barometric vario smoothing.
    private var lastPressureAltM: Float? = null
    private var lastPressureTimeNs: Long = 0L

    // GPS vario smoothing.
    private var lastGpsAltM: Double? = null
    private var lastGpsTimeMs: Long = 0L

    fun updateSources(heading: EfisHeadingSource, vario: EfisVarioSource) {
        headingSource = heading
        varioSource = vario
        if (_demoActive.value) return
        // Re-publish heading using the newly selected source.
        _state.value = _state.value.copy(
            headingDeg = if (headingSource == EfisHeadingSource.GPS_TRACK) gpsTrack else magneticHeading,
        )
    }

    fun updateResponsiveness(value: Float) {
        responsiveness = value.coerceIn(0.05f, 1f)
    }

    /** Capture the current attitude as the level (0/0) reference. */
    fun calibrate() {
        pitchOffset = lastRawPitch
        rollOffset = lastRawRoll
    }

    /** Clear the attitude calibration (back to raw device attitude). */
    fun resetCalibration() {
        pitchOffset = 0f
        rollOffset = 0f
    }

    /** Exponential smoothing toward [target], using the responsiveness factor. */
    private fun smooth(current: Float, target: Float): Float = current + (target - current) * responsiveness

    /** Angular smoothing that handles the 0/360 wrap. */
    private fun smoothAngle(current: Float, target: Float): Float {
        var delta = ((target - current + 540f) % 360f) - 180f
        return (current + delta * responsiveness + 360f) % 360f
    }

    private val sensorListener = object : SensorEventListener {
        private val rotationMatrix = FloatArray(9)
        private val orientation = FloatArray(3)

        override fun onSensorChanged(event: SensorEvent) {
            if (_demoActive.value) return
            when (event.sensor.type) {
                Sensor.TYPE_ROTATION_VECTOR -> {
                    SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
                    SensorManager.getOrientation(rotationMatrix, orientation)
                    // orientation: [azimuth, pitch, roll] in radians.
                    val azimuth = ((Math.toDegrees(orientation[0].toDouble()) + 360.0) % 360.0).toFloat()
                    // Raw attitude (portrait mounting: negate pitch so nose-up = sky up).
                    val rawPitch = -Math.toDegrees(orientation[1].toDouble()).toFloat()
                    // Roll sign chosen so that banking/tilting the device to the RIGHT
                    // gives a POSITIVE roll — the same convention as the demo flight
                    // (roll > 0 ⇒ right turn ⇒ aircraft banks right). The instruments
                    // are all drawn against this single convention.
                    val rawRoll = Math.toDegrees(orientation[2].toDouble()).toFloat()
                    lastRawPitch = rawPitch
                    lastRawRoll = rawRoll
                    magneticHeading = azimuth
                    val cur = _state.value
                    val newHeadingTarget = if (headingSource == EfisHeadingSource.GPS_TRACK) gpsTrack else magneticHeading
                    _state.value = cur.copy(
                        // Subtract the calibrated reference so the mount orientation reads level.
                        pitchDeg = smooth(cur.pitchDeg, rawPitch - pitchOffset),
                        rollDeg = smooth(cur.rollDeg, rawRoll - rollOffset),
                        headingDeg = smoothAngle(cur.headingDeg, newHeadingTarget),
                    )
                }
                Sensor.TYPE_ACCELEROMETER -> {
                    // Slip/skid ball = lateral specific-force vs the "down" the wings
                    // feel. Use ax / sqrt(ay^2+az^2) (an inclinometer ratio) rather
                    // than ax/g so bank angle doesn't leak in, and smooth heavily to
                    // kill engine/airframe vibration flicker.
                    val ax = event.values[0]
                    val ay = event.values[1]
                    val az = event.values[2]
                    val vertical = kotlin.math.sqrt(ay * ay + az * az).coerceAtLeast(0.5f)
                    val raw = (ax / vertical).coerceIn(-1f, 1f)
                    // Extra-slow filter for the ball (≈ a damped physical ball).
                    val ballAlpha = (responsiveness * 0.4f).coerceIn(0.02f, 0.2f)
                    val prev = _state.value.slip
                    _state.value = _state.value.copy(slip = prev + (raw - prev) * ballAlpha)
                }
                Sensor.TYPE_PRESSURE -> {
                    if (varioSource != EfisVarioSource.BAROMETER) return
                    val hPa = event.values[0]
                    val altM = SensorManager.getAltitude(SensorManager.PRESSURE_STANDARD_ATMOSPHERE, hPa)
                    val now = event.timestamp
                    val prevAlt = lastPressureAltM
                    if (prevAlt != null && lastPressureTimeNs != 0L) {
                        val dtSec = (now - lastPressureTimeNs) / 1_000_000_000f
                        if (dtSec > 0.05f) {
                            val ftMin = ((altM - prevAlt) * 3.28084f) / dtSec * 60f
                            _state.value = _state.value.copy(
                                verticalSpeedFtMin = smooth(_state.value.verticalSpeedFtMin, ftMin),
                            )
                            lastPressureAltM = altM
                            lastPressureTimeNs = now
                        }
                    } else {
                        lastPressureAltM = altM
                        lastPressureTimeNs = now
                    }
                }
            }
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
    }

    private val locationListener = LocationListener { location -> onLocation(location) }

    private fun onLocation(location: Location) {
        if (_demoActive.value) return
        val speedKmh = location.speed * 3.6f
        val altFt = (location.altitude * 3.28084).toFloat()
        if (location.hasBearing() && location.speed > 0.5f) {
            gpsTrack = location.bearing
        }
        // GPS-derived vertical speed.
        var vs = _state.value.verticalSpeedFtMin
        if (varioSource == EfisVarioSource.GPS) {
            val now = location.time.takeIf { it > 0 } ?: System.currentTimeMillis()
            val prevAlt = lastGpsAltM
            if (prevAlt != null && lastGpsTimeMs != 0L) {
                val dtSec = (now - lastGpsTimeMs) / 1000f
                if (dtSec > 0.5f) {
                    val ftMin = ((location.altitude - prevAlt) * 3.28084 / dtSec * 60f).toFloat()
                    vs = smooth(vs, ftMin)
                    lastGpsAltM = location.altitude
                    lastGpsTimeMs = now
                }
            } else {
                lastGpsAltM = location.altitude
                lastGpsTimeMs = now
            }
        }
        _state.value = _state.value.copy(
            gpsSpeedKmh = smooth(_state.value.gpsSpeedKmh, speedKmh),
            gpsAltitudeFt = smooth(_state.value.gpsAltitudeFt, altFt),
            verticalSpeedFtMin = vs,
            hasFix = true,
            headingDeg = if (headingSource == EfisHeadingSource.GPS_TRACK)
                smoothAngle(_state.value.headingDeg, gpsTrack) else _state.value.headingDeg,
            latitude = location.latitude,
            longitude = location.longitude,
            gpsTrackDeg = gpsTrack,
            hasPosition = true,
        )
        pushTrail(location.latitude, location.longitude)
    }

    /** Toggle the scripted demo flight on/off. */
    fun toggleDemo() {
        if (_demoActive.value) stopDemo() else startDemo()
    }

    /**
     * Cycle to the next demo scenario. If a demo is running it restarts immediately with
     * the new variant; if none is running it starts one. Wraps around the variants.
     */
    fun nextDemo() {
        _demoVariant.value = (_demoVariant.value + 1) % DEMO_VARIANTS
        stopDemo()
        startDemo()
    }

    /** Start a scripted flight: calibration → climb → descent → turn → speed sweep. */
    fun startDemo() {
        if (_demoActive.value) return
        _demoActive.value = true
        val variant = _demoVariant.value
        demoJob = demoScope.launch { if (variant == 1) runApproachDemo() else runDemo() }
    }

    fun stopDemo() {
        demoJob?.let { runCatching { it.cancel() } }
        demoJob = null
        _demoActive.value = false
    }

    private suspend fun runDemo() {
        val frameMs = 40L                 // 25 fps
        var alt = 800f
        var heading = 0f
        var lat = 48.90    // start near LFRK (Caen) for a plausible demo location
        var lon = -0.45
        clearTrail()
        // Reset to a level, stationary state.
        _state.value = _state.value.copy(
            pitchDeg = 0f, rollDeg = 0f, slip = 0f, headingDeg = heading,
            gpsSpeedKmh = 0f, gpsAltitudeFt = alt, verticalSpeedFtMin = 0f,
            hasFix = true, hasPosition = true, latitude = lat, longitude = lon, gpsTrackDeg = heading,
        )

        // Integrate position from current speed + heading, feed the trail + state.
        fun advance(dt: Float) {
            val spd = _state.value.gpsSpeedKmh          // km/h
            val distM = spd / 3.6f * dt                 // metres this frame
            val hdgRad = Math.toRadians(heading.toDouble())
            lat += (distM * kotlin.math.cos(hdgRad)) / 111_320.0
            lon += (distM * kotlin.math.sin(hdgRad)) / (111_320.0 * kotlin.math.cos(Math.toRadians(lat)))
            _state.value = _state.value.copy(latitude = lat, longitude = lon, gpsTrackDeg = heading)
            pushTrail(lat, lon)
        }

        // Helper: animate over [durationMs] calling [onProgress] with t in 0..1.
        suspend fun animate(durationMs: Long, onProgress: (Float) -> Unit) {
            var elapsed = 0L
            while (elapsed <= durationMs) {
                if (!_demoActive.value) return
                onProgress((elapsed.toFloat() / durationMs).coerceIn(0f, 1f))
                advance(frameMs / 1000f)
                delay(frameMs)
                elapsed += frameMs
            }
        }
        fun set(build: EfisState.() -> EfisState) { _state.value = _state.value.build() }
        val dt = frameMs / 1000f

        while (_demoActive.value) {
            // Cruise a bit so the map has motion before the manoeuvres.
            set { copy(pitchDeg = 0f, rollDeg = 0f, slip = 0f, verticalSpeedFtMin = 0f, gpsSpeedKmh = 140f) }
            animate(1600) { /* hold level cruise */ }

            // 2) Climb 800 → 1300 ft (steady ~+750 ft/min) so the magenta target-alt
            //    cursor can be watched sweeping up the ALT scale, then hold at 1300.
            run {
                val startAlt = 800f
                val endAlt = 1300f
                alt = startAlt
                animate(4000) { t ->
                    alt = startAlt + (endAlt - startAlt) * t
                    set { copy(pitchDeg = 8f, verticalSpeedFtMin = 750f, gpsAltitudeFt = alt) }
                }
                alt = endAlt
                set { copy(pitchDeg = 0f, verticalSpeedFtMin = 0f, gpsAltitudeFt = alt) }
                animate(2500) { /* hold at 1300 ft */ }
            }

            // 3) Descent: pitch down, altitude falling, strong negative vario (~-1100 ft/min).
            animate(3500) { t ->
                val p = -12f * kotlin.math.sin(t * Math.PI).toFloat()
                alt += 1100f / 60f * dt * (p / 12f)  // negative while descending
                set { copy(pitchDeg = p, verticalSpeedFtMin = 1100f * (p / 12f), gpsAltitudeFt = alt) }
            }
            set { copy(pitchDeg = 0f, verticalSpeedFtMin = 0f) }

            // 4) Turn: bank right, ball deflects, heading turns a clear ~120° then rolls out.
            animate(4500) { t ->
                // Roll in, hold, roll out (trapezoid).
                val roll = when {
                    t < 0.25f -> 25f * (t / 0.25f)
                    t > 0.75f -> 25f * ((1f - t) / 0.25f)
                    else -> 25f
                }
                heading = (heading + 20f * (roll / 25f) * dt + 360f) % 360f   // ~20°/s at full bank
                set { copy(rollDeg = roll, slip = (roll / 25f) * 0.4f, headingDeg = heading) }
            }
            set { copy(rollDeg = 0f, slip = 0f) }

            // 5) Speed sweep: accelerate 0→230, hold 2 s, then brake back to 0.
            animate(3000) { t -> set { copy(gpsSpeedKmh = 230f * t) } }
            set { copy(gpsSpeedKmh = 230f) }
            animate(2000) { /* hold 230 */ }
            animate(3000) { t -> set { copy(gpsSpeedKmh = 230f * (1f - t)) } }
            set { copy(gpsSpeedKmh = 0f) }
        }
    }

    /**
     * Demo 2 — final approach toward the nearest aerodrome. The aircraft starts ~5 km out
     * at circuit height on the extended runway axis and flies a descending final while
     * wandering left/right of the axis and above/below the 3° plane, so the NUMAPP tunnel,
     * the AXE (lateral) and PLAN (glide) deviations all move clearly. Loops.
     *
     * Geometry is self-contained (integrates its own lat/lon) and anchored near LFRK so
     * the NUMAPP AUTO resolver locks onto that field. Approach axis QFU ≈ 040° (arbitrary
     * plausible runway); the NUMAPP instrument derives its own QFU from the field, but the
     * lateral/vertical wander is what makes AXE/PLAN swing.
     */
    private suspend fun runApproachDemo() {
        val frameMs = 40L
        // Anchor on LFRK (Caen-Carpiquet)'s REAL ARP + a real runway heading, so the NUMAPP
        // AUTO resolver (which picks the nearest aerodrome and a QFU from its circuit text
        // "12/30 - QFU 124/304") locks onto the SAME field and axis this demo flies. If the
        // two disagree the instrument's cross-track blows up (e.g. thousands of metres) and
        // the runway never appears to approach — so they must match.
        val thrLat = 49.1733
        val thrLon = -0.4500
        val fieldElevFt = 243f          // LFRK elevation
        val qfuDeg = 304.0              // land toward 304 (approach from the SE)
        val mPerDegLat = 111_320.0
        val mPerDegLon = 111_320.0 * kotlin.math.cos(Math.toRadians(thrLat))
        // Unit vectors: fwd points along the landing direction (toward threshold heading),
        // right is 90° clockwise of fwd. Ship sits "behind" the threshold on final.
        val hdg = Math.toRadians(qfuDeg)
        val fwdE = kotlin.math.sin(hdg); val fwdN = kotlin.math.cos(hdg)
        val rightE = kotlin.math.cos(hdg); val rightN = -kotlin.math.sin(hdg)
        val glideRad = Math.toRadians(3.0)

        clearTrail()

        suspend fun animate(durationMs: Long, onProgress: (Float) -> Unit) {
            var elapsed = 0L
            while (elapsed <= durationMs) {
                if (!_demoActive.value) return
                onProgress((elapsed.toFloat() / durationMs).coerceIn(0f, 1f))
                delay(frameMs)
                elapsed += frameMs
            }
        }

        val startAlong = 3500.0         // start 3.5 km out on final (closer = faster visual)
        val approachSpeed = 110f        // km/h — realistic final-approach speed

        // Place using an explicit height-above-field so the flare/go-around can hug the ground.
        fun placeAbove(alongM: Double, crossM: Double, aboveFieldFt: Float, vsFtMin: Float, spdKmh: Float, track: Float, pitch: Float) {
            val east = (-alongM) * fwdE + crossM * rightE
            val north = (-alongM) * fwdN + crossM * rightN
            val lat = thrLat + north / mPerDegLat
            val lon = thrLon + east / mPerDegLon
            _state.value = _state.value.copy(
                hasFix = true, hasPosition = true,
                latitude = lat, longitude = lon,
                gpsAltitudeFt = fieldElevFt + aboveFieldFt, verticalSpeedFtMin = vsFtMin,
                gpsSpeedKmh = spdKmh, headingDeg = track, gpsTrackDeg = track,
                pitchDeg = pitch, rollDeg = 0f, slip = 0f,
            )
            pushTrail(lat, lon)
        }

        // Height of the nominal 3° plane above field at a given along-track distance (ft).
        fun planeFtAt(alongM: Double): Float =
            (kotlin.math.tan(glideRad) * alongM.coerceAtLeast(0.0) * 3.280839895).toFloat()

        // Ground speed (m/s) along the final, from the approach speed.
        val gsMs = approachSpeed / 3.6                                   // ≈ 30.6 m/s
        // Nominal vertical speed to stay on the 3° plane at this speed (negative = descending).
        val nominalVs = -(gsMs * kotlin.math.tan(glideRad) * 3.280839895 * 60.0).toFloat()  // ≈ −315 ft/min

        while (_demoActive.value) {
            // Realistic descent profile, integrating the along-track distance from the ground
            // speed and the height from the chosen vertical speed. We drive the LOG scenario:
            //  1a) ~3 s ABOVE the plane at a gentle 500 ft/min (slower correction) — sits high;
            //  1b) increase to 700 ft/min to sink back THROUGH the plane, weaving around the
            //      axis (localizer S-turns) — passes clearly below;
            //  1c) recapture axis AND plane and track it smoothly down to the threshold, where
            //      we go around at 100 ft AGL.
            var alongM = startAlong
            var aglFt = planeFtAt(startAlong) + 140f    // start ~140 ft ABOVE the 3° plane

            // 1a) 3 s at 500 ft/min: descends slower than the plane demands (~315 ft/min would
            //     hold it), so relative to the plane we stay high → aircraft/PLAN read high.
            animate(3000) { _ ->
                alongM -= gsMs * (frameMs / 1000.0)
                aglFt += (-500f) * (frameMs / 60000f)
                val above = aglFt - planeFtAt(alongM)
                placeAbove(alongM, 0.0, aglFt, -500f, approachSpeed, qfuDeg.toFloat(), -3f)
            }
            // 1b) 700 ft/min while weaving gently ±40 m around the axis: sinks through the plane
            //     and ends clearly BELOW it (RED), staying reasonably close to the axis.
            animate(6000) { t ->
                alongM -= gsMs * (frameMs / 1000.0)
                aglFt += (-700f) * (frameMs / 60000f)
                val crossM = 40.0 * kotlin.math.sin(t * Math.PI * 3.0)      // gentle S-turns near the axis
                val track = (qfuDeg + kotlin.math.cos(t * Math.PI * 3.0) * 4.0).toFloat()
                placeAbove(alongM, crossM, aglFt, -700f, approachSpeed, track, -3.5f)
            }
            // 1c) Recapture: blend cross-track back to the axis and height back onto the 3° plane,
            //     then track the plane down to the threshold. Go around at 100 ft AGL.
            val goAroundAglFt = 100f
            val recaptureAgl = aglFt
            animate(9_000) { t ->
                alongM -= gsMs * (frameMs / 1000.0)
                val planeHere = planeFtAt(alongM)
                // Ease height from wherever we were onto the plane over the first ~40% of the leg.
                val blend = (t / 0.4f).coerceIn(0f, 1f)
                val target = planeHere.coerceAtLeast(goAroundAglFt)
                aglFt = recaptureAgl + (target - recaptureAgl) * blend
                // Damp the cross-track weave to zero as we settle on the axis.
                val crossM = 35.0 * kotlin.math.sin(t * Math.PI * 2.0) * (1f - blend)
                val track = (qfuDeg + kotlin.math.cos(t * Math.PI * 2.0) * 3.0 * (1f - blend)).toFloat()
                placeAbove(alongM.coerceAtLeast(0.0), crossM, aglFt.coerceAtLeast(goAroundAglFt), nominalVs, approachSpeed, track, -3f)
            }
            // 2) LOW PASS: hold 100 ft AGL level and track down the axis past the threshold
            //    (alongM 0 → −400) so the runway visibly slides under and behind the aircraft
            //    before any climb begins.
            animate(3500) { t ->
                val a = 0.0 - 400.0 * t                          // over the field, still low
                placeAbove(a, 0.0, goAroundAglFt, 0f, approachSpeed, qfuDeg.toFloat(), 0f)
            }
            // 3) GO-AROUND: full power, pitch up, strong climb back to circuit height while
            //    continuing past the field (along goes further negative).
            animate(5000) { t ->
                val a = -400.0 - 400.0 * t                       // continue past the field
                val aboveFt = goAroundAglFt + (920f - goAroundAglFt) * t   // climb 100 → 920 ft AGL
                placeAbove(a, 0.0, aboveFt, 900f, approachSpeed - 10f + 30f * t, qfuDeg.toFloat(), 10f)
            }
            // 4) Brief hold at circuit height, then the loop restarts a fresh final.
            animate(1500) { placeAbove(-800.0, 0.0, 920f, 0f, approachSpeed, qfuDeg.toFloat(), 0f) }
        }
    }

    fun start() {
        rotationSensor?.let { sensorManager.registerListener(sensorListener, it, SensorManager.SENSOR_DELAY_UI) }
        accelSensor?.let { sensorManager.registerListener(sensorListener, it, SensorManager.SENSOR_DELAY_UI) }
        pressureSensor?.let { sensorManager.registerListener(sensorListener, it, SensorManager.SENSOR_DELAY_NORMAL) }
        startLocation()
    }

    /** Call after the location permission is granted (also on start if already granted). */
    @SuppressLint("MissingPermission")
    fun startLocation() {
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

    fun stop() {
        sensorManager.unregisterListener(sensorListener)
        runCatching { locationManager.removeUpdates(locationListener) }
    }

    private companion object {
        const val MAX_TRAIL = 2000
        const val DEMO_VARIANTS = 2
    }
}
