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
        private const val KEY_THEME_MODE = "theme_mode"
        const val DEFAULT_INTERVAL = 60 // 默认 60 分钟

        // 主题模式：0=跟随系统, 1=光态（浅色）, 2=暗态（深色）
        const val THEME_FOLLOW_SYSTEM = 0
        const val THEME_LIGHT = 1
        const val THEME_DARK = 2

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

    private val _themeMode = MutableStateFlow(
        prefs.getInt(KEY_THEME_MODE, THEME_FOLLOW_SYSTEM)
    )
    val themeMode: StateFlow<Int> = _themeMode.asStateFlow()

    fun setIntervalMinutes(minutes: Int) {
        prefs.edit().putInt(KEY_REMINDER_INTERVAL, minutes).apply()
        _intervalMinutes.value = minutes
    }

    fun setThemeMode(mode: Int) {
        prefs.edit().putInt(KEY_THEME_MODE, mode).apply()
        _themeMode.value = mode
    }
}
