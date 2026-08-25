package dev.rooni.aovo.ble

import java.util.UUID

object Protocol {

    private fun uuid(short: String): UUID =
        UUID.fromString("0000$short-0000-1000-8000-00805f9b34fb")

    val DATA_SERVICE: UUID = uuid("F1F0")
    val DATA_TX: UUID = uuid("F1F1")
    val DATA_RX: UUID = uuid("F1F2")

    val CMD_SERVICE: UUID = uuid("F2F0")
    val CMD_TX: UUID = uuid("F2F1")
    val CMD_RX: UUID = uuid("F2F2")

    val CCCD: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

    /** Frame headers. */
    const val HEAD_ESC: Byte = 0x01
    const val HEAD_MONITOR: Byte = 0xAC.toByte()
    const val HEAD_TRAN: Byte = 0xA5.toByte()
    const val END_TRAN: Byte = 0x5A

    /** Controller commands (byte 1 of an ESC frame). */
    const val CMD_TRAN: Byte = 0x00
    const val CMD_PACK: Byte = 0x01
    const val CMD_KEEP: Byte = 0x02
    const val CMD_READ_PARAMETER: Byte = 0x03
    const val CMD_ESC_INFO: Byte = 0x07
    const val CMD_BAT_INFO: Byte = 0x08
    const val CMD_WRITE_PARAMETER: Byte = 0x10
    const val CMD_RW_PARAMETER: Byte = 0x17
    const val CMD_UPDATE_FM: Byte = 0x50
    const val CMD_HANDSHAKE: Byte = 0x51
    const val CMD_ERASE_FLASH: Byte = 0x52
    const val CMD_BOOT_EXIT: Byte = 0x53
    const val CMD_TRAN_STOP: Byte = 0xFF.toByte()

    const val CMD_FAIL: Byte = 0x81.toByte()
    const val CMD_READ_PARAM_FAIL: Byte = 0x83.toByte()
    const val CMD_READ_INFO_FAIL: Byte = 0x87.toByte()
    const val CMD_READ_BAT_FAIL: Byte = 0x88.toByte()
    const val CMD_WRITE_PARAM_FAIL: Byte = 0x90.toByte()
    const val CMD_RW_PARAM_FAIL: Byte = 0x97.toByte()
    const val CMD_ERASE_FAIL: Byte = 0xD2.toByte()
    const val CMD_UPDATE_FAIL: Byte = 0xD1.toByte()
    const val CMD_HANDSHAKE_FAIL: Byte = 0xD0.toByte()

    const val MTU_SIZE = 200
    const val FIRMWARE_CHUNK = 512

    /** CRC-16/MODBUS: poly 0x8005 reflected, init 0xFFFF, no final xor. */
    fun crc16(data: ByteArray, offset: Int = 0, length: Int = data.size): Int {
        var crc = 0xFFFF
        val end = minOf(offset + length, data.size)
        for (i in offset until end) {
            crc = crc xor (data[i].toInt() and 0xFF)
            repeat(8) {
                crc = if (crc and 1 != 0) (crc ushr 1) xor 0xA001 else crc ushr 1
            }
        }
        return crc and 0xFFFF
    }

    /** Appends the little-endian CRC of [len] leading bytes at position [len]. */
    private fun seal(frame: ByteArray, len: Int): ByteArray {
        val crc = crc16(frame, 0, len)
        frame[len] = (crc and 0xFF).toByte()
        frame[len + 1] = ((crc shr 8) and 0xFF).toByte()
        return frame
    }

    // ---- outgoing frame builders -------------------------------------------------

    /** `A5 cmd ~cmd 00 00 00 00 5A` — transparent-mode control. */
    fun tranFrame(cmd: Byte): ByteArray = byteArrayOf(
        HEAD_TRAN, cmd, cmd.toInt().inv().toByte(), 0, 0, 0, 0, END_TRAN
    )

    /** `A5 02 FD 5A` — monitor-mode heartbeat. */
    fun keepFrame(): ByteArray = byteArrayOf(HEAD_TRAN, CMD_KEEP, 0xFD.toByte(), END_TRAN)

