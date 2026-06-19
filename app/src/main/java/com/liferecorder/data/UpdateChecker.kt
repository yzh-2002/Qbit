package com.liferecorder.data

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

private const val TAG = "UpdateChecker"

/**
 * 检查 GitHub Release 获取最新版本信息
 */
object UpdateChecker {

    private const val API_URL =
        "https://api.github.com/repos/yzh-2002/Qbit/releases/latest"

    data class UpdateInfo(
        val latestVersion: String,   // 例如 "0.2.0"
        val tagName: String,         // 例如 "v0.2.0"
        val releaseNotes: String,    // Release 说明
        val downloadUrl: String,     // Release 页面地址（备用）
        val apkDownloadUrl: String?  // APK 直接下载链接
    )

    /**
     * 检查是否有新版本
     * @param currentVersion 当前 app 的 versionName（如 "0.2.0"）
     * @return 如果有新版本返回 UpdateInfo，否则返回 null
     */
    suspend fun checkUpdate(currentVersion: String): UpdateInfo? {
        return withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Checking update, current version: $currentVersion")
                val url = URL(API_URL)
                val connection = url.openConnection() as HttpURLConnection
                connection.apply {
                    requestMethod = "GET"
                    connectTimeout = 15000
                    readTimeout = 15000
                    setRequestProperty("Accept", "application/vnd.github.v3+json")
                    setRequestProperty("User-Agent", "Qbit-Android")
                }

                val code = connection.responseCode
                Log.d(TAG, "Response code: $code")
                if (code != 200) {
                    connection.disconnect()
                    return@withContext null
                }

                val response = connection.inputStream.bufferedReader().use { it.readText() }
                connection.disconnect()

                val json = JSONObject(response)
                val tagName = json.optString("tag_name", "")
                val body = json.optString("body", "")
                val htmlUrl = json.optString("html_url", "")

                // 从 assets 中找 release APK 下载链接
                val apkUrl = findApkDownloadUrl(json)

                val latestVersion = tagName.removePrefix("v")

                if (latestVersion.isNotEmpty() && isNewer(latestVersion, currentVersion)) {
                    UpdateInfo(
                        latestVersion = latestVersion,
                        tagName = tagName,
                        releaseNotes = body,
                        downloadUrl = htmlUrl,
                        apkDownloadUrl = apkUrl
                    )
                } else {
                    null
                }
            } catch (e: Exception) {
                Log.e(TAG, "Update check failed: ${e.javaClass.simpleName}: ${e.message}")
                null
            }
        }
    }

    /**
     * 从 Release 的 assets 中查找 release APK 的下载链接
     */
    private fun findApkDownloadUrl(json: JSONObject): String? {
        val assets = json.optJSONArray("assets") ?: return null
        // 优先找 release 版本的 APK
        for (i in 0 until assets.length()) {
            val asset = assets.getJSONObject(i)
            val name = asset.optString("name", "")
            if (name.endsWith(".apk") && name.contains("release")) {
                return asset.optString("browser_download_url", null)
            }
        }
        // 没有 release 就找任意 APK
        for (i in 0 until assets.length()) {
            val asset = assets.getJSONObject(i)
            val name = asset.optString("name", "")
            if (name.endsWith(".apk")) {
                return asset.optString("browser_download_url", null)
            }
        }
        return null
    }

    /**
     * 简单的版本号比较：按 "." 分割后逐段比较数字
     */
    private fun isNewer(latest: String, current: String): Boolean {
        val latestParts = latest.split(".").mapNotNull { it.toIntOrNull() }
        val currentParts = current.split(".").mapNotNull { it.toIntOrNull() }
        val maxLen = maxOf(latestParts.size, currentParts.size)
        for (i in 0 until maxLen) {
            val l = latestParts.getOrElse(i) { 0 }
            val c = currentParts.getOrElse(i) { 0 }
            if (l > c) return true
            if (l < c) return false
        }
        return false
    }
}
