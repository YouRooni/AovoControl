package dev.rooni.aovo.data

import org.json.JSONArray
import org.json.JSONObject

/** Everything that can occupy a slot on the dashboard. */
enum class TileType(val key: String) {
    Gauge("gauge"),
    Connection("connection"),
    ModeSelector("mode"),

    Headlight("headlight"),
    AmbientLight("ambient"),
    Cruise("cruise"),
    Lock("lock"),

    /** Makes the scooter beep and flash so it can be found in a rack. */
    FindScooter("find"),

    Speed("speed"),

    /** Cap the controller is currently holding the scooter to, for the mode it is in. */
    SpeedLimit("speed_limit"),
    Battery("battery"),
    Voltage("voltage"),
    Current("current"),
    Power("power"),
    EscTemperature("esc_temp"),
    MotorTemperature("motor_temp"),

    /** Motor speed, the one reading a gear limit cannot inflate. */
    MotorRpm("motor_rpm"),

    /** Live throttle position, as the dashboard reads it off the handlebar. */
    Throttle("throttle"),

    /** Live brake position. Shows both levers on the scooters that have two. */
    Brake("brake"),
    Trip("trip"),
    Odometer("odometer"),
    ServiceDue("service_due"),

    /** Applies a saved settings profile in one tap. */
    Profile("profile"),

    /** Deliberate empty space; may be added as many times as the user likes. */
    Spacer("spacer"),
    ;

    /** Full-width by nature: these read badly squeezed into half a row. */
    val prefersFullWidth: Boolean
        get() = this == Gauge || this == ModeSelector

        val allowedSpans: List<Int>
        get() = when (this) {
            Gauge, ModeSelector -> listOf(DashboardLayout.COLUMNS)
            Connection -> listOf(2, DashboardLayout.COLUMNS)
            else -> listOf(1, 2, 3, 4)
        }

    val canResizeWidth: Boolean
        get() = allowedSpans.size > 1

    /** Only tiles whose content can actually use the extra room grow vertically. */
    val canResizeHeight: Boolean
        get() = this == Gauge || this == Spacer

    /** At one column a tile has room for its icon but not its caption. */
    val showsLabelAtSpan: (Int) -> Boolean
        get() = { span -> span > 1 }

    /** Half a row is the natural size for a small tile; the wide ones fill the row. */
    val defaultSpan: Int
        get() = when (this) {
            Gauge, ModeSelector, Connection, Spacer -> DashboardLayout.COLUMNS
            else -> 2
        }

    companion object {
        fun from(key: String): TileType? = entries.firstOrNull { it.key == key }
    }
}

object TileHeights {
    const val MIN = 1
    const val MAX = 8

        fun minFor(type: TileType): Int = when (type) {
        TileType.Gauge -> 3
        else -> MIN
    }

    /** Steps a freshly added tile starts at. */
    fun defaultFor(type: TileType): Int = when (type) {
        TileType.Gauge -> 4
        else -> MIN
    }

    /** Reads the height of a layout saved before heights were numeric. */
    fun fromLegacyKey(key: String): Int? = when (key) {
        "s" -> 1
        "m" -> 2
        "l" -> 3
        else -> null
    }
}

data class DashboardTile(
    val id: String,
    val type: TileType,
    val span: Int = type.defaultSpan,
    val heightStep: Int = TileHeights.defaultFor(type),
    /** Set only for [TileType.Profile]. */
    val profileId: String? = null,
) {
        fun clampedSpan(): Int {
        val allowed = type.allowedSpans
        return allowed.minWithOrNull(
            compareBy({ kotlin.math.abs(it - span) }, { -it })
        ) ?: allowed.first()
    }

    /** Next width when the handle is tapped, wrapping round to the narrowest. */
    fun nextSpan(): Int {
        val allowed = type.allowedSpans
        val index = allowed.indexOf(clampedSpan())
        return allowed[(index + 1) % allowed.size]
    }

    /** Next width one step in [direction], stopping at the ends rather than wrapping. */
    fun stepSpan(direction: Int): Int {
        val allowed = type.allowedSpans
        val index = allowed.indexOf(clampedSpan())
        return allowed[(index + direction).coerceIn(0, allowed.lastIndex)]
    }

    val showsLabel: Boolean get() = type.showsLabelAtSpan(clampedSpan())

    /** Height steps this tile may take; fixed-height tiles report a single step. */
    fun clampedHeight(): Int = if (type.canResizeHeight) {
        heightStep.coerceIn(TileHeights.minFor(type), TileHeights.MAX)
    } else {
        1
    }
}

data class DashboardLayout(val tiles: List<DashboardTile>) {

    /** Rows as they will be drawn, each holding tiles whose spans sum to at most [COLUMNS]. */
    fun rows(): List<List<DashboardTile>> {
        val rows = mutableListOf<MutableList<DashboardTile>>()
        var used = COLUMNS
        for (tile in tiles) {
            val span = tile.clampedSpan()
            if (used + span > COLUMNS) {
                rows.add(mutableListOf(tile))
                used = span
            } else {
                rows.last().add(tile)
                used += span
            }
        }
        return rows
    }

