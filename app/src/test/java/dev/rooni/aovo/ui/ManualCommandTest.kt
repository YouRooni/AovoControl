package dev.rooni.aovo.ui

import dev.rooni.aovo.ui.screen.parseHexByte
import dev.rooni.aovo.ui.screen.parseHexBytes
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The manual command box sends whatever it parses straight to the scooter, so anything it
 * misreads is a command nobody meant to send.
 */
class ManualCommandTest {

    @Test
    fun `a command code is read with or without a prefix`() {
        assertEquals(0x3C, parseHexByte("3C"))
        assertEquals(0x3C, parseHexByte("3c"))
        assertEquals(0x3C, parseHexByte("0x3C"))
        assertEquals(0x05, parseHexByte("5"))
        assertEquals(0xFF, parseHexByte(" FF "))
    }

    @Test
    fun `anything that is not one byte of hex is refused`() {
        assertNull(parseHexByte(""))
        assertNull(parseHexByte("GG"))
        assertNull(parseHexByte("3C1"))
        assertNull(parseHexByte("-1"))
    }

    @Test
    fun `payload bytes are read with the separators people actually type`() {
        assertArrayEquals(byteArrayOf(0x1E, 0x25), parseHexBytes("1E25"))
        assertArrayEquals(byteArrayOf(0x1E, 0x25), parseHexBytes("1E 25"))
        assertArrayEquals(byteArrayOf(0x1E, 0x25), parseHexBytes("1E,25"))
        assertArrayEquals(byteArrayOf(0x05, 0x01, 0x2C), parseHexBytes("05 01 2c"))
    }

    /** A command with no arguments is ordinary here, so empty has to parse rather than fail. */
    @Test
    fun `an empty payload is valid`() {
        assertArrayEquals(ByteArray(0), parseHexBytes(""))
        assertArrayEquals(ByteArray(0), parseHexBytes("   "))
    }

    /**
     * An odd digit count is the dangerous case: dropping or padding one would shift every
     * byte after it and send a different command than the one that was typed.
     */
    @Test
    fun `a half-written byte is refused rather than guessed at`() {
        assertNull(parseHexBytes("1E2"))
        assertNull(parseHexBytes("1E 2"))
        assertNull(parseHexBytes("ZZ"))
    }
}
