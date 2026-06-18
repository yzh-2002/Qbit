package com.liferecorder.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 管理 App 设置项（提醒间隔等）
 */
class SettingsManager(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("life_recorder_settings", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_REMINDER_INTERVAL = "reminder_interval_minutes"
        const val DEFAULT_INTERVAL = 60 // 默认 60 分钟

        @Volatile
        private var INSTANCE: SettingsManager? = null

        fun getInstance(context: Context): SettingsManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: SettingsManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    private val _intervalMinutes = MutableStateFlow(
        prefs.getInt(KEY_REMINDER_INTERVAL, DEFAULT_INTERVAL)
    )
    val intervalMinutes: StateFlow<Int> = _intervalMinutes.asStateFlow()

    fun setIntervalMinutes(minutes: Int) {
        prefs.edit().putInt(KEY_REMINDER_INTERVAL, minutes).apply()
        _intervalMinutes.value = minutes
    }
}
