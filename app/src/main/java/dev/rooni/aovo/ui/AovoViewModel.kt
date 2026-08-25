package dev.rooni.aovo.ui

import dev.rooni.aovo.ble.VicontApiClient
import kotlinx.coroutines.flow.update

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import dev.rooni.aovo.AovoApp
import dev.rooni.aovo.R
import dev.rooni.aovo.ble.AovoCore
import dev.rooni.aovo.ble.AuthMode
import dev.rooni.aovo.ble.ConnectionState
import dev.rooni.aovo.ble.CoreEvent
import dev.rooni.aovo.ble.ScannedDevice
import dev.rooni.aovo.ble.ScooterFamily
import dev.rooni.aovo.ble.Telemetry
import dev.rooni.aovo.data.AppSettings
import dev.rooni.aovo.data.DashboardLayout
import dev.rooni.aovo.data.DashboardTile
import dev.rooni.aovo.data.Prefs
import dev.rooni.aovo.data.ProfileCapture
import dev.rooni.aovo.data.ProfileIcons
import dev.rooni.aovo.data.RideProfile
import dev.rooni.aovo.data.SessionLog
import dev.rooni.aovo.data.TileType
import dev.rooni.aovo.data.ThemeMode

class AovoViewModel : ViewModel() {

    private val app = AovoApp.instance
    val core: AovoCore = app.core
    private val prefs: Prefs = app.prefs

    val settings = prefs.settings.stateIn(
        viewModelScope, SharingStarted.Eagerly, AppSettings()
    )

    /** Off by default: the scooter list should not be a list of every radio in the room. */
    private val _showAllDevices = MutableStateFlow(false)
    val showAllDevices = _showAllDevices.asStateFlow()

