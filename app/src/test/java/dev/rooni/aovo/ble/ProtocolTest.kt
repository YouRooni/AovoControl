package dev.rooni.aovo.ble

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Exercises the wire format against frames built the way the controller expects them.
 *
 * The reference values come from the stock firmware tooling: CRC-16/MODBUS over the frame
 * body, little-endian checksum appended, big-endian payload fields.
 */
class ProtocolTest {

    private fun hex(vararg values: Int) = ByteArray(values.size) { values[it].toByte() }

    private fun ByteArray.hexString() = joinToString(" ") { "%02X".format(it) }

    private fun sealed(body: ByteArray): ByteArray {
        val crc = Protocol.crc16(body, 0, body.size)
        return body + byteArrayOf((crc and 0xFF).toByte(), ((crc shr 8) and 0xFF).toByte())
    }

    @Test
    fun `crc16 matches the modbus reference vector`() {
        assertEquals(0x4B37, Protocol.crc16("123456789".toByteArray()))
    }

    @Test
    fun `crc16 honours the offset and length window`() {
        val padded = byteArrayOf(0xEE.toByte()) + "123456789".toByteArray() + byteArrayOf(0xEE.toByte())
        assertEquals(0x4B37, Protocol.crc16(padded, 1, 9))
    }

    @Test
    fun `read frame carries address and register count big-endian`() {
        val frame = Protocol.readFrame(Protocol.CMD_READ_PARAMETER, 0x0140, 16)
        assertEquals(8, frame.size)
        assertArrayEquals(
            sealed(hex(0x01, 0x03, 0x01, 0x40, 0x00, 0x10)),
            frame,
        )
    }

    @Test
    fun `short frame is header command and checksum only`() {
        val frame = Protocol.shortFrame(Protocol.CMD_HANDSHAKE)
        assertEquals(4, frame.size)
        assertArrayEquals(sealed(hex(0x01, 0x51)), frame)
    }

    @Test
    fun `transparent mode frame carries the complement of the command`() {
        assertArrayEquals(
            hex(0xA5, 0x00, 0xFF, 0x00, 0x00, 0x00, 0x00, 0x5A),
            Protocol.tranFrame(Protocol.CMD_TRAN),
        )
        assertArrayEquals(
            hex(0xA5, 0x01, 0xFE, 0x00, 0x00, 0x00, 0x00, 0x5A),
            Protocol.tranFrame(Protocol.CMD_PACK),
        )
    }

    @Test
    fun `keep alive frame is the four byte heartbeat`() {
        assertArrayEquals(hex(0xA5, 0x02, 0xFD, 0x5A), Protocol.keepFrame())
    }

    @Test
    fun `write frame repeats the address block and states the payload size`() {
        val frame = Protocol.writeFrame(9, byteArrayOf(0x75, 0x30))
        assertEquals(15, frame.size)
        assertArrayEquals(
            sealed(
                hex(
                    0x01, 0x17,
                    0x00, 0x09, 0x00, 0x01,
                    0x00, 0x09, 0x00, 0x01,
                    0x02, 0x75, 0x30,
                )
            ),
            frame,
        )
    }

    @Test
    fun `write frame handles a 32 bit payload as two registers`() {
        val frame = Protocol.writeFrame(74, byteArrayOf(0, 0, 0x12, 0x34))
        assertEquals(17, frame.size)
        assertEquals(0x02, frame[5].toInt())
        assertEquals(4, frame[10].toInt())
    }

    @Test
    fun `monitor frame packs the switch byte and the four limits`() {
        val frame = Protocol.monitorFrame(0x44, 20, 15, 20, 25)
        assertEquals(10, frame.size)
        assertArrayEquals(
            sealed(hex(0xAC, 0x00, 0x0A, 0x44, 20, 15, 20, 25)),
            frame,
        )
    }

    @Test
    fun `display frame is not checksum protected`() {
        assertArrayEquals(
            hex(0x5A, 0xFA, 0x03, 0x01, 0x19, 0xA5, 0xAF),
            Protocol.displayFrame(3, 25),
        )
    }

    @Test
    fun `firmware chunking indexes blocks and keeps the tail short`() {
        val payload = ByteArray(1100) { (it % 251).toByte() }
        val chunks = Protocol.firmwareChunks(payload)

        assertEquals(3, chunks.size)
        assertEquals(512 + 8, chunks[0].size)
        assertEquals(512 + 8, chunks[1].size)
        assertEquals(76 + 8, chunks[2].size)

        chunks.forEachIndexed { index, chunk ->
            assertEquals(Protocol.CMD_UPDATE_FM, chunk[1])
            assertEquals(index, Decoder.u16(chunk, 2))
            val body = chunk.size - 2
            val crc = Protocol.crc16(chunk, 0, body)
            assertEquals((crc and 0xFF).toByte(), chunk[body])
            assertEquals(((crc shr 8) and 0xFF).toByte(), chunk[body + 1])
        }

        val rebuilt = chunks.flatMap { it.copyOfRange(6, it.size - 2).toList() }.toByteArray()
        assertArrayEquals(payload, rebuilt)
    }

    @Test
    fun `firmware chunking handles an exact multiple of the block size`() {
        val chunks = Protocol.firmwareChunks(ByteArray(1024))
        assertEquals(2, chunks.size)
        assertEquals(520, chunks[1].size)
    }

    @Test
    fun `at commands are wrapped in the bracket syntax`() {
        assertEquals("AT+PWD[123456]", String(Protocol.atPassword("123456")))
        assertEquals("AT+PWDM[654321]", String(Protocol.atChangePassword("654321")))
        assertEquals("AT+NAME[Scooter]", String(Protocol.atName("Scooter")))
        assertEquals("AT+NFC[1]", String(Protocol.atSetNfc(true)))
        assertEquals("AT+NFC[0]", String(Protocol.atSetNfc(false)))
        assertEquals("AT+DRIVEMODE[2]", String(Protocol.atSetDriveMode(2)))
        assertEquals("AT+TLVOICEOFF[1]", String(Protocol.atSetVoice(1)))
        assertEquals("AT+DEL[1]", String(Protocol.atNfcDelete()))
    }

    @Test
    fun `frame builders never disagree with the reader about length`() {
        val frames = listOf(
            Protocol.readFrame(Protocol.CMD_ESC_INFO, 0, 4),
            Protocol.shortFrame(Protocol.CMD_ERASE_FLASH),
            Protocol.writeFrame(32, byteArrayOf(0x01, 0x2C)),
        )
        frames.forEach { frame ->
            val body = frame.size - 2
            assertTrue(
                frame.hexString(),
                frame[body] == (Protocol.crc16(frame, 0, body) and 0xFF).toByte(),
            )
        }
    }
}
