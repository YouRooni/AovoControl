package dev.rooni.aovo.ble

import android.content.Context
import android.location.LocationManager
import android.os.Build
import android.util.Log
import dev.rooni.aovo.R
import dev.rooni.aovo.data.SessionLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus
import kotlinx.coroutines.Dispatchers
import java.util.concurrent.atomic.AtomicInteger

class AovoCore(context: Context) : AovoGatt.Listener {

    enum class Mode { IDLE, MONITORING, ESC_INFO, READ_PARAMS, WRITE_PARAM, OTA }

    private val appContext = context.applicationContext
    private val gatt = AovoGatt(appContext)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val reader = FrameReader { onFrame(it) }

    /** Separate reassembly for the ZYD frames a ViCont scooter broadcasts alongside its own. */
    private val legacyReader = FrameReader { onLegacyFrame(it) }

    private val _connection = MutableStateFlow(ConnectionState.IDLE)
    val connection = _connection.asStateFlow()

    /** Everything the radio saw; the UI decides how much of it to show. */
    private val _allDevices = MutableStateFlow<List<ScannedDevice>>(emptyList())
    val allDevices = _allDevices.asStateFlow()

    private val _telemetry = MutableStateFlow(Telemetry())
    val telemetry = _telemetry.asStateFlow()

    private val _ride = MutableStateFlow(RideState())
    val ride = _ride.asStateFlow()

    private val _params = MutableStateFlow(ControllerParams())
    val params = _params.asStateFlow()

    /** Set when the register sweep gave up, so the UI can offer a retry. */
    private val _paramsStalled = MutableStateFlow(false)
    val paramsStalled = _paramsStalled.asStateFlow()

    private val _escInfo = MutableStateFlow(EscInfo())
    val escInfo = _escInfo.asStateFlow()

    private val _firmwareVersions = MutableStateFlow<List<String>>(emptyList())
    val firmwareVersions = _firmwareVersions.asStateFlow()

    private val _ota = MutableStateFlow(OtaProgress())
    val ota = _ota.asStateFlow()

    private val _events = MutableSharedFlow<CoreEvent>(extraBufferCapacity = 16)
    val events = _events.asSharedFlow()

    private val _connectedDevice = MutableStateFlow<ScannedDevice?>(null)
    val connectedDevice = _connectedDevice.asStateFlow()

        private val _family = MutableStateFlow(ScooterFamily.UNKNOWN)
    val family = _family.asStateFlow()

        private val _engineeringValues = MutableStateFlow<Map<Int, Int>>(emptyMap())
    val engineeringValues = _engineeringValues.asStateFlow()

        private val _sensors = MutableStateFlow<ViContDecoder.Sensors?>(null)
    val sensors = _sensors.asStateFlow()

        private val _calibration = MutableStateFlow<ViContDecoder.Calibration?>(null)
    val calibration = _calibration.asStateFlow()

    /** Firmware identity of both boards, as a ViCont dashboard reports it. */
    private val _versions = MutableStateFlow<ViContDecoder.Versions?>(null)
    val versions = _versions.asStateFlow()

    /** Gears this scooter admits, reported by ViCont dashboards; ZYD always has three. */
    private val _gears = MutableStateFlow(listOf(1, 2, 3))
    val gears = _gears.asStateFlow()

        private val isViCont: Boolean get() = _family.value == ScooterFamily.VICONT

        @Volatile
    private var viContTuningLoaded = false

        @Volatile
    private var viContTractionCutoff = 0

    @Volatile
    private var mode = Mode.IDLE

    private var pendingDevice: ScannedDevice? = null
    private var pendingPassword: String = ""

    /** Scooter the app should quietly re-attach to, and the loop doing the watching. */
    private var autoTarget: ScannedDevice? = null
    private var autoPassword: String = ""
    private var autoJob: Job? = null

        @Volatile
    private var silentAttempt = false

    private val paramsPage = ByteArray(1024)
    private val infoPage = ByteArray(96)

    private var readIndex = 0

        @Volatile
    private var pollingParams = false

        @Volatile
    private var writeInFlight = false

    /** Last controller request, replayed by the watchdog when no answer comes back. */
    private var lastRequest: ByteArray? = null
    private var requestAttempts = 0
    private var watchdogJob: Job? = null

        @Volatile
    private var suppressSwitchEchoUntil = 0L
    private val monitorTicks = AtomicInteger(0)

    private var authJob: Job? = null
    private var pollJob: Job? = null
    private var monitorJob: Job? = null
    private var otaJob: Job? = null
    private var otaChunks: List<ByteArray> = emptyList()
    private var otaIndex = 0
    private var handshakeConfirmed = false

    private val firmwareRepo = FirmwareRepository(appContext)

    init {
        gatt.listener = this
    }

    val isBluetoothEnabled: Boolean get() = gatt.isBluetoothEnabled

    // ---- public API ----------------------------------------------------------------

