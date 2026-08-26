package dev.rooni.aovo.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "aovo")

enum class ThemeMode { SYSTEM, LIGHT, DARK }
enum class GaugeStyle { CLASSIC, EXPRESSIVE }

data class AppSettings(
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = true,
    val palette: String = "default",
    val amoled: Boolean = false,
    val autoConnect: Boolean = true,
    val keepScreenOn: Boolean = false,
    val haptics: Boolean = true,
    val expertMode: Boolean = false,
    val logging: Boolean = false,
    val viContLimits: String = "",
    val lastDeviceName: String = "",
    val lastDeviceAddress: String = "",
    val lastDevicePassword: String = "",
    val dashboard: DashboardLayout = DashboardLayout.Default,
    val profiles: List<RideProfile> = emptyList(),
    val gaugeStyle: GaugeStyle = GaugeStyle.CLASSIC,
    val ignoredUpdateVersion: String = "",
)

class Prefs(private val context: Context) {

    val settings: Flow<AppSettings> = context.dataStore.data.map { it.toSettings() }

    private fun Preferences.toSettings() = AppSettings(
        theme = ThemeMode.entries.getOrElse(this[KEY_THEME] ?: 0) { ThemeMode.SYSTEM },
        dynamicColor = this[KEY_DYNAMIC] ?: true,
        palette = this[KEY_PALETTE] ?: "default",
        amoled = this[KEY_AMOLED] ?: false,
        autoConnect = this[KEY_AUTOCONNECT] ?: true,
        keepScreenOn = this[KEY_KEEP_SCREEN] ?: false,
        haptics = this[KEY_HAPTICS] ?: true,
        expertMode = this[KEY_EXPERT] ?: false,
        logging = this[KEY_LOGGING] ?: false,
        viContLimits = this[KEY_VICONT_LIMITS].orEmpty(),
        lastDeviceName = this[KEY_NAME].orEmpty(),
        lastDeviceAddress = this[KEY_ADDRESS].orEmpty(),
        lastDevicePassword = this[KEY_PASSWORD].orEmpty(),
        dashboard = DashboardLayout.decode(this[KEY_DASHBOARD]),
        profiles = RideProfile.decode(this[KEY_PROFILES]),
        gaugeStyle = GaugeStyle.entries.getOrElse(this[KEY_GAUGE_STYLE] ?: 0) { GaugeStyle.CLASSIC },
        ignoredUpdateVersion = this[KEY_IGNORED_UPDATE].orEmpty(),
    )

    suspend fun setTheme(mode: ThemeMode) = edit { it[KEY_THEME] = mode.ordinal }
    suspend fun setDynamicColor(enabled: Boolean) = edit { it[KEY_DYNAMIC] = enabled }
    suspend fun setPalette(key: String) = edit { it[KEY_PALETTE] = key }
    suspend fun setAmoled(enabled: Boolean) = edit { it[KEY_AMOLED] = enabled }
    suspend fun setAutoConnect(enabled: Boolean) = edit { it[KEY_AUTOCONNECT] = enabled }
    suspend fun setKeepScreenOn(enabled: Boolean) = edit { it[KEY_KEEP_SCREEN] = enabled }
    suspend fun setExpertMode(enabled: Boolean) = edit { it[KEY_EXPERT] = enabled }
    suspend fun setHaptics(enabled: Boolean) = edit { it[KEY_HAPTICS] = enabled }
    suspend fun setLogging(enabled: Boolean) = edit { it[KEY_LOGGING] = enabled }
    suspend fun setGaugeStyle(style: GaugeStyle) = edit { it[KEY_GAUGE_STYLE] = style.ordinal }

    suspend fun rememberViContLimits(address: String, limits: List<Int>) = edit {
        it[KEY_VICONT_LIMITS] = (listOf(address) + limits).joinToString("|")
    }

    suspend fun setDashboard(layout: DashboardLayout) = edit {
        it[KEY_DASHBOARD] = layout.encode()
    }

    suspend fun resetDashboard() = edit { it.remove(KEY_DASHBOARD) }

    suspend fun setIgnoredUpdateVersion(version: String) = edit { it[KEY_IGNORED_UPDATE] = version }

    suspend fun setProfiles(profiles: List<RideProfile>) = edit {
        it[KEY_PROFILES] = RideProfile.encode(profiles)
    }

    suspend fun rememberDevice(name: String, address: String, password: String) = edit {
        it[KEY_NAME] = name
        it[KEY_ADDRESS] = address
        it[KEY_PASSWORD] = password
    }

    suspend fun forgetDevice() = edit {
        it.remove(KEY_NAME)
        it.remove(KEY_ADDRESS)
        it.remove(KEY_PASSWORD)
    }

    private suspend fun edit(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        context.dataStore.edit(block)
    }

    private companion object {
        val KEY_THEME = intPreferencesKey("theme")
        val KEY_DYNAMIC = booleanPreferencesKey("dynamic_color")
        val KEY_PALETTE = stringPreferencesKey("palette")
        val KEY_AMOLED = booleanPreferencesKey("amoled")
        val KEY_AUTOCONNECT = booleanPreferencesKey("auto_connect")
        val KEY_KEEP_SCREEN = booleanPreferencesKey("keep_screen_on")
        val KEY_EXPERT = booleanPreferencesKey("expert_mode")
        val KEY_HAPTICS = booleanPreferencesKey("haptics")
        val KEY_LOGGING = booleanPreferencesKey("logging")
        val KEY_VICONT_LIMITS = stringPreferencesKey("vicont_limits")
        val KEY_NAME = stringPreferencesKey("device_name")
        val KEY_ADDRESS = stringPreferencesKey("device_address")
        val KEY_PASSWORD = stringPreferencesKey("device_password")
        val KEY_DASHBOARD = stringPreferencesKey("dashboard_layout")
        val KEY_PROFILES = stringPreferencesKey("ride_profiles")
        val KEY_GAUGE_STYLE = intPreferencesKey("gauge_style")
        val KEY_IGNORED_UPDATE = stringPreferencesKey("ignored_update_version")
    }
}
