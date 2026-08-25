package dev.rooni.aovo.ble

object ViContDecoder {

    private fun u8(a: ByteArray, i: Int): Int = a[i].toInt() and 0xFF

    private fun u16(a: ByteArray, i: Int): Int = (u8(a, i) shl 8) or u8(a, i + 1)

    private fun bit(value: Int, index: Int): Boolean = (value shr index) and 1 == 1

    private fun round1(v: Float): Float = Math.round(v * 10f) / 10f

    private fun round2(v: Float): Float = Math.round(v * 100f) / 100f

    /** `0x10` — the fast-moving numbers, pushed every 200–300 ms. */
    data class Telemetry(
        val voltage: Float = 0f,
        val currentMilliamps: Int = 0,
        val speed: Float = 0f,
        val battery: Int = 0,
        val controllerTemperature: Int = 0,
        val motorTemperature: Int = 0,
        val batteryTemperature: Int = 0,
        val motorRpm: Int = 0,
    ) {
        val current: Float get() = round2(currentMilliamps / 1000f)
        val power: Float get() = round1(voltage * current)
    }

    /** `0x11` — switch positions, trip counters and the fault bits. */
    data class State(
        val gear: Int = 0,
        val headLight: Boolean = false,
        val tailLight: Boolean = false,
        val kickStart: Boolean = false,
        val cruiseEnabled: Boolean = false,
        val imperial: Boolean = false,
        val powerSwitch: Boolean = false,
        val parking: Boolean = false,
        val horn: Boolean = false,
        val turnLeft: Boolean = false,
        val turnRight: Boolean = false,
        val ambientLight: Boolean = false,
        val bound: Boolean = false,
        val tripDistance: Float = 0f,
        val totalDistance: Int = 0,
        val lightSensor: Int = 0,
        val cruising: Boolean = false,
        val braking: Boolean = false,
        val locked: Boolean = false,
        val communicationFault: Boolean = false,
        val batteryOvervoltage: Boolean = false,
        val batteryUndervoltage: Boolean = false,
        val motorPhaseFault: Boolean = false,
        val charging: Boolean = false,
        val rotorLocked: Boolean = false,
        val hardwareOvercurrent: Boolean = false,
        val controllerFault: Boolean = false,
        val throttleFault: Boolean = false,
        val brakeSensorFault: Boolean = false,
        val motorHalfFault: Boolean = false,
    )

        data class Versions(
        val instrumentId: Int = 0,
        val instrumentHardware: Int = 0,
        val instrumentSoftware: Int = 0,
        val controllerId: Int = 0,
        val controllerHardware: Int = 0,
        val controllerSoftware: Int = 0,
        val gearMask: Int = 0,
    ) {
        /** Gear numbers the dashboard reports as usable, e.g. `[1, 2, 3]` on a stock unit. */
        val gears: List<Int> get() = (0 until 7).filter { bit(gearMask, it) }.map { it + 1 }
    }

        data class Sensors(
        val throttle: Int = 0,
        val brake1: Int = 0,
        val brake2: Int = 0,
        val throttleRaw: Int = 0,
        val brake1Raw: Int = 0,
        val brake2Raw: Int = 0,
    ) {
                val hasSecondBrake: Boolean get() = brake2Raw > 0
    }

        data class Calibration(
        val phaseOrder: Int = 0,
        val coefficient1: Int = 0,
        val coefficient2: Int = 0,
        val coefficient3: Int = 0,
        val displayWrites: Int = 0,
        val motorWrites: Int = 0,
        val trailing: ByteArray = ByteArray(0),
    ) {
        override fun equals(other: Any?): Boolean =
            other is Calibration && phaseOrder == other.phaseOrder &&
                coefficient1 == other.coefficient1 &&
                coefficient2 == other.coefficient2 && coefficient3 == other.coefficient3 &&
                displayWrites == other.displayWrites && motorWrites == other.motorWrites &&
                trailing.contentEquals(other.trailing)

        override fun hashCode(): Int =
            ((((phaseOrder * 31 + coefficient1) * 31 + coefficient2) * 31 + coefficient3) * 31 +
                displayWrites) * 31 + motorWrites
    }