    /** Register read / info request: `01 cmd addrH addrL cntH cntL crcL crcH`. */
    fun readFrame(command: Byte, address: Int, count: Int): ByteArray = seal(
        byteArrayOf(
            HEAD_ESC, command,
            (address shr 8).toByte(), (address and 0xFF).toByte(),
            (count shr 8).toByte(), (count and 0xFF).toByte(),
            0, 0
        ), 6
    )

    /** Short command with no payload: `01 cmd crcL crcH`. */
    fun shortFrame(command: Byte): ByteArray =
        seal(byteArrayOf(HEAD_ESC, command, 0, 0), 2)

        fun writeFrame(address: Int, value: ByteArray): ByteArray {
        val regs = value.size / 2
        val frame = ByteArray(value.size + 13)
        frame[0] = HEAD_ESC
        frame[1] = CMD_RW_PARAMETER
        frame[2] = (address shr 8).toByte()
        frame[3] = (address and 0xFF).toByte()
        frame[4] = (regs shr 8).toByte()
        frame[5] = (regs and 0xFF).toByte()
        frame[6] = frame[2]
        frame[7] = frame[3]
        frame[8] = frame[4]
        frame[9] = frame[5]
        frame[10] = (value.size and 0xFF).toByte()
        value.copyInto(frame, 11)
        return seal(frame, frame.size - 2)
    }

        fun monitorFrame(
        flags: Byte,
        limitCruise: Int,
        limitMode1: Int,
        limitMode2: Int,
        limitMode3: Int,
    ): ByteArray = seal(
        byteArrayOf(
            HEAD_MONITOR, 0x00, 0x0A, flags,
            limitCruise.toByte(), limitMode1.toByte(),
            limitMode2.toByte(), limitMode3.toByte(),
            0, 0
        ), 8
    )

    /** Display-unit command: `5A FA fn 01 value A5 AF` (not CRC protected). */
    fun displayFrame(function: Int, value: Int): ByteArray = byteArrayOf(
        END_TRAN, 0xFA.toByte(), function.toByte(), 0x01,
        value.toByte(), HEAD_TRAN, 0xAF.toByte()
    )

    /** One OTA payload chunk: `01 50 idxH idxL lenH lenL <data> crcL crcH`. */
    fun firmwareChunks(data: ByteArray): List<ByteArray> {
        val tail = data.size % FIRMWARE_CHUNK
        var blocks = data.size / FIRMWARE_CHUNK
        if (tail > 0) blocks++
        val out = ArrayList<ByteArray>(blocks)
        var read = 0
        for (index in 0 until blocks) {
            val len = if (tail > 0 && index == blocks - 1) tail else FIRMWARE_CHUNK
            val frame = ByteArray(len + 8)
            frame[0] = HEAD_ESC
            frame[1] = CMD_UPDATE_FM
            frame[2] = (index shr 8).toByte()
            frame[3] = (index and 0xFF).toByte()
            frame[4] = (len shr 8).toByte()
            frame[5] = (len and 0xFF).toByte()
            data.copyInto(frame, 6, read, read + len)
            out.add(seal(frame, len + 6))
            read += len
        }
        return out
    }

    // ---- AT commands (CMD channel) -----------------------------------------------

    fun at(command: String): ByteArray = command.toByteArray(Charsets.UTF_8)

    fun atPassword(pwd: String) = at("AT+PWD[$pwd]")
    fun atChangePassword(pwd: String) = at("AT+PWDM[$pwd]")
    fun atName(name: String) = at("AT+NAME[$name]")
    fun atDeviceType() = at("AT+DEVICE?")
    fun atDriveMode() = at("AT+DRIVEMODE?")
    fun atSetDriveMode(type: Int) = at("AT+DRIVEMODE[$type]")
    fun atNfc() = at("AT+NFC?")
    fun atSetNfc(on: Boolean) = at("AT+NFC[${if (on) 1 else 0}]")
    fun atNfcDelete() = at("AT+DEL[1]")
    fun atVoice() = at("AT+TLVOICEOFF?")
    fun atSetVoice(type: Int) = at("AT+TLVOICEOFF[$type]")
}
