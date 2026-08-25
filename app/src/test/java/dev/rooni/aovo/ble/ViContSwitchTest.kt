package dev.rooni.aovo.ble

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The switches whose app-side meaning is not the same as the dashboard's.
 *
 * Getting one of these backwards is not cosmetic: the rider sets off expecting to kick and
 * the scooter pulls away from the throttle, or the reverse.
 */
class ViContSwitchTest {

    private fun hex(vararg values: Int) = ByteArray(values.size) { values[it].toByte() }

    /**
     * `0x35` switches kick-start — "you must kick off first". The app's flag is zero start,
     * "ride away from the throttle", which is its inverse.
     */
    @Test
    fun `zero start on asks the dashboard to switch kick-start off`() {
        val zeroStartOn = ViContProtocol.command(ViContProtocol.CMD_KICK_START, ViContProtocol.OFF)
        assertEquals(
            "kick start",
            FrameSummary.outgoing(ScooterFamily.VICONT, zeroStartOn).label,
        )
        assertEquals("off", FrameSummary.outgoing(ScooterFamily.VICONT, zeroStartOn).detail)
        assertEquals(ViContProtocol.OFF, zeroStartOn[6].toInt() and 0xFF)
    }

    @Test
    fun `the dashboard reporting kick-start means zero start is off`() {
        // flags1 bit 5 set: the dashboard wants a kick before the throttle does anything.
        val kicking = ViContDecoder.state(
            hex(0x23, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00),
        )!!
        assertEquals(true, kicking.kickStart)

        val rolling = ViContDecoder.state(
            hex(0x03, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00),
        )!!
        assertEquals(false, rolling.kickStart)
    }

    /**
     * Captured from a SAMIK: the sport limit is already at the highest the dashboard will
     * take, which is why setting 45 or 60 changed nothing.
     */
    @Test
    fun `a tuning reply reports the ceiling alongside the value`() {
        val atCeiling = ViContProtocol.parseReply(hex(0x5A, 0x3C, 0x02, 0x23, 0x23, 0xDE, 0x00))!!
        assertEquals(35, atCeiling.payload[0].toInt() and 0xFF)
        assertEquals(35, atCeiling.payload[1].toInt() and 0xFF)

        val belowCeiling = ViContProtocol.parseReply(hex(0x5A, 0x3C, 0x02, 0x1E, 0x23, 0xD9, 0x00))!!
        assertEquals(30, belowCeiling.payload[0].toInt() and 0xFF)
        assertEquals(35, belowCeiling.payload[1].toInt() and 0xFF)
    }

    /** P8 and P9 are the Eco and Drive limits; the wire index is one below the P number. */
    @Test
    fun `the per-gear limit writes address the parameters the dashboard uses`() {
        assertEquals(
            listOf(0xFA, 0xAF, 0xA5, 0x5A, 0xD2, 0x02, 0x07, 0x10, 0x45).map { it.toByte() },
            ViContProtocol.writeParameter(index = 7, value = 16).toList(),
        )
        assertEquals(
            listOf(0xFA, 0xAF, 0xA5, 0x5A, 0xD2, 0x02, 0x08, 0x23, 0x59).map { it.toByte() },
            ViContProtocol.writeParameter(index = 8, value = 35).toList(),
        )
    }

    @Test
    fun `the limit writes are named by their P number in the log`() {
        val note = FrameSummary.outgoing(
            ScooterFamily.VICONT,
            ViContProtocol.writeParameter(index = 7, value = 16),
        )
        assertEquals("P8 = 16", note.detail)
    }




    /** Max speed is not halved, which is why only the two response settings are doubled. */
    @Test
    fun `the speed limit is stored exactly as sent`() {
        val reply = ViContProtocol.parseReply(hex(0x5A, 0x3C, 0x02, 0x1D, 0x23, 0xD8, 0x00))!!
        assertEquals(29, reply.payload[0].toInt() and 0xFF)
    }

    /** P10 is the sport limit; the wire index is one below the P number, as with P8 and P9. */
    @Test
    fun `the sport limit write addresses P10`() {
        val note = FrameSummary.outgoing(
            ScooterFamily.VICONT,
            ViContProtocol.writeParameter(index = 9, value = 45),
        )
        assertEquals("write parameter", note.label)
        assertEquals("P10 = 45", note.detail)
    }

    @Test
    fun `find scooter is a single command the log names`() {
        val frame = ViContProtocol.command(ViContProtocol.CMD_FIND, 0x01)
        assertEquals("find scooter", FrameSummary.outgoing(ScooterFamily.VICONT, frame).label)
    }
    /**
     * The legacy F1F0 service on a ViCont scooter broadcasts the per-gear limits that its
     * own protocol cannot report. Byte 3 is the cruise limit, then Eco, Drive and Sport.
     */
    @Test
    fun `the legacy limit frame carries the per-gear caps`() {
        val frame = hex(
            0xAC, 0x01, 0x00, 0x06, 0x0C, 0x12, 0x23,
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0xEA, 0x6D,
        )
        assertEquals(12, frame[4].toInt() and 0xFF)
        assertEquals(18, frame[5].toInt() and 0xFF)
        assertEquals(35, frame[6].toInt() and 0xFF)
        val note = FrameSummary.incoming(ScooterFamily.ZYD, frame)
        assertEquals("limits", note.label)
        assertTrue(note.detail, note.detail.contains("12/18/35"))
    }

