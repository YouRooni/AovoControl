package dev.rooni.aovo.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import android.util.Log
import dev.rooni.aovo.data.SessionLog
import java.util.UUID
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean

@SuppressLint("MissingPermission")
class AovoGatt(private val context: Context) {

    interface Listener {
        fun onScanResult(device: ScannedDevice)
        fun onScanStopped()
        fun onConnected()
        fun onServicesReady()
        fun onDisconnected()
        fun onConnectFailed(reason: String)
        fun onData(payload: ByteArray)
        fun onCommand(text: String, raw: ByteArray)

                fun onLegacyData(payload: ByteArray) = Unit
    }

    var listener: Listener? = null

    private val handler = Handler(Looper.getMainLooper())
    private val manager get() = context.getSystemService(BluetoothManager::class.java)
    private val adapter: BluetoothAdapter? get() = manager?.adapter

    private var gatt: BluetoothGatt? = null
    private var dataTx: BluetoothGattCharacteristic? = null
    private var cmdTx: BluetoothGattCharacteristic? = null

        @Volatile
    var endpoint: ScooterEndpoint? = null
        private set

    val family: ScooterFamily get() = endpoint?.family ?: ScooterFamily.UNKNOWN

        @Volatile
    var listeningToLegacy: Boolean = false
        private set

    private val pending = ConcurrentLinkedQueue<() -> Unit>()
    private val busy = AtomicBoolean(false)

    @Volatile
    var isConnected: Boolean = false
        private set

    @Volatile
    var isScanning: Boolean = false
        private set

    /** Largest payload a single ATT write can carry, once MTU negotiation settles. */
    @Volatile
    var maxWriteLength: Int = 20
        private set

    val isBluetoothEnabled: Boolean get() = adapter?.isEnabled == true

