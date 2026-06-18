package com.liferecorder.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.liferecorder.ui.AlarmActivity

/**
 * 接收定时闹钟广播，启动全屏闹钟提醒界面
 */
class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        // 启动全屏闹钟界面
        val alarmIntent = Intent(context, AlarmActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        context.startActivity(alarmIntent)

        // 重新设置下一次闹钟（因为 setExactAndAllowWhileIdle 是一次性的）
        ReminderScheduler.scheduleNextReminder(context)
    }
}
