package com.liferecorder.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * 开机自启动，重新注册闹钟提醒
 * （手机重启后 AlarmManager 的闹钟会被清除，需要重新设置）
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action == Intent.ACTION_BOOT_COMPLETED) {
            ReminderScheduler.scheduleNextReminder(context)
        }
    }
}
