package dev.rooni.aovo.ble

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A log of bare hex is no better than no log, so the point of these is that the common
 * frames come back named, and that the repeating ones are marked for sampling.
 */
class FrameSummaryTest {

    private fun hex(vararg values: Int) = ByteArray(values.size) { values[it].toByte() }

    @Test
    fun `vicont switch commands read as words`() {
        val note = FrameSummary.outgoing(
            ScooterFamily.VICONT,
            ViContProtocol.command(ViContProtocol.CMD_LIGHT, ViContProtocol.ON),
        )
        assertEquals("light", note.label)
        assertEquals("on", note.detail)
    }

    @Test
    fun `parking is described as locking rather than as on`() {
        val lock = FrameSummary.outgoing(
            ScooterFamily.VICONT,
            ViContProtocol.command(ViContProtocol.CMD_PARKING, ViContProtocol.ON),
        )
        assertEquals("lock", lock.detail)
        val unlock = FrameSummary.outgoing(
            ScooterFamily.VICONT,
            ViContProtocol.command(ViContProtocol.CMD_PARKING, ViContProtocol.OFF),
        )
        assertEquals("unlock", unlock.detail)
    }

    @Test
    fun `a query is called a query`() {
        val note = FrameSummary.outgoing(
            ScooterFamily.VICONT,
            ViContProtocol.query(ViContProtocol.CMD_MAX_SPEED),
        )
        assertEquals("max speed", note.label)
        assertEquals("query", note.detail)
    }

    @Test
    fun `an engineering write names the parameter the user would recognise`() {
        // Index 30 on the wire is P31 in the dashboard's own numbering.
        val note = FrameSummary.outgoing(
            ScooterFamily.VICONT,
            ViContProtocol.writeParameter(index = 30, value = 37),
        )
        assertEquals("write parameter", note.label)
        assertEquals("P31 = 37", note.detail)
    }

    @Test
    fun `vicont telemetry is marked as a sampled stream`() {
        val frame = hex(
            0x5A, 0x10, 0x0C,
            0x10, 0x41, 0x01, 0x90, 0x01, 0x08, 0x64, 0x1A, 0x00, 0x15, 0x02, 0x5E,
            0x00, 0x00,
        )
        val note = FrameSummary.incoming(ScooterFamily.VICONT, frame)
        assertEquals("telemetry", note.label)
        assertEquals("telemetry", note.stream)
        assertTrue(note.detail, note.detail.contains("41.61 V"))
        assertTrue(note.detail, note.detail.contains("26.4 km/h"))
    }

    @Test
    fun `faults are called out in the state line`() {
        val frame = hex(
            0x5A, 0x11, 0x0A,
            0x0B, 0x00, 0x00, 0x00, 0x03, 0xB7, 0x00, 0x00, 0x00, 0x09,
            0x00, 0x00,
        )
        val note = FrameSummary.incoming(ScooterFamily.VICONT, frame)
        assertEquals("state", note.label)
        assertEquals("state", note.stream)
        assertTrue(note.detail, note.detail.contains("FAULT"))
        assertTrue(note.detail, note.detail.contains("rotor"))
        assertTrue(note.detail, note.detail.contains("throttle"))
    }

    @Test
    fun `a healthy state line carries no fault marker`() {
        val frame = hex(
            0x5A, 0x11, 0x0A,
            0x0B, 0x00, 0x00, 0x00, 0x03, 0xB7, 0x00, 0x00, 0x00, 0x00,
            0x00, 0x00,
        )
        val note = FrameSummary.incoming(ScooterFamily.VICONT, frame)
        assertTrue(note.detail, !note.detail.contains("FAULT"))
        assertTrue(note.detail, note.detail.contains("light"))
    }

    @Test
    fun `zyd keepalive is sampled instead of logged every time`() {
        val note = FrameSummary.outgoing(ScooterFamily.ZYD, Protocol.keepFrame())
        assertEquals("keepalive", note.label)
        assertEquals("keepalive", note.stream)
    }

    @Test
    fun `zyd control frames show their flags and limits`() {
        val note = FrameSummary.outgoing(
            ScooterFamily.ZYD,
            Protocol.monitorFrame(0xA6.toByte(), 6, 12, 18, 35),
        )
        assertEquals("control", note.label)
        assertTrue(note.detail, note.detail.contains("6/12/18/35"))
    }

    @Test
    fun `zyd register reads name the address`() {
        val note = FrameSummary.outgoing(
            ScooterFamily.ZYD,
            Protocol.readFrame(Protocol.CMD_READ_PARAMETER, 64, 16),
        )
        assertEquals("read registers", note.label)
        assertTrue(note.detail, note.detail.contains("64"))
    }

    @Test
    fun `an unrecognised frame still produces a line`() {
        val note = FrameSummary.incoming(ScooterFamily.ZYD, hex(0x77, 0x88, 0x99))
        assertNotNull(note.label)
        assertTrue(note.label.isNotEmpty())
        assertNull(note.stream)
    }
}
