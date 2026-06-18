package com.liferecorder.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.*

/**
 * 节假日数据管理器
 * 数据来源：https://github.com/NateScarlet/holiday-cn（国务院官方数据）
 * 通过 jsdelivr CDN 访问，稳定可靠
 */
class HolidayManager(private val context: Context) {

    private val db = AppDatabase.getDatabase(context)
    private val dao = db.holidayCacheDao()
    companion object {
        private const val COUNTRY = "CN"
        /** holiday-cn 通过 jsdelivr CDN 获取，不会被 Cloudflare 拦截 */
        private const val HOLIDAY_CN_BASE =
            "https://cdn.jsdelivr.net/gh/NateScarlet/holiday-cn@master"
    }

    /**
     * 拉取并缓存某年的节假日数据
     * 返回是否成功
     */
    suspend fun fetchAndCache(year: Int): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val url = URL("$HOLIDAY_CN_BASE/$year.json")
                val connection = url.openConnection() as HttpURLConnection
                connection.apply {
                    requestMethod = "GET"
                    connectTimeout = 15000
                    readTimeout = 15000
                    setRequestProperty("Accept", "application/json")
                    // jsdelivr CDN 需要 User-Agent
                    setRequestProperty("User-Agent", "LifeRecorder/1.0")
                }

                val responseCode = connection.responseCode
                if (responseCode != 200) {
                    connection.disconnect()
                    return@withContext false
                }

                val response = connection.inputStream.bufferedReader().use { it.readText() }
                connection.disconnect()

                val json = JSONObject(response)
                val daysArray = json.optJSONArray("days") ?: return@withContext false

                // 解析 days 数组
                val holidays = mutableListOf<HolidayCache>()
                for (i in 0 until daysArray.length()) {
                    val item = daysArray.getJSONObject(i)
                    val date = item.getString("date")       // "2026-01-01"
                    val name = item.getString("name")       // "元旦"
                    val isOffDay = item.getBoolean("isOffDay") // true=放假, false=调休补班

                    // type: 2=节日放假, 3=调休补班
                    val type = if (isOffDay) 2 else 3

                    holidays.add(HolidayCache(
                        date = date,
                        type = type,
                        name = name,
                        country = COUNTRY,
                        year = year
                    ))
                }

                // 先删除旧数据再插入新数据
                dao.deleteByYear(year, COUNTRY)
                dao.insertAll(holidays)

                true
            } catch (e: Exception) {
                e.printStackTrace()
                false
            }
        }
    }

    /**
     * 检查某年数据是否需要刷新（没有缓存数据）
     */
    suspend fun needsRefresh(year: Int): Boolean {
        return withContext(Dispatchers.IO) {
            val count = dao.hasYearData(year, COUNTRY)
            count == 0
        }
    }

    /**
     * 获取某天的节假日信息（走本地缓存）
     */
    suspend fun getHoliday(date: String): HolidayCache? {
        return withContext(Dispatchers.IO) {
            dao.getHoliday(date, COUNTRY)
        }
    }

    /**
     * 获取某月的所有节假日
     */
    suspend fun getHolidaysInMonth(year: Int, month: Int): List<HolidayCache> {
        return withContext(Dispatchers.IO) {
            // month 来自 Calendar.MONTH，是 0-based，需要 +1
            val yearMonth = String.format("%04d-%02d", year, month + 1)
            dao.getHolidaysInMonth(yearMonth, COUNTRY)
        }
    }

    /**
     * 判断某天是否为工作日（结合节假日缓存）
     * 工作日 = 非周末且非节日，或者调休补班日
     */
    suspend fun isWorkday(date: String): Boolean {
        val holiday = getHoliday(date) ?: return isNormalWorkday(date)
        return holiday.type == 0 || holiday.type == 3 // 工作日或调休补班都算工作日
    }

    /**
     * 判断某天是否为休息日（结合节假日缓存）
     * 休息日 = 周末或节日，但不包括调休补班
     */
    suspend fun isRestDay(date: String): Boolean {
        val holiday = getHoliday(date) ?: return isNormalWeekend(date)
        return holiday.type == 1 || holiday.type == 2 // 周末或节日
    }

    /** 获取某天的节假日类型和名称 */
    suspend fun getDayInfo(date: String): Pair<Int, String> {
        val holiday = getHoliday(date)
        return if (holiday != null) {
            holiday.type to holiday.name
        } else {
            val cal = parseDate(date)
            val dayOfWeek = cal.get(Calendar.DAY_OF_WEEK)
            if (dayOfWeek == Calendar.SATURDAY || dayOfWeek == Calendar.SUNDAY) {
                1 to "周末"
            } else {
                0 to "工作日"
            }
        }
    }

    // ===== 私有工具方法 =====

    private fun isNormalWorkday(date: String): Boolean {
        val cal = parseDate(date)
        val dayOfWeek = cal.get(Calendar.DAY_OF_WEEK)
        return dayOfWeek != Calendar.SATURDAY && dayOfWeek != Calendar.SUNDAY
    }

    private fun isNormalWeekend(date: String): Boolean {
        val cal = parseDate(date)
        val dayOfWeek = cal.get(Calendar.DAY_OF_WEEK)
        return dayOfWeek == Calendar.SATURDAY || dayOfWeek == Calendar.SUNDAY
    }

    private fun parseDate(date: String): Calendar {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        return Calendar.getInstance().apply { time = sdf.parse(date)!! }
    }
}
