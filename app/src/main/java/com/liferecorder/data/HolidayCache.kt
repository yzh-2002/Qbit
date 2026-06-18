package com.liferecorder.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 节假日缓存数据
 * 从网络 API 拉取后存储到本地，支持离线查询
 */
@Entity(
    tableName = "holiday_cache",
    indices = [Index(value = ["country", "year"], name = "idx_holiday_country_year")]
)
data class HolidayCache(
    @PrimaryKey
    val date: String,            // 日期格式 "yyyy-MM-dd"
    val type: Int,             // 0=工作日, 1=周末, 2=节日, 3=调休补班
    val name: String,          // 节日名称或描述，如"国庆节"、"国庆节前补班"
    val country: String,       // 国家代码，如 "CN"、"US"
    val year: Int,             // 年份，方便按年清理缓存
    @ColumnInfo(defaultValue = "0")
    val cachedAt: Long = System.currentTimeMillis()
)
