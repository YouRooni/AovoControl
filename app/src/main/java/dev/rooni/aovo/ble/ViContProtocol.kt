package dev.rooni.aovo.ble

import java.util.UUID

object ViContProtocol {

    private fun uuid(short: String): UUID =
        UUID.fromString("0000$short-0000-1000-8000-00805f9b34fb")

    /** Primary service. [CHARACTERISTIC] is both written to and subscribed for notifications. */
    val SERVICE: UUID = uuid("FEE0")
    val CHARACTERISTIC: UUID = uuid("FEE2")

    /** Older dashboards expose the same protocol here; the stock app subscribes to both. */
    val ALT_SERVICE: UUID = uuid("FFF0")
    val ALT_CHARACTERISTIC: UUID = uuid("FFF1")

    /** Fixed three-byte preamble on everything the app sends. Replies do not carry it. */
    private val PREAMBLE = byteArrayOf(0xFA.toByte(), 0xAF.toByte(), 0xA5.toByte())

        const val ZT_DEFAULT: Byte = 0x5A
    const val ZT_ALT: Byte = 0xFA.toByte()

    // ---- command codes -----------------------------------------------------------

    /** Sent once after subscribing; the dashboard starts streaming telemetry in response. */
    const val CMD_START_TELEMETRY: Int = 0x01

    const val CMD_PARKING: Int = 0x33
    const val CMD_FIND: Int = 0x34
    const val CMD_KICK_START: Int = 0x35
    const val CMD_CRUISE: Int = 0x36
    const val CMD_CABIN_LOCK: Int = 0x37
    const val CMD_HELMET_LOCK: Int = 0x38
    const val CMD_STORAGE_LOCK: Int = 0x39
    const val CMD_BRAKE_LOCK: Int = 0x3A

    const val CMD_MAX_SPEED: Int = 0x3C
    const val CMD_STARTING_TORQUE: Int = 0x3D
    const val CMD_MAX_CURRENT: Int = 0x3E
    const val CMD_EBRAKE_STRENGTH: Int = 0x3F

    const val CMD_AMBIENCE_LAMP: Int = 0x41
    const val CMD_GEAR: Int = 0x42
    const val CMD_UNITS: Int = 0x43
    const val CMD_LIGHT: Int = 0x45
    const val CMD_END_TRIP: Int = 0x47
    const val CMD_WORK_MODEL: Int = 0x4A
    const val CMD_OTA_PREPARE: Int = 0x4B
    const val CMD_BINDING: Int = 0x4C

    const val CMD_OTA_CHUNK: Int = 0xA1
    const val CMD_OTA_VERIFY: Int = 0xA2
    const val CMD_OTA_REBOOT: Int = 0xA3
    const val CMD_VERSION_QUERY: Int = 0xAB
    const val CMD_OTA_CHECK: Int = 0xAC

    const val CMD_MOTOR_LEARN: Int = 0xCA
    const val CMD_MOTOR_PARAMS: Int = 0xCB

    /** Engineering commands. Absent from every stock app; found by probing the dashboard. */
    const val CMD_PARAM_SHOW: Int = 0xC9
    const val CMD_ADC_CALIBRATION_WRITE: Int = 0xD1
    const val CMD_PARAM_WRITE: Int = 0xD2
    const val CMD_BATTERY_THRESHOLDS_WRITE: Int = 0xD3

    /** Notification types the dashboard pushes unprompted. */
    const val RX_TELEMETRY: Int = 0x10
    const val RX_STATE: Int = 0x11
    const val RX_VERSIONS: Int = 0x12
    const val RX_SENSORS: Int = 0x13
    const val RX_CALIBRATION: Int = 0x14
    const val RX_BATTERY_THRESHOLDS: Int = 0x16

    /** Shared argument convention: writes use 1/2, and 0 asks for the current value. */
    const val QUERY: Int = 0x00
    const val OFF: Int = 0x01
    const val ON: Int = 0x02

        const val BIND: Int = 0x01
    const val RELEASE: Int = 0x02

    /** Passed as the value of a tuning command to restore that setting's factory default. */
    const val RESET: Int = 0xFF

    // ---- framing -----------------------------------------------------------------

        fun checksum(zt: Byte, command: Int, payload: ByteArray): Int {
        var sum = (zt.toInt() and 0xFF) + (command and 0xFF) + payload.size
        for (b in payload) sum += b.toInt() and 0xFF
        return sum and 0xFF
    }