    fun startScan() {
        if (!gatt.isBluetoothEnabled) {
            emit(CoreEvent.Error(appContext.getString(R.string.bluetooth_off)))
            return
        }
        // Below Android 12 the platform silently returns no scan results unless location
        // services are actually switched on, permission alone is not enough.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S && !locationServicesEnabled()) {
            emit(CoreEvent.Error(appContext.getString(R.string.location_services_off)))
            return
        }
        _allDevices.value = emptyList()
        _connection.value = ConnectionState.SCANNING
        gatt.startScan()
    }

    fun stopScan() {
        gatt.stopScan()
    }

    // ---- quiet reconnection ------------------------------------------------------------

        fun setAutoReconnect(device: ScannedDevice?, password: String) {
        autoTarget = device
        autoPassword = password
        autoJob?.cancel()
        if (device == null) return
        autoJob = scope.launch {
            var idleWait = AUTO_RETRY_MIN_MS
            while (true) {
                val target = autoTarget ?: return@launch
                val state = _connection.value
                val busy = state != ConnectionState.IDLE && state != ConnectionState.DISCONNECTED
                if (busy || !gatt.isBluetoothEnabled) {
                    delay(AUTO_POLL_MS)
                    idleWait = AUTO_RETRY_MIN_MS
                    continue
                }

                val seen = sweepFor(target.address)
                if (seen != null) {
                    silentAttempt = true
                    gatt.stopScan()
                    pendingDevice = seen
                    pendingPassword = autoPassword
                    _connection.value = ConnectionState.CONNECTING
                    gatt.connect(seen.address)
                    delay(AUTO_SETTLE_MS)
                    idleWait = AUTO_RETRY_MIN_MS
                } else {
                    // Nothing nearby: back off so an out-of-range scooter does not keep the
                    // radio busy all afternoon.
                    delay(idleWait)
                    idleWait = (idleWait * 2).coerceAtMost(AUTO_RETRY_MAX_MS)
                }
            }
        }
    }

    /** One short scan burst, returning the wanted scooter as soon as it shows up. */
    private suspend fun sweepFor(address: String): ScannedDevice? {
        // Drop any stale sighting first: only a fresh advertisement proves the scooter is
        // actually here, and connecting on a memory is what produces GATT timeouts.
        _allDevices.update { list -> list.filterNot { it.address == address } }
        gatt.startScan(AUTO_SCAN_MS)
        val deadline = System.currentTimeMillis() + AUTO_SCAN_MS
        while (System.currentTimeMillis() < deadline) {
            _allDevices.value.firstOrNull { it.address == address }?.let {
                return it
            }
            delay(250)
        }
        gatt.stopScan()
        return null
    }

    fun connect(device: ScannedDevice, password: String) {
        silentAttempt = false
        pendingDevice = device
        pendingPassword = password
        reader.reset()
        mode = Mode.IDLE
        _connection.value = ConnectionState.CONNECTING
        gatt.connect(device.address)
    }

    /** Stops the quiet reconnect loop, so a deliberate disconnect stays disconnected. */
    fun disconnect() {
        autoJob?.cancel()
        autoJob = null
        autoTarget = null
        cancelJobs()
        mode = Mode.IDLE
        gatt.disconnect()
        _connection.value = ConnectionState.DISCONNECTED
        _connectedDevice.value = null
    }

        fun applyRide(transform: (RideState) -> RideState) {
        val previous = _ride.value
        val next = transform(previous)
        _ride.value = next
        suppressSwitchEchoUntil = System.currentTimeMillis() + SWITCH_ECHO_GRACE_MS
        scope.launch {
            if (_family.value == ScooterFamily.VICONT) {
                sendViContRide(previous, next)
                return@launch
            }
            gatt.writeData(
                Protocol.monitorFrame(
                    next.flagByte(),
                    next.limitCruise,
                    next.limitEco,
                    next.limitComfort,
                    next.limitSport,
                )
            )
        }
    }

    private suspend fun sendViContRide(previous: RideState, next: RideState) {
        suspend fun send(command: Int, value: Int) {
            gatt.writeData(ViContProtocol.command(command, value))
            delay(VICONT_COMMAND_SPACING_MS)
        }

        fun flag(on: Boolean) = if (on) ViContProtocol.ON else ViContProtocol.OFF

        if (next.gear != previous.gear) {
            // Gears are 1-based on the wire, and anything past the dashboard's own list is
            // refused here: a scooter power-cycled on an unsupported gear comes back with a
            // speed limit of zero and stops answering the throttle.
            val wire = next.gear + 1
            if (wire in _gears.value) send(ViContProtocol.CMD_GEAR, wire)
        }
        if (next.headLight != previous.headLight) {
            send(ViContProtocol.CMD_LIGHT, flag(next.headLight))
        }
        if (next.cruiseControl != previous.cruiseControl) {
            send(ViContProtocol.CMD_CRUISE, flag(next.cruiseControl))
        }
        if (next.zeroStart != previous.zeroStart) {
            // 0x35 switches kick-start, which is the opposite of zero start: the app's flag
            // means "ride off from the throttle", the dashboard's means "kick off first".
            send(ViContProtocol.CMD_KICK_START, flag(!next.zeroStart))
        }
        if (next.imperial != previous.imperial) {
            send(ViContProtocol.CMD_UNITS, flag(next.imperial))
        }
        if (next.locked != previous.locked) {
            send(ViContProtocol.CMD_PARKING, flag(next.locked))
        }
        if (next.ambientLight != previous.ambientLight) {
            val mode = if (next.ambientLight) ViContProtocol.LAMP_SOLID else ViContProtocol.LAMP_OFF
            // Hue 0 leaves the stored colour alone.
            gatt.writeData(ViContProtocol.ambienceLamp(mode, 0))
            delay(VICONT_COMMAND_SPACING_MS)
        }

        // Per-gear speed limits, all three through the engineering parameters. The sport
        // limit does have a command of its own, 0x3C, but that is the same setting as the
        // controller screen's overall speed limit and the dashboard clamps it to whatever
        // ceiling it reports — 35 on a SAMIK. Writing P10 directly keeps the three gear
        // limits behaving alike and takes the values the other two already accept.
        // Each of these lands in EEPROM at once, so nothing is sent unless the value moved.
        if (next.limitSport != previous.limitSport && next.limitSport > 0) {
            val wanted = next.limitSport.coerceIn(1, 99)
            val ceiling = _params.value.speedLimitCeiling
            if (ceiling == null || wanted <= ceiling) {
                // 0x3C is the same setting, and the better path when the value fits: it
                // answers with what was applied and leaves the dashboard's engineering
                // display alone.
                send(ViContProtocol.CMD_MAX_SPEED, wanted)
            } else {
                // Above the ceiling the command clamps silently, so the only way through is
                // to write P10 directly.
                gatt.writeData(ViContProtocol.writeParameter(P_LIMIT_SPORT, wanted))
                delay(VICONT_COMMAND_SPACING_MS)
            }
        }
        if (next.limitEco != previous.limitEco && next.limitEco > 0) {
            gatt.writeData(ViContProtocol.writeParameter(P_LIMIT_ECO, next.limitEco.coerceIn(1, 99)))
            delay(VICONT_COMMAND_SPACING_MS)
        }
        if (next.limitComfort != previous.limitComfort && next.limitComfort > 0) {
            gatt.writeData(
                ViContProtocol.writeParameter(P_LIMIT_DRIVE, next.limitComfort.coerceIn(1, 99))
            )
            delay(VICONT_COMMAND_SPACING_MS)
        }
        if (next.limitCruise != previous.limitCruise) {
            SessionLog.warn("cruise limit skipped", "not supported on ViCont")
        }
    }

        fun seedModeLimits(limits: List<Int>) {
        fun at(index: Int, current: Int) = limits.getOrNull(index)?.takeIf { it > 0 } ?: current
        _ride.update {
            it.copy(
                limitEco = at(0, it.limitEco),
                limitComfort = at(1, it.limitComfort),
                limitSport = at(2, it.limitSport),
                limitGear4 = at(3, it.limitGear4),
                limitGear5 = at(4, it.limitGear5),
            )
        }
    }

        fun setGearLimit(gear: Int, kmh: Int) {
        if (!isViCont) return
        val index = when (gear) {
            4 -> P_LIMIT_GEAR4
            5 -> P_LIMIT_GEAR5
            else -> return
        }
        val clamped = kmh.coerceIn(1, 99)
        val current = if (gear == 4) _ride.value.limitGear4 else _ride.value.limitGear5
        if (current == clamped) return
        scope.launch { gatt.writeData(ViContProtocol.writeParameter(index, clamped)) }
        publishEngineering(if (gear == 4) 11 else 12, clamped)
    }

    /** Steps to the next gear the connected scooter actually supports. */
    fun cycleGear() = applyRide {
        val supported = _gears.value
        val index = supported.indexOf(it.gear + 1)
        val wire = supported[if (index < 0) 0 else (index + 1) % supported.size]
        it.copy(gear = wire - 1)
    }

    fun setGear(gear: Int) = applyRide {
        val supported = _gears.value
        if (gear + 1 in supported) it.copy(gear = gear) else it
    }

    fun setHeadLight(on: Boolean) = applyRide { it.copy(headLight = on) }

    fun setAmbientLight(on: Boolean) = applyRide { it.copy(ambientLight = on) }

    fun setCruiseControl(on: Boolean) = applyRide { it.copy(cruiseControl = on) }

    fun setZeroStart(on: Boolean) = applyRide { it.copy(zeroStart = on) }

    fun setImperial(on: Boolean) = applyRide { it.copy(imperial = on) }

    fun setLocked(locked: Boolean) = applyRide { it.copy(locked = locked) }

    fun setModeLimits(cruise: Int, eco: Int, comfort: Int, sport: Int) = applyRide {
        it.copy(limitCruise = cruise, limitEco = eco, limitComfort = comfort, limitSport = sport)
    }

        fun applyRideBundle(
        gear: Int? = null,
        headLight: Boolean? = null,
        ambientLight: Boolean? = null,
        cruiseControl: Boolean? = null,
        zeroStart: Boolean? = null,
        imperial: Boolean? = null,
        limitCruise: Int? = null,
        limitEco: Int? = null,
        limitComfort: Int? = null,
        limitSport: Int? = null,
    ) = applyRide { current ->
        current.copy(
            gear = gear ?: current.gear,
            headLight = headLight ?: current.headLight,
            ambientLight = ambientLight ?: current.ambientLight,
            cruiseControl = cruiseControl ?: current.cruiseControl,
            zeroStart = zeroStart ?: current.zeroStart,
            imperial = imperial ?: current.imperial,
            limitCruise = limitCruise ?: current.limitCruise,
            limitEco = limitEco ?: current.limitEco,
            limitComfort = limitComfort ?: current.limitComfort,
            limitSport = limitSport ?: current.limitSport,
        )
    }

        fun applyRegisterBundle(throttle: Int?, brake: Int?, speedLimit: Int?) {
        if (!gatt.isConnected) return
        scope.launch {
            throttle?.let {
                setThrottleResponse(it)
                delay(REGISTER_WRITE_SPACING_MS)
            }
            brake?.let {
                setBrakeResponse(it)
                delay(REGISTER_WRITE_SPACING_MS)
            }
            speedLimit?.let { setSpeedLimit(it) }
        }
    }

        fun requestServiceMileage() {
        if (isViCont) return
        if (!gatt.isConnected || pollingParams || mode == Mode.OTA) return
        scope.launch {
            gatt.writeData(Protocol.tranFrame(Protocol.CMD_TRAN))
            delay(120)
            issueRequest(Protocol.readFrame(Protocol.CMD_READ_PARAMETER, 74, 4))
        }
    }

    /** Reads the controller identity block; also refreshes the OTA version list. */
    fun requestEscInfo() {
        // ViCont reports its versions unprompted in the 0x12 notification.
        if (isViCont) return
        if (!gatt.isConnected || mode == Mode.OTA) return
        scope.launch {
            pollJob?.cancel()
            clearWatchdog()
            mode = Mode.ESC_INFO
            infoPage.fill(0)
            readIndex = 0
            gatt.writeData(Protocol.tranFrame(Protocol.CMD_TRAN))
            delay(60)
            issueRequest(Protocol.readFrame(Protocol.CMD_ESC_INFO, 0, 4))
        }
    }

        fun startParamPolling() {
        if (!gatt.isConnected) return
        // ViCont has no register page to sweep: each setting is a command that answers once
        // when asked, so opening the screen re-reads them instead of polling.
        if (isViCont) {
            queryViContTuning()
            return
        }
        pollingParams = true
        if (mode == Mode.OTA) return
        beginParamSweep()
    }

    private fun beginParamSweep() {
        pollJob?.cancel()
        clearWatchdog()
        _paramsStalled.value = false
        pollJob = scope.launch {
            mode = Mode.READ_PARAMS
            readIndex = 0
            gatt.writeData(Protocol.tranFrame(Protocol.CMD_TRAN))
            delay(120)
            issueRequest(Protocol.readFrame(Protocol.CMD_READ_PARAMETER, 0, 16))
        }
    }

    /** Leaves register mode and returns the controller to live telemetry. */
    fun stopParamPolling() {
        pollingParams = false
        scope.launch { stopPolling() }
    }

    private suspend fun stopPolling() {
        pollJob?.cancel()
        pollJob = null
        clearWatchdog()
        mode = Mode.IDLE
        if (!gatt.isConnected) return
        repeat(5) {
            gatt.writeData(Protocol.tranFrame(Protocol.CMD_TRAN_STOP))
            delay(50)
        }
        mode = Mode.MONITORING
        repeat(3) {
            gatt.writeData(Protocol.keepFrame())
            delay(50)
        }
        startMonitoringLoop()
    }

    /** Writes one 16-bit register and lets the read loop pick the new value back up. */
    fun writeRegister(register: Int, value: Int) {
        if (isViCont) return
        if (!gatt.isConnected) return
        writeInFlight = true
        releaseWriteGuardLater()
        scope.launch {
            mode = Mode.WRITE_PARAM
            delay(150)
            gatt.writeData(Protocol.tranFrame(Protocol.CMD_TRAN))
            delay(40)
            gatt.writeData(
                Protocol.writeFrame(
                    register,
                    byteArrayOf(((value shr 8) and 0xFF).toByte(), (value and 0xFF).toByte())
                )
            )
        }
    }

    private fun writeRegister32(register: Int, value: Int) {
        if (!gatt.isConnected) return
        writeInFlight = true
        releaseWriteGuardLater()
        scope.launch {
            mode = Mode.WRITE_PARAM
            delay(150)
            gatt.writeData(Protocol.tranFrame(Protocol.CMD_TRAN))
            delay(40)
            gatt.writeData(
                Protocol.writeFrame(
                    register,
                    byteArrayOf(
                        ((value shr 24) and 0xFF).toByte(),
                        ((value shr 16) and 0xFF).toByte(),
                        ((value shr 8) and 0xFF).toByte(),
                        (value and 0xFF).toByte(),
                    )
                )
            )
        }
    }

    /** Register 73 is the controller's "persist what I just wrote" trigger. */
    private fun commitRegisters() {
        scope.launch {
            delay(60)
            writeRegister(ParamRegistry.REG_COMMIT, (0..50).random())
        }
    }

    private fun setBit(register: Int, bit: Int, on: Boolean) {
        val current = _params.value.registerZero
        val next = if (on) current or (1 shl bit) else current and (1 shl bit).inv()
        _params.update { it.copy(registerZero = next) }
        writeRegister(register, next and 0xFFFF)
    }

    // ---- advanced parameter setters -------------------------------------------------

    fun queryAdvParams() {
        if (!gatt.isConnected) return
        if (isViCont) {
            queryViContTuning()
            return
        }
        scope.launch {
            mode = Mode.READ_PARAMS
            delay(80)
            gatt.writeData(Protocol.tranFrame(Protocol.CMD_TRAN))
            delay(50)
            issueRequest(Protocol.readFrame(Protocol.CMD_READ_PARAMETER, 0, 16))
            delay(250)
            issueRequest(Protocol.readFrame(Protocol.CMD_READ_PARAMETER, 32, 16))
        }
    }

    fun setThrottleResponse(value: Int) {
        val clamped = value.coerceIn(1, 10)
        val previous = _params.value.throttleResponse
        _params.update { it.copy(throttleResponse = clamped) }
        if (isViCont) {
            if (previous != clamped) {
                sendViCont(ViContProtocol.CMD_STARTING_TORQUE, ViContProtocol.responseToWire(clamped))
            }
            return
        }
        writeInFlight = true
        releaseWriteGuardLater()
        scope.launch {
            mode = Mode.WRITE_PARAM
            delay(100)
            gatt.writeData(Protocol.tranFrame(Protocol.CMD_TRAN))
            delay(40)
            gatt.writeData(
                Protocol.writeFrame(
                    ParamRegistry.REG_THROTTLE_RESPONSE,
                    byteArrayOf(((clamped * 3000 shr 8) and 0xFF).toByte(), (clamped * 3000 and 0xFF).toByte())
                )
            )
            delay(150)
            gatt.writeData(Protocol.displayFrame(1, clamped))
        }
    }

    fun setBrakeResponse(value: Int) {
        val clamped = value.coerceIn(1, 10)
        val previous = _params.value.brakeResponse
        _params.update { it.copy(brakeResponse = clamped) }
        if (isViCont) {
            if (previous != clamped) {
                sendViCont(ViContProtocol.CMD_EBRAKE_STRENGTH, ViContProtocol.responseToWire(clamped))
            }
            return
        }
        writeInFlight = true
        releaseWriteGuardLater()
        scope.launch {
            mode = Mode.WRITE_PARAM
            delay(100)
            gatt.writeData(Protocol.tranFrame(Protocol.CMD_TRAN))
            delay(40)
            gatt.writeData(
                Protocol.writeFrame(
                    ParamRegistry.REG_BRAKE_RESPONSE,
                    byteArrayOf(((clamped * 3000 shr 8) and 0xFF).toByte(), (clamped * 3000 and 0xFF).toByte())
                )
            )
            delay(150)
            gatt.writeData(Protocol.displayFrame(2, clamped))
        }
    }

    fun setSpeedLimit(kmh: Int) {
        val clamped = kmh.coerceIn(2, 60)
        val previous = _params.value.speedLimit
        _params.update { it.copy(speedLimit = clamped) }
        if (isViCont) {
            // Unchanged values are not resent: the dashboard beeps for every command it
            // takes, and this one also lands in EEPROM.
            if (previous != clamped) {
                // 99 means "no limit" on this protocol, so the range stops one short of it.
                sendViCont(ViContProtocol.CMD_MAX_SPEED, clamped.coerceIn(1, 98))
                _ride.update { it.copy(limitSport = clamped) }
            }
            return
        }
        writeInFlight = true
        releaseWriteGuardLater()
        scope.launch {
            mode = Mode.WRITE_PARAM
            delay(100)
            gatt.writeData(Protocol.tranFrame(Protocol.CMD_TRAN))
            delay(40)
            gatt.writeData(
                Protocol.writeFrame(
                    ParamRegistry.REG_SPEED_LIMIT,
                    byteArrayOf(((clamped * 10 shr 8) and 0xFF).toByte(), (clamped * 10 and 0xFF).toByte())
                )
            )
            delay(150)
            val display = if (_ride.value.imperial) Math.round(clamped * 1.61).toInt() else clamped
            gatt.writeData(Protocol.displayFrame(3, display))
        }
    }

    fun setMaxModulationDepth(value: Int) {
        _params.update { it.copy(maxModulationDepth = value) }
        writeRegister(ParamRegistry.REG_MODULATION, value * 21845 / 50)
    }

    fun setMotorPolePairs(value: Int) {
        _params.update { it.copy(motorPolePairs = value) }
        writeRegister(ParamRegistry.REG_POLE_PAIRS, value)
    }

    fun setMaxDischargeCurrent(amps: Float) {
        val previous = _params.value.maxDischargeCurrent
        _params.update { it.copy(maxDischargeCurrent = amps) }
        if (isViCont) {
            // Whole amps here, not the ZYD sixty-fourths — and doubled on the wire like the
            // other tuning commands, which store half of what they are given.
            // The command takes a percentage of its own ceiling, not the current itself, so
            // the ceiling the scooter reported has to be part of the conversion. Sending the
            // amps doubled asked for that many percent instead: 23 A became 23% of 23 A.
            val ceiling = _params.value.dischargeCurrentCeiling ?: DEFAULT_CURRENT_CEILING
            val stored = Math.round(amps).coerceIn(1, ceiling)
            if (Math.round(previous).coerceIn(1, ceiling) != stored) {
                sendViCont(
                    ViContProtocol.CMD_MAX_CURRENT,
                    ViContProtocol.tuningToWire(stored, ceiling),
                )
            }
            return
        }
        writeRegister(ParamRegistry.REG_DISCHARGE_CURRENT, (amps * 64f).toInt())
    }

    fun setMaxBrakingCurrent(amps: Float) {
        _params.update { it.copy(maxBrakingCurrent = amps) }
        writeRegister(ParamRegistry.REG_BRAKING_CURRENT, (amps * 64f).toInt())
    }

    fun setVoltageProtection(volts: Float) {
        _params.update { it.copy(voltageProtection = volts) }
        writeRegister(ParamRegistry.REG_VOLTAGE_PROTECTION, (volts * 10f).toInt())
    }

    fun setMotorDiameter(inches: Float) {
        _params.update { it.copy(motorDiameter = inches) }
        writeRegister(ParamRegistry.REG_MOTOR_DIAMETER, (inches * 25.4f).toInt())
    }

    fun setPwmFrequency(index: Int) {
        _params.update { it.copy(pwmFrequency = index) }
        writeRegister(ParamRegistry.REG_PWM, index)
    }

    fun setCruiseActivationTime(seconds: Int) {
        // must be held first, so there is nothing to write there.
        if (isViCont) return
        _params.update { it.copy(cruiseActivationTime = seconds) }
        writeRegister(ParamRegistry.REG_CRUISE_TIME, seconds)
    }

    fun setAutoShutdownTime(minutes: Int) {
        val previous = _params.value.autoShutdownTime
        _params.update { it.copy(autoShutdownTime = minutes) }
        if (isViCont) {
            // P5, the idle shutdown timer. Engineering parameter only; no command reads or
            // writes it, so like the gear limits it goes straight to EEPROM.
            if (previous != minutes) {
                scope.launch {
                    gatt.writeData(
                        ViContProtocol.writeParameter(P_AUTO_SHUTDOWN, minutes.coerceIn(1, 99))
                    )
                }
            }
            return
        }
        writeRegister(ParamRegistry.REG_SHUTDOWN_TIME, minutes)
    }

    fun setServiceMileage(km: Int) {
        _params.update { it.copy(serviceMileage = km) }
        writeRegister(ParamRegistry.REG_SERVICE_MILEAGE, km)
        commitRegisters()
    }

    fun setLastServiceMileage(km: Int) {
        _params.update { it.copy(lastServiceMileage = km) }
        writeRegister32(ParamRegistry.REG_LAST_SERVICE, km)
        commitRegisters()
    }

    fun setCruiseRegister(on: Boolean) = setBit(ParamRegistry.REG_STATUS, ParamRegistry.BIT_CRUISE, on)

    fun setMetricRegister(metric: Boolean) =
        setBit(ParamRegistry.REG_STATUS, ParamRegistry.BIT_METRIC, !metric)

    fun setZeroStartRegister(on: Boolean) =
        setBit(ParamRegistry.REG_STATUS, ParamRegistry.BIT_ZERO_START, on)

    fun restoreControllerDefaults() =
        setBit(ParamRegistry.REG_STATUS, ParamRegistry.BIT_RESTORE_DEFAULT, true)

    // ---- bridge module (AT) commands ------------------------------------------------

        val supportsAtCommands: Boolean get() = !isViCont

    private fun atUnsupported(): Boolean {
        if (supportsAtCommands) return false
        SessionLog.warn("AT command refused", "not supported on ${_family.value}")
        emit(CoreEvent.Error(appContext.getString(R.string.module_unsupported)))
        return true
    }

    fun changePassword(newPassword: String) {
        if (atUnsupported()) return
        gatt.writeCommand(Protocol.atChangePassword(newPassword))
    }

    fun renameDevice(name: String) {
        if (atUnsupported()) return
        gatt.writeCommand(Protocol.atName(name))
    }

    fun queryNfc() {
        if (atUnsupported()) return
        gatt.writeCommand(Protocol.atNfc())
    }

    fun setNfc(on: Boolean) {
        if (atUnsupported()) return
        gatt.writeCommand(Protocol.atSetNfc(on))
    }

    fun clearNfcCards() {
        if (atUnsupported()) return
        gatt.writeCommand(Protocol.atNfcDelete())
    }

    fun queryVoice() {
        if (atUnsupported()) return
        gatt.writeCommand(Protocol.atVoice())
    }

    fun setVoice(type: Int) {
        if (atUnsupported()) return
        gatt.writeCommand(Protocol.atSetVoice(type))
    }

    fun queryDriveMode() {
        if (atUnsupported()) return
        gatt.writeCommand(Protocol.atDriveMode())
    }

    fun setDriveMode(type: Int) {
        if (atUnsupported()) return
        gatt.writeCommand(Protocol.atSetDriveMode(type))
    }

    fun queryDeviceType() {
        if (atUnsupported()) return
        gatt.writeCommand(Protocol.atDeviceType())
    }

    // ---- firmware over the air --------------------------------------------------------

    fun availableFirmware(): List<String> = _firmwareVersions.value

    fun flashFirmware(version: String) {
        val hardware = _escInfo.value.hardware
        val payload = firmwareRepo.load(hardware, version)
        if (payload == null) {
            _ota.value = OtaProgress(OtaState.FAILED, 0, "Firmware image not found")
            return
        }
        flashFirmware(payload)
    }

    fun flashFirmware(payload: ByteArray) {
        if (!gatt.isConnected) return
        otaJob?.cancel()
        otaChunks = Protocol.firmwareChunks(payload)
        otaIndex = 0
        handshakeConfirmed = false
        otaJob = scope.launch {
            mode = Mode.OTA
            stopPolling()
            _ota.value = OtaProgress(OtaState.HANDSHAKE, 0, "Handshake")
            mode = Mode.OTA
            delay(150)
            gatt.writeData(Protocol.tranFrame(Protocol.CMD_TRAN))
            delay(60)
            gatt.writeData(Protocol.shortFrame(Protocol.CMD_HANDSHAKE))
        }
    }

    fun cancelFirmware() {
        otaJob?.cancel()
        otaChunks = emptyList()
        _ota.value = OtaProgress()
        scope.launch { stopPolling() }
    }

    // ---- GATT listener ----------------------------------------------------------------

    override fun onScanResult(device: ScannedDevice) {
        _allDevices.update { list ->
            val existing = list.indexOfFirst { it.address == device.address }
            if (existing >= 0) {
                list.toMutableList().also { it[existing] = device }
            } else {
                list + device
            }.sortedByDescending { it.rssi }
        }
    }

    override fun onScanStopped() {
        if (_connection.value == ConnectionState.SCANNING) _connection.value = ConnectionState.IDLE
    }

    override fun onConnected() {
        _connection.value = ConnectionState.CONNECTING
        SessionLog.state("Connected", pendingDevice?.name.orEmpty())
    }

    override fun onServicesReady() {
        val device = pendingDevice
        _family.value = gatt.family
        SessionLog.state("Services ready", "family ${gatt.family}")
        // ViCont dashboards have no AT channel and no password; going straight through is
        // correct here, not a shortcut. Their F1F0 stub would "accept" any password anyway.
        if (gatt.family == ScooterFamily.VICONT) {
            onViContReady()
            return
        }
        // The hw_z / zyd modules have no password stage at all; everything else expects an
        // AT+PWD before it will answer.
        if (device == null || device.authMode == AuthMode.NONE || pendingPassword.isEmpty()) {
            onAuthenticated()
            return
        }
        _connection.value = ConnectionState.AUTHENTICATING
        gatt.writeCommand(Protocol.atPassword(pendingPassword))
        authJob?.cancel()
        authJob = scope.launch {
            delay(6000)
            if (_connection.value == ConnectionState.AUTHENTICATING) {
                emit(CoreEvent.PasswordTimeout)
                disconnect()
            }
        }
    }

    private fun onAuthenticated() {
        SessionLog.state("Authenticated")
        authJob?.cancel()
        authJob = null
        _connection.value = ConnectionState.CONNECTED
        _connectedDevice.value = pendingDevice
        _telemetry.value = Telemetry()
        _params.value = ControllerParams()
        scope.launch {
            mode = Mode.IDLE
            repeat(5) {
                gatt.writeData(Protocol.tranFrame(Protocol.CMD_TRAN_STOP))
                delay(50)
            }
            mode = Mode.MONITORING
            repeat(3) {
                gatt.writeData(Protocol.keepFrame())
                delay(50)
            }
            startMonitoringLoop()
            delay(400)
            requestEscInfo()
        }
    }

    override fun onDisconnected() {
        SessionLog.state("Disconnected")
        viContTuningLoaded = false
        viContTractionCutoff = 0
        _engineeringValues.value = emptyMap()
        _versions.value = null
        _gears.value = listOf(1, 2, 3)
        legacyReader.reset()
        cancelJobs()
        mode = Mode.IDLE
        _connection.value = ConnectionState.DISCONNECTED
        _connectedDevice.value = null
    }

    override fun onConnectFailed(reason: String) {
        SessionLog.warn("Connect failed", reason)
        cancelJobs()
        mode = Mode.IDLE
        _connection.value = ConnectionState.DISCONNECTED
        _connectedDevice.value = null
        // A background attempt failing just means the scooter is not around yet.
        if (silentAttempt) {
            silentAttempt = false
            Log.d(TAG, "quiet reconnect attempt failed: " + reason)
            return
        }
        emit(CoreEvent.Error(reason))
    }

    override fun onLegacyData(payload: ByteArray) {
        onLegacyNotification(payload)
    }

    override fun onData(payload: ByteArray) {
        if (_family.value == ScooterFamily.VICONT) onViContNotification(payload) else reader.append(payload)
    }

    // ---- ViCont (FEE0) ---------------------------------------------------------------

    private fun onViContReady() {
        SessionLog.state("ViCont ready", "no password stage")
        viContTuningLoaded = true
        authJob?.cancel()
        authJob = null
        mode = Mode.MONITORING
        _connection.value = ConnectionState.CONNECTED
        _connectedDevice.value = pendingDevice
        _telemetry.value = Telemetry()
        _params.value = ControllerParams()
        scope.launch {
            gatt.writeData(ViContProtocol.startTelemetry())
            delay(300)
            for (command in VICONT_QUERIES) {
                gatt.writeData(ViContProtocol.query(command))
                delay(120)
            }
            startMonitoringLoop()
        }
    }

    private fun onLegacyNotification(payload: ByteArray) {
        legacyReader.append(payload)
    }

    private fun onLegacyFrame(frame: ByteArray) {
        if (frame.size < 7) return
        if (frame[0] != Protocol.HEAD_MONITOR || frame[1] != 0x01.toByte()) return
        val eco = frame[4].toInt() and 0xFF
        val drive = frame[5].toInt() and 0xFF
        val sport = frame[6].toInt() and 0xFF
        // A dashboard that has not filled the frame in yet reports zeroes; adopting those
        // would wipe the values the user can see on the scooter itself.
        if (eco == 0 && drive == 0 && sport == 0) return
        val changed = _ride.value.let { it.limitEco != eco || it.limitComfort != drive }
        _ride.update {
            it.copy(
                limitEco = if (eco > 0) eco else it.limitEco,
                limitComfort = if (drive > 0) drive else it.limitComfort,
                limitSport = if (sport > 0) sport else it.limitSport,
            )
        }
        publishEngineering(mapOf(8 to eco, 9 to drive, 10 to sport))
        if (changed) SessionLog.state("Limits from legacy service", "$eco/$drive/$sport")
    }

    private fun onViContNotification(payload: ByteArray) {
        val reply = ViContProtocol.parseReply(payload) ?: return
        when (reply.command) {
            ViContProtocol.RX_TELEMETRY -> ViContDecoder.telemetry(reply.payload)?.let { t ->
                // Charge comes from the scooter. Deriving it from voltage instead was worse
                // in practice: pack voltage sags under load and recovers on the overrun, so
                // the figure swung about the whole time the scooter was moving.
                _telemetry.update { current ->
                    current.copy(
                        speed = t.speed,
                        battery = t.battery,
                        voltage = t.voltage,
                        current = t.current,
                        power = t.power,
                        escTemperature = t.controllerTemperature,
                        motorTemperature = t.motorTemperature,
                        motorRpm = t.motorRpm,
                    )
                }
            }

            ViContProtocol.RX_STATE -> ViContDecoder.state(reply.payload)?.let { s ->
                _telemetry.update { current ->
                    current.copy(
                        tripDistance = s.tripDistance,
                        totalDistance = s.totalDistance.toFloat(),
                    )
                }
                // Same rule as the ZYD path: ignore the dashboard's echo of switch state for
                // a moment after a write, or the user's tap visibly springs back.
                publishEngineering(
                    mapOf(
                        2 to if (s.kickStart) 1 else 0,
                        3 to if (s.cruiseEnabled) 1 else 0,
                        4 to if (s.imperial) 1 else 0,
                        22 to if (s.parking) 1 else 0,
                    )
                )
                if (System.currentTimeMillis() >= suppressSwitchEchoUntil) {
                    _ride.update { current ->
                        current.copy(
                            gear = (s.gear - 1).coerceAtLeast(0),
                            headLight = s.headLight,
                            ambientLight = s.ambientLight,
                            cruiseControl = s.cruiseEnabled,
                            // The dashboard reports kick-start; the app shows its inverse.
                            zeroStart = !s.kickStart,
                            imperial = s.imperial,
                            locked = s.parking,
                        )
                    }
                }
            }

            ViContProtocol.RX_VERSIONS -> ViContDecoder.versions(reply.payload)?.let { v ->
                _versions.value = v
                // The mask says which gears the dashboard puts an indicator against, not
                // which the gear command will take: 0x42 accepts four and five on scooters
                // whose mask lists three, giving the walking figure and a blank indicator.
                // Six and above are the dangerous ones — a scooter power-cycled on one comes
                // back with no speed limit and an unresponsive throttle — so the set offered
                // is the first five plus anything further the mask actually claims.
                _gears.value = (VICONT_BASE_GEARS + v.gears).distinct().sorted()
                _escInfo.value = EscInfo(
                    model = "ViCont",
                    hardware = "%d.%d".format(v.instrumentHardware, v.instrumentSoftware),
                    firmware = "%d.%d".format(v.controllerHardware, v.controllerSoftware),
                )
                _ride.update { it.copy(displayVersion = "V%d.%d".format(v.instrumentHardware, v.instrumentSoftware)) }
            }

            ViContProtocol.CMD_MAX_SPEED -> viContTuningValue(reply.payload)?.let { value ->
                publishEngineering(10, value)
                val ceiling = viContTuningCeiling(reply.payload)
                _params.update {
                    it.copy(speedLimit = value, speedLimitCeiling = ceiling, loaded = true)
                }
                _ride.update { it.copy(limitSport = value) }
            }

            ViContProtocol.CMD_MAX_CURRENT -> viContTuningValue(reply.payload)?.let { value ->
                publishEngineering(18, value)
                _params.update {
                    it.copy(
                        maxDischargeCurrent = value.toFloat(),
                        dischargeCurrentCeiling = viContTuningCeiling(reply.payload),
                        loaded = true,
                    )
                }
            }

            ViContProtocol.CMD_STARTING_TORQUE -> viContTuningValue(reply.payload)?.let { value ->
                publishEngineering(20, value)
                _params.update {
                    it.copy(
                        throttleResponse = ViContProtocol.responseFromStored(
                            value,
                            viContTuningCeiling(reply.payload) ?: DEFAULT_RESPONSE_CEILING,
                        ),
                        // The app's scale always runs to ten; the dashboard's ceiling only
                        // decides what each of those ten steps is worth.
                        throttleResponseCeiling = 10,
                        loaded = true,
                    )
                }
            }

            ViContProtocol.CMD_EBRAKE_STRENGTH -> viContTuningValue(reply.payload)?.let { value ->
                publishEngineering(21, value)
                _params.update {
                    it.copy(
                        brakeResponse = ViContProtocol.responseFromStored(
                            value,
                            viContTuningCeiling(reply.payload) ?: DEFAULT_RESPONSE_CEILING,
                        ),
                        brakeResponseCeiling = 10,
                        loaded = true,
                    )
                }
            }

            ViContProtocol.RX_SENSORS ->
                ViContDecoder.sensors(reply.payload)?.let { _sensors.value = it }

            ViContProtocol.RX_CALIBRATION ->
                ViContDecoder.calibration(reply.payload)?.let { _calibration.value = it }

            ViContProtocol.RX_BATTERY_THRESHOLDS ->
                ViContDecoder.batteryThresholds(reply.payload)?.let { thresholds ->
                    viContTractionCutoff = thresholds.tractionCutoff
                    publishEngineering(
                        mapOf(
                            17 to thresholds.tractionCutoff,
                            29 to thresholds.bar1,
                            30 to thresholds.bar2,
                            31 to thresholds.bar3,
                            32 to thresholds.bar4,
                            33 to thresholds.bar5,
                        )
                    )
                    _params.update {
                        it.copy(voltageProtection = thresholds.tractionCutoff.toFloat(), loaded = true)
                    }
                }
        }
    }

        private fun viContTuningValue(payload: ByteArray): Int? =
        if (payload.isEmpty()) null else payload[0].toInt() and 0xFF

    /** The ceiling the dashboard reports for a setting, when it reports one. */
    private fun viContTuningCeiling(payload: ByteArray): Int? =
        if (payload.size >= 2) payload[1].toInt() and 0xFF else null

        private fun sendViCont(command: Int, value: Int) {
        if (!gatt.isConnected) return
        scope.launch {
            gatt.writeData(ViContProtocol.command(command, value))
            delay(VICONT_READBACK_DELAY_MS)
            gatt.writeData(ViContProtocol.query(command))
        }
    }

        private fun queryViContTuning(force: Boolean = false) {
        if (!gatt.isConnected) return
        if (viContTuningLoaded && !force) return
        viContTuningLoaded = true
        scope.launch {
            for (command in VICONT_QUERIES) {
                gatt.writeData(ViContProtocol.query(command))
                delay(VICONT_QUERY_SPACING_MS)
            }
        }
    }

        fun findScooter(): Boolean {
        if (!gatt.isConnected || !isViCont) return false
        scope.launch { gatt.writeData(ViContProtocol.command(ViContProtocol.CMD_FIND, 0x01)) }
        return true
    }

        fun showEngineeringParam(number: Int): Boolean {
        val param = EngineeringParams.byNumber(number) ?: return false
        if (!gatt.isConnected || !isViCont) return false
        scope.launch { gatt.writeData(ViContProtocol.showParameter(param.index)) }
        return true
    }

        fun writeEngineeringParam(number: Int, value: Int): Boolean {
        val param = EngineeringParams.byNumber(number) ?: return false
        if (!gatt.isConnected || !isViCont) return false
        val clamped = value.coerceIn(param.min, param.max)
        scope.launch { gatt.writeData(ViContProtocol.writeParameter(param.index, clamped)) }
        publishEngineering(number, clamped)
        return true
    }

    private fun publishEngineering(number: Int, value: Int) {
        _engineeringValues.update { it + (number to value) }
        // P11 and P12 are the fourth and fifth gear caps, which nothing else reports. When
        // one is written it becomes the only account of that gear's limit there is.
        when (number) {
            11 -> _ride.update { it.copy(limitGear4 = value) }
            12 -> _ride.update { it.copy(limitGear5 = value) }
        }
    }

    private fun publishEngineering(values: Map<Int, Int>) {
        _engineeringValues.update { it + values }
    }

        fun setAmbienceLamp(mode: Int, hue: Int): Boolean {
        if (!gatt.isConnected || !isViCont) return false
        scope.launch { gatt.writeData(ViContProtocol.ambienceLamp(mode, hue.coerceIn(0, 360))) }
        _ride.update { it.copy(ambientLight = mode != ViContProtocol.LAMP_OFF) }
        return true
    }

        fun sendRawCommand(command: Int, payload: ByteArray): Boolean {
        if (!gatt.isConnected || !isViCont) return false
        SessionLog.warn(
            "manual command",
            String.format(java.util.Locale.ROOT, "%02X", command) +
                if (payload.isEmpty()) "" else " " +
                    payload.joinToString(" ") { String.format(java.util.Locale.ROOT, "%02X", it) },
        )
        scope.launch { gatt.writeData(ViContProtocol.command(command, payload)) }
        return true
    }

    /** Forces a re-read even though the values are already known. */
    fun refreshParams() {
        if (isViCont) queryViContTuning(force = true) else queryAdvParams()
    }

    override fun onCommand(text: String, raw: ByteArray) {
        Log.d(TAG, "AT <- " + text.trim())
        when {
            text.contains("OK+PWD:Y") || text.contains("ERR+AT") -> onAuthenticated()
            text.contains("OK+PWD:N") -> {
                emit(CoreEvent.WrongPassword)
                disconnect()
            }

            text.contains("OK+PWDM") -> emit(CoreEvent.PasswordChanged(true))
            text.contains("ERR+PWDM") -> emit(CoreEvent.PasswordChanged(false))
            text.contains("OK+NAME:") -> emit(CoreEvent.NameChanged(true))
            text.contains("ERR+NAME") -> emit(CoreEvent.NameChanged(false))
            text.contains("OK+NFC:0") -> emit(CoreEvent.NfcState(false))
            text.contains("OK+NFC:1") -> emit(CoreEvent.NfcState(true))
            text.contains("OK+DEL:1") -> emit(CoreEvent.NfcCardsCleared)
            text.contains("OK+TLVOICEOFF:0") -> emit(CoreEvent.VoiceState(0))
            text.contains("OK+TLVOICEOFF:1") -> emit(CoreEvent.VoiceState(1))
            text.contains("OK+DEVICE") -> emit(CoreEvent.DeviceType(text.substringAfter("OK+DEVICE").trim()))
            text.contains("OK+DRIVEMODE:") -> {
                val value = text.substringAfter("OK+DRIVEMODE:").trim().takeWhile { it.isDigit() }
                value.toIntOrNull()?.let { emit(CoreEvent.DriveMode(it)) }
            }
        }
    }

    // ---- frame handling ------------------------------------------------------------

    private fun onFrame(frame: ByteArray) {
        when (frame[0]) {
            Protocol.HEAD_MONITOR -> handleMonitor(frame)
            Protocol.HEAD_ESC -> handleEsc(frame)
        }
    }

    private fun handleMonitor(frame: ByteArray) {
        if (monitorTicks.incrementAndGet() % 5 == 0 && mode == Mode.MONITORING) {
            gatt.writeData(Protocol.keepFrame())
        }
        when (frame[1]) {
            0x00.toByte() -> parseTelemetry(frame)
            0x01.toByte() -> parseLimits(frame)
        }
    }

    private fun parseTelemetry(f: ByteArray) {
        _telemetry.value = Decoder.telemetry(f)
        if (System.currentTimeMillis() >= suppressSwitchEchoUntil) {
            _ride.update { Decoder.switches(it, f) }
        }
    }

    private fun parseLimits(f: ByteArray) {
        _ride.update { Decoder.limits(it, f) }
    }

    private fun handleEsc(f: ByteArray) {
        clearWatchdog()
        when (f[1]) {
            Protocol.CMD_READ_PARAMETER -> onRegisterBlock(f)
            Protocol.CMD_ESC_INFO -> onInfoBlock(f)
            Protocol.CMD_RW_PARAMETER, Protocol.CMD_WRITE_PARAMETER -> onWriteAck()
            Protocol.CMD_HANDSHAKE -> onOtaHandshake()
            Protocol.CMD_ERASE_FLASH -> onOtaErased()
            Protocol.CMD_UPDATE_FM -> onOtaChunkAck(f)
            Protocol.CMD_ERASE_FAIL -> retryErase()
            Protocol.CMD_UPDATE_FAIL -> failOta("Controller rejected the image")
            Protocol.CMD_WRITE_PARAM_FAIL, Protocol.CMD_RW_PARAM_FAIL -> {
                writeInFlight = false
                emit(CoreEvent.Error(appContext.getString(R.string.write_refused)))
            }

            // An explicit refusal is final: retrying the same read six times would only
            // stall the screen for another few seconds.
            Protocol.CMD_READ_PARAM_FAIL, Protocol.CMD_READ_INFO_FAIL,
            Protocol.CMD_READ_BAT_FAIL, Protocol.CMD_FAIL -> abandonRequest()
        }
    }

    private fun onRegisterBlock(f: ByteArray) {
        val address = Decoder.u16(f, 2)
        val registers = (f[4].toInt() and 0xFF) shr 1
        val byteOffset = address * 2
        val length = registers * 2
        if (byteOffset + length <= paramsPage.size && 5 + length <= f.size) {
            f.copyInto(paramsPage, byteOffset, 5, 5 + length)
        }

        if (address == 74) {
            val service = Decoder.u16(f, 9)
            val last = Decoder.u32(f, 5)
            _ride.update {
                it.copy(
                    serviceMileage = if (service == 0) 300 else service,
                    lastServiceMileage = last,
                )
            }
        }
        decodeParams()

        if (!pollingParams || mode != Mode.READ_PARAMS) return
        readIndex++
        pollJob = scope.launch {
            delay(100)
            if (!pollingParams || mode != Mode.READ_PARAMS) return@launch
            if (readIndex >= 10) {
                readIndex = 0
                gatt.writeData(Protocol.tranFrame(Protocol.CMD_TRAN))
                delay(100)
                issueRequest(Protocol.readFrame(Protocol.CMD_READ_PARAMETER, 0, 16))
            } else {
                val count = if (readIndex == 4) 15 else 16
                issueRequest(
                    Protocol.readFrame(Protocol.CMD_READ_PARAMETER, readIndex * 16, count)
                )
            }
        }
    }

    private fun decodeParams() {
        val decoded = Decoder.params(paramsPage)
        // Ignore a page that was read before the pending write landed.
        if (writeInFlight) return
        _params.value = decoded
        _ride.update {
            it.copy(
                serviceMileage = if (decoded.serviceMileage == 0) 300 else decoded.serviceMileage,
                lastServiceMileage = decoded.lastServiceMileage,
            )
        }
    }

    private fun onInfoBlock(f: ByteArray) {
        val address = Decoder.u16(f, 2)
        if (address + 8 <= infoPage.size && f.size >= 13) {
            f.copyInto(infoPage, address, 5, 13)
        }
        if (address >= 64) {
            decodeInfo()
            return
        }
        if (mode != Mode.ESC_INFO) return
        scope.launch {
            delay(40)
            if (mode == Mode.ESC_INFO) {
                issueRequest(Protocol.readFrame(Protocol.CMD_ESC_INFO, address + 8, 4))
            }
        }
    }

    private fun decodeInfo() {
        val info = Decoder.escInfo(infoPage)
        _escInfo.value = info
        _firmwareVersions.value = firmwareRepo.versionsFor(info.hardware)
        scope.launch {
            delay(80)
            // If the parameters screen was opened while the identity was still being read,
            // start its sweep now instead of dropping back to telemetry.
            if (pollingParams) {
                beginParamSweep()
                return@launch
            }
            mode = Mode.MONITORING
            gatt.writeData(Protocol.keepFrame())
            delay(250)
            requestServiceMileage()
        }
    }

    private fun onWriteAck() {
        writeInFlight = false
        if (mode != Mode.WRITE_PARAM) return
        if (!pollingParams) {
            mode = Mode.MONITORING
            return
        }
        scope.launch {
            delay(120)
            beginParamSweep()
        }
    }

    // ---- OTA state machine ------------------------------------------------------------

    private fun onOtaHandshake() {
        if (mode != Mode.OTA) return
        // The bootloader answers the handshake twice; the erase is only accepted after the second.
        if (!handshakeConfirmed) {
            handshakeConfirmed = true
            scope.launch {
                delay(120)
                gatt.writeData(Protocol.shortFrame(Protocol.CMD_HANDSHAKE))
            }
            return
        }
        _ota.value = OtaProgress(OtaState.ERASING, 0, "Erasing flash")
        scope.launch {
            delay(120)
            gatt.writeData(Protocol.shortFrame(Protocol.CMD_ERASE_FLASH))
        }
    }

    private fun retryErase() {
        if (mode != Mode.OTA) return
        scope.launch {
            delay(150)
            gatt.writeData(Protocol.shortFrame(Protocol.CMD_ERASE_FLASH))
        }
    }

    private fun onOtaErased() {
        if (mode != Mode.OTA) return
        _ota.value = OtaProgress(OtaState.WRITING, 0, "Writing firmware")
        otaIndex = 0
        scope.launch {
            delay(800)
            sendOtaChunk()
        }
    }

    private fun onOtaChunkAck(f: ByteArray) {
        if (mode != Mode.OTA) return
        otaIndex = Decoder.u16(f, 2) + 1
        if (otaIndex < otaChunks.size) {
            val percent = otaIndex * 100 / otaChunks.size
            _ota.value = OtaProgress(OtaState.WRITING, percent, "Writing firmware")
            scope.launch {
                delay(20)
                sendOtaChunk()
            }
        } else {
            _ota.value = OtaProgress(OtaState.DONE, 100, "Update complete")
            scope.launch {
                delay(100)
                gatt.writeData(Protocol.shortFrame(Protocol.CMD_BOOT_EXIT))
                mode = Mode.IDLE
            }
        }
    }

        private fun sendOtaChunk() {
        val frame = otaChunks.getOrNull(otaIndex) ?: return
        // The bootloader accepts up to 130 bytes per fragment; never exceed the negotiated MTU.
        val fragmentSize = minOf(130, gatt.maxWriteLength)
        scope.launch {
            gatt.writeData(Protocol.tranFrame(Protocol.CMD_PACK))
            delay(60)
            var offset = 0
            while (offset < frame.size) {
                val end = minOf(offset + fragmentSize, frame.size)
                gatt.writeData(frame.copyOfRange(offset, end))
                offset = end
                delay(15)
            }
            delay(30)
            gatt.writeData(Protocol.tranFrame(Protocol.CMD_PACK))
        }
    }

    private fun failOta(message: String) {
        mode = Mode.IDLE
        _ota.value = OtaProgress(OtaState.FAILED, _ota.value.percent, message)
    }

    // ---- helpers -----------------------------------------------------------------------

    private fun cancelJobs() {
        pollingParams = false
        writeInFlight = false
        clearWatchdog()
        authJob?.cancel(); authJob = null
        pollJob?.cancel(); pollJob = null
        monitorJob?.cancel(); monitorJob = null
        otaJob?.cancel(); otaJob = null
        reader.reset()
    }

    private fun startMonitoringLoop() {
        monitorJob?.cancel()
        monitorJob = scope.launch {
            while (true) {
                delay(200)
                if (!gatt.isConnected || mode != Mode.MONITORING) continue
                if (isViCont) {
                    gatt.writeData(ViContProtocol.command(ViContProtocol.CMD_START_TELEMETRY))
                } else {
                    gatt.writeData(Protocol.keepFrame())
                }
            }
        }
    }

    private fun issueRequest(frame: ByteArray) {
        lastRequest = frame
        requestAttempts = 0
        gatt.writeData(frame)
        armWatchdog()
    }

    /** Frees the guard if the controller never answers a write at all. */
    private fun releaseWriteGuardLater() {
        scope.launch {
            delay(WRITE_GUARD_MAX_MS)
            writeInFlight = false
        }
    }

    private fun armWatchdog() {
        watchdogJob?.cancel()
        watchdogJob = scope.launch {
            delay(REQUEST_TIMEOUT_MS)
            val frame = lastRequest ?: return@launch
            if (!gatt.isConnected) {
                lastRequest = null
                return@launch
            }
            if (requestAttempts >= MAX_REQUEST_ATTEMPTS) {
                lastRequest = null
                abandonRequest()
                return@launch
            }
            requestAttempts++
            gatt.writeData(Protocol.tranFrame(Protocol.CMD_TRAN))
            delay(40)
            gatt.writeData(frame)
            armWatchdog()
        }
    }

        private fun abandonRequest() {
        when (mode) {
            Mode.ESC_INFO -> {
                // Not every controller answers the identity command; carry on without it.
                _escInfo.value = _escInfo.value
                scope.launch {
                    if (pollingParams) beginParamSweep() else resumeMonitoring()
                }
            }

            Mode.READ_PARAMS -> {
                _paramsStalled.value = true
                scope.launch { resumeMonitoring() }
            }

            else -> Unit
        }
    }

    private suspend fun resumeMonitoring() {
        mode = Mode.MONITORING
        if (!gatt.isConnected) return
        repeat(3) {
            gatt.writeData(Protocol.keepFrame())
            delay(50)
        }
        startMonitoringLoop()
    }

    private fun clearWatchdog() {
        watchdogJob?.cancel()
        watchdogJob = null
        lastRequest = null
        requestAttempts = 0
    }

    private fun locationServicesEnabled(): Boolean {
        val manager = appContext.getSystemService(LocationManager::class.java) ?: return true
        return runCatching {
            manager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
        }.getOrDefault(true)
    }

    private fun emit(event: CoreEvent) {
        scope.launch { _events.emit(event) }
    }

    fun shutdown() {
        autoJob?.cancel()
        autoJob = null
        cancelJobs()
        gatt.close()
        scope.cancel()
    }

    private companion object {
        const val TAG = "AovoCore"
        const val SWITCH_ECHO_GRACE_MS = 900L

        /** Spacing between the individual ViCont switch commands of one change. */
        const val VICONT_COMMAND_SPACING_MS = 60L

        /** Spacing between the tuning queries fired when the parameters screen opens. */
        const val VICONT_QUERY_SPACING_MS = 120L

        /** Ceilings assumed until the scooter reports its own, as the stock app assumes them. */
        const val DEFAULT_RESPONSE_CEILING = 99
        const val DEFAULT_CURRENT_CEILING = 25

        /** Gears every ViCont dashboard takes, whatever its indicator mask claims. */
        val VICONT_BASE_GEARS = listOf(1, 2, 3, 4, 5)

                const val P_LIMIT_ECO = 7
        const val P_LIMIT_DRIVE = 8

                const val P_LIMIT_SPORT = 9

        /** P11 and P12, the caps for the walking gear and the fifth. */
        const val P_LIMIT_GEAR4 = 10
        const val P_LIMIT_GEAR5 = 11

        /** P5, minutes of inactivity before the scooter switches itself off. */
        const val P_AUTO_SHUTDOWN = 4

        /** Long enough for the write to land before the value is read back. */
        const val VICONT_READBACK_DELAY_MS = 250L

                val VICONT_QUERIES = listOf(
            ViContProtocol.CMD_MAX_SPEED,
            ViContProtocol.CMD_MAX_CURRENT,
            ViContProtocol.CMD_STARTING_TORQUE,
            ViContProtocol.CMD_EBRAKE_STRENGTH,
        )

        const val REQUEST_TIMEOUT_MS = 700L
        const val REGISTER_WRITE_SPACING_MS = 700L

        /** Longest the display may hold a value the controller has not confirmed. */
        const val WRITE_GUARD_MAX_MS = 4_000L

        /** How long one reconnect scan burst lasts. */
        const val AUTO_SCAN_MS = 6_000L

        /** Pause between checks while connected or otherwise busy. */
        const val AUTO_POLL_MS = 3_000L

        /** Grace period after firing a connect, before the loop looks again. */
        const val AUTO_SETTLE_MS = 12_000L

        const val AUTO_RETRY_MIN_MS = 8_000L
        const val AUTO_RETRY_MAX_MS = 45_000L
        const val MAX_REQUEST_ATTEMPTS = 6
    }
}
