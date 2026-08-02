package com.example.ui

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.max

/**
 * Lightweight on-device Electronic Image Stabilization helper.
 *
 * CameraX's native preview/video stabilization is enabled separately when the
 * hardware supports it. This class adds a gyro-driven smooth transform for the
 * viewfinder so older or mid-range devices still get a steadier preview HUD.
 * It intentionally keeps the correction small because stabilizing requires a
 * crop margin; too much translation would expose borders.
 */
class GyroStabilizationEngine(
    context: Context,
    private val scope: CoroutineScope,
    private val enabled: StateFlow<Boolean>,
    private val mode: StateFlow<String>,
    private val level: StateFlow<String>,
    private val previewDelayMs: StateFlow<Int>,
    private val onSample: (StabilizationState) -> Unit
) : SensorEventListener {

    data class StabilizationState(
        val offsetX: Float = 0f,
        val offsetY: Float = 0f,
        val rollDegrees: Float = 0f,
        val motionScore: Float = 0f,
        val source: String = "GYRO"
    )

    private data class DelayedSample(
        val timestampMs: Long,
        val state: StabilizationState
    )

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val gyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
    private val delayedSamples = ArrayDeque<DelayedSample>()

    @Volatile private var registered = false
    @Volatile private var lastTimestampNs = 0L
    @Volatile private var velocityX = 0f
    @Volatile private var velocityY = 0f
    @Volatile private var velocityZ = 0f
    @Volatile private var smoothX = 0f
    @Volatile private var smoothY = 0f
    @Volatile private var smoothRoll = 0f
    @Volatile private var currentMotion = 0f

    private var outputJob: Job? = null

    fun start() {
        if (!registered && gyroscope != null) {
            sensorManager.registerListener(this, gyroscope, SensorManager.SENSOR_DELAY_GAME)
            registered = true
        }
        if (outputJob == null) {
            outputJob = scope.launch(Dispatchers.Default) {
                while (isActive) {
                    delay(16)
                    emitDelayedOrLatest()
                }
            }
        }
    }

    fun stop() {
        if (registered) {
            sensorManager.unregisterListener(this)
            registered = false
        }
        outputJob?.cancel()
        outputJob = null
        delayedSamples.clear()
        onSample(StabilizationState(source = if (gyroscope == null) "NO_GYRO" else "OFF"))
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type != Sensor.TYPE_GYROSCOPE) return
        if (!enabled.value) {
            decayToNeutral()
            return
        }

        val dt = if (lastTimestampNs == 0L) 0.016f else ((event.timestamp - lastTimestampNs) / 1_000_000_000f).coerceIn(0.001f, 0.05f)
        lastTimestampNs = event.timestamp

        velocityX = event.values.getOrNull(0) ?: 0f
        velocityY = event.values.getOrNull(1) ?: 0f
        velocityZ = event.values.getOrNull(2) ?: 0f
        currentMotion = (abs(velocityX) + abs(velocityY) + abs(velocityZ)).coerceIn(0f, 12f) / 12f

        val gain = modeGain() * levelGain()
        val maxShiftPx = maxShift()

        // Opposite-direction correction. Gyro X roughly maps to vertical shake;
        // gyro Y maps to horizontal shake; gyro Z maps to horizon/roll.
        val targetX = (-velocityY * dt * 900f * gain).coerceIn(-maxShiftPx, maxShiftPx)
        val targetY = ( velocityX * dt * 900f * gain).coerceIn(-maxShiftPx, maxShiftPx)
        val targetRoll = (-velocityZ * dt * 55f * gain).coerceIn(-3.5f, 3.5f)

        val smoothing = when (level.value) {
            "Low" -> 0.12f
            "Medium" -> 0.18f
            "High" -> 0.24f
            else -> 0.30f
        }
        smoothX += (targetX - smoothX) * smoothing
        smoothY += (targetY - smoothY) * smoothing
        smoothRoll += (targetRoll - smoothRoll) * smoothing

        synchronized(delayedSamples) {
            delayedSamples.addLast(
                DelayedSample(
                    System.currentTimeMillis(),
                    StabilizationState(smoothX, smoothY, smoothRoll, currentMotion, source = "GYRO+EIS")
                )
            )
            while (delayedSamples.size > 180) delayedSamples.removeFirst()
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    private fun emitDelayedOrLatest() {
        if (!enabled.value) {
            decayToNeutral()
            onSample(StabilizationState(source = if (gyroscope == null) "NO_GYRO" else "OFF"))
            return
        }
        val now = System.currentTimeMillis()
        val requestedDelay = previewDelayMs.value.coerceIn(0, 2000)
        var selected: StabilizationState? = null
        synchronized(delayedSamples) {
            while (delayedSamples.size > 1 && now - delayedSamples.first().timestampMs >= requestedDelay) {
                selected = delayedSamples.removeFirst().state
            }
            if (selected == null) selected = delayedSamples.lastOrNull()?.state
        }
        onSample(selected ?: StabilizationState(source = if (gyroscope == null) "NO_GYRO" else "GYRO_IDLE"))
    }

    private fun decayToNeutral() {
        smoothX *= 0.82f
        smoothY *= 0.82f
        smoothRoll *= 0.82f
        currentMotion *= 0.75f
    }

    private fun levelGain(): Float = when (level.value) {
        "Low" -> 0.45f
        "Medium" -> 0.70f
        "High" -> 1.0f
        else -> 1.25f
    }

    private fun modeGain(): Float = when (mode.value) {
        "Extreme Stabilizer" -> 1.35f
        "Action Mode" -> 1.15f
        "Cinematic Mode" -> 0.85f
        "Normal Video" -> 0.35f
        else -> 1.0f
    }

    private fun maxShift(): Float = when (level.value) {
        "Low" -> 10f
        "Medium" -> 18f
        "High" -> 28f
        else -> 42f
    } * max(0.8f, modeGain())
}
