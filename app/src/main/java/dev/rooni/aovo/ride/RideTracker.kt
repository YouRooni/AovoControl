package dev.rooni.aovo.ride

import dev.rooni.aovo.ble.AovoCore
import dev.rooni.aovo.ble.ConnectionState
import dev.rooni.aovo.ble.Telemetry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class RideTracker(
    private val repository: RideRepository,
    private val core: AovoCore,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var trackingJob: Job? = null

    private data class ActiveSession(
        val deviceName: String,
        val deviceAddress: String,
        val startTime: Long,
        var lastMoveTime: Long,
        val startBattery: Int,
        val startVoltage: Float,
        val startTripDistance: Float,
        var maxSpeed: Float = 0f,
        var maxCurrent: Float = 0f,
        var maxPowerWatts: Float = 0f,
        var tempSum: Float = 0f,
        var tempCount: Int = 0,
        val samples: MutableList<RideSample> = ArrayList(500),
    )

    private var activeSession: ActiveSession? = null

    private val _isRecording = MutableStateFlow(false)
    val isRecording = _isRecording.asStateFlow()

    fun start() {
        trackingJob?.cancel()
        trackingJob = scope.launch {
            while (true) {
                val connection = core.connection.value
                val device = core.connectedDevice.value
                val telemetry = core.telemetry.value

                if (connection == ConnectionState.CONNECTED && device != null) {
                    processTelemetry(device.name.ifBlank { "Aovo" }, device.address, telemetry)
                } else {
                    if (activeSession != null) {
                        finishAndSaveSession(telemetry)
                    }
                }

                delay(1000L)
            }
        }
    }

    private fun processTelemetry(deviceName: String, deviceAddress: String, telemetry: Telemetry) {
        val now = System.currentTimeMillis()
        val speed = telemetry.speed
        val current = telemetry.current
        val voltage = telemetry.voltage
        val power = if (telemetry.power > 0f) telemetry.power else (voltage * current)
        val escTemp = telemetry.escTemperature.toFloat()

        var session = activeSession
        if (session == null) {
            // Start recording automatically when scooter starts moving
            if (speed >= 1.5f) {
                session = ActiveSession(
                    deviceName = deviceName,
                    deviceAddress = deviceAddress,
                    startTime = now,
                    lastMoveTime = now,
                    startBattery = telemetry.battery,
                    startVoltage = voltage,
                    startTripDistance = telemetry.tripDistance,
                )
                activeSession = session
                _isRecording.value = true
            }
        }

        if (session != null) {
            if (speed > 0.5f) {
                session.lastMoveTime = now
            }

            session.maxSpeed = maxOf(session.maxSpeed, speed)
            session.maxCurrent = maxOf(session.maxCurrent, current)
            session.maxPowerWatts = maxOf(session.maxPowerWatts, power)
            if (escTemp > 0f) {
                session.tempSum += escTemp
                session.tempCount++
            }

            session.samples.add(
                RideSample(
                    timestamp = now,
                    speed = speed,
                    current = current,
                    voltage = voltage,
                    powerWatts = power,
                    battery = telemetry.battery,
                    escTemp = escTemp,
                )
            )

            // Auto-finish if idle without motion for over 4 minutes
            if (now - session.lastMoveTime > 4 * 60 * 1000L) {
                finishAndSaveSession(telemetry)
            }
        }
    }

    private fun finishAndSaveSession(telemetry: Telemetry) {
        val session = activeSession ?: return
        activeSession = null
        _isRecording.value = false

        val endTime = System.currentTimeMillis()
        val durationSeconds = ((endTime - session.startTime) / 1000L).coerceAtLeast(1L)
        val deltaDistance = (telemetry.tripDistance - session.startTripDistance).coerceAtLeast(0f)
        val batteryConsumed = (session.startBattery - telemetry.battery).coerceAtLeast(0)

        val samples = session.samples
        val avgSpeed = if (samples.isNotEmpty()) {
            samples.map { it.speed }.average().toFloat()
        } else {
            0f
        }

        val avgTemp = if (session.tempCount > 0) {
            session.tempSum / session.tempCount
        } else {
            0f
        }

        // Save only valid rides (not just turning on the scooter for a second)
        val isSignificantRide = deltaDistance >= 0.03f || (durationSeconds >= 15 && session.maxSpeed >= 3.0f)
        if (isSignificantRide) {
            val rideRecord = RideSession(
                deviceName = session.deviceName,
                deviceAddress = session.deviceAddress,
                startTime = session.startTime,
                endTime = endTime,
                durationSeconds = durationSeconds,
                distanceKm = deltaDistance,
                maxSpeed = session.maxSpeed,
                avgSpeed = avgSpeed,
                startBattery = session.startBattery,
                endBattery = telemetry.battery,
                batteryConsumed = batteryConsumed,
                startVoltage = session.startVoltage,
                endVoltage = telemetry.voltage,
                maxCurrent = session.maxCurrent,
                maxPowerWatts = session.maxPowerWatts,
                avgEscTemp = avgTemp,
                samples = samples,
            )

            scope.launch {
                repository.saveRide(rideRecord)
            }
        }
    }

    fun stop() {
        trackingJob?.cancel()
        activeSession?.let {
            finishAndSaveSession(core.telemetry.value)
        }
        scope.cancel()
    }
}
