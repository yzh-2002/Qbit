package com.liferecorder.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.liferecorder.R
import com.liferecorder.ui.AlarmActivity

/**
 * 前台服务，用于在后台触发全屏闹钟界面。
 *
 * 铃声和振动在此服务中播放，不依赖 AlarmActivity 是否被拉起。
 * 当用户点击通知或 AlarmActivity 启动后，调用 stop() 停止服务和铃声。
 */
class AlarmService : Service() {

    companion object {
        // 使用新 Channel ID，避免旧 Channel 缓存的静音设置
        const val CHANNEL_ID = "qbit_alarm_channel_v2"
        const val NOTIFICATION_ID = 3001

        fun start(context: Context) {
            val intent = Intent(context, AlarmService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, AlarmService::class.java)
            context.stopService(intent)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = buildAlarmNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        // 铃声和振动由通知 Channel 自动处理，无需手动播放
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // TODO: 后续版本（用户基本升级后）移除此行旧 Channel 清理代码
            val manager = getSystemService(NotificationManager::class.java)
            manager.deleteNotificationChannel("qbit_alarm_channel")

            val alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            val audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()

            val channel = NotificationChannel(
                CHANNEL_ID,
                "记录提醒",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Qbit 定时记录提醒"
                setBypassDnd(true)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                setSound(alarmUri, audioAttributes)
                enableVibration(true)
                vibrationPattern = longArrayOf(500, 800, 500, 800)
            }
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildAlarmNotification(): Notification {
        val fullScreenIntent = Intent(this, AlarmActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val fullScreenPendingIntent = PendingIntent.getActivity(
            this, 0, fullScreenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_edit)
            .setContentTitle("该记录了！")
            .setContentText("过去一段时间你做了什么？点击去记录")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setFullScreenIntent(fullScreenPendingIntent, true)
            .setContentIntent(fullScreenPendingIntent)
            .setAutoCancel(true)
            .setOngoing(true)
            .build()
    }
}
