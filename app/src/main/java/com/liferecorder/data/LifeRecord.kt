package com.liferecorder.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 生活记录实体，每条记录代表用户在某个时间段做了什么
 */
@Entity(tableName = "life_records")
data class LifeRecord(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val content: String,          // 记录内容：这一小时做了什么
    val timestamp: Long,          // 记录结束时间（毫秒时间戳），兼容旧版作为排序依据
    val startTime: Long = 0,      // 记录起始时间（毫秒时间戳）
    val hourLabel: String         // 时间段标签，例如 "09:00 - 10:00"
)
