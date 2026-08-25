package dev.rooni.aovo.ble

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The family decides which protocol every later command is written in, so the interesting
 * case is the scooter that looks like both.
 */
class FamilyDetectionTest {

    private val zydServices = listOf(Protocol.DATA_SERVICE, Protocol.CMD_SERVICE)
    private val viContServices = listOf(ViContProtocol.SERVICE)

    @Test
    fun `a zyd controller is recognised by its data service`() {
        assertEquals(ScooterFamily.ZYD, FamilyDetection.familyFor(zydServices))
    }

    @Test
    fun `a vicont dashboard is recognised by FEE0`() {
        assertEquals(ScooterFamily.VICONT, FamilyDetection.familyFor(viContServices))
    }

    @Test
    fun `older dashboards on FFF0 are still vicont`() {
        val endpoint = FamilyDetection.endpointFor(listOf(ViContProtocol.ALT_SERVICE))!!
        assertEquals(ScooterFamily.VICONT, endpoint.family)
        assertEquals(ViContProtocol.ALT_CHARACTERISTIC, endpoint.write)
        assertEquals(ViContProtocol.ALT_CHARACTERISTIC, endpoint.notify)
    }

    /**
     * The case the name-based guess got wrong. ViCont hardware carries a half-implemented
     * F1F0 service alongside its real one; choosing ZYD there is what left every expert
     * parameter and speed limit silently inert.
     */
    @Test
    fun `a dashboard exposing both services is treated as vicont`() {
        val both = viContServices + zydServices
        assertEquals(ScooterFamily.VICONT, FamilyDetection.familyFor(both))
        assertEquals(ScooterFamily.VICONT, FamilyDetection.familyFor(both.reversed()))
    }

    @Test
    fun `vicont reads and writes over one characteristic`() {
        val endpoint = FamilyDetection.endpointFor(viContServices)!!
        assertEquals(ViContProtocol.CHARACTERISTIC, endpoint.write)
        assertEquals(endpoint.write, endpoint.notify)
    }

    @Test
    fun `zyd keeps its separate transmit and receive characteristics`() {
        val endpoint = FamilyDetection.endpointFor(zydServices)!!
        assertEquals(Protocol.DATA_TX, endpoint.write)
        assertEquals(Protocol.DATA_RX, endpoint.notify)
    }

    @Test
    fun `an unrelated device offers no endpoint`() {
        val headphones = listOf(
            UUIDs.of("110B"), // audio sink
            UUIDs.of("180F"), // battery service
        )
        assertNull(FamilyDetection.endpointFor(headphones))
        assertEquals(ScooterFamily.UNKNOWN, FamilyDetection.familyFor(headphones))
        assertEquals(ScooterFamily.UNKNOWN, FamilyDetection.familyFor(emptyList()))
    }

    private object UUIDs {
        fun of(short: String): java.util.UUID =
            java.util.UUID.fromString("0000$short-0000-1000-8000-00805f9b34fb")
    }
}
