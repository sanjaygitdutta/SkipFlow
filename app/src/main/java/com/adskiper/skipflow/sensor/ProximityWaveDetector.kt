package com.adskiper.skipflow.sensor

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.SystemClock
import android.util.Log

class ProximityWaveDetector(
    context: Context,
    private val onWaveDetected: () -> Unit
) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.AUDIO_SERVICE).let {
        context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    }
    private val proximitySensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY)

    private var isListening = false
    private var nearStartTime = 0L
    private var lastWaveTriggerTime = 0L

    companion object {
        private const val TAG = "ProximityWaveDetector"
        private const val MIN_WAVE_DURATION_MS = 60L
        private const val MAX_WAVE_DURATION_MS = 900L
        private const val DEBOUNCE_TIME_MS = 1500L
    }

    fun start() {
        if (isListening || proximitySensor == null) return
        sensorManager.registerListener(this, proximitySensor, SensorManager.SENSOR_DELAY_UI)
        isListening = true
        Log.d(TAG, "Proximity wave listener started")
    }

    fun stop() {
        if (!isListening) return
        sensorManager.unregisterListener(this)
        isListening = false
        Log.d(TAG, "Proximity wave listener stopped")
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null || proximitySensor == null) return

        val distance = event.values[0]
        val maxRange = proximitySensor.maximumRange
        val isNear = distance < maxRange && distance < 5.0f

        val now = SystemClock.elapsedRealtime()

        if (isNear) {
            nearStartTime = now
        } else {
            if (nearStartTime > 0) {
                val duration = now - nearStartTime
                nearStartTime = 0L

                if (duration in MIN_WAVE_DURATION_MS..MAX_WAVE_DURATION_MS) {
                    if (now - lastWaveTriggerTime > DEBOUNCE_TIME_MS) {
                        lastWaveTriggerTime = now
                        Log.d(TAG, "Hand wave detected! Duration: ${duration}ms")
                        onWaveDetected()
                    }
                }
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // No-op
    }
}
