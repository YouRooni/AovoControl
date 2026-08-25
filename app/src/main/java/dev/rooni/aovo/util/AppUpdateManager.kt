package dev.rooni.aovo.util

import dev.rooni.aovo.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class AppRelease(
    val tagName: String,
    val title: String,
    val body: String,
    val htmlUrl: String,
    val downloadUrl: String?,
)

object AppUpdateManager {

    private const val GITHUB_REPO_API = "https://api.github.com/repos/YouRooni/AovoControl/releases/latest"

    suspend fun checkForUpdates(): Result<AppRelease?> = withContext(Dispatchers.IO) {
        try {
            val url = URL(GITHUB_REPO_API)
            val connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                setRequestProperty("Accept", "application/vnd.github.v3+json")
                setRequestProperty("User-Agent", "AovoControl-App")
                connectTimeout = 8000
                readTimeout = 8000
            }

            if (connection.responseCode != 200) {
                return@withContext Result.failure(Exception("HTTP \${connection.responseCode}"))
            }

            val responseText = connection.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(responseText)

            val tagName = json.optString("tag_name", "")
            val title = json.optString("name", tagName)
            val body = json.optString("body", "").trim()
            val htmlUrl = json.optString("html_url", "https://github.com/YouRooni/AovoControl/releases")

            var downloadUrl: String? = null
            val assets = json.optJSONArray("assets")
            if (assets != null) {
                for (i in 0 until assets.length()) {
                    val asset = assets.getJSONObject(i)
                    val name = asset.optString("name", "")
                    if (name.endsWith(".apk", ignoreCase = true)) {
                        downloadUrl = asset.optString("browser_download_url")
                        break
                    }
                }
            }

            if (isNewer(tagName, BuildConfig.VERSION_NAME)) {
                Result.success(
                    AppRelease(
                        tagName = tagName,
                        title = title,
                        body = body,
                        htmlUrl = htmlUrl,
                        downloadUrl = downloadUrl,
                    )
                )
            } else {
                Result.success(null)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun isNewer(remoteVersion: String, currentVersion: String): Boolean {
        val cleanRemote = remoteVersion.trim().removePrefix("v").removePrefix("V")
        val cleanCurrent = currentVersion.trim().removePrefix("v").removePrefix("V")

        val remoteParts = cleanRemote.split(".").mapNotNull { it.takeWhile { ch -> ch.isDigit() }.toIntOrNull() }
        val currentParts = cleanCurrent.split(".").mapNotNull { it.takeWhile { ch -> ch.isDigit() }.toIntOrNull() }

        if (remoteParts.isEmpty() || currentParts.isEmpty()) {
            return cleanRemote != cleanCurrent && cleanRemote.isNotEmpty()
        }

        val maxLen = maxOf(remoteParts.size, currentParts.size)
        for (i in 0 until maxLen) {
            val r = remoteParts.getOrElse(i) { 0 }
            val c = currentParts.getOrElse(i) { 0 }
            if (r > c) return true
            if (r < c) return false
        }
        return false
    }
}
