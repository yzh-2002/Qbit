package com.liferecorder.ui

import android.Manifest
import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.liferecorder.BuildConfig
import com.liferecorder.data.ApkDownloader
import com.liferecorder.data.SettingsManager
import com.liferecorder.data.UpdateChecker
import com.liferecorder.service.ReminderScheduler
import com.liferecorder.ui.theme.LifeRecorderTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* 用户授权结果 */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // 请求通知权限（Android 13+）
        requestNotificationPermission()

        // 请求精确闹钟权限（Android 12+）
        requestExactAlarmPermission()

        // 设置每小时提醒
        ReminderScheduler.scheduleNextReminder(this)

        setContent {
            val vm: MainViewModel = viewModel()
            val themeMode by vm.themeMode.collectAsStateWithLifecycle()
            val darkTheme = when (themeMode) {
                SettingsManager.THEME_LIGHT -> false
                SettingsManager.THEME_DARK -> true
                else -> isSystemInDarkTheme()
            }

            LifeRecorderTheme(darkTheme = darkTheme) {

                // 如果从通知点进来，自动弹出输入框
                if (intent.getBooleanExtra("show_input", false)) {
                    vm.openInputDialog()
                    intent.removeExtra("show_input")
                }

                // 检查更新
                var updateInfo by remember { mutableStateOf<UpdateChecker.UpdateInfo?>(null) }
                LaunchedEffect(Unit) {
                    updateInfo = UpdateChecker.checkUpdate(BuildConfig.VERSION_NAME)
                }

                // 下载状态
                val downloadState by ApkDownloader.state.collectAsStateWithLifecycle()
                val coroutineScope = rememberCoroutineScope()

                // 更新提示弹窗
                updateInfo?.let { info ->
                    AlertDialog(
                        onDismissRequest = { updateInfo = null },
                        title = {
                            Text("发现新版本", fontWeight = FontWeight.Bold)
                        },
                        text = {
                            Text("Qbit ${info.tagName} 已发布，当前版本 v${BuildConfig.VERSION_NAME}。\n\n${info.releaseNotes.take(200)}")
                        },
                        confirmButton = {
                            TextButton(onClick = {
                                if (info.apkDownloadUrl != null) {
                                    coroutineScope.launch {
                                        ApkDownloader.download(
                                            this@MainActivity,
                                            info.apkDownloadUrl,
                                            info.tagName
                                        )
                                    }
                                } else {
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(info.downloadUrl))
                                    startActivity(intent)
                                }
                                updateInfo = null
                            }) {
                                Text(if (info.apkDownloadUrl != null) "下载更新" else "前往下载")
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { updateInfo = null }) {
                                Text("稍后再说")
                            }
                        }
                    )
                }

                // 下载进度弹窗
                when (val state = downloadState) {
                    is ApkDownloader.DownloadState.Downloading -> {
                        AlertDialog(
                            onDismissRequest = { /* 下载中不可关闭 */ },
                            title = {
                                Text("正在下载", fontWeight = FontWeight.Bold)
                            },
                            text = {
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    if (state.progress >= 0) {
                                        // 确定进度
                                        LinearProgressIndicator(
                                            progress = { state.progress / 100f },
                                            modifier = Modifier.fillMaxWidth(),
                                            color = MaterialTheme.colorScheme.primary,
                                            trackColor = MaterialTheme.colorScheme.surfaceVariant
                                        )
                                        Spacer(modifier = Modifier.height(12.dp))
                                        Text(
                                            text = "${state.progress}%",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    } else {
                                        // 总大小未知，显示不确定进度条
                                        LinearProgressIndicator(
                                            modifier = Modifier.fillMaxWidth(),
                                            color = MaterialTheme.colorScheme.primary,
                                            trackColor = MaterialTheme.colorScheme.surfaceVariant
                                        )
                                        Spacer(modifier = Modifier.height(12.dp))
                                        Text(
                                            text = "已下载 ${state.downloadedMB} MB",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            },
                            confirmButton = {}
                        )
                    }
                    is ApkDownloader.DownloadState.Installing -> {
                        AlertDialog(
                            onDismissRequest = { },
                            title = {
                                Text("下载完成", fontWeight = FontWeight.Bold)
                            },
                            text = {
                                Text("正在打开安装程序...")
                            },
                            confirmButton = {}
                        )
                    }
                    is ApkDownloader.DownloadState.Failed -> {
                        AlertDialog(
                            onDismissRequest = { ApkDownloader.reset() },
                            title = {
                                Text("下载失败", fontWeight = FontWeight.Bold)
                            },
                            text = {
                                Text(state.message)
                            },
                            confirmButton = {
                                TextButton(onClick = { ApkDownloader.reset() }) {
                                    Text("知道了")
                                }
                            }
                        )
                    }
                    else -> { /* Idle，不显示 */ }
                }

                MainScreen(viewModel = vm)
            }
        }
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this, Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    private fun requestExactAlarmPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
            if (!alarmManager.canScheduleExactAlarms()) {
                val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                    data = Uri.parse("package:$packageName")
                }
                startActivity(intent)
            }
        }
    }
}
