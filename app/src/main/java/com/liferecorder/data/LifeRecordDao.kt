package com.liferecorder.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface LifeRecordDao {

    /** 按时间倒序获取某一天的所有记录 */
    @Query("SELECT * FROM life_records WHERE timestamp >= :dayStart AND timestamp < :dayEnd ORDER BY timestamp DESC")
    fun getRecordsByDay(dayStart: Long, dayEnd: Long): Flow<List<LifeRecord>>

    /** 获取某一天的记录数量（用于日历标记） */
    @Query("SELECT COUNT(*) FROM life_records WHERE timestamp >= :dayStart AND timestamp < :dayEnd")
    suspend fun getRecordCountByDay(dayStart: Long, dayEnd: Long): Int

    /** 获取某月内每天的记录数，返回时间戳列表（用于日历页标记哪些天有记录） */
    @Query("SELECT timestamp FROM life_records WHERE timestamp >= :monthStart AND timestamp < :monthEnd")
    suspend fun getTimestampsInRange(monthStart: Long, monthEnd: Long): List<Long>

    /** 获取今天的记录数量 */
    @Query("SELECT COUNT(*) FROM life_records WHERE timestamp >= :dayStart AND timestamp < :dayEnd")
    fun getRecordCountByDayFlow(dayStart: Long, dayEnd: Long): Flow<Int>

    /** 插入一条记录 */
    @Insert
    suspend fun insert(record: LifeRecord)

    /** 删除一条记录 */
    @Delete
    suspend fun delete(record: LifeRecord)

    /** 更新一条记录 */
    @Update
    suspend fun update(record: LifeRecord)
}
