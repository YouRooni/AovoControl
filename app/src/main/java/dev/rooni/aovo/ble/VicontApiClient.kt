package dev.rooni.aovo.ble

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

object VicontApiClient {

    private const val TAG = "VicontApiClient"
    private const val BASE_URL = "http://api.vicontsz.com"

    data class OnlineFirmwareInfo(
        val deviceId: String,
        val deviceName: String,
        val brandName: String,
        val modelName: String,
        val version: Long,
        val crc16: Int,
        val changelog: String,
        val upgradeTypeCode: String,
        val downloadUrl: String,
    )

    sealed class CheckResult {
        data class Available(val firmware: OnlineFirmwareInfo) : CheckResult()
        data class NoUpdate(val deviceName: String, val modelName: String) : CheckResult()
        data class NotFound(val reason: String) : CheckResult()
        data class Error(val error: String) : CheckResult()
    }

        private suspend fun fetchGuestToken(): String? = withContext(Dispatchers.IO) {
        try {
            val url = URL("$BASE_URL/login?grant_type=app")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 8000
                readTimeout = 8000
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                setRequestProperty("Accept-Language", "zh-Hans")
                setRequestProperty("Accept-Platform", "android")
                setRequestProperty("User-Agent", "Mozilla/5.0")
                setRequestProperty("Authorization", "Basic YXBwOmFwcF9zZWNyZXQ=")
            }

            val body = JSONObject().put("loginType", "TEMPORARY_USER").toString()
            conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }

            if (conn.responseCode != 200) {
                Log.w(TAG, "Guest login HTTP ${conn.responseCode}")
                return@withContext null
            }

            val text = conn.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(text)
            if (json.optInt("code") == 0) {
                json.optJSONObject("data")?.optString("access_token")
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get guest token", e)
            null
        }
    }

        private suspend fun resolveDevice(token: String, code: String): JSONObject? = withContext(Dispatchers.IO) {
        try {
            val encoded = URLEncoder.encode(code, "UTF-8")
            val url = URL("$BASE_URL/api/device/user/inner/info?code=$encoded")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 8000
                readTimeout = 8000
                setRequestProperty("Authorization", "Bearer $token")
                setRequestProperty("Accept-Language", "zh-Hans")
                setRequestProperty("Accept-Platform", "android")
                setRequestProperty("User-Agent", "Mozilla/5.0")
            }

            if (conn.responseCode != 200) return@withContext null
            val text = conn.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(text)
            if (json.optInt("code") == 0) json.optJSONObject("data") else null
        } catch (e: Exception) {
            Log.e(TAG, "Failed to resolve device info", e)
            null
        }
    }

        suspend fun checkForUpdates(
        scooterCode: String,
        typeCode: String = "AD102030",
    ): CheckResult = withContext(Dispatchers.IO) {
        if (scooterCode.isBlank()) {
            return@withContext CheckResult.NotFound("Scooter name or serial code is empty")
        }

        val token = fetchGuestToken()
            ?: return@withContext CheckResult.Error("Could not authenticate with Vicont server")

        val device = resolveDevice(token, scooterCode)
            ?: return@withContext CheckResult.NotFound("Device '$scooterCode' not found in server database")

        val deviceId = device.optString("deviceId").ifBlank { device.optString("id") }
        val devName = device.optString("deviceName", scooterCode)
        val brandName = device.optString("brandName", "Unknown")
        val modelName = device.optString("modelName", "Unknown")

        if (deviceId.isBlank()) {
            return@withContext CheckResult.NotFound("Server did not return a valid deviceId")
        }

        try {
            val url = URL("$BASE_URL/api/upgrade/last/info?deviceId=$deviceId&upgradeTypeCode=$typeCode")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 8000
                readTimeout = 8000
                setRequestProperty("Authorization", "Bearer $token")
                setRequestProperty("Accept-Language", "zh-Hans")
                setRequestProperty("Accept-Platform", "android")
                setRequestProperty("User-Agent", "Mozilla/5.0")
            }

            if (conn.responseCode != 200) {
                return@withContext CheckResult.Error("Server returned HTTP ${conn.responseCode}")
            }

            val text = conn.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(text)
            if (json.optInt("code") != 0) {
                return@withContext CheckResult.NotFound(json.optString("msg", "No firmware info"))
            }

            val data = json.optJSONObject("data")
            if (data == null) {
                return@withContext CheckResult.NoUpdate(devName, modelName)
            }

            val binInfo = data.optJSONObject("binFileInfo")
            val filePath = binInfo?.optString("filePath").orEmpty()
            if (filePath.isBlank()) {
                return@withContext CheckResult.NoUpdate(devName, modelName)
            }

            CheckResult.Available(
                OnlineFirmwareInfo(
                    deviceId = deviceId,
                    deviceName = devName,
                    brandName = brandName,
                    modelName = modelName,
                    version = data.optLong("version", 0L),
                    crc16 = data.optInt("crc16", 0),
                    changelog = data.optString("content", ""),
                    upgradeTypeCode = typeCode,
                    downloadUrl = filePath,
                )
            )
        } catch (e: Exception) {
            Log.e(TAG, "Firmware check failed", e)
            CheckResult.Error(e.localizedMessage ?: "Connection error")
        }
    }

        suspend fun downloadFirmwareBytes(fileUrl: String): ByteArray? = withContext(Dispatchers.IO) {
        try {
            val url = URL(fileUrl)
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 15000
                readTimeout = 15000
                setRequestProperty("User-Agent", "Mozilla/5.0")
            }

            if (conn.responseCode != 200) return@withContext null
            val output = ByteArrayOutputStream()
            conn.inputStream.use { input -> input.copyTo(output) }
            output.toByteArray()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to download firmware binary", e)
            null
        }
    }
}
