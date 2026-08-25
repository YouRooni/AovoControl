package dev.rooni.aovo.ble

enum class ParamKind { BIT, RATIO, DIVISOR, RAW, DIAMETER, ENUM, MILEAGE }

data class ParamSpec(
    val id: Int,
    val register: Int,
    val kind: ParamKind,
    val titleRes: Int,
    val bit: Int = -1,
    val min: Float = 0f,
    val max: Float = 0f,
    val step: Float = 1f,
    val real: Int = 0,
    val opv: Float = 1f,
    val unit: String = "",
    val options: List<String> = emptyList(),
    /** Registers the stock app refuses to expose because a wrong value can immobilise the scooter. */
    val expert: Boolean = false,
) {
    fun decode(raw: Int): Float = when (kind) {
        ParamKind.RATIO -> if (real == 0) 0f else (raw.toFloat() * max) / real
        ParamKind.DIVISOR -> addHalf(raw / opv.toDouble()).toFloat()
        ParamKind.DIAMETER -> addHalf(raw * 10.0 / (opv * 10.0)).toFloat()
        else -> raw.toFloat()
    }

    fun encode(value: Float): Int = when (kind) {
        ParamKind.RATIO -> if (max == 0f) 0 else (value * (real / max)).toInt()
        ParamKind.DIVISOR, ParamKind.DIAMETER -> (value * opv).toInt()
        else -> value.toInt()
    }

    /** The stock firmware rounds .1–.3 down, .4–.6 to .5 and .7–.9 up. */
    private fun addHalf(v: Double): Double {
        val text = String.format(java.util.Locale.ENGLISH, "%.1f", v)
        val whole = text.substringBefore('.').toDouble()
        val frac = text.takeLast(1).toDouble()
        return whole + when {
            frac <= 3.0 -> 0.0
            frac >= 7.0 -> 1.0
            else -> 0.5
        }
    }
}

object ParamRegistry {
    // Register map of the controller, byte offsets inside the mirrored register page are 2*register.
    const val REG_STATUS = 0
    const val REG_MODULATION = 2
    const val REG_POLE_PAIRS = 4
    const val REG_THROTTLE_RESPONSE = 9
    const val REG_BRAKE_RESPONSE = 10
    const val REG_DISCHARGE_CURRENT = 11
    const val REG_BRAKING_CURRENT = 12
    const val REG_VOLTAGE_PROTECTION = 19
    const val REG_MOTOR_DIAMETER = 23
    const val REG_SPEED_LIMIT = 32
    const val REG_PWM = 33
    const val REG_CRUISE_TIME = 51
    const val REG_SHUTDOWN_TIME = 52
    const val REG_COMMIT = 73
    const val REG_LAST_SERVICE = 74
    const val REG_SERVICE_MILEAGE = 76

    const val BIT_CRUISE = 9
    const val BIT_METRIC = 6
    const val BIT_ZERO_START = 5
    const val BIT_RESTORE_DEFAULT = 13

    val pwmOptions = listOf("8 kHz", "10 kHz", "12 kHz", "15 kHz", "AUTO")
}
