package com.liferecorder.data

import android.content.Context
import android.content.Intent
import android.os.Environment
import android.util.Log
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

private const val TAG = "ApkDownloader"

/**
 * 应用内 APK 下载与安装
 * 使用 HttpURLConnection 手动下载，解决 DownloadManager 对 GitHub 302 重定向处理不佳的问题
 */
object ApkDownloader {

    /** 下载状态 */
    sealed class DownloadState {
        data object Idle : DownloadState()
        /** progress: 0-100 表示确定进度，-1 表示不确定进度；downloadedMB 已下载大小 */
        data class Downloading(val progress: Int, val downloadedMB: String) : DownloadState()
        data object Installing : DownloadState()
        data class Failed(val message: String) : DownloadState()
    }

    private val _state = MutableStateFlow<DownloadState>(DownloadState.Idle)
    val state: StateFlow<DownloadState> = _state.asStateFlow()

    fun reset() {
        _state.value = DownloadState.Idle
    }

    /**
     * 下载 APK 并自动安装
     */
    suspend fun download(context: Context, url: String, version: String) {
        val fileName = "Qbit-$version.apk"

        // 清理旧 APK
        val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
        dir?.listFiles()?.filter { it.name.endsWith(".apk") }?.forEach { it.delete() }

        val targetFile = File(dir, fileName)

        _state.value = DownloadState.Downloading(-1, "0.0")
        Log.d(TAG, "Download started, url=$url")

        withContext(Dispatchers.IO) {
            try {
                downloadFile(url, targetFile)
                // 下载完成，触发安装
                _state.value = DownloadState.Installing
                Log.d(TAG, "Download complete (${targetFile.length()} bytes), installing...")
                withContext(Dispatchers.Main) {
                    installApk(context, targetFile)
                }
                delay(1000)
                _state.value = DownloadState.Idle
            } catch (e: Exception) {
                Log.e(TAG, "Download failed: ${e.javaClass.simpleName}: ${e.message}")
                _state.value = DownloadState.Failed("下载失败：${e.message ?: "未知错误"}")
                // 清理不完整的文件
                targetFile.delete()
            }
        }
    }

    /**
     * 用 HttpURLConnection 下载文件，自动跟随重定向，实时更新进度
     */
    private fun downloadFile(urlStr: String, targetFile: File) {
        var currentUrl = urlStr
        var redirectCount = 0
        val maxRedirects = 5

        // 手动跟随重定向（HttpURLConnection 跨协议/跨域重定向不自动跟随）
        while (redirectCount < maxRedirects) {
            val url = URL(currentUrl)
            val connection = url.openConnection() as HttpURLConnection
            connection.apply {
                requestMethod = "GET"
                connectTimeout = 30000
                readTimeout = 60000
                instanceFollowRedirects = false  // 手动处理重定向
                setRequestProperty("User-Agent", "Qbit-Android")
            }

            val responseCode = connection.responseCode
            Log.d(TAG, "Request $currentUrl -> HTTP $responseCode")

            if (responseCode in 300..399) {
                val location = connection.getHeaderField("Location")
                connection.disconnect()
                if (location.isNullOrEmpty()) {
                    throw Exception("重定向无 Location 头")
                }
                currentUrl = location
                redirectCount++
                Log.d(TAG, "Redirect #$redirectCount -> $currentUrl")
                continue
            }

            if (responseCode != 200) {
                connection.disconnect()
                throw Exception("服务器返回 HTTP $responseCode")
            }

            // 开始下载
            val contentLength = connection.contentLength.toLong()
            Log.d(TAG, "Content-Length: $contentLength")

            connection.inputStream.use { input ->
                FileOutputStream(targetFile).use { output ->
                    val buffer = ByteArray(8192)
                    var bytesDownloaded = 0L
                    var lastUpdateTime = 0L

                    while (true) {
                        val bytesRead = input.read(buffer)
                        if (bytesRead == -1) break

                        output.write(buffer, 0, bytesRead)
                        bytesDownloaded += bytesRead

                        // 限制状态更新频率（最多每 200ms 一次）
                        val now = System.currentTimeMillis()
                        if (now - lastUpdateTime >= 200) {
                            lastUpdateTime = now
                            val downloadedMB = String.format("%.1f", bytesDownloaded / 1048576.0)
                            val progress = if (contentLength > 0) {
                                (bytesDownloaded * 100 / contentLength).toInt()
                            } else {
                                -1
                            }
                            _state.value = DownloadState.Downloading(progress, downloadedMB)
                        }
                    }
                }
            }

            connection.disconnect()
            return  // 下载完成
        }

        throw Exception("重定向次数过多 ($maxRedirects)")
    }

    /**
     * 通过 FileProvider 安装 APK
     */
    private fun installApk(context: Context, file: File) {
        if (!file.exists()) {
            _state.value = DownloadState.Failed("APK 文件不存在")
            return
        }

        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )

        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}