        val devices = combine(core.allDevices, _showAllDevices) { found, showAll ->
        if (showAll) found else found.filter { it.isScooter }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _snackbar = MutableStateFlow<String?>(null)
    val snackbar = _snackbar.asStateFlow()

    /** Device the user picked but has not yet supplied a password for. */
    private val _passwordPrompt = MutableStateFlow<ScannedDevice?>(null)
    val passwordPrompt = _passwordPrompt.asStateFlow()

    /** Password waiting on the module to confirm it, so it can be saved on success. */
    private var pendingNewPassword: String? = null

    /** Last device we tried to reach, so a rejected password can be retried by hand. */
    private var lastAttempt: ScannedDevice? = null

    init {
        // Logging is a stored preference, so a session that starts with it already on
        // begins recording without the user having to switch it on again.
        viewModelScope.launch {
            settings.collect { SessionLog.setEnabled(it.logging) }
        }

        // ViCont cannot report its Eco and Drive limits, so they are restored from what was
        // last written to this particular scooter and kept up to date as the user changes
        // them. Seeded once per connection, before any of the user's own edits land.
        viewModelScope.launch {
            core.connection.collect { state ->
                if (state != ConnectionState.CONNECTED) return@collect
                if (core.family.value != ScooterFamily.VICONT) return@collect
                val saved = settings.value.viContLimits.split('|')
                val address = core.connectedDevice.value?.address ?: return@collect
                if (saved.size < 4 || saved[0] != address) return@collect
                core.seedModeLimits(saved.drop(1).map { it.toIntOrNull() ?: 0 })
            }
        }

        viewModelScope.launch {
            core.ride.collect { ride ->
                if (core.family.value != ScooterFamily.VICONT) return@collect
                val address = core.connectedDevice.value?.address ?: return@collect
                val limits = listOf(
                    ride.limitEco, ride.limitComfort, ride.limitSport,
                    ride.limitGear4, ride.limitGear5,
                )
                val encoded = (listOf(address) + limits).joinToString("|")
                if (encoded == settings.value.viContLimits) return@collect
                prefs.rememberViContLimits(address, limits)
            }
        }

        viewModelScope.launch {
            core.events.collect { event ->
                when (event) {
                    is CoreEvent.PasswordChanged -> onPasswordChangeResult(event.ok)
                    // The stored password no longer matches the module: ask instead of
                    // silently failing every future reconnect.
                    CoreEvent.WrongPassword, CoreEvent.PasswordTimeout ->
                        _passwordPrompt.value = lastAttempt

                    else -> Unit
                }
                _snackbar.value = event.describe()
            }
        }

        // Hand the reconnect target to the core and let it own the radio: it knows when a
        // connection is in flight, and a poll from here would fight it.
        viewModelScope.launch {
            settings.collect { current ->
                val target = current.lastDeviceAddress
                    .takeIf { current.autoConnect && it.isNotEmpty() }
                    ?.let { ScannedDevice(current.lastDeviceName, it, 0) }
                core.setAutoReconnect(target, current.lastDevicePassword)
            }
        }
    }

        fun changePassword(newPassword: String) {
        pendingNewPassword = newPassword
        core.changePassword(newPassword)
    }

    private fun onPasswordChangeResult(ok: Boolean) {
        val pending = pendingNewPassword ?: return
        pendingNewPassword = null
        if (!ok) return
        val saved = settings.value
        viewModelScope.launch {
            prefs.rememberDevice(saved.lastDeviceName, saved.lastDeviceAddress, pending)
        }
    }

    private fun CoreEvent.describe(): String = when (this) {
        is CoreEvent.Error -> message
        is CoreEvent.Info -> message
        CoreEvent.WrongPassword -> "Wrong password"
        CoreEvent.PasswordTimeout -> "Password verification timed out"
        is CoreEvent.PasswordChanged -> app.getString(
            if (ok) R.string.password_saved else R.string.password_change_failed
        )
        is CoreEvent.NameChanged -> if (ok) "Scooter renamed" else "Rename failed"
        is CoreEvent.NfcState -> if (enabled) "NFC unlock is on" else "NFC unlock is off"
        CoreEvent.NfcCardsCleared -> "NFC cards cleared"
        is CoreEvent.VoiceState -> if (type == 0) "Voice prompts on" else "Voice prompts off"
        is CoreEvent.DriveMode -> "Drive mode: " + (if (type == 2) "dual motor" else "single motor")
        is CoreEvent.DeviceType -> "Module: " + value
    }

    fun clearSnackbar() {
        _snackbar.value = null
    }

    fun notify(message: String) {
        _snackbar.value = message
    }

    // ---- connection ------------------------------------------------------------------

    fun setShowAllDevices(show: Boolean) {
        _showAllDevices.value = show
    }

    fun startScan() = core.startScan()

    fun stopScan() = core.stopScan()

    fun onDevicePicked(device: ScannedDevice) {
        val saved = settings.value
        val remembered = saved.lastDeviceAddress == device.address
        val savedPassword = saved.lastDevicePassword.takeIf { remembered && it.isNotEmpty() }
        when (device.authMode) {
            AuthMode.NONE -> connect(device, "")
            AuthMode.DEFAULT_PASSWORD ->
                connect(device, savedPassword ?: ScannedDevice.DEFAULT_PASSWORD)

            AuthMode.USER_PASSWORD ->
                if (savedPassword != null) connect(device, savedPassword)
                else _passwordPrompt.value = device
        }
    }

    fun dismissPasswordPrompt() {
        _passwordPrompt.value = null
    }

    fun connect(device: ScannedDevice, password: String) {
        _passwordPrompt.value = null
        lastAttempt = device
        // Connecting by hand re-arms the loop that a previous manual disconnect stopped.
        if (settings.value.autoConnect) core.setAutoReconnect(device, password)
        core.connect(device, password)
        viewModelScope.launch { prefs.rememberDevice(device.name, device.address, password) }
    }

    fun reconnectLast() {
        val saved = settings.value
        if (saved.lastDeviceAddress.isEmpty()) {
            startScan()
            return
        }
        core.connect(
            ScannedDevice(saved.lastDeviceName, saved.lastDeviceAddress, 0),
            saved.lastDevicePassword,
        )
    }

    /** A tap on Disconnect also cancels the quiet reconnect loop until the next connect. */
    fun disconnect() = core.disconnect()

    fun forgetDevice() {
        viewModelScope.launch { prefs.forgetDevice() }
        core.disconnect()
    }

    val isConnected: Boolean get() = core.connection.value == ConnectionState.CONNECTED

    // ---- settings --------------------------------------------------------------------

    fun setTheme(mode: ThemeMode) = viewModelScope.launch { prefs.setTheme(mode) }
    fun setDynamicColor(on: Boolean) = viewModelScope.launch { prefs.setDynamicColor(on) }
    fun setPalette(key: String) = viewModelScope.launch { prefs.setPalette(key) }
    fun setAmoled(on: Boolean) = viewModelScope.launch { prefs.setAmoled(on) }
    fun setAutoConnect(on: Boolean) = viewModelScope.launch { prefs.setAutoConnect(on) }
    fun setKeepScreenOn(on: Boolean) = viewModelScope.launch { prefs.setKeepScreenOn(on) }
    fun setExpertMode(on: Boolean) = viewModelScope.launch { prefs.setExpertMode(on) }
    fun setHaptics(on: Boolean) = viewModelScope.launch { prefs.setHaptics(on) }

    // ---- diagnostics ---------------------------------------------------------------

    val logEntries = SessionLog.entries

        fun setLogging(on: Boolean) = viewModelScope.launch {
        prefs.setLogging(on)
        SessionLog.setEnabled(on)
    }

    /** Text an export writes out; empty buffers still produce a readable file. */
    fun logText(): String = SessionLog.export()

    /** Timestamped so several exports from one session do not overwrite each other. */
    fun logFileName(): String =
        "aovo-log-" + LOG_STAMP.format(java.time.LocalDateTime.now()) + ".txt"

    private companion object {
        val LOG_STAMP: java.time.format.DateTimeFormatter =
            java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")
    }

    // ---- dashboard layout --------------------------------------------------------------

    /** Editing is a session-scoped mode, not something to persist across launches. */
    private val _editingDashboard = MutableStateFlow(false)
    val editingDashboard = _editingDashboard.asStateFlow()

    fun setEditingDashboard(editing: Boolean) {
        _editingDashboard.value = editing
    }

    val dashboard get() = settings.value.dashboard

    private fun updateDashboard(transform: (DashboardLayout) -> DashboardLayout) {
        val next = transform(settings.value.dashboard)
        viewModelScope.launch { prefs.setDashboard(next) }
    }

    fun moveTile(id: String, delta: Int) = updateDashboard { it.move(id, delta) }
    fun moveTileTo(from: Int, to: Int) = updateDashboard { it.moveTo(from, to) }

    /** Stores the order a drag ended on, in one write. */
    fun setTileOrder(tiles: List<DashboardTile>) = updateDashboard { it.copy(tiles = tiles) }
    fun resizeTile(id: String, span: Int) = updateDashboard { it.resize(id, span) }
    fun cycleTileHeight(id: String) = updateDashboard { it.cycleHeight(id) }
    fun setTileHeight(id: String, step: Int) = updateDashboard { it.setHeight(id, step) }
    fun removeTile(id: String) = updateDashboard { it.remove(id) }

    fun addTile(type: TileType, profileId: String? = null) =
        updateDashboard { it.add(type, profileId) }

    fun resetDashboard() = viewModelScope.launch { prefs.resetDashboard() }

    // ---- ride profiles -----------------------------------------------------------------

    val profiles get() = settings.value.profiles

    fun profile(id: String?): RideProfile? =
        id?.let { wanted -> settings.value.profiles.firstOrNull { it.id == wanted } }

    private fun updateProfiles(transform: (List<RideProfile>) -> List<RideProfile>) {
        val next = transform(settings.value.profiles)
        viewModelScope.launch {
            prefs.setProfiles(next)
            // A tile pointing at a deleted profile would be a dead button.
            val ids = next.map { it.id }.toSet()
            val layout = settings.value.dashboard
            val pruned = layout.pruneProfiles(ids)
            if (pruned != layout) prefs.setDashboard(pruned)
        }
    }

        private fun captureInto(base: RideProfile, capture: ProfileCapture): RideProfile {
        val ride = core.ride.value
        val params = core.params.value
        val readable = params.loaded
        return base.copy(
            gear = ride.gear.takeIf { capture.ride },
            headLight = ride.headLight.takeIf { capture.ride },
            ambientLight = ride.ambientLight.takeIf { capture.ride },
            cruiseControl = ride.cruiseControl.takeIf { capture.ride },
            zeroStart = ride.zeroStart.takeIf { capture.ride },
            imperial = ride.imperial.takeIf { capture.ride },
            limitCruise = ride.limitCruise.takeIf { capture.modeLimits },
            limitEco = ride.limitEco.takeIf { capture.modeLimits },
            limitComfort = ride.limitComfort.takeIf { capture.modeLimits },
            limitSport = ride.limitSport.takeIf { capture.modeLimits },
            throttleResponse = params.throttleResponse
                .takeIf { capture.throttleResponse && readable },
            brakeResponse = params.brakeResponse.takeIf { capture.brakeResponse && readable },
            speedLimit = params.speedLimit.takeIf { capture.speedLimit && readable },
        )
    }

    /** Captures the settings currently in effect under a new name. */
    fun saveProfile(name: String, icon: String, capture: ProfileCapture) {
        val profile = captureInto(
            RideProfile(
                id = RideProfile.newId(),
                name = name.trim().ifBlank { "Profile" },
                icon = ProfileIcons.normalise(icon),
            ),
            capture,
        )
        updateProfiles { it + profile }
    }

    /** Renames a profile and swaps its icon in one go. */
    fun editProfile(id: String, name: String, icon: String) = updateProfiles { list ->
        list.map {
            if (it.id == id) {
                it.copy(
                    name = name.trim().ifBlank { it.name },
                    icon = ProfileIcons.normalise(icon),
                )
            } else {
                it
            }
        }
    }

    fun deleteProfile(id: String) = updateProfiles { list -> list.filterNot { it.id == id } }

    /** Re-captures the current settings into an existing profile, keeping its name. */
    fun overwriteProfile(id: String, capture: ProfileCapture) {
        val existing = profile(id) ?: return
        val updated = captureInto(existing, capture)
        updateProfiles { list -> list.map { if (it.id == id) updated else it } }
    }

        fun applyProfile(profile: RideProfile) {
        if (!isConnected) {
            notify(app.getString(R.string.disconnected))
            return
        }
        val holdBack = core.telemetry.value.settingsLocked && profile.touchesStoppedOnly
        if (holdBack && !profile.touchesWhileMoving) {
            notify(app.getString(R.string.profile_blocked_moving, profile.name, lockSpeedText))
            return
        }
        if (profile.touchesWhileMoving || (profile.touchesRideState && !holdBack)) {
            core.applyRideBundle(
                gear = profile.gear,
                headLight = profile.headLight,
                ambientLight = profile.ambientLight,
                cruiseControl = profile.cruiseControl,
                zeroStart = profile.zeroStart,
                imperial = profile.imperial,
                limitCruise = profile.limitCruise.takeUnless { holdBack },
                limitEco = profile.limitEco.takeUnless { holdBack },
                limitComfort = profile.limitComfort.takeUnless { holdBack },
                limitSport = profile.limitSport.takeUnless { holdBack },
            )
        }
        if (profile.touchesRegisters && !holdBack) {
            core.applyRegisterBundle(
                throttle = profile.throttleResponse,
                brake = profile.brakeResponse,
                speedLimit = profile.speedLimit,
            )
        }
        notify(
            if (holdBack) {
                app.getString(R.string.profile_applied_partial, profile.name, lockSpeedText)
            } else {
                app.getString(R.string.profile_applied, profile.name)
            }
        )
    }

    /** Speed the controller stops accepting parameter writes at, as it is shown to the user. */
    private val lockSpeedText: String
        get() = Telemetry.SETTINGS_LOCK_SPEED_KMH.toInt().toString()

    data class OnlineUpdateUiState(
        val checking: Boolean = false,
        val result: VicontApiClient.CheckResult? = null,
        val downloading: Boolean = false,
    )

    private val _onlineUpdate = MutableStateFlow(OnlineUpdateUiState())
    val onlineUpdate = _onlineUpdate.asStateFlow()

    fun checkOnlineUpdates(targetType: String = "AD102030") {
        val device = core.connectedDevice.value ?: return
        val name = device.name.ifBlank { device.address }
        viewModelScope.launch {
            _onlineUpdate.update { it.copy(checking = true, result = null) }
            val res = VicontApiClient.checkForUpdates(name, targetType)
            _onlineUpdate.update { it.copy(checking = false, result = res) }
        }
    }

    fun downloadAndFlashOnline(url: String) {
        viewModelScope.launch {
            _onlineUpdate.update { it.copy(downloading = true) }
            val bytes = VicontApiClient.downloadFirmwareBytes(url)
            _onlineUpdate.update { it.copy(downloading = false) }
            if (bytes == null || bytes.isEmpty()) {
                notify(app.getString(R.string.flash_failed))
            } else {
                core.flashFirmware(bytes)
            }
        }
    }
}