        data class BatteryThresholds(
        val tractionCutoff: Int = 0,
        val bar1: Int = 0,
        val bar2: Int = 0,
        val bar3: Int = 0,
        val bar4: Int = 0,
        val bar5: Int = 0,
    )

    fun telemetry(p: ByteArray): Telemetry? {
        if (p.size < 12) return null
        return Telemetry(
            voltage = round2(u16(p, 0) / 100f),
            currentMilliamps = u16(p, 2),
            speed = round1(u16(p, 4) / 10f),
            battery = u8(p, 6),
            controllerTemperature = u8(p, 7),
            motorTemperature = u8(p, 8),
            batteryTemperature = u8(p, 9),
            motorRpm = u16(p, 10),
        )
    }

    fun state(p: ByteArray): State? {
        if (p.size < 10) return null
        val f1 = u8(p, 0)
        val f2 = u8(p, 1)
        val f3 = u8(p, 8)
        val f4 = u8(p, 9)
        return State(
            gear = f1 and 0x07,
            headLight = bit(f1, 3),
            tailLight = bit(f1, 4),
            kickStart = bit(f1, 5),
            cruiseEnabled = bit(f1, 6),
            imperial = bit(f1, 7),
            powerSwitch = bit(f2, 0),
            parking = bit(f2, 1),
            horn = bit(f2, 2),
            turnLeft = bit(f2, 3),
            turnRight = bit(f2, 4),
            ambientLight = bit(f2, 5),
            bound = bit(f2, 6),
            tripDistance = round2(u16(p, 2) / 100f),
            totalDistance = u16(p, 4),
            lightSensor = u16(p, 6),
            cruising = bit(f3, 0),
            braking = bit(f3, 1),
            locked = bit(f3, 2),
            communicationFault = bit(f3, 3),
            batteryOvervoltage = bit(f3, 4),
            batteryUndervoltage = bit(f3, 5),
            motorPhaseFault = bit(f3, 6),
            charging = bit(f3, 7),
            rotorLocked = bit(f4, 0),
            hardwareOvercurrent = bit(f4, 1),
            controllerFault = bit(f4, 2),
            throttleFault = bit(f4, 3),
            brakeSensorFault = bit(f4, 4),
            motorHalfFault = bit(f4, 5),
        )
    }

    fun versions(p: ByteArray): Versions? {
        if (p.size < 9) return null
        return Versions(
            instrumentId = u16(p, 0),
            instrumentHardware = u8(p, 2),
            instrumentSoftware = u8(p, 3),
            controllerId = u16(p, 4),
            controllerHardware = u8(p, 6),
            controllerSoftware = u8(p, 7),
            gearMask = u8(p, 8),
        )
    }

    fun sensors(p: ByteArray): Sensors? {
        if (p.size < 12) return null
        return Sensors(
            throttle = u16(p, 0),
            brake1 = u16(p, 2),
            brake2 = u16(p, 4),
            throttleRaw = u16(p, 6),
            brake1Raw = u16(p, 8),
            brake2Raw = u16(p, 10),
        )
    }

    fun calibration(p: ByteArray): Calibration? {
        if (p.size < 11) return null
        return Calibration(
            phaseOrder = u8(p, 0),
            coefficient1 = u16(p, 1),
            coefficient2 = u16(p, 3),
            coefficient3 = u16(p, 5),
            displayWrites = u16(p, 7),
            motorWrites = u16(p, 9),
            trailing = p.copyOfRange(11, p.size),
        )
    }

    fun batteryThresholds(p: ByteArray): BatteryThresholds? {
        if (p.size < 6) return null
        return BatteryThresholds(
            tractionCutoff = u8(p, 0),
            bar1 = u8(p, 1),
            bar2 = u8(p, 2),
            bar3 = u8(p, 3),
            bar4 = u8(p, 4),
            bar5 = u8(p, 5),
        )
    }
}
