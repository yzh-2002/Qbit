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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.text.font.FontWeight
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.liferecorder.BuildConfig
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
            LifeRecorderTheme {
                val vm: MainViewModel = viewModel()

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
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(info.downloadUrl))
                                startActivity(intent)
                                updateInfo = null
                            }) {
                                Text("前往下载")
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { updateInfo = null }) {
                                Text("稍后再说")
                            }
                        }
                    )
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
