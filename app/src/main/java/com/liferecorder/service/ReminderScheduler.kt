package com.liferecorder.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.liferecorder.data.AppDatabase
import com.liferecorder.data.QuietRule
import com.liferecorder.data.SettingsManager
import kotlinx.coroutines.runBlocking
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * 负责设置定时提醒，支持自定义间隔和免打扰规则
 */
object ReminderScheduler {

    private const val REQUEST_CODE = 2001

    /** 设置下一次提醒 */
    fun scheduleNextReminder(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, ReminderReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context, REQUEST_CODE, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val intervalMinutes = SettingsManager.getInstance(context).intervalMinutes.value
        val nextTime = calculateNextReminderTime(context, intervalMinutes)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (alarmManager.canScheduleExactAlarms()) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP, nextTime, pendingIntent
                )
            } else {
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP, nextTime, pendingIntent
                )
            }
        } else {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP, nextTime, pendingIntent
            )
        }
    }

    /** 取消提醒 */
    fun cancelReminder(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, ReminderReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context, REQUEST_CODE, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)
    }

    /**
     * 计算下一次提醒时间：
     * 从当前时间往后推 intervalMinutes 分钟，如果落在免打扰时段内，
     * 则跳到该时段结束时间
     */
    private fun calculateNextReminderTime(context: Context, intervalMinutes: Int): Long {
        val rules = runBlocking {
            try {
                AppDatabase.getDatabase(context).quietRuleDao().getEnabledRules()
            } catch (_: Exception) {
                emptyList()
            }
        }

        val candidate = Calendar.getInstance().apply {
            add(Calendar.MINUTE, intervalMinutes)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        return skipQuietPeriods(context, candidate, rules)
    }

    private fun skipQuietPeriods(context: Context, time: Calendar, rules: List<QuietRule>): Long {
        var current = time.clone() as Calendar
        // 最多循环几次防止无限循环
        repeat(10) {
            val matchedRule = findMatchingRule(context, current, rules)
            if (matchedRule == null) {
                return current.timeInMillis
            }
            // 跳到规则结束时间
            current.set(Calendar.HOUR_OF_DAY, matchedRule.endHour)
            current.set(Calendar.MINUTE, matchedRule.endMinute)
            current.set(Calendar.SECOND, 0)
            // 如果结束时间在开始时间之前（跨天规则），需要加一天
            if (matchedRule.endHour < matchedRule.startHour ||
                (matchedRule.endHour == matchedRule.startHour && matchedRule.endMinute <= matchedRule.startMinute)
            ) {
                current.add(Calendar.DAY_OF_MONTH, 1)
            }
        }
        return current.timeInMillis
    }

    private fun findMatchingRule(context: Context, time: Calendar, rules: List<QuietRule>): QuietRule? {
        val dayOfWeek = time.get(Calendar.DAY_OF_WEEK) // 1=Sunday, 7=Saturday
        val isWeekday = dayOfWeek in Calendar.MONDAY..Calendar.FRIDAY
        val isWeekend = !isWeekday
        val dateStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(time.time)

        val hour = time.get(Calendar.HOUR_OF_DAY)
        val minute = time.get(Calendar.MINUTE)
        val timeValue = hour * 60 + minute

        for (rule in rules) {
            // 检查日期模式是否匹配
            val dateMatches = when (rule.applyMode) {
                0 -> true        // 每天
                1 -> {           // 仅工作日（结合节假日缓存）
                    if (isWeekday) {
                        // 检查是否是法定节假日（调休补班算工作日）
                        val holiday = runBlocking {
                            try {
                                AppDatabase.getDatabase(context).holidayCacheDao()
                                    .getHoliday(dateStr, "CN")
                            } catch (_: Exception) { null }
                        }
                        // 调休补班(type=3)算工作日，节日(type=2)和周末(type=1)算休息日
                        holiday == null || holiday.type == 3 || holiday.type == 0
                    } else {
                        // 周六日，检查是否是调休补班
                        val holiday = runBlocking {
                            try {
                                AppDatabase.getDatabase(context).holidayCacheDao()
                                    .getHoliday(dateStr, "CN")
                            } catch (_: Exception) { null }
                        }
                        holiday?.type == 3 // 只有调休补班日算工作日
                    }
                }
                2 -> {           // 仅周末（含法定节假日）
                    if (isWeekend) {
                        true
                    } else {
                        // 工作日，检查是否是法定节假日
                        val holiday = runBlocking {
                            try {
                                AppDatabase.getDatabase(context).holidayCacheDao()
                                    .getHoliday(dateStr, "CN")
                            } catch (_: Exception) { null }
                        }
                        holiday?.type == 2 || holiday?.type == 1 // 节日或周末
                    }
                }
                else -> false
            }
            if (!dateMatches) continue

            val ruleStart = rule.startHour * 60 + rule.startMinute
            val ruleEnd = rule.endHour * 60 + rule.endMinute

            val inRange = if (ruleStart <= ruleEnd) {
                timeValue in ruleStart until ruleEnd
            } else {
                timeValue >= ruleStart || timeValue < ruleEnd
            }

            if (inRange) return rule
        }
        return null
    }
}
