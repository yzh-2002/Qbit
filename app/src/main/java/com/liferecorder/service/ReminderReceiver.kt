package com.liferecorder.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * 接收定时闹钟广播，启动前台 AlarmService 触发全屏提醒
 */
class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        // 启动前台服务，由服务通过 Full-Screen Intent 拉起闹钟界面
        AlarmService.start(context)

        // 重新设置下一次闹钟（因为 setExactAndAllowWhileIdle 是一次性的）
        ReminderScheduler.scheduleNextReminder(context)
    }
}