    // ---- scanning -----------------------------------------------------------------

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device = result.device ?: return
            val name = result.scanRecord?.deviceName ?: runCatching { device.name }.getOrNull()
            if (name.isNullOrBlank()) return
            listener?.onScanResult(ScannedDevice(name, device.address, result.rssi))
        }

        override fun onBatchScanResults(results: MutableList<ScanResult>) {
            results.forEach { onScanResult(ScanSettings.CALLBACK_TYPE_ALL_MATCHES, it) }
        }

        override fun onScanFailed(errorCode: Int) {
            Log.e(TAG, "scan failed: " + errorCode)
            isScanning = false
            listener?.onScanStopped()
        }
    }

    private val stopScanRunnable = Runnable { stopScan() }

    fun startScan(durationMs: Long = 20_000L) {
        val scanner = adapter?.bluetoothLeScanner ?: return
        if (isScanning) return
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
            .build()
        // Many bridge modules omit the service UUID from their advertisement, so the scan runs
        // unfiltered and the UI ranks results instead.
        val filters = listOf(
            ScanFilter.Builder().setServiceUuid(ParcelUuid(Protocol.DATA_SERVICE)).build(),
            ScanFilter.Builder().setServiceUuid(ParcelUuid(ViContProtocol.SERVICE)).build(),
            ScanFilter.Builder().build(),
        )
        isScanning = true
        runCatching { scanner.startScan(filters, settings, scanCallback) }
            .onFailure { isScanning = false }
        handler.removeCallbacks(stopScanRunnable)
        handler.postDelayed(stopScanRunnable, durationMs)
    }

    fun stopScan() {
        handler.removeCallbacks(stopScanRunnable)
        if (!isScanning) return
        isScanning = false
        runCatching { adapter?.bluetoothLeScanner?.stopScan(scanCallback) }
        listener?.onScanStopped()
    }

    // ---- connection ---------------------------------------------------------------

    fun connect(address: String) {
        stopScan()
        close()
        val device: BluetoothDevice = runCatching { adapter?.getRemoteDevice(address) }.getOrNull()
            ?: run {
                listener?.onConnectFailed("Unknown device")
                return
            }
        gatt = device.connectGatt(context, false, callback, BluetoothDevice.TRANSPORT_LE)
    }

    fun disconnect() {
        runCatching { gatt?.disconnect() }
        close()
    }

    fun close() {
        maxWriteLength = 20
        pending.clear()
        busy.set(false)
        dataTx = null
        cmdTx = null
        endpoint = null
        listeningToLegacy = false
        isConnected = false
        runCatching { gatt?.close() }
        gatt = null
    }

    // ---- writes -------------------------------------------------------------------

    fun writeData(payload: ByteArray) = enqueueWrite(dataTx, payload)

    fun writeCommand(payload: ByteArray) = enqueueWrite(cmdTx, payload)

    private fun enqueueWrite(target: BluetoothGattCharacteristic?, payload: ByteArray) {
        val characteristic = target ?: run {
            SessionLog.warn("write dropped", "no characteristic bound")
            return
        }
        val connection = gatt ?: return
        if (characteristic != cmdTx && !belongsToFamily(payload)) {
            // Sending one protocol's frames to the other family's characteristic is never
            // merely useless. A ViCont dashboard reads a length byte straight out of the
            // frame, so a ZYD frame whose third byte happens to be large leaves its parser
            // waiting mid-packet and the bytes that follow get read as some other command
            // entirely. Refuse at the wire rather than trusting every caller upstream.
            SessionLog.warn(
                "frame refused",
                "not a ${family.name} frame: " + payload.take(4).joinToString(" ") {
                    String.format(java.util.Locale.ROOT, "%02X", it)
                },
            )
            Log.w(TAG, "refused foreign frame for family " + family)
            return
        }
        if (SessionLog.enabled.value) {
            if (characteristic == cmdTx) {
                SessionLog.tx("AT", String(payload, Charsets.UTF_8).trim { it <= ' ' })
            } else {
                val note = FrameSummary.outgoing(family, payload)
                if (note.stream != null) SessionLog.stream(note.stream, note.detail)
                else SessionLog.tx(note.label, note.detail, payload)
            }
        }
        pending.add {
            val supportsAck =
                characteristic.properties and BluetoothGattCharacteristic.PROPERTY_WRITE != 0
            val type = if (supportsAck) {
                BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            } else {
                BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
            }
            val ok = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                connection.writeCharacteristic(characteristic, payload, type) ==
                    BluetoothGatt.GATT_SUCCESS
            } else {
                @Suppress("DEPRECATION")
                characteristic.writeType = type
                @Suppress("DEPRECATION")
                characteristic.value = payload
                @Suppress("DEPRECATION")
                connection.writeCharacteristic(characteristic)
            }
            if (!ok) next()
        }
        drain()
    }

        private fun belongsToFamily(payload: ByteArray): Boolean {
        val viCont = payload.size >= 3 &&
            payload[0] == 0xFA.toByte() &&
            payload[1] == 0xAF.toByte() &&
            payload[2] == 0xA5.toByte()
        return when (family) {
            ScooterFamily.VICONT -> viCont
            ScooterFamily.ZYD -> !viCont
            // Before discovery resolves there is nothing to check against.
            ScooterFamily.UNKNOWN -> true
        }
    }

    private fun drain() {
        if (!busy.compareAndSet(false, true)) return
        val op = pending.poll()
        if (op == null) {
            busy.set(false)
            return
        }
        handler.post { runCatching(op).onFailure { next() } }
    }

    private fun next() {
        busy.set(false)
        drain()
    }

    // ---- callbacks ----------------------------------------------------------------

    private val callback = object : BluetoothGattCallback() {

        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    isConnected = true
                    listener?.onConnected()
                    handler.postDelayed({ runCatching { g.discoverServices() } }, 250)
                }

                BluetoothProfile.STATE_DISCONNECTED -> {
                    val wasConnected = isConnected
                    isConnected = false
                    close()
                    if (wasConnected) listener?.onDisconnected()
                    else listener?.onConnectFailed("GATT status " + status)
                }
            }
        }

        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                listener?.onConnectFailed("Service discovery failed (" + status + ")")
                return
            }
            val resolved = FamilyDetection.endpointFor(g.services.orEmpty().map { it.uuid })
            endpoint = resolved
            if (resolved == null) {
                listener?.onConnectFailed("Controller service not found")
                return
            }
            dataTx = g.getService(resolved.service)?.getCharacteristic(resolved.write)
            if (dataTx == null) {
                listener?.onConnectFailed("Controller service not found")
                return
            }
            // The AT channel only exists on ZYD modules; ViCont has no password stage.
            cmdTx = if (resolved.family == ScooterFamily.ZYD) {
                g.getService(Protocol.CMD_SERVICE)?.getCharacteristic(Protocol.CMD_TX)
            } else {
                null
            }
            // ViCont hardware carries a half-built F1F0 service alongside its own. Its
            // register reads never answer, but it does broadcast the speed-limit frame
            // unprompted, so it is worth listening to. Read-only, by subscription alone.
            listeningToLegacy = resolved.family == ScooterFamily.VICONT &&
                g.getService(Protocol.DATA_SERVICE)?.getCharacteristic(Protocol.DATA_RX) != null
            pending.add { if (!g.requestMtu(Protocol.MTU_SIZE)) next() }
            if (resolved.family == ScooterFamily.ZYD) {
                enableNotify(g, Protocol.CMD_SERVICE, Protocol.CMD_RX)
            }
            enableNotify(g, resolved.service, resolved.notify)
            if (listeningToLegacy) enableNotify(g, Protocol.DATA_SERVICE, Protocol.DATA_RX)
            pending.add {
                next()
                listener?.onServicesReady()
            }
            drain()
        }

        override fun onMtuChanged(g: BluetoothGatt, mtu: Int, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                maxWriteLength = (mtu - 3).coerceIn(20, 512)
            }
            next()
        }

        override fun onDescriptorWrite(
            g: BluetoothGatt,
            descriptor: BluetoothGattDescriptor,
            status: Int,
        ) {
            next()
        }

        override fun onCharacteristicWrite(
            g: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int,
        ) {
            next()
        }

        override fun onCharacteristicChanged(
            g: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
        ) {
            dispatch(characteristic.uuid, value)
        }

        @Deprecated("Deprecated in API 33")
        @Suppress("DEPRECATION")
        override fun onCharacteristicChanged(
            g: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
        ) {
            dispatch(characteristic.uuid, characteristic.value ?: return)
        }
    }

    private fun dispatch(uuid: UUID, value: ByteArray) {
        when (uuid) {
            endpoint?.notify -> {
                if (SessionLog.enabled.value) {
                    val note = FrameSummary.incoming(family, value)
                    if (note.stream != null) SessionLog.stream(note.stream, note.detail)
                    else SessionLog.rx(note.label, note.detail, value)
                }
                listener?.onData(value)
            }

            Protocol.DATA_RX -> if (listeningToLegacy) listener?.onLegacyData(value)

            Protocol.CMD_RX -> {
                // The module terminates AT replies with a NUL, which trim() does not treat
                // as whitespace, so it would otherwise land in the log verbatim.
                SessionLog.rx("AT", String(value, Charsets.UTF_8).trim { it <= ' ' })
                listener?.onCommand(String(value, Charsets.UTF_8), value)
            }
        }
    }

    private fun enableNotify(g: BluetoothGatt, service: UUID, characteristic: UUID) {
        val ch = g.getService(service)?.getCharacteristic(characteristic) ?: return
        pending.add {
            if (!g.setCharacteristicNotification(ch, true)) {
                next()
                return@add
            }
            val cccd = ch.getDescriptor(Protocol.CCCD)
            if (cccd == null) {
                next()
                return@add
            }
            val enable = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            val ok = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                g.writeDescriptor(cccd, enable) == BluetoothGatt.GATT_SUCCESS
            } else {
                @Suppress("DEPRECATION")
                cccd.setValue(enable)
                @Suppress("DEPRECATION")
                g.writeDescriptor(cccd)
            }
            if (!ok) next()
        }
    }

    private companion object {
        const val TAG = "AovoGatt"
    }
}
