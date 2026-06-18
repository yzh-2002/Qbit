package com.liferecorder.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.liferecorder.data.*
import com.liferecorder.service.ReminderScheduler
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getDatabase(application)
    private val recordDao = db.lifeRecordDao()
    private val ruleDao = db.quietRuleDao()
    private val settings = SettingsManager.getInstance(application)
    private val holidayManager = HolidayManager(application)

    // ======================== 今日记录 ========================

    val todayRecords: StateFlow<List<LifeRecord>> = run {
        val (start, end) = todayRange()
        recordDao.getRecordsByDay(start, end)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _showInputDialog = MutableStateFlow(false)
    val showInputDialog: StateFlow<Boolean> = _showInputDialog.asStateFlow()

    fun openInputDialog() { _showInputDialog.value = true }
    fun closeInputDialog() { _showInputDialog.value = false }

    fun addRecord(content: String) {
        if (content.isBlank()) return
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val cal = Calendar.getInstance()
            val currentHour = cal.get(Calendar.HOUR_OF_DAY)
            val interval = settings.intervalMinutes.value
            val prevHour = if (interval >= 60) {
                val hoursBack = interval / 60
                if (currentHour - hoursBack < 0) 0 else currentHour - hoursBack
            } else currentHour
            val hourLabel = String.format("%02d:00 - %02d:00", prevHour, currentHour)
            recordDao.insert(LifeRecord(content = content.trim(), timestamp = now, hourLabel = hourLabel))
            _showInputDialog.value = false
        }
    }

    fun deleteRecord(record: LifeRecord) {
        viewModelScope.launch { recordDao.delete(record) }
    }

    // ======================== 历史日历 ========================

    private val _calendarYearMonth = MutableStateFlow(
        Calendar.getInstance().let { it.get(Calendar.YEAR) * 100 + it.get(Calendar.MONTH) }
    )
    val calendarYearMonth: StateFlow<Int> = _calendarYearMonth.asStateFlow()

    private val _daysWithRecords = MutableStateFlow<Set<Int>>(emptySet())
    val daysWithRecords: StateFlow<Set<Int>> = _daysWithRecords.asStateFlow()

    private val _monthHolidays = MutableStateFlow<Map<String, HolidayCache>>(emptyMap())
    val monthHolidays: StateFlow<Map<String, HolidayCache>> = _monthHolidays.asStateFlow()

    private val _holidayUnavailableHint = MutableStateFlow<String?>(null)
    val holidayUnavailableHint: StateFlow<String?> = _holidayUnavailableHint.asStateFlow()

    private val _isLoadingHolidays = MutableStateFlow(false)
    val isLoadingHolidays: StateFlow<Boolean> = _isLoadingHolidays.asStateFlow()

    private val _selectedHistoryDate = MutableStateFlow<Long?>(null)
    val selectedHistoryDate: StateFlow<Long?> = _selectedHistoryDate.asStateFlow()

    val selectedDayRecords: StateFlow<List<LifeRecord>> = _selectedHistoryDate
        .filterNotNull()
        .flatMapLatest { dayStart ->
            val dayEnd = dayStart + 24 * 60 * 60 * 1000L
            recordDao.getRecordsByDay(dayStart, dayEnd)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        // 监听日历年月变化，刷新记录和节假日数据
        viewModelScope.launch {
            _calendarYearMonth.collectLatest { ym ->
                val year = ym / 100
                val month = ym % 100
                loadDaysWithRecords(year, month)
                loadMonthHolidays(year, month)
            }
        }
        // App 启动时自动拉取当年节假日数据
        viewModelScope.launch {
            val year = Calendar.getInstance().get(Calendar.YEAR)
            if (holidayManager.needsRefresh(year)) {
                holidayManager.fetchAndCache(year)
            }
        }
    }

    private suspend fun loadDaysWithRecords(year: Int, month: Int) {
        val cal = Calendar.getInstance().apply {
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, month)
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val monthStart = cal.timeInMillis
        cal.add(Calendar.MONTH, 1)
        val monthEnd = cal.timeInMillis

        val timestamps = recordDao.getTimestampsInRange(monthStart, monthEnd)
        val days = timestamps.map { ts ->
            Calendar.getInstance().apply { timeInMillis = ts }.get(Calendar.DAY_OF_MONTH)
        }.toSet()
        _daysWithRecords.value = days
    }

    private suspend fun loadMonthHolidays(year: Int, month: Int) {
        // 如果该年份尚未缓存，先自动拉取
        if (holidayManager.needsRefresh(year)) {
            _isLoadingHolidays.value = true
            holidayManager.fetchAndCache(year)
            _isLoadingHolidays.value = false
        }
        // 拉取后仍无数据，说明该年节假日尚未发布
        if (holidayManager.needsRefresh(year)) {
            _monthHolidays.value = emptyMap()
            _holidayUnavailableHint.value = "${year}年节假日数据暂未发布"
            return
        }
        _holidayUnavailableHint.value = null
        val holidays = holidayManager.getHolidaysInMonth(year, month)
        _monthHolidays.value = holidays.associateBy { it.date }
    }

    fun previousMonth() {
        val ym = _calendarYearMonth.value
        val year = ym / 100
        val month = ym % 100
        _calendarYearMonth.value = if (month == 0) (year - 1) * 100 + 11 else year * 100 + (month - 1)
    }

    fun nextMonth() {
        val ym = _calendarYearMonth.value
        val year = ym / 100
        val month = ym % 100
        _calendarYearMonth.value = if (month == 11) (year + 1) * 100 else year * 100 + (month + 1)
    }

    fun selectDate(year: Int, month: Int, day: Int) {
        val cal = Calendar.getInstance().apply {
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, month)
            set(Calendar.DAY_OF_MONTH, day)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        _selectedHistoryDate.value = cal.timeInMillis
    }

    fun clearSelectedDate() {
        _selectedHistoryDate.value = null
    }

    suspend fun getHolidayInfo(date: String): HolidayCache? {
        return holidayManager.getHoliday(date)
    }

    fun formatDate(timestamp: Long): String {
        return SimpleDateFormat("yyyy年M月d日 EEEE", Locale.CHINESE).format(Date(timestamp))
    }

    fun formatIsoDate(timestamp: Long): String {
        return SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(timestamp))
    }

    fun formatYearMonth(ym: Int): String {
        val year = ym / 100
        val month = ym % 100 + 1
        return "${year}年${month}月"
    }

    // ======================== 设置 ========================

    val intervalMinutes: StateFlow<Int> = settings.intervalMinutes
    val themeMode: StateFlow<Int> = settings.themeMode

    fun setThemeMode(mode: Int) {
        settings.setThemeMode(mode)
    }

    val quietRules: StateFlow<List<QuietRule>> = ruleDao.getAllRules()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _isRefreshingHolidays = MutableStateFlow(false)
    val isRefreshingHolidays: StateFlow<Boolean> = _isRefreshingHolidays.asStateFlow()

    fun setInterval(minutes: Int) {
        settings.setIntervalMinutes(minutes)
        ReminderScheduler.cancelReminder(getApplication())
        ReminderScheduler.scheduleNextReminder(getApplication())
    }

    fun refreshHolidays() {
        viewModelScope.launch {
            _isRefreshingHolidays.value = true
            val year = Calendar.getInstance().get(Calendar.YEAR)
            val success = holidayManager.fetchAndCache(year)
            if (success) {
                val ym = _calendarYearMonth.value
                loadMonthHolidays(ym / 100, ym % 100)
            }
            _isRefreshingHolidays.value = false
        }
    }

    fun addQuietRule(rule: QuietRule) {
        viewModelScope.launch {
            ruleDao.insert(rule)
            reschedule()
        }
    }

    fun updateQuietRule(rule: QuietRule) {
        viewModelScope.launch {
            ruleDao.update(rule)
            reschedule()
        }
    }

    fun deleteQuietRule(rule: QuietRule) {
        viewModelScope.launch {
            ruleDao.delete(rule)
            reschedule()
        }
    }

    fun toggleQuietRule(rule: QuietRule) {
        viewModelScope.launch {
            ruleDao.update(rule.copy(enabled = !rule.enabled))
            reschedule()
        }
    }

    private fun reschedule() {
        val app = getApplication<Application>()
        ReminderScheduler.cancelReminder(app)
        ReminderScheduler.scheduleNextReminder(app)
    }

    // ======================== 工具方法 ========================

    private fun todayRange(): Pair<Long, Long> {
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val start = cal.timeInMillis
        cal.add(Calendar.DAY_OF_MONTH, 1)
        return start to cal.timeInMillis
    }
}