        fun command(command: Int, payload: ByteArray = ByteArray(0), zt: Byte = ZT_DEFAULT): ByteArray {
        val frame = ByteArray(PREAMBLE.size + 3 + payload.size + 1)
        PREAMBLE.copyInto(frame)
        frame[3] = zt
        frame[4] = command.toByte()
        frame[5] = payload.size.toByte()
        payload.copyInto(frame, 6)
        frame[frame.size - 1] = checksum(zt, command, payload).toByte()
        return frame
    }

    /** Single-argument form, which covers every command except the lamp, OTA and P-writes. */
    fun command(command: Int, value: Int, zt: Byte = ZT_DEFAULT): ByteArray =
        command(command, byteArrayOf(value.toByte()), zt)

    /** `00` asks the dashboard to report a setting rather than change it. */
    fun query(command: Int, zt: Byte = ZT_DEFAULT): ByteArray = command(command, QUERY, zt)

    /** Wakes the telemetry stream. Sent once, after notifications are enabled. */
    fun startTelemetry(zt: Byte = ZT_DEFAULT): ByteArray = command(CMD_START_TELEMETRY, ByteArray(0), zt)

        fun ambienceLamp(mode: Int, hue: Int, zt: Byte = ZT_DEFAULT): ByteArray = command(
        CMD_AMBIENCE_LAMP,
        byteArrayOf(mode.toByte(), ((hue shr 8) and 0xFF).toByte(), (hue and 0xFF).toByte()),
        zt,
    )

        const val LAMP_QUERY = 0
    const val LAMP_OFF = 1
    const val LAMP_CYCLE = 2
    const val LAMP_CYCLE_BREATHE = 3
    const val LAMP_SOLID = 4
    const val LAMP_SOLID_BREATHE = 5

    /** Shows one engineering parameter on the dashboard for a few seconds. Zero-based. */
    fun showParameter(index: Int, zt: Byte = ZT_DEFAULT): ByteArray =
        command(CMD_PARAM_SHOW, byteArrayOf(index.toByte()), zt)

        fun writeParameter(index: Int, value: Int, zt: Byte = ZT_DEFAULT): ByteArray =
        command(CMD_PARAM_WRITE, byteArrayOf(index.toByte(), value.toByte()), zt)

    // ---- tuning scale ------------------------------------------------------------

        fun tuningToWire(stored: Int, ceiling: Int): Int {
        if (ceiling <= 0) return stored.coerceIn(1, 200)
        return Math.round(stored * 200f / ceiling).coerceIn(1, 200)
    }

    /** What the dashboard will hold after being sent [wire]. */
    fun tuningFromWire(wire: Int, ceiling: Int): Int =
        Math.round(wire / 2f * ceiling / 100f).coerceIn(0, ceiling)

        fun responseToWire(value: Int): Int = (value.coerceIn(1, 10) * 20).coerceIn(1, 200)

    /** Turns a stored reading back into the 1..10 scale, against its own ceiling. */
    fun responseFromStored(stored: Int, ceiling: Int): Int {
        if (ceiling <= 0) return 1
        return Math.round(stored * 10f / ceiling).coerceIn(1, 10)
    }

    // ---- reply parsing -----------------------------------------------------------

        data class Reply(
        val zt: Byte,
        val command: Int,
        val payload: ByteArray,
        val status: Int,
        val checksumValid: Boolean,
    ) {
        val ok: Boolean get() = status == 0

        override fun equals(other: Any?): Boolean =
            other is Reply && zt == other.zt && command == other.command &&
                payload.contentEquals(other.payload) && status == other.status &&
                checksumValid == other.checksumValid

        override fun hashCode(): Int =
            (((zt.hashCode() * 31 + command) * 31 + payload.contentHashCode()) * 31 + status) * 31 +
                checksumValid.hashCode()
    }

        fun parseReply(frame: ByteArray): Reply? {
        if (frame.size < 5) return null
        val length = frame[2].toInt() and 0xFF
        if (frame.size < length + 5) return null
        val payload = frame.copyOfRange(3, 3 + length)
        val zt = frame[0]
        val command = frame[1].toInt() and 0xFF
        return Reply(
            zt = zt,
            command = command,
            payload = payload,
            status = frame[length + 4].toInt() and 0xFF,
            checksumValid = (frame[length + 3].toInt() and 0xFF) == checksum(zt, command, payload),
        )
    }
}