    fun move(id: String, delta: Int): DashboardLayout {
        val index = tiles.indexOfFirst { it.id == id }
        if (index < 0) return this
        val target = (index + delta).coerceIn(0, tiles.lastIndex)
        if (target == index) return this
        val next = tiles.toMutableList()
        next.add(target, next.removeAt(index))
        return copy(tiles = next)
    }

    /** Drops the tile at [from] and reinserts it at [to], as a drag would. */
    fun moveTo(from: Int, to: Int): DashboardLayout {
        if (from !in tiles.indices) return this
        val target = to.coerceIn(0, tiles.lastIndex)
        if (target == from) return this
        val next = tiles.toMutableList()
        next.add(target, next.removeAt(from))
        return copy(tiles = next)
    }

    fun resize(id: String, span: Int): DashboardLayout = copy(
        tiles = tiles.map {
            if (it.id == id) it.copy(span = it.copy(span = span).clampedSpan()) else it
        }
    )

    fun cycleSpan(id: String): DashboardLayout = copy(
        tiles = tiles.map {
            if (it.id == id) it.copy(span = it.nextSpan()) else it
        }
    )

    fun setHeight(id: String, step: Int): DashboardLayout = copy(
        tiles = tiles.map {
            if (it.id == id && it.type.canResizeHeight) {
                it.copy(
                    heightStep = step.coerceIn(TileHeights.minFor(it.type), TileHeights.MAX)
                )
            } else {
                it
            }
        }
    )

    fun cycleHeight(id: String): DashboardLayout = copy(
        tiles = tiles.map {
            if (it.id == id && it.type.canResizeHeight) {
                val next = it.clampedHeight() + 1
                val lowest = TileHeights.minFor(it.type)
                it.copy(heightStep = if (next > TileHeights.MAX) lowest else next)
            } else {
                it
            }
        }
    )

    fun remove(id: String): DashboardLayout = copy(tiles = tiles.filterNot { it.id == id })

    fun add(type: TileType, profileId: String? = null): DashboardLayout = copy(
        tiles = tiles + DashboardTile(
            id = newId(type),
            type = type,
            profileId = profileId,
        )
    )

    /** Drops tiles pointing at a profile that no longer exists. */
    fun pruneProfiles(existing: Set<String>): DashboardLayout = copy(
        tiles = tiles.filterNot { it.type == TileType.Profile && it.profileId !in existing }
    )

    fun encode(): String {
        val array = JSONArray()
        tiles.forEach { tile ->
            array.put(
                JSONObject().apply {
                    put("id", tile.id)
                    put("type", tile.type.key)
                    put("span", tile.clampedSpan())
                    put("hstep", tile.clampedHeight())
                    tile.profileId?.let { put("profile", it) }
                }
            )
        }
        return array.toString()
    }

    companion object {
        /** Column count of the dashboard grid: 4 slots for modern flexibility. */
        const val COLUMNS = 4

        private var counter = 0

        fun newId(type: TileType): String = type.key + "-" + (++counter) + "-" +
            System.currentTimeMillis().toString(36)

        val Default = DashboardLayout(
            listOf(
                DashboardTile("d-connection", TileType.Connection, span = 4),
                DashboardTile("d-gauge", TileType.Gauge, span = 4, heightStep = 4),
                DashboardTile("d-mode", TileType.ModeSelector, span = 4),
                DashboardTile("d-speed-limit", TileType.SpeedLimit, span = 4),
                DashboardTile("d-headlight", TileType.Headlight, span = 2),
                DashboardTile("d-lock", TileType.Lock, span = 2),
                DashboardTile("d-cruise", TileType.Cruise, span = 2),
                DashboardTile("d-ambient", TileType.AmbientLight, span = 2),
                DashboardTile("d-power", TileType.Power, span = 2),
                DashboardTile("d-battery", TileType.Battery, span = 2),
                DashboardTile("d-trip", TileType.Trip, span = 2),
                DashboardTile("d-odo", TileType.Odometer, span = 2),
            )
        )

        fun decode(raw: String?): DashboardLayout {
            if (raw.isNullOrBlank()) return Default
            return runCatching {
                val array = JSONArray(raw)
                val tiles = (0 until array.length()).mapNotNull { index ->
                    val item = array.getJSONObject(index)
                    val type = TileType.from(item.optString("type")) ?: return@mapNotNull null
                    DashboardTile(
                        id = item.optString("id").ifBlank { newId(type) },
                        type = type,
                        span = item.optInt("span", type.defaultSpan),
                        heightStep = item.optInt(
                            "hstep",
                            TileHeights.fromLegacyKey(item.optString("height"))
                                ?: TileHeights.defaultFor(type),
                        ),
                        profileId = item.optString("profile").ifBlank { null },
                    )
                }
                if (tiles.isEmpty()) Default else DashboardLayout(tiles)
            }.getOrDefault(Default)
        }
    }
}