    @Test
    fun `find takes any non-zero value and reads as on`() {
        val on = ViContProtocol.command(ViContProtocol.CMD_FIND, 0x01)
        assertEquals("on", FrameSummary.outgoing(ScooterFamily.VICONT, on).detail)
        val off = ViContProtocol.command(ViContProtocol.CMD_FIND, 0x00)
        assertEquals("off", FrameSummary.outgoing(ScooterFamily.VICONT, off).detail)
    }

    /** P5 is the idle shutdown timer; like the gear limits it has no command of its own. */
    @Test
    fun `the auto shutdown write addresses P5`() {
        val note = FrameSummary.outgoing(
            ScooterFamily.VICONT,
            ViContProtocol.writeParameter(index = 4, value = 30),
        )
        assertEquals("P5 = 30", note.detail)
    }


    /** Max speed is the exception: it is stored exactly as sent, so it must not be doubled. */
    @Test
    fun `max speed is sent unchanged`() {
        val frame = ViContProtocol.command(ViContProtocol.CMD_MAX_SPEED, 29)
        assertEquals(29, frame[6].toInt() and 0xFF)
    }
    /**
     * ViCont dashboards report up to seven gears in the 0x12 mask, and five are used in
     * practice. Reading the gear off the legacy F1F0 service instead would cap it at three
     * and misreport the fourth and fifth.
     */
    @Test
    fun `the gear mask admits more than the three ZYD gears`() {
        val fiveGears = ViContDecoder.versions(ByteArray(8) + hex(0x1F))!!
        assertEquals(listOf(1, 2, 3, 4, 5), fiveGears.gears)
        val sevenGears = ViContDecoder.versions(ByteArray(8) + hex(0x7F))!!
        assertEquals(listOf(1, 2, 3, 4, 5, 6, 7), sevenGears.gears)
    }

    /** Walk is gear 4 on the wire, and the state frame carries it in the low three bits. */
    @Test
    fun `the walk gear is reported as the fourth`() {
        val walking = ViContDecoder.state(
            hex(0x04, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00),
        )!!
        assertEquals(4, walking.gear)

        val fifth = ViContDecoder.state(
            hex(0x05, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00),
        )!!
        assertEquals(5, fifth.gear)
    }
    /**
     * The tuning commands take a percentage of that setting's own ceiling, doubled — the
     * 1..200 range the notes describe — not the value itself.
     *
     * Where the ceiling is 99 that looks like a plain halving, which is how it was first
     * mistaken for one. The current limit ceilings at 23 A and gives the model away: sending
     * 46 asked for 23% of 23 A and set 5.
     */
    @Test
    fun `every captured tuning write matches the percentage model`() {
        val captured = listOf(
            Triple(99, 99, 49),
            Triple(89, 99, 44),
            Triple(79, 99, 39),
            Triple(69, 99, 34),
            Triple(30, 99, 15),
            Triple(10, 99, 5),
            Triple(46, 23, 5),
        )
        captured.forEach { (wire, ceiling, observed) ->
            assertEquals(
                "wire $wire against ceiling $ceiling",
                observed,
                ViContProtocol.tuningFromWire(wire, ceiling),
            )
        }
    }

    /** Asking for a value has to land on it, whatever the ceiling happens to be. */
    @Test
    fun `a tuning value survives the round trip at any ceiling`() {
        listOf(23, 25, 50, 99).forEach { ceiling ->
            for (stored in 1..ceiling) {
                val wire = ViContProtocol.tuningToWire(stored, ceiling)
                assertEquals(
                    "stored $stored of $ceiling via $wire",
                    stored,
                    ViContProtocol.tuningFromWire(wire, ceiling),
                )
            }
        }
    }

    /** The bug this replaced: amps doubled read as that many percent of the ceiling. */
    @Test
    fun `doubling the value instead of the percentage set the wrong current`() {
        // What the code used to send for 23 A, and what the scooter ended up on.
        assertEquals(5, ViContProtocol.tuningFromWire(23 * 2, 23))
        // What it sends now.
        assertEquals(200, ViContProtocol.tuningToWire(23, 23))
        assertEquals(23, ViContProtocol.tuningFromWire(200, 23))
        assertEquals(148, ViContProtocol.tuningToWire(17, 23))
        assertEquals(17, ViContProtocol.tuningFromWire(148, 23))
    }

    @Test
    fun `the wire value stays inside the range the commands accept`() {
        listOf(23, 25, 99).forEach { ceiling ->
            for (stored in 1..ceiling) {
                val wire = ViContProtocol.tuningToWire(stored, ceiling)
                assertTrue("stored $stored of $ceiling gave $wire", wire in 1..200)
            }
        }
    }

    /** The 1..10 response scale is tenths of whatever the ceiling is, so it needs no ceiling. */
    @Test
    fun `a response step is a tenth of the ceiling`() {
        for (ui in 1..10) {
            val wire = ViContProtocol.responseToWire(ui)
            assertEquals("ui $ui", ui * 20, wire)
            listOf(50, 99).forEach { ceiling ->
                val stored = ViContProtocol.tuningFromWire(wire, ceiling)
                assertEquals(
                    "ui $ui at ceiling $ceiling",
                    ui,
                    ViContProtocol.responseFromStored(stored, ceiling),
                )
            }
        }
    }
}
