package dev.rooni.aovo.ble

import java.util.Locale

data class FrameNote(
    val label: String,
    val detail: String = "",
    val stream: String? = null,
)

object FrameSummary {

    fun outgoing(family: ScooterFamily, frame: ByteArray): FrameNote =
        if (family == ScooterFamily.VICONT) viContOutgoing(frame) else zydOutgoing(frame)

    fun incoming(family: ScooterFamily, frame: ByteArray): FrameNote =
        if (family == ScooterFamily.VICONT) viContIncoming(frame) else zydIncoming(frame)

    // ---- ViCont ------------------------------------------------------------------

    private fun viContOutgoing(f: ByteArray): FrameNote {
        if (f.size < 6) return FrameNote("write")
        val command = f[4].toInt() and 0xFF
        val payload = f.copyOfRange(6, (6 + (f[5].toInt() and 0xFF)).coerceAtMost(f.size))
        val name = viContName(command)
        val argument = when {
            payload.isEmpty() -> ""
            command == ViContProtocol.CMD_PARAM_WRITE && payload.size >= 2 ->
                "P${(payload[0].toInt() and 0xFF) + 1} = ${payload[1].toInt() and 0xFF}"
            payload.size == 1 -> viContArgument(command, payload[0].toInt() and 0xFF)
            else -> payload.joinToString(" ") { String.format(Locale.ROOT, "%02X", it) }
        }
        return FrameNote(name, argument)
    }

    private fun viContArgument(command: Int, value: Int): String = when {
        // Find takes any non-zero value, so 1 means on here rather than the usual off.
        command == ViContProtocol.CMD_FIND -> if (value == 0) "off" else "on"
        value == ViContProtocol.QUERY -> "query"
        value == ViContProtocol.RESET -> "reset"
        command == ViContProtocol.CMD_PARKING -> if (value == ViContProtocol.ON) "lock" else "unlock"
        command == ViContProtocol.CMD_UNITS -> if (value == ViContProtocol.ON) "mph" else "km/h"
        command == ViContProtocol.CMD_GEAR -> "gear $value"
        value == ViContProtocol.ON -> "on"
        value == ViContProtocol.OFF -> "off"
        else -> value.toString()
    }

    private fun viContName(command: Int): String = when (command) {
        ViContProtocol.CMD_START_TELEMETRY -> "start telemetry"
        ViContProtocol.CMD_PARKING -> "parking"
        ViContProtocol.CMD_FIND -> "find scooter"
        ViContProtocol.CMD_KICK_START -> "kick start"
        ViContProtocol.CMD_CRUISE -> "cruise"
        ViContProtocol.CMD_MAX_SPEED -> "max speed"
        ViContProtocol.CMD_STARTING_TORQUE -> "starting torque"
        ViContProtocol.CMD_MAX_CURRENT -> "max current"
        ViContProtocol.CMD_EBRAKE_STRENGTH -> "brake strength"
        ViContProtocol.CMD_AMBIENCE_LAMP -> "ambience lamp"
        ViContProtocol.CMD_GEAR -> "gear"
        ViContProtocol.CMD_UNITS -> "units"
        ViContProtocol.CMD_LIGHT -> "light"
        ViContProtocol.CMD_END_TRIP -> "end trip"
        ViContProtocol.CMD_BINDING -> "binding"
        ViContProtocol.CMD_VERSION_QUERY -> "version query"
        ViContProtocol.CMD_PARAM_SHOW -> "show parameter"
        ViContProtocol.CMD_PARAM_WRITE -> "write parameter"
        else -> String.format(Locale.ROOT, "cmd %02X", command)
    }

