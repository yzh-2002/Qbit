package com.liferecorder.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.liferecorder.data.AppDatabase
import com.liferecorder.data.LifeRecord
import kotlinx.coroutines.runBlocking
import java.text.SimpleDateFormat
import java.util.*

/**
 * 在免打扰时段结束时刻触发，自动插入一条以规则名称为内容的记录。
 * 由 ReminderScheduler 在跳过免打扰时段时注册对应的一次性闹钟。
 */
class QuietEndReceiver : BroadcastReceiver() {

    companion object {
        const val EXTRA_RULE_NAME = "rule_name"
        const val EXTRA_START_HOUR = "start_hour"
        const val EXTRA_START_MINUTE = "start_minute"
        const val EXTRA_END_HOUR = "end_hour"
        const val EXTRA_END_MINUTE = "end_minute"
    }

    override fun onReceive(context: Context, intent: Intent?) {
        val ruleName = intent?.getStringExtra(EXTRA_RULE_NAME) ?: return
        val startHour = intent.getIntExtra(EXTRA_START_HOUR, -1)
        val startMinute = intent.getIntExtra(EXTRA_START_MINUTE, -1)
        val endHour = intent.getIntExtra(EXTRA_END_HOUR, -1)
        val endMinute = intent.getIntExtra(EXTRA_END_MINUTE, -1)

        if (startHour < 0 || startMinute < 0 || endHour < 0 || endMinute < 0) return

        runBlocking {
            try {
                insertQuietPeriodRecord(context, ruleName, startHour, startMinute, endHour, endMinute)
            } catch (_: Exception) {
                // 静默失败，不影响正常流程
            }
        }
    }

    private suspend fun insertQuietPeriodRecord(
        context: Context,
        ruleName: String,
        startHour: Int,
        startMinute: Int,
        endHour: Int,
        endMinute: Int
    ) {
        val db = AppDatabase.getDatabase(context)
        val now = Calendar.getInstance()

        // 计算记录的起止时间
        val ruleStartMinutes = startHour * 60 + startMinute
        val ruleEndMinutes = endHour * 60 + endMinute
        val isCrossDay = ruleStartMinutes > ruleEndMinutes

        val recordStart = Calendar.getInstance().apply {
            timeInMillis = now.timeInMillis
            set(Calendar.HOUR_OF_DAY, startHour)
            set(Calendar.MINUTE, startMinute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (isCrossDay) {
                // 跨天规则：开始时间在昨天
                add(Calendar.DAY_OF_MONTH, -1)
            }
        }.timeInMillis

        val recordEnd = Calendar.getInstance().apply {
            timeInMillis = now.timeInMillis
            set(Calendar.HOUR_OF_DAY, endHour)
            set(Calendar.MINUTE, endMinute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        // 检查是否已经为这个时段插入过记录（避免重复）
        val existingCount = db.lifeRecordDao().countByTimeRange(recordStart, recordEnd)
        if (existingCount > 0) return

        val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
        val hourLabel = "${timeFormat.format(Date(recordStart))} - ${timeFormat.format(Date(recordEnd))}"

        db.lifeRecordDao().insert(
            LifeRecord(
                content = ruleName,
                timestamp = recordEnd,
                startTime = recordStart,
                hourLabel = hourLabel
            )
        )
    }
}
