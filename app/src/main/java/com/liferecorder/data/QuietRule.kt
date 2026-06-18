package com.liferecorder.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 免打扰规则
 * 在指定时间段内不发送提醒通知
 */
@Entity(tableName = "quiet_rules")
data class QuietRule(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,            // 规则名称，如"睡觉时间"
    val startHour: Int,          // 开始小时 (0-23)
    val startMinute: Int,        // 开始分钟 (0-59)
    val endHour: Int,            // 结束小时 (0-23)
    val endMinute: Int,          // 结束分钟 (0-59)
    val applyMode: Int,          // 适用模式: 0=每天, 1=仅工作日, 2=仅周末
    val enabled: Boolean         // 是否启用
)
