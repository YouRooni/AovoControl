package dev.rooni.aovo.ble

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The GATT notification stream does not align with frame boundaries, so the reader has to
 * buffer, validate and resynchronise on its own. These cases cover the ways the stream
 * actually arrives on a real link.
 */
class FrameReaderTest {

    private fun seal(body: ByteArray): ByteArray {
        val crc = Protocol.crc16(body, 0, body.size)
        return body + byteArrayOf((crc and 0xFF).toByte(), ((crc shr 8) and 0xFF).toByte())
    }

    /** Subtype-0 monitor frame: 23 payload bytes plus checksum. */
    private fun monitorFrame(
        battery: Int = 87,
        speedMilli: Int = 18_400,
        voltageTenths: Int = 415,
        currentRaw: Int = 320,
        gear: Int = 2,
        status: Int = 0b1000_0010_0100_0100,
    ): ByteArray {
        val body = ByteArray(23)
        body[0] = Protocol.HEAD_MONITOR
        body[1] = 0x00
        body[2] = 0x17
        body[3] = 0x00
        body[4] = gear.toByte()
        body[5] = battery.toByte()
        body[6] = (speedMilli shr 8).toByte()
        body[7] = (speedMilli and 0xFF).toByte()
        body[8] = 0
        body[9] = 0
        body[10] = (voltageTenths shr 8).toByte()
        body[11] = (voltageTenths and 0xFF).toByte()
        body[12] = (currentRaw shr 8).toByte()
        body[13] = (currentRaw and 0xFF).toByte()
        body[14] = 41
        body[15] = 33
        body[16] = 0
        body[17] = 62          // trip 6.2
        body[18] = 0
        body[19] = 0x05
        body[20] = 0x39        // odo 133.7
        body[21] = (status shr 8).toByte()
        body[22] = (status and 0xFF).toByte()
        return seal(body)
    }

    /** Register read response: `01 03 addrH addrL byteCount <payload> crc`. */
    private fun registerFrame(address: Int, registers: IntArray): ByteArray {
        val body = ByteArray(5 + registers.size * 2)
        body[0] = Protocol.HEAD_ESC
        body[1] = Protocol.CMD_READ_PARAMETER
        body[2] = (address shr 8).toByte()
        body[3] = (address and 0xFF).toByte()
        body[4] = (registers.size * 2).toByte()
        registers.forEachIndexed { index, value ->
            body[5 + index * 2] = (value shr 8).toByte()
            body[6 + index * 2] = (value and 0xFF).toByte()
        }
        return seal(body)
    }

    private fun collect(vararg packets: ByteArray): List<ByteArray> {
        val out = mutableListOf<ByteArray>()
        val reader = FrameReader { out.add(it) }
        packets.forEach { reader.append(it) }
        return out
    }

    @Test
    fun `reads a whole monitor frame from a single notification`() {
        val frame = monitorFrame()
        val frames = collect(frame)
        assertEquals(1, frames.size)
        assertArrayEquals(frame, frames[0])
    }

    @Test
    fun `reassembles a frame split across notifications`() {
        val frame = monitorFrame()
        val frames = collect(
            frame.copyOfRange(0, 7),
            frame.copyOfRange(7, 20),
            frame.copyOfRange(20, frame.size),
        )
        assertEquals(1, frames.size)
        assertArrayEquals(frame, frames[0])
    }

    @Test
    fun `splits two frames arriving in one notification`() {
        val a = monitorFrame(battery = 90)
        val b = monitorFrame(battery = 55)
        val frames = collect(a + b)
        assertEquals(2, frames.size)
        assertEquals(90, frames[0][5].toInt())
        assertEquals(55, frames[1][5].toInt())
    }

    @Test
    fun `drops a corrupt frame and recovers on the next good one`() {
        val broken = monitorFrame().copyOf().also { it[24] = (it[24] + 1).toByte() }
        val good = monitorFrame(battery = 42)
        val frames = collect(broken + good)
        assertEquals(1, frames.size)
        assertEquals(42, frames[0][5].toInt())
    }

    @Test
    fun `skips leading noise before a valid header`() {
        val junk = ByteArray(30) { 0x7E }
        val frame = monitorFrame()
        val frames = collect(junk + frame)
        assertEquals(1, frames.size)
        assertArrayEquals(frame, frames[0])
    }

