package dev.rooni.aovo.ble

/** Live telemetry pushed by the dashboard ~10x/s. */
data class Telemetry(
    val speed: Float = 0f,
    val battery: Int = 0,
    val voltage: Float = 0f,
    val current: Float = 0f,
    val power: Float = 0f,
    val escTemperature: Int = 0,
    val motorTemperature: Int = 0,
    val tripDistance: Float = 0f,
    val totalDistance: Float = 0f,
    val statusRegister: Int = 0,
        val motorRpm: Int = 0,
) {
    val faultCode: String get() = "%04X".format(statusRegister)

        val settingsLocked: Boolean get() = speed > SETTINGS_LOCK_SPEED_KMH

    companion object {
        /** Same threshold the stock app locks its limit and response screens at. */
        const val SETTINGS_LOCK_SPEED_KMH = 3f
    }
}

/** Switch state and per-mode speed limits, mirrored from the dashboard. */
data class RideState(
    val gear: Int = 0,
    val headLight: Boolean = false,
    val ambientLight: Boolean = false,
    val cruiseControl: Boolean = false,
    val zeroStart: Boolean = false,
    val imperial: Boolean = false,
    val locked: Boolean = false,
    val limitCruise: Int = 0,
    val limitEco: Int = 0,
    val limitComfort: Int = 0,
    val limitSport: Int = 0,
        val limitGear4: Int = 0,
    val limitGear5: Int = 0,
    val displayName: String = "",
    val displayVersion: String = "",
    val serviceMileage: Int = 0,
    val lastServiceMileage: Int = 0,
) {
        fun flagByte(): Byte {
        var v = 0
        v = v or ((gear shr 0) and 1)
        v = v or (((gear shr 1) and 1) shl 1)
        if (headLight) v = v or (1 shl 2)
        if (ambientLight) v = v or (1 shl 3)
        if (cruiseControl) v = v or (1 shl 4)
        if (zeroStart) v = v or (1 shl 5)
        if (imperial) v = v or (1 shl 6)
        if (!locked) v = v or (1 shl 7)
        return v.toByte()
    }
}

/** Full controller register snapshot (the "advanced" parameter set). */
data class ControllerParams(
    val cruiseEnabled: Boolean = false,
    val metric: Boolean = true,
    val zeroStart: Boolean = false,
    val maxModulationDepth: Int = 50,
    val motorPolePairs: Int = 15,
    val throttleResponse: Int = 1,
    val brakeResponse: Int = 1,
    val maxDischargeCurrent: Float = 15f,
    val maxBrakingCurrent: Float = 15f,
    val voltageProtection: Float = 31f,
    val speedLimit: Int = 25,
    val motorDiameter: Float = 8.5f,
    val pwmFrequency: Int = 0,
    val cruiseActivationTime: Int = 5,
    val autoShutdownTime: Int = 5,
    val serviceMileage: Int = 300,
    val lastServiceMileage: Int = 0,
    val registerZero: Int = 0,
    val loaded: Boolean = true,
        val speedLimitCeiling: Int? = null,
    val dischargeCurrentCeiling: Int? = null,
    val throttleResponseCeiling: Int? = null,
    val brakeResponseCeiling: Int? = null,
)

/** Controller identity block, read 8 bytes at a time from register 0 of the info page. */
data class EscInfo(
    val model: String = "",
    val hardware: String = "",
    val firmware: String = "",
    val bootloader: String = "",
    val uniqueCode: String = "",
) {
    val isEmpty: Boolean get() = model.isBlank() && hardware.isBlank()
}

enum class AuthMode { NONE, DEFAULT_PASSWORD, USER_PASSWORD }

data class ScannedDevice(
    val name: String,
    val address: String,
    val rssi: Int,
) {
        val isScooter: Boolean
        get() = SCOOTER_PREFIXES.any { name.startsWith(it, ignoreCase = true) }

    val authMode: AuthMode
        get() = when {
            name.startsWith("hw_ug", ignoreCase = true) -> AuthMode.USER_PASSWORD
            name.startsWith("hw_z", ignoreCase = true) -> AuthMode.NONE
            name.startsWith("zyd", ignoreCase = true) -> AuthMode.NONE
            else -> AuthMode.DEFAULT_PASSWORD
        }

    /** True only when the owner has to supply the password themselves. */
    val requiresPassword: Boolean get() = authMode == AuthMode.USER_PASSWORD

    companion object {
                val SCOOTER_PREFIXES = listOf("hw", "zyd", "sn", "e-s", "vc_", "vc-", "va-")

        /** Factory default burned into every module the stock app talks to. */
        const val DEFAULT_PASSWORD = "888888"
    }
}

enum class ConnectionState { IDLE, SCANNING, CONNECTING, AUTHENTICATING, CONNECTED, DISCONNECTED }

enum class OtaState { IDLE, HANDSHAKE, ERASING, WRITING, DONE, FAILED }

data class OtaProgress(val state: OtaState = OtaState.IDLE, val percent: Int = 0, val message: String = "")

/** Things worth telling the user about that are not part of the steady state. */
sealed interface CoreEvent {
    data class Error(val message: String) : CoreEvent
    data class Info(val message: String) : CoreEvent
    data object WrongPassword : CoreEvent
    data object PasswordTimeout : CoreEvent
    data class PasswordChanged(val ok: Boolean) : CoreEvent
    data class NameChanged(val ok: Boolean) : CoreEvent
    data class NfcState(val enabled: Boolean) : CoreEvent
    data object NfcCardsCleared : CoreEvent
    data class VoiceState(val type: Int) : CoreEvent
    data class DriveMode(val type: Int) : CoreEvent
    data class DeviceType(val value: String) : CoreEvent
}
