package dev.rooni.aovo.ble

class FrameReader(private val onFrame: (ByteArray) -> Unit) {

    private val buffer = ArrayDeque<Byte>()
    private val maxBuffer = 4096

    fun reset() = buffer.clear()

    fun append(data: ByteArray) {
        if (data.isEmpty()) return
        for (b in data) buffer.addLast(b)
        while (buffer.size > maxBuffer) buffer.removeFirst()
        while (step()) { /* drain every complete frame */ }
    }

    private fun peek(index: Int): Int = buffer.elementAt(index).toInt() and 0xFF

    private fun take(length: Int): ByteArray {
        val out = ByteArray(length)
        for (i in 0 until length) out[i] = buffer.removeFirst()
        return out
    }

    private fun crcOk(length: Int, crcLength: Int): Boolean {
        val snapshot = ByteArray(length)
        for (i in 0 until length) snapshot[i] = buffer.elementAt(i)
        val crc = Protocol.crc16(snapshot, 0, crcLength)
        return snapshot[crcLength] == (crc and 0xFF).toByte() &&
            snapshot[crcLength + 1] == ((crc shr 8) and 0xFF).toByte()
    }

    private fun step(): Boolean {
        if (buffer.size < 4) return false
        val head = peek(0)

        if (head == (Protocol.HEAD_ESC.toInt() and 0xFF)) {
            val length = escFrameLength() ?: return false
            if (buffer.size < length) return false
            if (!crcOk(length, length - 2)) {
                buffer.removeFirst()
                return true
            }
            onFrame(take(length))
            return true
        }

        if (head == (Protocol.HEAD_MONITOR.toInt() and 0xFF)) {
            if (buffer.size < MONITOR_FRAME) return false
            if (!crcOk(MONITOR_FRAME, MONITOR_FRAME - 2)) {
                buffer.removeFirst()
                return true
            }
            onFrame(take(MONITOR_FRAME))
            return true
        }

        // Unknown header: resynchronise by discarding up to the next plausible one.
        val limit = buffer.size - 1
        for (i in 1 until limit) {
            val b = peek(i)
            if (b == (Protocol.HEAD_ESC.toInt() and 0xFF) ||
                b == (Protocol.HEAD_MONITOR.toInt() and 0xFF)
            ) {
                repeat(i) { buffer.removeFirst() }
                return true
            }
        }
        if (buffer.size > MONITOR_FRAME) {
            buffer.removeFirst()
            return true
        }
        return false
    }

    /** Total on-wire size of the ESC frame at the head of the buffer, or null if undecidable yet. */
    private fun escFrameLength(): Int? = when (peek(1).toByte()) {
        Protocol.CMD_READ_PARAMETER,
        Protocol.CMD_ESC_INFO,
        Protocol.CMD_BAT_INFO,
        Protocol.CMD_RW_PARAMETER -> {
            if (buffer.size < 5) null else peek(4) + 7
        }
        Protocol.CMD_WRITE_PARAMETER, Protocol.CMD_UPDATE_FM -> 8
        Protocol.CMD_READ_PARAM_FAIL,
        Protocol.CMD_READ_INFO_FAIL,
        Protocol.CMD_READ_BAT_FAIL,
        Protocol.CMD_WRITE_PARAM_FAIL,
        Protocol.CMD_RW_PARAM_FAIL -> 5
        else -> 4
    }

    companion object {
        const val MONITOR_FRAME = 25
    }
}
