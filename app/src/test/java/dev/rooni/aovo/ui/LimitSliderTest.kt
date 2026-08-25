package dev.rooni.aovo.ui

import dev.rooni.aovo.ui.screen.LIMIT_MAX
import dev.rooni.aovo.ui.screen.LIMIT_MIN
import dev.rooni.aovo.ui.screen.snapLimit
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The per-gear limit sliders let the rider pick a speed cap, so a value that cannot be
 * selected is a value the scooter can never be set to.
 */
class LimitSliderTest {

    private val steps = (LIMIT_MAX - LIMIT_MIN).toInt() - 1

    /** How the slider works out the position of detent [index], fractions and all. */
    private fun detent(index: Int): Float {
        val fraction = index.toFloat() / (steps + 1).toFloat()
        return LIMIT_MIN + fraction * (LIMIT_MAX - LIMIT_MIN)
    }

    @Test
    fun `every whole speed in range can be selected`() {
        val reachable = (0..steps + 1).map { snapLimit(detent(it)) }.toSet()
        val expected = (LIMIT_MIN.toInt()..LIMIT_MAX.toInt()).toSet()
        assertEquals(expected, reachable)
    }

    @Test
    fun `each detent stands for exactly one speed`() {
        val values = (0..steps + 1).map { snapLimit(detent(it)) }
        assertEquals(values.size, values.distinct().size)
        assertEquals(values.sorted(), values)
    }

    /**
     * The bug this replaced: truncating a detent that arrives a hair under its whole number
     * repeats the value below and skips the one it was meant to be.
     */
    @Test
    fun `a position just short of a whole number rounds up rather than down`() {
        assertEquals(25, snapLimit(24.999998f))
        assertEquals(25, snapLimit(25.000002f))
        assertEquals(24, snapLimit(24.4f))
    }

    @Test
    fun `positions outside the track are held at its ends`() {
        assertEquals(LIMIT_MIN.toInt(), snapLimit(-3f))
        assertEquals(LIMIT_MAX.toInt(), snapLimit(120f))
    }
}
