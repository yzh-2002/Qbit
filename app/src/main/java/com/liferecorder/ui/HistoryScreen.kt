package com.liferecorder.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.liferecorder.data.HolidayCache
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(viewModel: MainViewModel) {
    val selectedDate by viewModel.selectedHistoryDate.collectAsStateWithLifecycle()

    // 在详情页时，拦截返回键，返回到日历页面而非退出应用
    BackHandler(enabled = selectedDate != null) {
        viewModel.clearSelectedDate()
    }

    if (selectedDate != null) {
        DayDetailScreen(viewModel, selectedDate!!)
    } else {
        CalendarScreen(viewModel)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CalendarScreen(viewModel: MainViewModel) {
    val yearMonth by viewModel.calendarYearMonth.collectAsStateWithLifecycle()
    val daysWithRecords by viewModel.daysWithRecords.collectAsStateWithLifecycle()
    val monthHolidays by viewModel.monthHolidays.collectAsStateWithLifecycle()
    val holidayHint by viewModel.holidayUnavailableHint.collectAsStateWithLifecycle()
    val isLoadingHolidays by viewModel.isLoadingHolidays.collectAsStateWithLifecycle()

    val year = yearMonth / 100
    val month = yearMonth % 100

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text("历史记录", fontWeight = FontWeight.Bold)
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // 月份切换栏
            MonthSelector(
                monthText = viewModel.formatYearMonth(yearMonth),
                onPrevious = { viewModel.previousMonth() },
                onNext = { viewModel.nextMonth() }
            )

            // 图例
            HolidayLegend()

            // 星期标题
            WeekDayHeader()

            // 日历网格（加载时显示 loading 遮罩）
            Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                CalendarGrid(
                    year = year,
                    month = month,
                    daysWithRecords = daysWithRecords,
                    holidays = monthHolidays,
                    onDayClick = { day -> viewModel.selectDate(year, month, day) }
                )

                if (isLoadingHolidays) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.7f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(32.dp),
                                strokeWidth = 3.dp
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "正在获取节假日数据...",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // 节假日数据不可用提示
            holidayHint?.let { hint ->
                Text(
                    text = hint,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun HolidayLegend() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        LegendItem(color = MaterialTheme.colorScheme.error.copy(alpha = 0.15f), label = "节日")
        LegendItem(color = Color(0xFF4A90D9).copy(alpha = 0.15f), label = "调休")
        LegendItem(color = MaterialTheme.colorScheme.surfaceVariant, label = "周末")
    }
}

@Composable
private fun LegendItem(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(color)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun MonthSelector(
    monthText: String,
    onPrevious: () -> Unit,
    onNext: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(onClick = onPrevious) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "上个月")
            }
            Text(
                text = monthText,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            IconButton(onClick = onNext) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "下个月")
            }
        }
    }
}

@Composable
private fun WeekDayHeader() {
    val weekDays = listOf("日", "一", "二", "三", "四", "五", "六")
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 8.dp)
    ) {
        weekDays.forEach { day ->
            Text(
                text = day,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Medium
            )
        }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
}

