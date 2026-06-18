package com.liferecorder.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface QuietRuleDao {

    @Query("SELECT * FROM quiet_rules ORDER BY startHour, startMinute")
    fun getAllRules(): Flow<List<QuietRule>>

    @Query("SELECT * FROM quiet_rules WHERE enabled = 1")
    suspend fun getEnabledRules(): List<QuietRule>

    @Insert
    suspend fun insert(rule: QuietRule)

    @Update
    suspend fun update(rule: QuietRule)

    @Delete
    suspend fun delete(rule: QuietRule)
}
