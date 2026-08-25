package dev.rooni.aovo.ble

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Checks the FEE0 wire format against frames captured off a real ViCont dashboard.
 *
 * The expected bytes are transcripts, not values recomputed from the same formulas the
 * production code uses, so a mistake in the checksum or field order fails here rather than
 * agreeing with itself.
 */
class ViContProtocolTest {

    private fun hex(vararg values: Int) = ByteArray(values.size) { values[it].toByte() }

    @Test
    fun `light on matches the captured frame`() {
        assertArrayEquals(
            hex(0xFA, 0xAF, 0xA5, 0x5A, 0x45, 0x01, 0x02, 0xA2),
            ViContProtocol.command(ViContProtocol.CMD_LIGHT, ViContProtocol.ON),
        )
    }

    /**
     * The circulating protocol notes give this frame as `… 3C 01 19 AF`, but `AF` is the
     * checksum for 24 (`0x18`) — the value and the sum in that transcript disagree. Every
     * other captured frame, including the two hard-coded strings inside the stock app,
     * agrees with the formula, so the transcript is wrong rather than the arithmetic.
     */
    @Test
    fun `max speed frames follow the checksum of their own value`() {
        assertArrayEquals(
            hex(0xFA, 0xAF, 0xA5, 0x5A, 0x3C, 0x01, 0x18, 0xAF),
            ViContProtocol.command(ViContProtocol.CMD_MAX_SPEED, 24),
        )
        assertArrayEquals(
            hex(0xFA, 0xAF, 0xA5, 0x5A, 0x3C, 0x01, 0x19, 0xB0),
            ViContProtocol.command(ViContProtocol.CMD_MAX_SPEED, 25),
        )
    }

    /** Lifted verbatim from the stock bundle, where it is sent as a literal hex string. */
    @Test
    fun `the legacy telemetry start string round-trips`() {
        assertArrayEquals(
            hex(0xFA, 0xAF, 0xA5, 0x5A, 0x31, 0x01, 0x01, 0x8D),
            ViContProtocol.command(0x31, 0x01),
        )
    }

    @Test
    fun `parameter write matches the captured frame`() {
        // P31 (index 30) set to 37 volts.
        assertArrayEquals(
            hex(0xFA, 0xAF, 0xA5, 0x5A, 0xD2, 0x02, 0x1E, 0x25, 0x71),
            ViContProtocol.writeParameter(index = 30, value = 37),
        )
    }

    @Test
    fun `telemetry start matches the captured frame`() {
        assertArrayEquals(
            hex(0xFA, 0xAF, 0xA5, 0x5A, 0x01, 0x00, 0x5B),
            ViContProtocol.startTelemetry(),
        )
    }

    @Test
    fun `the alternate family byte is carried into the frame and its checksum`() {
        val frame = ViContProtocol.startTelemetry(ViContProtocol.ZT_ALT)
        assertArrayEquals(hex(0xFA, 0xAF, 0xA5, 0xFA, 0x01, 0x00, 0xFB), frame)
    }

    @Test
    fun `checksum covers the family byte but not the preamble`() {
        // 0x5A + 0x45 + 0x01 + 0x02 = 0xA2; including FA AF A5 would not land on it.
        assertEquals(
            0xA2,
            ViContProtocol.checksum(ViContProtocol.ZT_DEFAULT, ViContProtocol.CMD_LIGHT, hex(0x02)),
        )
    }

    @Test
    fun `checksum wraps to a single byte`() {
        val payload = ByteArray(4) { 0xFF.toByte() }
        val sum = ViContProtocol.checksum(ViContProtocol.ZT_DEFAULT, 0xD3, payload)
        assertTrue("checksum out of range: $sum", sum in 0..255)
        assertEquals((0x5A + 0xD3 + 4 + 0xFF * 4) and 0xFF, sum)
    }

    /**
     * The stock app truncates the checksum through `toString(16).substr(-2, 2)`, which drops
     * to one hex digit below 0x10 and corrupts the frame. Ours stays two digits wide.
     */
    @Test
    fun `a small checksum still occupies a whole byte`() {
        // 0x00 + 0x02 + 0x01 + 0x05 = 0x08, which the stock app would emit as one digit.
        val frame = ViContProtocol.command(0x02, byteArrayOf(0x05), zt = 0x00)
        assertEquals(8, frame.size)
        assertEquals(0x08.toByte(), frame[frame.size - 1])
    }

    @Test
    fun `the lamp hue occupies two bytes`() {
        val frame = ViContProtocol.ambienceLamp(ViContProtocol.LAMP_SOLID_BREATHE, 300)
        assertArrayEquals(
            hex(0xFA, 0xAF, 0xA5, 0x5A, 0x41, 0x03, 0x05, 0x01, 0x2C, 0xD0),
            frame,
        )
    }

    @Test
    fun `a query asks for the value instead of setting it`() {
        assertArrayEquals(
            hex(0xFA, 0xAF, 0xA5, 0x5A, 0x36, 0x01, 0x00, 0x91),
            ViContProtocol.query(ViContProtocol.CMD_CRUISE),
        )
    }

    @Test
    fun `a reply exposes its payload status and checksum`() {
        // Cruise control switched on, accepted.
        val reply = ViContProtocol.parseReply(hex(0x5A, 0x36, 0x01, 0x02, 0x93, 0x00))!!
        assertEquals(ViContProtocol.CMD_CRUISE, reply.command)
        assertArrayEquals(hex(0x02), reply.payload)
        assertTrue(reply.ok)
        assertTrue(reply.checksumValid)
    }

    @Test
    fun `a bad checksum is reported rather than thrown away`() {
        val reply = ViContProtocol.parseReply(hex(0x5A, 0x36, 0x01, 0x02, 0x00, 0x00))!!
        assertFalse(reply.checksumValid)
        assertArrayEquals(hex(0x02), reply.payload)
    }

    @Test
    fun `a non-zero status marks the reply as failed`() {
        val reply = ViContProtocol.parseReply(hex(0x5A, 0x36, 0x01, 0x02, 0x93, 0x01))!!
        assertFalse(reply.ok)
    }

    @Test
    fun `a truncated reply decodes to nothing`() {
        assertNull(ViContProtocol.parseReply(hex(0x5A, 0x10, 0x0C, 0x10, 0x41)))
        assertNull(ViContProtocol.parseReply(hex(0x5A, 0x36)))
    }
}
