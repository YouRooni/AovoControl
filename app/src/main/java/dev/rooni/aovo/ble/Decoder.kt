package dev.rooni.aovo.ble

import java.util.Locale

object Decoder {

    fun u16(a: ByteArray, i: Int): Int =
        ((a[i].toInt() and 0xFF) shl 8) or (a[i + 1].toInt() and 0xFF)

    fun s16(a: ByteArray, i: Int): Int = (u16(a, i) shl 16) shr 16

    fun u32(a: ByteArray, i: Int): Int =
        ((a[i].toInt() and 0xFF) shl 24) or ((a[i + 1].toInt() and 0xFF) shl 16) or
            ((a[i + 2].toInt() and 0xFF) shl 8) or (a[i + 3].toInt() and 0xFF)

    fun bit(value: Int, index: Int): Boolean = (value shr index) and 1 == 1

    fun round1(v: Float): Float = Math.round(v * 10f) / 10f

        fun addHalf(v: Double): Double {
        val text = String.format(Locale.ENGLISH, "%.1f", v)
        val negative = text.startsWith("-")
        val body = if (negative) text.substring(1) else text
        val whole = body.substringBefore('.').toDouble()
        val frac = body.takeLast(1).toDouble()
        val magnitude = whole + when {
            frac <= 3.0 -> 0.0
            frac >= 7.0 -> 1.0
            else -> 0.5
        }
        return if (negative) -magnitude else magnitude
    }

    /** Monitor frame subtype 0: speed, battery and the switch/status register. */
    fun telemetry(f: ByteArray): Telemetry {
        val rpmA = u16(f, 6)
        val rpmB = u16(f, 8)
        val voltage = round1(u16(f, 10) / 10f)
        val current = round1(s16(f, 12) / 64f)
        return Telemetry(
            speed = round1(maxOf(rpmA, rpmB) / 1000f),
            battery = f[5].toInt() and 0xFF,
            voltage = voltage,
            current = current,
            power = round1(voltage * current),
            escTemperature = f[14].toInt(),
            motorTemperature = f[15].toInt(),
            tripDistance = round1(u16(f, 16) / 10f),
            totalDistance = round1(
                (((f[18].toInt() and 0xFF) shl 16) or
                    ((f[19].toInt() and 0xFF) shl 8) or
                    (f[20].toInt() and 0xFF)) / 10f
            ),
            statusRegister = u16(f, 21),
        )
    }

        fun switches(previous: RideState, f: ByteArray): RideState {
        val status = u16(f, 21)
        return previous.copy(
            gear = f[4].toInt() and 0xFF,
            headLight = bit(status, 2),
            ambientLight = bit(status, 15),
            cruiseControl = bit(status, 9),
            zeroStart = bit(status, 5),
            imperial = bit(status, 6),
            locked = !bit(status, 11),
        )
    }

    /** Monitor frame subtype 1: per-mode speed limits and the dashboard identity. */
    fun limits(previous: RideState, f: ByteArray): RideState {
        val name = "%02X%02X".format(f[18], f[19])
        return previous.copy(
            limitCruise = f[3].toInt() and 0xFF,
            limitEco = f[4].toInt() and 0xFF,
            limitComfort = f[5].toInt() and 0xFF,
            limitSport = f[6].toInt() and 0xFF,
            displayName = if (name == "0000") previous.displayName else name,
            displayVersion = if (name == "0000") previous.displayVersion else
                "V" + f[20].toInt() + "." + f[21].toInt() + "." + f[22].toInt(),
        )
    }

        fun params(page: ByteArray): ControllerParams {
        val status = s16(page, 0)
        val rawThrottle = s16(page, 18)
        val throttle = when {
            rawThrottle > 10 -> Math.round(rawThrottle / 3000.0).toInt().coerceIn(1, 10)
            rawThrottle > 0 -> rawThrottle.coerceIn(1, 10)
            else -> 1
        }
        val rawBrake = s16(page, 20)
        val brake = when {
            rawBrake > 10 -> Math.round(rawBrake / 3000.0).toInt().coerceIn(1, 10)
            rawBrake > 0 -> rawBrake.coerceIn(1, 10)
            else -> 1
        }
        val rawSpeed = s16(page, 64)
        val speed = when {
            rawSpeed > 60 -> Math.round(rawSpeed / 10.0).toInt().coerceIn(2, 60)
            rawSpeed in 2..60 -> rawSpeed
            else -> 25
        }

        return ControllerParams(
            cruiseEnabled = bit(status, ParamRegistry.BIT_CRUISE),
            metric = !bit(status, ParamRegistry.BIT_METRIC),
            zeroStart = bit(status, ParamRegistry.BIT_ZERO_START),
            maxModulationDepth = if (s16(page, 4) > 0) s16(page, 4) * 50 / 21845 else 50,
            motorPolePairs = if (s16(page, 8) > 0) s16(page, 8) else 15,
            throttleResponse = throttle,
            brakeResponse = brake,
            maxDischargeCurrent = if (s16(page, 22) > 0) addHalf(s16(page, 22) / 64.0).toFloat() else 15f,
            maxBrakingCurrent = if (s16(page, 24) > 0) addHalf(s16(page, 24) / 64.0).toFloat() else 15f,
            voltageProtection = if (s16(page, 38) > 0) addHalf(s16(page, 38) / 10.0).toFloat() else 31f,
            speedLimit = speed,
            motorDiameter = if (s16(page, 46) > 0) addHalf(s16(page, 46) * 10.0 / 254.0).toFloat() else 8.5f,
            pwmFrequency = s16(page, 66),
            cruiseActivationTime = if (s16(page, 102) > 0) s16(page, 102) else 5,
            autoShutdownTime = if (s16(page, 104) > 0) s16(page, 104) else 5,
            serviceMileage = if (s16(page, 152) > 0) s16(page, 152) else 300,
            lastServiceMileage = u32(page, 148),
            registerZero = status and 0xFFFF,
            loaded = true,
        )
    }

    /** Controller identity, five 16-byte space-padded ASCII fields. */
    fun escInfo(page: ByteArray): EscInfo {
        fun field(offset: Int) = String(page, offset, 16, Charsets.UTF_8).trim { it <= ' ' }
        return EscInfo(
            model = field(0),
            hardware = field(16),
            bootloader = field(32),
            firmware = field(48),
            uniqueCode = field(64),
        )
    }
}
