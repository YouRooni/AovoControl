package dev.rooni.aovo.ble

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Replies captured from a SAMIK dashboard, which is the only way to settle where the applied
 * value sits in a tuning reply.
 */
class ViContReplyTest {

    private fun hex(vararg values: Int) = ByteArray(values.size) { values[it].toByte() }

    /**
     * `5A 3E 02 11 17 C2 00` — max current. The scooter's engineering menu shows P18 = 17
     * with a ceiling of 23, so the applied value is the first payload byte. Reading the
     * second one returns the ceiling and makes every setting display its maximum.
     */
    @Test
    fun `a tuning reply carries the applied value before the ceiling`() {
        val reply = ViContProtocol.parseReply(hex(0x5A, 0x3E, 0x02, 0x11, 0x17, 0xC2, 0x00))!!
        assertEquals(ViContProtocol.CMD_MAX_CURRENT, reply.command)
        assertEquals(2, reply.payload.size)
        assertEquals(17, reply.payload[0].toInt() and 0xFF)
        assertEquals(23, reply.payload[1].toInt() and 0xFF)
        assertTrue(reply.checksumValid)
        assertTrue(reply.ok)
    }

    @Test
    fun `captured tuning replies all verify against the checksum`() {
        val captured = listOf(
            hex(0x5A, 0x3C, 0x02, 0x23, 0x23, 0xDE, 0x00), // max speed 35 of 35
            hex(0x5A, 0x3E, 0x02, 0x11, 0x17, 0xC2, 0x00), // max current 17 of 23
            hex(0x5A, 0x3D, 0x02, 0x42, 0x63, 0x3E, 0x00), // starting torque 66 of 99
            hex(0x5A, 0x3F, 0x02, 0x42, 0x63, 0x40, 0x00), // brake strength 66 of 99
        )
        captured.forEach {
            val reply = ViContProtocol.parseReply(it)!!
            assertTrue(reply.command.toString(), reply.checksumValid)
        }
    }

    @Test
    fun `the captured versions frame decodes to the dashboard's own numbers`() {
        val frame = hex(
            0x5A, 0x12, 0x09,
            0x5A, 0x72, 0x68, 0x34, 0x00, 0x00, 0x0A, 0x2E, 0x07,
            0x1C, 0x00,
        )
        val reply = ViContProtocol.parseReply(frame)!!
        val versions = ViContDecoder.versions(reply.payload)!!
        assertEquals(104, versions.instrumentHardware)
        assertEquals(52, versions.instrumentSoftware)
        assertEquals(10, versions.controllerHardware)
        assertEquals(46, versions.controllerSoftware)
        assertEquals(listOf(1, 2, 3), versions.gears)
    }

    /** `5A 16 06 1E 22 23 24 25 27 49 00` — the battery gauge voltages, at their defaults. */
    @Test
    fun `the captured battery threshold frame decodes to volts`() {
        val frame = hex(0x5A, 0x16, 0x06, 0x1E, 0x22, 0x23, 0x24, 0x25, 0x27, 0x49, 0x00)
        val reply = ViContProtocol.parseReply(frame)!!
        assertEquals(ViContProtocol.RX_BATTERY_THRESHOLDS, reply.command)
        assertTrue(reply.checksumValid)
        val thresholds = ViContDecoder.batteryThresholds(reply.payload)!!
        assertEquals(30, thresholds.tractionCutoff)
        assertEquals(34, thresholds.bar1)
        assertEquals(39, thresholds.bar5)
    }

    @Test
    fun `the battery threshold frame is named rather than shown as a raw command`() {
        val frame = hex(0x5A, 0x16, 0x06, 0x1E, 0x22, 0x23, 0x24, 0x25, 0x27, 0x49, 0x00)
        val note = FrameSummary.incoming(ScooterFamily.VICONT, frame)
        assertEquals("battery levels", note.label)
        assertEquals("battery levels", note.stream)
        assertTrue(note.detail, note.detail.contains("cutoff 30 V"))
    }

    /** Both repeat every few hundred milliseconds, so neither may be logged in full. */
    @Test
    fun `the repeating notifications are all marked for sampling`() {
        val frames = mapOf(
            "telemetry" to hex(
                0x5A, 0x10, 0x0C, 0x10, 0x41, 0x01, 0x90, 0x01, 0x08, 0x64,
                0x1A, 0x00, 0x15, 0x02, 0x5E, 0x00, 0x00,
            ),
            "versions" to hex(
                0x5A, 0x12, 0x09, 0x5A, 0x72, 0x68, 0x34, 0x00, 0x00, 0x0A, 0x2E, 0x07, 0x1C, 0x00,
            ),
            "battery levels" to hex(
                0x5A, 0x16, 0x06, 0x1E, 0x22, 0x23, 0x24, 0x25, 0x27, 0x49, 0x00,
            ),
        )
        frames.forEach { (expected, frame) ->
            assertEquals(expected, FrameSummary.incoming(ScooterFamily.VICONT, frame).stream)
        }
    }
}
