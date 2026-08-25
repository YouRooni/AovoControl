package dev.rooni.aovo.ble

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The catalogue drives writes straight into the dashboard's EEPROM, so an off-by-one in the
 * index would silently rewrite the wrong setting.
 */
class EngineeringParamsTest {

    @Test
    fun `the menu holds every parameter the dashboard exposes`() {
        assertEquals(33, EngineeringParams.ALL.size)
        assertEquals((1..33).toList(), EngineeringParams.ALL.map { it.number })
    }

    /** The display counts from P1 while the wire counts from zero. */
    @Test
    fun `the wire index is one below the P number`() {
        EngineeringParams.ALL.forEach {
            assertEquals("P${it.number}", it.number - 1, it.index)
        }
    }

    /**
     * These four are also written from the ride and controller screens. If the catalogue and
     * those paths ever disagree, one of them is writing to the wrong parameter.
     */
    @Test
    fun `the indices shared with the rest of the app match`() {
        assertEquals(4, EngineeringParams.byNumber(5)!!.index)   // auto shutdown
        assertEquals(7, EngineeringParams.byNumber(8)!!.index)   // Eco limit
        assertEquals(8, EngineeringParams.byNumber(9)!!.index)   // Drive limit
        assertEquals(9, EngineeringParams.byNumber(10)!!.index)  // Sport limit
    }

    @Test
    fun `every default sits inside its own range`() {
        EngineeringParams.ALL.forEach {
            assertTrue(
                "P${it.number} default ${it.default} outside ${it.min}..${it.max}",
                it.default in it.min..it.max,
            )
        }
    }

    @Test
    fun `no parameter can be written outside what the display can show`() {
        EngineeringParams.ALL.forEach {
            assertTrue("P${it.number} min", it.min >= 0)
            // Above 99 the dashboard stops displaying the item at all.
            assertTrue("P${it.number} max", it.max <= 99)
            assertTrue("P${it.number} range", it.min < it.max)
        }
    }

    /**
     * The two that can immobilise the scooter or take the pack below its safe floor. Both
     * rescale every voltage the controller works with.
     */
    @Test
    fun `the settings that can strand a rider are marked as dangerous`() {
        assertEquals(ParamRisk.DANGER, EngineeringParams.byNumber(17)!!.risk)
        assertEquals(ParamRisk.DANGER, EngineeringParams.byNumber(23)!!.risk)
    }

    @Test
    fun `the parameters nobody has identified are marked unknown`() {
        (24..28).forEach {
            assertEquals("P$it", ParamRisk.UNKNOWN, EngineeringParams.byNumber(it)!!.risk)
        }
    }

    @Test
    fun `the boolean settings offer only zero and one`() {
        listOf(2, 3, 4, 19, 22, 24).forEach {
            val param = EngineeringParams.byNumber(it)!!
            assertEquals("P$it min", 0, param.min)
            assertEquals("P$it max", 1, param.max)
        }
    }

    @Test
    fun `lookup finds real entries and refuses invented ones`() {
        assertNotNull(EngineeringParams.byNumber(1))
        assertNotNull(EngineeringParams.byNumber(33))
        assertNull(EngineeringParams.byNumber(0))
        assertNull(EngineeringParams.byNumber(34))
    }

    /** The firmware variable names are the only way to confirm a write from the boot log. */
    @Test
    fun `the settings visible in the boot log carry their variable name`() {
        assertEquals("SMGlux", EngineeringParams.byNumber(1)!!.firmwareName)
        assertEquals("speedLit1", EngineeringParams.byNumber(8)!!.firmwareName)
        assertEquals("car.lowVol", EngineeringParams.byNumber(17)!!.firmwareName)
        assertEquals("bat.batV5", EngineeringParams.byNumber(33)!!.firmwareName)
    }
}
