package com.liferecorder.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface LifeRecordDao {

    /** 按时间倒序获取某一天的所有记录（含跨天记录：startTime 或 endTime 落在当天范围内） */
    @Query("SELECT * FROM life_records WHERE startTime < :dayEnd AND timestamp >= :dayStart ORDER BY timestamp DESC")
    fun getRecordsByDay(dayStart: Long, dayEnd: Long): Flow<List<LifeRecord>>

    /** 获取某一天的记录数量（用于日历标记，含跨天记录） */
    @Query("SELECT COUNT(*) FROM life_records WHERE startTime < :dayEnd AND timestamp >= :dayStart")
    suspend fun getRecordCountByDay(dayStart: Long, dayEnd: Long): Int

    /** 获取某月内涉及的记录（含跨天），返回 startTime 和 timestamp 用于判断哪些天有记录 */
    @Query("SELECT * FROM life_records WHERE startTime < :monthEnd AND timestamp >= :monthStart")
    suspend fun getRecordsInRange(monthStart: Long, monthEnd: Long): List<LifeRecord>

    /** 获取今天的记录数量（含跨天记录） */
    @Query("SELECT COUNT(*) FROM life_records WHERE startTime < :dayEnd AND timestamp >= :dayStart")
    fun getRecordCountByDayFlow(dayStart: Long, dayEnd: Long): Flow<Int>

    /** 获取某天最新的一条记录（用于计算下条记录的起始时间） */
    @Query("SELECT * FROM life_records WHERE timestamp >= :dayStart AND timestamp < :dayEnd ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLatestRecordOfDay(dayStart: Long, dayEnd: Long): LifeRecord?

    /** 获取全局最新的一条记录（按结束时间倒序） */
    @Query("SELECT * FROM life_records ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLatestRecord(): LifeRecord?

    /** 检查某个精确结束时间是否已存在记录（用于避免重复插入免打扰记录） */
    @Query("SELECT COUNT(*) FROM life_records WHERE startTime = :startTime AND timestamp = :endTime")
    suspend fun countByTimeRange(startTime: Long, endTime: Long): Int

    /** 同步获取某天的记录（用于 AI 上下文构建，含跨天记录） */
    @Query("SELECT * FROM life_records WHERE startTime < :dayEnd AND timestamp >= :dayStart ORDER BY startTime ASC")
    suspend fun getRecordsByDaySync(dayStart: Long, dayEnd: Long): List<LifeRecord>

    /** 插入一条记录 */
    @Insert
    suspend fun insert(record: LifeRecord)

    /** 获取所有记录（用于导出） */
    @Query("SELECT * FROM life_records ORDER BY timestamp ASC")
    suspend fun getAllRecords(): List<LifeRecord>

    /** 删除一条记录 */
    @Delete
    suspend fun delete(record: LifeRecord)

    /** 更新一条记录 */
    @Update
    suspend fun update(record: LifeRecord)
}