@Composable
private fun CalendarGrid(
    year: Int,
    month: Int,
    daysWithRecords: Set<Int>,
    holidays: Map<String, HolidayCache>,
    onDayClick: (Int) -> Unit
) {
    val cal = Calendar.getInstance().apply {
        set(Calendar.YEAR, year)
        set(Calendar.MONTH, month)
        set(Calendar.DAY_OF_MONTH, 1)
    }
    val firstDayOfWeek = cal.get(Calendar.DAY_OF_WEEK) - 1
    val daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH)

    val today = Calendar.getInstance()
    val isCurrentMonth = today.get(Calendar.YEAR) == year && today.get(Calendar.MONTH) == month
    val todayDay = today.get(Calendar.DAY_OF_MONTH)

    LazyVerticalGrid(
        columns = GridCells.Fixed(7),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
        contentPadding = PaddingValues(vertical = 4.dp)
    ) {
        items(firstDayOfWeek) {
            Box(modifier = Modifier.aspectRatio(1f))
        }
        items(daysInMonth) { index ->
            val day = index + 1
            val dateStr = String.format("%04d-%02d-%02d", year, month + 1, day)
            val holiday = holidays[dateStr]
            val hasRecord = day in daysWithRecords
            val isToday = isCurrentMonth && day == todayDay
            // 计算这一天是星期几（网格位置 = firstDayOfWeek + index，对 7 取模，0=周日, 6=周六）
            val dayOfWeek = (firstDayOfWeek + index) % 7
            val isWeekend = dayOfWeek == 0 || dayOfWeek == 6

            DayCell(
                day = day,
                holiday = holiday,
                hasRecord = hasRecord,
                isToday = isToday,
                isWeekend = isWeekend,
                onClick = { onDayClick(day) }
            )
        }
    }
}

@Composable
private fun DayCell(
    day: Int,
    holiday: HolidayCache?,
    hasRecord: Boolean,
    isToday: Boolean,
    isWeekend: Boolean,
    onClick: () -> Unit
) {
    // 根据节假日类型决定背景色
    // 优先级：节日 > 调休补班 > 普通周末
    val backgroundColor = when {
        holiday?.type == 2 -> MaterialTheme.colorScheme.error.copy(alpha = 0.12f) // 节日（法定假日）
        holiday?.type == 3 -> Color(0xFF4A90D9).copy(alpha = 0.12f) // 调休补班
        isWeekend -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f) // 普通周末
        else -> Color.Transparent // 工作日
    }

    val textColor = when {
        holiday?.type == 2 -> MaterialTheme.colorScheme.error // 节日红色
        holiday?.type == 3 -> Color(0xFF4A90D9) // 调休补班蓝色
        isWeekend -> MaterialTheme.colorScheme.onSurfaceVariant // 周末灰色
        else -> MaterialTheme.colorScheme.onSurface
    }

    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .padding(2.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(backgroundColor)
            .then(
                if (isToday) Modifier.background(
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                ) else Modifier
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "$day",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                color = textColor
            )
            // 节日名称缩写或调休/休标记
            when {
                holiday?.type == 2 -> Text(
                    text = holiday.name.take(2),
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp),
                    color = MaterialTheme.colorScheme.error,
                    maxLines = 1
                )
                holiday?.type == 3 -> Text(
                    text = "班",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp),
                    color = Color(0xFF4A90D9),
                    maxLines = 1
                )
                isWeekend -> Text(
                    text = "休",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
                else -> Spacer(modifier = Modifier.size(10.dp))
            }
            // 有记录的标记点
            if (hasRecord) {
                Box(
                    modifier = Modifier
                        .size(5.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                )
            } else {
                Spacer(modifier = Modifier.size(5.dp))
            }
        }
    }
}

// ======================== 日期详情页 ========================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DayDetailScreen(viewModel: MainViewModel, dateTimestamp: Long) {
    val records by viewModel.selectedDayRecords.collectAsStateWithLifecycle()
    val dateStr = viewModel.formatDate(dateTimestamp)
    val isoDate = viewModel.formatIsoDate(dateTimestamp)
    val holidayInfo = remember { mutableStateOf<HolidayCache?>(null) }

    LaunchedEffect(isoDate) {
        holidayInfo.value = viewModel.getHolidayInfo(isoDate)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(dateStr)
                        holidayInfo.value?.let { h ->
                            Text(
                                h.name,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (h.type == 2) MaterialTheme.colorScheme.error
                                else MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { viewModel.clearSelectedDate() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { padding ->
        if (records.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("📭", fontSize = 48.sp)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "这一天没有记录",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Text(
                        "共 ${records.size} 条记录",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                }
                items(records, key = { it.id }) { record ->
                    RecordCard(record = record, onDelete = { viewModel.deleteRecord(record) })
                }
            }
        }
    }
}
