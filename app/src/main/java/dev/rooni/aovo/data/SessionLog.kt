package dev.rooni.aovo.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** What a log line describes, and how it is marked in the exported text. */
enum class LogKind(val glyph: String) {
    /** Written to the scooter. */
    TX("->"),

    /** Received from the scooter. */
    RX("<-"),

    /** Connection or app state changed. */
    STATE(" *"),

    /** Something went wrong, or was refused. */
    WARN(" !"),
}

data class LogEntry(
    val at: Long,
    val kind: LogKind,
    val label: String,
    val detail: String = "",
    val repeats: Int = 1,
)

object SessionLog {

    private val _enabled = MutableStateFlow(false)
    val enabled = _enabled.asStateFlow()

    private val _entries = MutableStateFlow<List<LogEntry>>(emptyList())
    val entries = _entries.asStateFlow()

    private val lock = Any()

        private val lastStreamAt = HashMap<String, Long>()

        const val CAPACITY = 6000

    /** Telemetry is sampled at most this often; everything else is logged as it happens. */
    const val TELEMETRY_INTERVAL_MS = 3_000L

    /** Longest hex dump kept on one line before it is elided. */
    const val MAX_HEX_BYTES = 24

    /** Turning logging off throws away what was collected; it is a diagnostic, not a record. */
    fun setEnabled(on: Boolean) {
        if (on == _enabled.value) return
        _enabled.value = on
        if (!on) clear() else state("Logging started")
    }

    fun clear() {
        synchronized(lock) {
            lastStreamAt.clear()
            _entries.value = emptyList()
        }
    }

    fun tx(label: String, detail: String = "", payload: ByteArray? = null) =
        add(LogKind.TX, label, join(detail, hex(payload)))

    fun rx(label: String, detail: String = "", payload: ByteArray? = null) =
        add(LogKind.RX, label, join(detail, hex(payload)))

    fun state(label: String, detail: String = "") = add(LogKind.STATE, label, detail)

    fun warn(label: String, detail: String = "") = add(LogKind.WARN, label, detail)

        fun stream(name: String, summary: String, now: Long = System.currentTimeMillis()) {
        if (!_enabled.value) return
        synchronized(lock) {
            val previous = lastStreamAt[name]
            if (previous != null && now - previous < TELEMETRY_INTERVAL_MS) return
            lastStreamAt[name] = now
        }
        add(LogKind.RX, name, summary, now)
    }

    fun telemetry(summary: String, now: Long = System.currentTimeMillis()) =
        stream("telemetry", summary, now)

    private fun add(
        kind: LogKind,
        label: String,
        detail: String,
        now: Long = System.currentTimeMillis(),
    ) {
        if (!_enabled.value) return
        synchronized(lock) {
            val current = _entries.value
            val last = current.lastOrNull()
            // Fold a repeat rather than printing the same line again.
            if (last != null && last.kind == kind && last.label == label && last.detail == detail) {
                _entries.value = current.dropLast(1) + last.copy(repeats = last.repeats + 1)
                return
            }
            val appended = current + LogEntry(now, kind, label, detail)
            _entries.value =
                if (appended.size > CAPACITY) appended.takeLast(CAPACITY) else appended
        }
    }

    private fun join(detail: String, hex: String): String = when {
        detail.isEmpty() -> hex
        hex.isEmpty() -> detail
        else -> "$detail   $hex"
    }

    private fun hex(payload: ByteArray?): String {
        if (payload == null || payload.isEmpty()) return ""
        val shown = payload.take(MAX_HEX_BYTES).joinToString(" ") { "%02X".format(it) }
        val hidden = payload.size - MAX_HEX_BYTES
        return if (hidden > 0) "$shown +$hidden" else shown
    }

    /** Renders the buffer as the text an export writes out. */
    fun export(entries: List<LogEntry> = _entries.value): String {
        if (entries.isEmpty()) return "No log entries.\n"
        val builder = StringBuilder()
        builder.append("AovoControl log, ").append(entries.size).append(" entries\n\n")
        for (entry in entries) {
            builder.append(TIME.format(Instant.ofEpochMilli(entry.at)))
            builder.append(' ').append(entry.kind.glyph).append(' ')
            builder.append(entry.label)
            if (entry.detail.isNotEmpty()) builder.append("  ").append(entry.detail)
            if (entry.repeats > 1) builder.append("  x").append(entry.repeats)
            builder.append('\n')
        }
        return builder.toString()
    }

    private val TIME: DateTimeFormatter =
        DateTimeFormatter.ofPattern("HH:mm:ss.SSS").withZone(ZoneId.systemDefault())
}
