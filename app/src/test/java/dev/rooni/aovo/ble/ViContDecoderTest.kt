package dev.rooni.aovo.ble

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Decodes notification payloads against readings taken off a scooter whose real state was
 * recorded alongside the capture: a charged 10S pack at 41.61 V, gear S, 26 km/h on a stand.
 */
class ViContDecoderTest {

    private fun hex(vararg values: Int) = ByteArray(values.size) { values[it].toByte() }

    @Test
    fun `telemetry scales voltage speed and temperatures`() {
        val t = ViContDecoder.telemetry(
            hex(
                0x10, 0x41, // 4161 -> 41.61 V
                0x01, 0x90, // 400 mA
                0x01, 0x08, // 264 -> 26.4 km/h
                0x64,       // 100 %
                0x1A,       // controller 26 C
                0x00,       // motor 0 C
                0x15,       // battery 21 C
                0x02, 0x5E, // 606 rpm
            ),
        )!!
        assertEquals(41.61f, t.voltage, 0.001f)
        assertEquals(400, t.currentMilliamps)
        assertEquals(0.4f, t.current, 0.001f)
        assertEquals(26.4f, t.speed, 0.001f)
        assertEquals(100, t.battery)
        assertEquals(26, t.controllerTemperature)
        assertEquals(21, t.batteryTemperature)
        assertEquals(606, t.motorRpm)
        assertEquals(16.6f, t.power, 0.05f)
    }

    @Test
    fun `telemetry rejects a short payload`() {
        assertNull(ViContDecoder.telemetry(hex(0x10, 0x41, 0x00)))
    }

    @Test
    fun `state splits the gear out of the low three bits`() {
        // Gear 3 (S) with the head light on: 0b0000_1011.
        val s = ViContDecoder.state(
            hex(0x0B, 0x00, 0x00, 0x00, 0x03, 0xB7, 0x00, 0x00, 0x00, 0x00),
        )!!
        assertEquals(3, s.gear)
        assertTrue(s.headLight)
        assertFalse(s.tailLight)
        assertFalse(s.imperial)
        assertEquals(951, s.totalDistance)
    }

    @Test
    fun `state reads the switch and fault bits`() {
        val s = ViContDecoder.state(
            hex(0xE1, 0x62, 0x01, 0x2C, 0x00, 0x00, 0x00, 0x00, 0x05, 0x09),
        )!!
        assertEquals(1, s.gear)
        assertTrue(s.kickStart)
        assertTrue(s.cruiseEnabled)
        assertTrue(s.imperial)
        assertTrue(s.parking)
        assertTrue(s.ambientLight)
        assertTrue(s.bound)
        assertFalse(s.powerSwitch)
        assertEquals(3.0f, s.tripDistance, 0.001f)
        assertTrue(s.cruising)
        assertTrue(s.locked)
        assertFalse(s.braking)
        assertTrue(s.rotorLocked)
        assertTrue(s.throttleFault)
        assertFalse(s.controllerFault)
    }

    /**
     * Hardware and software versions are adjacent single bytes, not one 16-bit number — the
     * pairing that makes `0A 2E` read as 10 and 46 rather than 2606.
     */
    @Test
    fun `versions keeps the two boards apart`() {
        val v = ViContDecoder.versions(
            hex(0xE0, 0x44, 0x68, 0x34, 0x00, 0x00, 0x0A, 0x2E, 0x07),
        )!!
        assertEquals(0xE044, v.instrumentId)
        assertEquals(104, v.instrumentHardware)
        assertEquals(52, v.instrumentSoftware)
        assertEquals(0, v.controllerId)
        assertEquals(10, v.controllerHardware)
        assertEquals(46, v.controllerSoftware)
        assertEquals(listOf(1, 2, 3), v.gears)
    }

    @Test
    fun `the gear mask decides how many gears exist`() {
        assertEquals(listOf(1, 2, 3, 4, 5), ViContDecoder.versions(ByteArray(8) + hex(0x1F))!!.gears)
        assertEquals(listOf(1), ViContDecoder.versions(ByteArray(8) + hex(0x01))!!.gears)
        assertTrue(ViContDecoder.versions(ByteArray(8) + hex(0x00))!!.gears.isEmpty())
    }

    @Test
    fun `sensors expose both the normalised and the raw reading`() {
        val s = ViContDecoder.sensors(
            hex(0x00, 0xFA, 0x00, 0x00, 0x00, 0x00, 0x07, 0xC6, 0x00, 0x05, 0x00, 0x00),
        )!!
        assertEquals(250, s.throttle)
        assertEquals(0, s.brake1)
        assertEquals(1990, s.throttleRaw)
        assertEquals(5, s.brake1Raw)
        assertEquals(0, s.brake2Raw)
    }

    @Test
    fun `calibration separates the display and motor write counters`() {
        val c = ViContDecoder.calibration(
            hex(0x05, 0x01, 0xF2, 0x08, 0x69, 0x0C, 0x8C, 0x03, 0x12, 0x02, 0x77, 0x00, 0x00),
        )!!
        assertEquals(5, c.phaseOrder)
        assertEquals(498, c.coefficient1)
        assertEquals(2153, c.coefficient2)
        assertEquals(3212, c.coefficient3)
        assertEquals(786, c.displayWrites)
        assertEquals(631, c.motorWrites)
        assertEquals(2, c.trailing.size)
    }

    @Test
    fun `battery thresholds come back in volts`() {
        val b = ViContDecoder.batteryThresholds(hex(0x1E, 0x22, 0x23, 0x24, 0x25, 0x27))!!
        assertEquals(30, b.tractionCutoff)
        assertEquals(34, b.bar1)
        assertEquals(39, b.bar5)
    }
    /**
     * A released lever still reads a little above zero, so an exact zero on the raw channel
     * means no sensor is wired rather than a lever nobody is pulling.
     */
    @Test
    fun `a second brake is told apart from a missing one by its raw channel`() {
        val oneBrake = ViContDecoder.sensors(
            hex(0x00, 0x00, 0x00, 0x05, 0x00, 0x00, 0x02, 0xDA, 0x00, 0x05, 0x00, 0x00),
        )!!
        assertFalse(oneBrake.hasSecondBrake)

        val twoBrakesReleased = ViContDecoder.sensors(
            hex(0x00, 0x00, 0x00, 0x05, 0x00, 0x00, 0x02, 0xDA, 0x00, 0x05, 0x00, 0x04),
        )!!
        assertTrue(twoBrakesReleased.hasSecondBrake)
        assertEquals(0, twoBrakesReleased.brake2)
    }

    /**
     * The first byte indexes the phase order table, not a revision: changing it can send the
     * motor backwards, which is why it is named for what it does.
     */
    @Test
    fun `calibration exposes the phase order separately from the hall coefficients`() {
        val c = ViContDecoder.calibration(
            hex(0x05, 0x01, 0xF2, 0x08, 0x89, 0x0D, 0x7D, 0x00, 0xD1, 0x00, 0x6A, 0x00, 0x00),
        )!!
        assertEquals(5, c.phaseOrder)
        assertEquals(498, c.coefficient1)
        assertEquals(2185, c.coefficient2)
        assertEquals(3453, c.coefficient3)
    }
}
