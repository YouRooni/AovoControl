package dev.rooni.aovo.ble

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The scan list must only offer scooters, and each family expects a different unlock
 * handshake. Getting either wrong shows the user their headphones, or connects without the
 * password the module is waiting for.
 */
class DeviceClassificationTest {

    private fun device(name: String) = ScannedDevice(name, "AA:BB:CC:DD:EE:FF", -55)

    @Test
    fun `scooter modules are recognised by their advertised prefix`() {
        listOf("hw_ug1234", "HW_Z900", "zyd-0042", "SN123456", "hwABC").forEach {
            assertTrue(it, device(it).isScooter)
        }
    }

    @Test
    fun `vicont dashboards advertise under their own prefixes`() {
        listOf("E-S1234", "VC_0042", "VC-9001", "VA-7788", "vc_lower").forEach {
            assertTrue(it, device(it).isScooter)
        }
    }

    @Test
    fun `everything else on the air is ignored`() {
        listOf(
            "AirPods Pro",
            "Galaxy Watch6",
            "Mi Band 8",
            "JBL Flip 5",
            "MacBook Pro",
            "",
        ).forEach { assertFalse(it, device(it).isScooter) }
    }

    @Test
    fun `hw_ug modules ask the owner for their own password`() {
        val device = device("hw_ug0001")
        assertEquals(AuthMode.USER_PASSWORD, device.authMode)
        assertTrue(device.requiresPassword)
    }

    @Test
    fun `hw_z and zyd modules skip the handshake entirely`() {
        assertEquals(AuthMode.NONE, device("hw_z100").authMode)
        assertEquals(AuthMode.NONE, device("ZYD_2024").authMode)
        assertFalse(device("hw_z100").requiresPassword)
    }

    @Test
    fun `other modules use the factory password without prompting`() {
        val device = device("sn778899")
        assertEquals(AuthMode.DEFAULT_PASSWORD, device.authMode)
        assertFalse(device.requiresPassword)
        assertEquals("888888", ScannedDevice.DEFAULT_PASSWORD)
    }

    @Test
    fun `prefix matching ignores case`() {
        assertEquals(AuthMode.USER_PASSWORD, device("HW_UG5555").authMode)
        assertEquals(AuthMode.NONE, device("Zyd_x").authMode)
    }
}
