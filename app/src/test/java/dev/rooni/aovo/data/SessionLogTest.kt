package dev.rooni.aovo.data

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The log is only useful if a connected scooter cannot drown it, so most of what matters
 * here is what does *not* get recorded.
 */
class SessionLogTest {

    @Before
    fun setUp() {
        SessionLog.setEnabled(false)
        SessionLog.setEnabled(true)
        SessionLog.clear()
    }

    @After
    fun tearDown() = SessionLog.setEnabled(false)

    @Test
    fun `nothing is recorded while logging is off`() {
        SessionLog.setEnabled(false)
        SessionLog.tx("light on", payload = byteArrayOf(1))
        SessionLog.state("connected")
        assertTrue(SessionLog.entries.value.isEmpty())
    }

    @Test
    fun `switching off discards what was collected`() {
        SessionLog.tx("light on")
        assertEquals(1, SessionLog.entries.value.size)
        SessionLog.setEnabled(false)
        assertTrue(SessionLog.entries.value.isEmpty())
    }

    @Test
    fun `repeated identical lines fold into a count`() {
        repeat(5) { SessionLog.tx("keepalive", payload = byteArrayOf(0xA5.toByte(), 0x02)) }
        val entries = SessionLog.entries.value
        assertEquals(1, entries.size)
        assertEquals(5, entries.single().repeats)
        assertTrue(SessionLog.export().contains("x5"))
    }

    @Test
    fun `a different line breaks the fold`() {
        SessionLog.tx("keepalive")
        SessionLog.tx("keepalive")
        SessionLog.tx("light on")
        SessionLog.tx("keepalive")
        val entries = SessionLog.entries.value
        assertEquals(3, entries.size)
        assertEquals(2, entries[0].repeats)
        assertEquals(1, entries[2].repeats)
    }

    @Test
    fun `telemetry is sampled rather than recorded in full`() {
        val start = 1_000_000L
        // A dashboard pushing five frames a second for six seconds.
        for (i in 0 until 30) {
            SessionLog.telemetry("speed ${i} km/h", start + i * 200L)
        }
        // Six seconds of traffic at a three-second sampling interval: the first frame and
        // the one three seconds later, and nothing in between.
        val samples = SessionLog.entries.value.filter { it.label == "telemetry" }
        assertEquals(2, samples.size)
    }

    @Test
    fun `telemetry sampling restarts after a clear`() {
        SessionLog.telemetry("first", 1_000L)
        SessionLog.clear()
        SessionLog.telemetry("second", 1_100L)
        assertEquals(1, SessionLog.entries.value.size)
    }

    @Test
    fun `the buffer is bounded and keeps the newest lines`() {
        for (i in 0 until SessionLog.CAPACITY + 50) SessionLog.state("step $i")
        val entries = SessionLog.entries.value
        assertEquals(SessionLog.CAPACITY, entries.size)
        assertEquals("step ${SessionLog.CAPACITY + 49}", entries.last().label)
    }

    @Test
    fun `long payloads are elided instead of filling the line`() {
        SessionLog.tx("firmware chunk", payload = ByteArray(64) { 0xAB.toByte() })
        val detail = SessionLog.entries.value.single().detail
        assertTrue(detail, detail.endsWith("+40"))
        assertEquals(SessionLog.MAX_HEX_BYTES, detail.substringBefore(" +").split(" ").size)
    }

    @Test
    fun `export carries direction label and payload`() {
        SessionLog.clear()
        SessionLog.tx("light on", payload = byteArrayOf(0xAC.toByte(), 0x00))
        val text = SessionLog.export()
        assertTrue(text, text.contains("->"))
        assertTrue(text, text.contains("light on"))
        assertTrue(text, text.contains("AC 00"))
    }

    @Test
    fun `an empty export still says something`() {
        SessionLog.clear()
        assertTrue(SessionLog.export().contains("No log entries"))
    }
}
