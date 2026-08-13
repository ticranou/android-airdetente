package com.airchecklists.app.data.sensors

import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorManager

/**
 * Snapshot of the device's flight-relevant hardware, computed once at startup.
 * Used to (a) warn the user about missing sensors and (b) mark instruments that
 * cannot work as unavailable instead of showing wrong/frozen data.
 */
data class DeviceCapabilities(
    val hasRotationVector: Boolean,
    val hasAccelerometer: Boolean,
    val hasMagnetometer: Boolean,
    val hasGyroscope: Boolean,
    val hasBarometer: Boolean,
    val hasGps: Boolean,
) {
    /**
     * Whether a usable attitude/heading (pitch, roll, magnetic heading) can be
     * produced. The app feeds attitude from TYPE_ROTATION_VECTOR; failing that,
     * accelerometer + magnetometer can still yield a (coarser) orientation.
     */
    val hasOrientation: Boolean
        get() = hasRotationVector || (hasAccelerometer && hasMagnetometer)

    companion object {
        fun detect(context: Context): DeviceCapabilities {
            val sm = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
            val pm = context.packageManager
            return DeviceCapabilities(
                hasRotationVector = sm.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR) != null,
                hasAccelerometer = sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) != null,
                hasMagnetometer = sm.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD) != null,
                hasGyroscope = sm.getDefaultSensor(Sensor.TYPE_GYROSCOPE) != null,
                hasBarometer = sm.getDefaultSensor(Sensor.TYPE_PRESSURE) != null,
                hasGps = pm.hasSystemFeature(PackageManager.FEATURE_LOCATION_GPS),
            )
        }
    }
}