    private fun viContIncoming(f: ByteArray): FrameNote {
        val reply = ViContProtocol.parseReply(f) ?: return FrameNote("unparsed", hexOf(f))
        val suffix = if (reply.ok) "" else " status ${reply.status}"
        return when (reply.command) {
            ViContProtocol.RX_TELEMETRY -> {
                val t = ViContDecoder.telemetry(reply.payload)
                    ?: return FrameNote("telemetry", hexOf(reply.payload), "telemetry")
                FrameNote(
                    "telemetry",
                    String.format(Locale.ROOT, "%.1f km/h  %d%%  %.2f V  %.2f A  %d rpm",
                        t.speed, t.battery, t.voltage, t.current, t.motorRpm,
                    ),
                    "telemetry",
                )
            }

            ViContProtocol.RX_STATE -> {
                val s = ViContDecoder.state(reply.payload)
                    ?: return FrameNote("state", hexOf(reply.payload), "state")
                val switches = buildList {
                    add("gear ${s.gear}")
                    if (s.headLight) add("light")
                    if (s.parking) add("parked")
                    if (s.cruising) add("cruising")
                    if (s.braking) add("braking")
                    if (s.charging) add("charging")
                    if (s.bound) add("bound")
                }
                val faults = faultsOf(s)
                val detail = switches.joinToString(" ") +
                    String.format(Locale.ROOT, "  trip %.2f km", s.tripDistance) +
                    if (faults.isEmpty()) "" else "  FAULT ${faults.joinToString(",")}"
                FrameNote("state", detail, "state")
            }

            ViContProtocol.RX_VERSIONS -> {
                val v = ViContDecoder.versions(reply.payload)
                    ?: return FrameNote("versions", hexOf(reply.payload))
                FrameNote(
                    "versions",
                    String.format(Locale.ROOT, "display %d.%d  controller %d.%d  gears %s",
                        v.instrumentHardware, v.instrumentSoftware,
                        v.controllerHardware, v.controllerSoftware,
                        v.gears.joinToString(","),
                    ),
                    "versions",
                )
            }

            ViContProtocol.RX_BATTERY_THRESHOLDS -> {
                val b = ViContDecoder.batteryThresholds(reply.payload)
                    ?: return FrameNote("battery levels", hexOf(reply.payload), "battery levels")
                FrameNote(
                    "battery levels",
                    String.format(
                        Locale.ROOT,
                        "cutoff %d V  bars %d/%d/%d/%d/%d",
                        b.tractionCutoff, b.bar1, b.bar2, b.bar3, b.bar4, b.bar5,
                    ),
                    "battery levels",
                )
            }

            ViContProtocol.RX_SENSORS -> {
                val s = ViContDecoder.sensors(reply.payload)
                    ?: return FrameNote("sensors", hexOf(reply.payload), "sensors")
                FrameNote(
                    "sensors",
                    "throttle ${s.throttle} (${s.throttleRaw})  brake ${s.brake1} (${s.brake1Raw})",
                    "sensors",
                )
            }

            ViContProtocol.RX_CALIBRATION -> FrameNote("calibration", hexOf(reply.payload), "calibration")

            else -> FrameNote(
                viContName(reply.command) + " reply",
                hexOf(reply.payload) + suffix,
            )
        }
    }

    private fun faultsOf(s: ViContDecoder.State): List<String> = buildList {
        if (s.rotorLocked) add("rotor")
        if (s.hardwareOvercurrent) add("overcurrent")
        if (s.controllerFault) add("controller")
        if (s.throttleFault) add("throttle")
        if (s.brakeSensorFault) add("brake sensor")
        if (s.motorHalfFault) add("motor")
        if (s.communicationFault) add("comms")
        if (s.batteryOvervoltage) add("overvoltage")
        if (s.batteryUndervoltage) add("undervoltage")
        if (s.motorPhaseFault) add("phase")
    }

    // ---- ZYD ---------------------------------------------------------------------

