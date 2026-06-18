package com.liferecorder.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * 检查 GitHub Release 获取最新版本信息
 */
object UpdateChecker {

    private const val API_URL =
        "https://api.github.com/repos/yzh-2002/Qbit/releases/latest"

    data class UpdateInfo(
        val latestVersion: String,   // 例如 "1.1"
        val tagName: String,         // 例如 "v1.1"
        val releaseNotes: String,    // Release 说明
        val downloadUrl: String      // 浏览器打开的 Release 页面地址
    )

    /**
     * 检查是否有新版本
     * @param currentVersion 当前 app 的 versionName（如 "1.0"）
     * @return 如果有新版本返回 UpdateInfo，否则返回 null
     */
    suspend fun checkUpdate(currentVersion: String): UpdateInfo? {
        return withContext(Dispatchers.IO) {
            try {
                val url = URL(API_URL)
                val connection = url.openConnection() as HttpURLConnection
                connection.apply {
                    requestMethod = "GET"
                    connectTimeout = 10000
                    readTimeout = 10000
                    setRequestProperty("Accept", "application/vnd.github.v3+json")
                    setRequestProperty("User-Agent", "Qbit-Android")
                }

                if (connection.responseCode != 200) {
                    connection.disconnect()
                    return@withContext null
                }

                val response = connection.inputStream.bufferedReader().use { it.readText() }
                connection.disconnect()

                val json = JSONObject(response)
                val tagName = json.optString("tag_name", "")      // "v1.1"
                val body = json.optString("body", "")              // Release notes
                val htmlUrl = json.optString("html_url", "")      // Release 页面链接

                // 去掉 "v" 前缀得到版本号
                val latestVersion = tagName.removePrefix("v")

                if (latestVersion.isNotEmpty() && isNewer(latestVersion, currentVersion)) {
                    UpdateInfo(
                        latestVersion = latestVersion,
                        tagName = tagName,
                        releaseNotes = body,
                        downloadUrl = htmlUrl
                    )
                } else {
                    null
                }
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
    }

    /**
     * 简单的版本号比较：按 "." 分割后逐段比较数字
     * 例如 "1.1" > "1.0"，"2.0" > "1.9"
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
