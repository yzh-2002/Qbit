package com.liferecorder.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface HolidayCacheDao {

    /** 查询某一天的节假日信息 */
    @Query("SELECT * FROM holiday_cache WHERE date = :date AND country = :country LIMIT 1")
    suspend fun getHoliday(date: String, country: String): HolidayCache?

    /** 查询某年某月的所有节假日 */
    @Query("SELECT * FROM holiday_cache WHERE date LIKE :yearMonth || '-%' AND country = :country")
    suspend fun getHolidaysInMonth(yearMonth: String, country: String): List<HolidayCache>

    /** 批量插入或更新 */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(holidays: List<HolidayCache>)

    /** 删除某国某年的旧数据 */
    @Query("DELETE FROM holiday_cache WHERE year = :year AND country = :country")
    suspend fun deleteByYear(year: Int, country: String)

    /** 查询某国是否有某年的数据 */
    @Query("SELECT COUNT(*) FROM holiday_cache WHERE year = :year AND country = :country")
    suspend fun hasYearData(year: Int, country: String): Int
}