    private fun zydOutgoing(f: ByteArray): FrameNote = when {
        f.isEmpty() -> FrameNote("write")

        f[0] == Protocol.HEAD_TRAN -> when (f.getOrNull(1)) {
            Protocol.CMD_KEEP -> FrameNote("keepalive", stream = "keepalive")
            Protocol.CMD_TRAN -> FrameNote("transparent on")
            Protocol.CMD_TRAN_STOP -> FrameNote("transparent off")
            Protocol.CMD_PACK -> FrameNote("pack mode")
            else -> FrameNote(String.format(Locale.ROOT, "tran %02X", f.getOrNull(1) ?: 0))
        }

        f[0] == Protocol.HEAD_MONITOR && f.size >= 8 ->
            FrameNote("control", String.format(Locale.ROOT, "flags %02X  limits %d/%d/%d/%d",
                f[3], f[4].toInt() and 0xFF, f[5].toInt() and 0xFF,
                f[6].toInt() and 0xFF, f[7].toInt() and 0xFF,
            ))

        f[0] == Protocol.HEAD_ESC && f.size >= 4 -> when (f[1]) {
            Protocol.CMD_READ_PARAMETER -> FrameNote("read registers", "at ${beOf(f, 2)} x${beOf(f, 4)}")
            Protocol.CMD_RW_PARAMETER -> FrameNote("write register", "at ${beOf(f, 2)}")
            Protocol.CMD_ESC_INFO -> FrameNote("read identity", "at ${beOf(f, 2)}")
            Protocol.CMD_BAT_INFO -> FrameNote("read battery", "at ${beOf(f, 2)}")
            Protocol.CMD_UPDATE_FM -> FrameNote("firmware chunk", "#${beOf(f, 2)}")
            Protocol.CMD_HANDSHAKE -> FrameNote("firmware handshake")
            Protocol.CMD_ERASE_FLASH -> FrameNote("erase flash")
            Protocol.CMD_BOOT_EXIT -> FrameNote("exit bootloader")
            else -> FrameNote(String.format(Locale.ROOT, "esc %02X", f[1]))
        }

        else -> FrameNote("write", hexOf(f))
    }

    private fun zydIncoming(f: ByteArray): FrameNote = when {
        f.size >= 23 && f[0] == Protocol.HEAD_MONITOR && f[1] == 0x00.toByte() -> {
            val t = Decoder.telemetry(f)
            FrameNote(
                "telemetry",
                String.format(Locale.ROOT, "%.1f km/h  %d%%  %.1f V  %.1f A", t.speed, t.battery, t.voltage, t.current),
                "telemetry",
            )
        }

        f.size >= 8 && f[0] == Protocol.HEAD_MONITOR && f[1] == 0x01.toByte() ->
            FrameNote("limits", String.format(Locale.ROOT, "%d/%d/%d/%d",
                f[3].toInt() and 0xFF, f[4].toInt() and 0xFF,
                f[5].toInt() and 0xFF, f[6].toInt() and 0xFF,
            ), "limits")

        f.size >= 2 && f[0] == Protocol.HEAD_ESC -> when (f[1]) {
            Protocol.CMD_READ_PARAMETER -> FrameNote("registers", "at ${beOf(f, 2)}")
            Protocol.CMD_RW_PARAMETER, Protocol.CMD_WRITE_PARAMETER ->
                FrameNote("write accepted", "at ${beOf(f, 2)}")
            Protocol.CMD_ESC_INFO -> FrameNote("identity", "at ${beOf(f, 2)}")
            Protocol.CMD_BAT_INFO -> FrameNote("battery info")
            Protocol.CMD_UPDATE_FM -> FrameNote("chunk accepted", "#${beOf(f, 2)}")
            Protocol.CMD_HANDSHAKE -> FrameNote("handshake ok")
            Protocol.CMD_ERASE_FLASH -> FrameNote("erase ok")
            Protocol.CMD_WRITE_PARAM_FAIL, Protocol.CMD_RW_PARAM_FAIL ->
                FrameNote("write refused")
            Protocol.CMD_READ_PARAM_FAIL -> FrameNote("read refused")
            else -> FrameNote(String.format(Locale.ROOT, "esc %02X", f[1]), hexOf(f))
        }

        else -> FrameNote("frame", hexOf(f))
    }

    private fun beOf(f: ByteArray, index: Int): Int =
        if (f.size <= index + 1) 0 else Decoder.u16(f, index)

    private fun hexOf(f: ByteArray): String = f.joinToString(" ") { String.format(Locale.ROOT, "%02X", it) }
}
