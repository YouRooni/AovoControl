package dev.rooni.aovo.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A profile only carries what it captured. Anything it left out must stay null through
 * storage, otherwise applying a "lights only" profile would also shove speed limits at the
 * controller.
 */
class RideProfileTest {

    @Test
    fun `a fully populated profile survives a round trip`() {
        val profile = RideProfile(
            id = "p1",
            name = "Night ride",
            gear = 1,
            headLight = true,
            ambientLight = false,
            cruiseControl = true,
            zeroStart = false,
            imperial = false,
            limitCruise = 18,
            limitEco = 12,
            limitComfort = 20,
            limitSport = 32,
            throttleResponse = 6,
            brakeResponse = 4,
            speedLimit = 25,
        )
        val decoded = RideProfile.decode(RideProfile.encode(listOf(profile)))
        assertEquals(listOf(profile), decoded)
    }

    @Test
    fun `absent groups stay absent after a round trip`() {
        val profile = RideProfile(id = "p2", name = "Lights", headLight = true)
        val decoded = RideProfile.decode(RideProfile.encode(listOf(profile))).single()

        assertEquals(true, decoded.headLight)
        assertNull(decoded.gear)
        assertNull(decoded.limitEco)
        assertNull(decoded.throttleResponse)
        assertNull(decoded.speedLimit)
    }

    @Test
    fun `false is preserved and not mistaken for absent`() {
        val profile = RideProfile(id = "p3", name = "Day", headLight = false, zeroStart = false)
        val decoded = RideProfile.decode(RideProfile.encode(listOf(profile))).single()
        assertEquals(false, decoded.headLight)
        assertEquals(false, decoded.zeroStart)
    }

    @Test
    fun `zero is preserved and not mistaken for absent`() {
        val profile = RideProfile(id = "p4", name = "Calm", gear = 0, throttleResponse = 0)
        val decoded = RideProfile.decode(RideProfile.encode(listOf(profile))).single()
        assertEquals(0, decoded.gear)
        assertEquals(0, decoded.throttleResponse)
    }

    @Test
    fun `a profile keeps its chosen icon through storage`() {
        val profile = RideProfile(id = "p", name = "Night", icon = "night", headLight = true)
        assertEquals("night", RideProfile.decode(RideProfile.encode(listOf(profile))).single().icon)
    }

    @Test
    fun `an unknown or missing icon falls back to the default`() {
        assertEquals(ProfileIcons.DEFAULT, ProfileIcons.normalise("no-such-icon"))
        assertEquals(ProfileIcons.DEFAULT, ProfileIcons.normalise(""))
        val legacy = RideProfile.decode("""[{"id":"p","name":"Old"}]""").single()
        assertEquals(ProfileIcons.DEFAULT, legacy.icon)
    }

    @Test
    fun `every offered icon is accepted as it is`() {
        ProfileIcons.ALL.forEach { assertEquals(it, ProfileIcons.normalise(it)) }
    }

    @Test
    fun `capture groups are independent`() {
        val throttleOnly = ProfileCapture(
            ride = false,
            modeLimits = false,
            throttleResponse = true,
            brakeResponse = false,
            speedLimit = false,
        )
        assertTrue(throttleOnly.any)
        assertTrue(throttleOnly.needsRegisters)

        val rideOnly = ProfileCapture(
            ride = true,
            modeLimits = false,
            throttleResponse = false,
            brakeResponse = false,
            speedLimit = false,
        )
        assertTrue(rideOnly.any)
        assertFalse(rideOnly.needsRegisters)

        val nothing = ProfileCapture(
            ride = false,
            modeLimits = false,
            throttleResponse = false,
            brakeResponse = false,
            speedLimit = false,
        )
        assertFalse(nothing.any)
    }

    @Test
    fun `per mode caps default to off because many controllers ignore them`() {
        assertFalse(ProfileCapture().modeLimits)
        assertTrue(ProfileCapture().ride)
    }

    @Test
    fun `scope flags describe which write paths a profile needs`() {
        val switchesOnly = RideProfile(id = "a", name = "a", headLight = true)
        assertTrue(switchesOnly.touchesRideState)
        assertFalse(switchesOnly.touchesRegisters)

        val registersOnly = RideProfile(id = "b", name = "b", speedLimit = 20)
        assertFalse(registersOnly.touchesRideState)
        assertTrue(registersOnly.touchesRegisters)

        val empty = RideProfile(id = "c", name = "c")
        assertFalse(empty.touchesRideState)
        assertFalse(empty.touchesRegisters)
    }

    @Test
    fun `malformed storage decodes to an empty list`() {
        assertEquals(emptyList<RideProfile>(), RideProfile.decode(null))
        assertEquals(emptyList<RideProfile>(), RideProfile.decode(""))
        assertEquals(emptyList<RideProfile>(), RideProfile.decode("{oops"))
    }

    @Test
    fun `entries without an id are skipped`() {
        val raw = """[{"name":"no id"},{"id":"ok","name":"fine"}]"""
        val decoded = RideProfile.decode(raw)
        assertEquals(listOf("ok"), decoded.map { it.id })
    }

    @Test
    fun `a missing name falls back to the id rather than being blank`() {
        val decoded = RideProfile.decode("""[{"id":"p9"}]""").single()
        assertEquals("p9", decoded.name)
    }

    @Test
    fun `generated ids are unique`() {
        val ids = (1..50).map { RideProfile.newId() }
        assertTrue(ids.toSet().size > 40)
    }
}