    @Test
    fun `register responses are length prefixed by their byte count`() {
        val frame = registerFrame(64, IntArray(15) { it })
        val frames = collect(frame)
        assertEquals(1, frames.size)
        assertEquals(15 * 2 + 7, frames[0].size)
        assertEquals(64, Decoder.u16(frames[0], 2))
    }

    @Test
    fun `interleaved register and monitor frames both come through`() {
        val register = registerFrame(0, IntArray(16) { 0 })
        val monitor = monitorFrame()
        val frames = collect(register.copyOfRange(0, 5), register.copyOfRange(5, register.size) + monitor)
        assertEquals(2, frames.size)
        assertEquals(Protocol.HEAD_ESC, frames[0][0])
        assertEquals(Protocol.HEAD_MONITOR, frames[1][0])
    }

    @Test
    fun `short ack frames are read as four bytes`() {
        val ack = Protocol.shortFrame(Protocol.CMD_HANDSHAKE)
        val frames = collect(ack)
        assertEquals(1, frames.size)
        assertArrayEquals(ack, frames[0])
    }

    @Test
    fun `reset clears a partial frame`() {
        val out = mutableListOf<ByteArray>()
        val reader = FrameReader { out.add(it) }
        val frame = monitorFrame()
        reader.append(frame.copyOfRange(0, 10))
        reader.reset()
        reader.append(frame.copyOfRange(10, frame.size))
        assertTrue(out.isEmpty())
    }

    @Test
    fun `telemetry decodes the documented field layout`() {
        val telemetry = Decoder.telemetry(monitorFrame())
        assertEquals(18.4f, telemetry.speed, 0.001f)
        assertEquals(87, telemetry.battery)
        assertEquals(41.5f, telemetry.voltage, 0.001f)
        assertEquals(5.0f, telemetry.current, 0.001f)
        assertEquals(207.5f, telemetry.power, 0.01f)
        assertEquals(41, telemetry.escTemperature)
        assertEquals(33, telemetry.motorTemperature)
        assertEquals(6.2f, telemetry.tripDistance, 0.001f)
        assertEquals(133.7f, telemetry.totalDistance, 0.001f)
    }

    @Test
    fun `switch bits map onto the documented positions`() {
        // bit2 headlight, bit6 imperial, bit9 cruise, bit11 unlocked, bit15 ambient
        val status = (1 shl 2) or (1 shl 6) or (1 shl 9) or (1 shl 11) or (1 shl 15)
        val state = Decoder.switches(RideState(), monitorFrame(gear = 1, status = status))
        assertEquals(1, state.gear)
        assertTrue(state.headLight)
        assertTrue(state.imperial)
        assertTrue(state.cruiseControl)
        assertTrue(state.ambientLight)
        assertFalse(state.locked)
        assertFalse(state.zeroStart)
    }

    @Test
    fun `bit 11 reports unlocked, so a clear bit means the scooter is locked`() {
        val unlocked = Decoder.switches(RideState(), monitorFrame(status = 1 shl 11))
        val locked = Decoder.switches(RideState(), monitorFrame(status = 0))
        assertFalse(unlocked.locked)
        assertTrue(locked.locked)
    }

    @Test
    fun `the lock bit survives a decode and encode round trip`() {
        listOf(0, 1 shl 11).forEach { status ->
            val decoded = Decoder.switches(RideState(), monitorFrame(status = status))
            val reencoded = decoded.flagByte().toInt() and 0xFF
            assertEquals(
                "status " + status,
                (status shr 11) and 1,
                (reencoded shr 7) and 1,
            )
        }
    }

    @Test
    fun `switch byte round trips through the monitor frame layout`() {
        val state = RideState(
            gear = 2,
            headLight = true,
            ambientLight = false,
            cruiseControl = true,
            zeroStart = false,
            imperial = true,
            locked = true,
        )
        val flags = state.flagByte().toInt() and 0xFF
        assertEquals(2, flags and 0b11)
        assertTrue(flags and (1 shl 2) != 0)
        assertTrue(flags and (1 shl 3) == 0)
        assertTrue(flags and (1 shl 4) != 0)
        assertTrue(flags and (1 shl 5) == 0)
        assertTrue(flags and (1 shl 6) != 0)
        // Bit 7 carries "unlocked", so a locked scooter leaves it clear.
        assertTrue(flags and (1 shl 7) == 0)
        assertTrue(state.copy(locked = false).flagByte().toInt() and (1 shl 7) != 0)
    }
}
