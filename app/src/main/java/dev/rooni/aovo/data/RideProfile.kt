package dev.rooni.aovo.data

import org.json.JSONArray
import org.json.JSONObject

data class RideProfile(
    val id: String,
    val name: String,
    /** Key of the icon shown on the card and on a dashboard tile. */
    val icon: String = ProfileIcons.DEFAULT,
    val gear: Int? = null,
    val headLight: Boolean? = null,
    val ambientLight: Boolean? = null,
    val cruiseControl: Boolean? = null,
    val zeroStart: Boolean? = null,
    val imperial: Boolean? = null,
    val limitCruise: Int? = null,
    val limitEco: Int? = null,
    val limitComfort: Int? = null,
    val limitSport: Int? = null,
    val throttleResponse: Int? = null,
    val brakeResponse: Int? = null,
    val speedLimit: Int? = null,
) {
    /** True when the profile touches anything the dashboard can send in one control frame. */
    val touchesRideState: Boolean
        get() = gear != null || headLight != null || ambientLight != null ||
            cruiseControl != null || zeroStart != null || imperial != null ||
            limitCruise != null || limitEco != null || limitComfort != null ||
            limitSport != null

    /** True when the profile writes controller registers, which is the slow path. */
    val touchesRegisters: Boolean
        get() = throttleResponse != null || brakeResponse != null || speedLimit != null

    /** True when the profile carries per-mode speed caps. */
    val touchesModeLimits: Boolean
        get() = limitCruise != null || limitEco != null || limitComfort != null ||
            limitSport != null

        val touchesStoppedOnly: Boolean
        get() = touchesModeLimits || touchesRegisters

    /** True when something in the profile can still be sent while the scooter is rolling. */
    val touchesWhileMoving: Boolean
        get() = gear != null || headLight != null || ambientLight != null ||
            cruiseControl != null || zeroStart != null || imperial != null

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("icon", icon)
        gear?.let { put("gear", it) }
        headLight?.let { put("head", it) }
        ambientLight?.let { put("ambient", it) }
        cruiseControl?.let { put("cruise", it) }
        zeroStart?.let { put("zero", it) }
        imperial?.let { put("imperial", it) }
        limitCruise?.let { put("lim_cruise", it) }
        limitEco?.let { put("lim_eco", it) }
        limitComfort?.let { put("lim_comfort", it) }
        limitSport?.let { put("lim_sport", it) }
        throttleResponse?.let { put("throttle", it) }
        brakeResponse?.let { put("brake", it) }
        speedLimit?.let { put("speed_limit", it) }
    }

    companion object {
        private fun JSONObject.intOrNull(key: String): Int? =
            if (has(key)) optInt(key) else null

        private fun JSONObject.boolOrNull(key: String): Boolean? =
            if (has(key)) optBoolean(key) else null

        fun fromJson(json: JSONObject): RideProfile? {
            val id = json.optString("id").ifBlank { return null }
            return RideProfile(
                id = id,
                name = json.optString("name").ifBlank { id },
                icon = json.optString("icon").ifBlank { ProfileIcons.DEFAULT },
                gear = json.intOrNull("gear"),
                headLight = json.boolOrNull("head"),
                ambientLight = json.boolOrNull("ambient"),
                cruiseControl = json.boolOrNull("cruise"),
                zeroStart = json.boolOrNull("zero"),
                imperial = json.boolOrNull("imperial"),
                limitCruise = json.intOrNull("lim_cruise"),
                limitEco = json.intOrNull("lim_eco"),
                limitComfort = json.intOrNull("lim_comfort"),
                limitSport = json.intOrNull("lim_sport"),
                throttleResponse = json.intOrNull("throttle"),
                brakeResponse = json.intOrNull("brake"),
                speedLimit = json.intOrNull("speed_limit"),
            )
        }

        fun encode(profiles: List<RideProfile>): String {
            val array = JSONArray()
            profiles.forEach { array.put(it.toJson()) }
            return array.toString()
        }

        fun decode(raw: String?): List<RideProfile> {
            if (raw.isNullOrBlank()) return emptyList()
            return runCatching {
                val array = JSONArray(raw)
                (0 until array.length()).mapNotNull { fromJson(array.getJSONObject(it)) }
            }.getOrDefault(emptyList())
        }

        fun newId(): String = "p-" + System.currentTimeMillis().toString(36) +
            "-" + (0..0xFFFF).random().toString(16)
    }
}

/** Icons a profile may be given. Stored as keys so the data layer stays free of Compose. */
object ProfileIcons {
    const val DEFAULT = "bookmark"

    val ALL = listOf(
        DEFAULT,
        "bolt",
        "eco",
        "sport",
        "rocket",
        "turtle",
        "night",
        "sun",
        "rain",
        "snow",
        "city",
        "offroad",
        "hill",
        "route",
        "work",
        "home",
        "school",
        "shop",
        "shield",
        "lock",
        "star",
        "heart",
        "fire",
        "battery",
    )

    fun normalise(key: String): String = if (key in ALL) key else DEFAULT
}

data class ProfileCapture(
    /** Riding mode, lights, cruise, start mode and units. */
    val ride: Boolean = true,
    /** Per-mode speed caps sent to the dashboard; not every controller honours these. */
    val modeLimits: Boolean = false,
    val throttleResponse: Boolean = true,
    val brakeResponse: Boolean = true,
    val speedLimit: Boolean = true,
) {
    val any: Boolean
        get() = ride || modeLimits || throttleResponse || brakeResponse || speedLimit

    /** True when anything in this capture needs the controller registers to have been read. */
    val needsRegisters: Boolean
        get() = throttleResponse || brakeResponse || speedLimit
}
