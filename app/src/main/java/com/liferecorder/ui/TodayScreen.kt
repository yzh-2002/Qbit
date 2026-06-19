package com.liferecorder.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.liferecorder.data.LifeRecord
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodayScreen(viewModel: MainViewModel) {
    val records by viewModel.todayRecords.collectAsStateWithLifecycle()
    val showDialog by viewModel.showInputDialog.collectAsStateWithLifecycle()
    val interval by viewModel.intervalMinutes.collectAsStateWithLifecycle()

    var editingRecord by remember { mutableStateOf<LifeRecord?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val todayText = SimpleDateFormat("yyyy年M月d日 EEEE", Locale.CHINESE).format(Date())

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("今日记录", fontWeight = FontWeight.Bold)
                        Text(
                            todayText,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    scope.launch {
                        val lastEnd = viewModel.getLastRecordEndTime()
                        val elapsed = System.currentTimeMillis() - lastEnd
                        if (records.isNotEmpty() && elapsed < 60_000) {
                            snackbarHostState.currentSnackbarData?.dismiss()
                            snackbarHostState.showSnackbar("这一刻才刚开始，让子弹再飞一会儿")
                        } else {
                            viewModel.openInputDialog()
                        }
                    }
                },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Icon(Icons.Default.Add, contentDescription = "添加记录")
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // 今日统计
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    StatItem("已记录", "${records.size} 条")
                    StatItem("提醒间隔", "${interval} 分钟")
                }
            }

            // 记录列表
            if (records.isEmpty()) {
                EmptyState()
            } else {
                RecordList(
                    records = records,
                    onDelete = { viewModel.deleteRecord(it) },
                    onEdit = { editingRecord = it }
                )
            }
        }

        if (showDialog) {
            // 获取上条记录的结束时间
            var lastEndTime by remember { mutableStateOf<Long?>(null) }
            LaunchedEffect(Unit) {
                lastEndTime = viewModel.getLastRecordEndTime()
            }
            lastEndTime?.let { startTime ->
                InputDialog(
                    defaultStartTime = startTime,
                    defaultEndTime = System.currentTimeMillis(),
                    minStartTime = startTime,
                    onDismiss = { viewModel.closeInputDialog() },
                    onConfirm = { content, start, end -> viewModel.addRecord(content, start, end) }
                )
            }
        }

        // 编辑弹窗
        editingRecord?.let { record ->
            // 计算时间约束：前一条记录的结束时间和后一条记录的起始时间
            val sortedRecords = records.sortedBy { it.timestamp }
            val idx = sortedRecords.indexOfFirst { it.id == record.id }
            val minStart = if (idx > 0) sortedRecords[idx - 1].timestamp else {
                Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
                }.timeInMillis
            }
            val maxEnd = if (idx < sortedRecords.size - 1) sortedRecords[idx + 1].startTime else System.currentTimeMillis()

            EditDialog(
                record = record,
                minStartTime = minStart,
                maxEndTime = maxEnd,
                onDismiss = { editingRecord = null },
                onConfirm = { newContent, newStart, newEnd ->
                    viewModel.updateRecord(record, newContent, newStart, newEnd)
                    editingRecord = null
                }
            )
        }
    }
}

@Composable
private fun StatItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun EmptyState() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = "📝", fontSize = 48.sp)
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "今天还没有记录\n点击右下角 + 开始记录吧",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun RecordList(
    records: List<LifeRecord>,
    onDelete: (LifeRecord) -> Unit,
    onEdit: (LifeRecord) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(records, key = { it.id }) { record ->
            RecordCard(
                record = record,
                onDelete = { onDelete(record) },
                onEdit = { onEdit(record) }
            )
        }
    }
}

@Composable
fun RecordCard(
    record: LifeRecord,
    onDelete: () -> Unit,
    onEdit: (() -> Unit)? = null
) {
    var showDeleteDialog by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Text(
                        text = record.hourLabel,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        fontWeight = FontWeight.Medium
                    )
                }
                Text(
                    text = SimpleDateFormat("HH:mm", Locale.getDefault())
                        .format(Date(record.timestamp)),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = record.content,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                if (onEdit != null) {
                    IconButton(onClick = onEdit, modifier = Modifier.size(32.dp)) {
                        Icon(
                            Icons.Default.Edit,
                            contentDescription = "编辑",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                }
                IconButton(onClick = { showDeleteDialog = true }, modifier = Modifier.size(32.dp)) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "删除",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("删除记录", fontWeight = FontWeight.Bold) },
            text = { Text("确定要删除这条记录吗？") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteDialog = false
                    onDelete()
                }) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("取消") }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InputDialog(
    defaultStartTime: Long,
    defaultEndTime: Long,
    minStartTime: Long,
    onDismiss: () -> Unit,
    onConfirm: (String, Long, Long) -> Unit
) {
    var text by remember { mutableStateOf("") }
    var startTime by remember { mutableStateOf(defaultStartTime) }
    var endTime by remember { mutableStateOf(defaultEndTime) }
    var showStartPicker by remember { mutableStateOf(false) }
    var showEndPicker by remember { mutableStateOf(false) }

    val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("记录这段时间", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                // 可点击编辑的时间段
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TimeChip(
                        label = timeFormat.format(Date(startTime)),
                        onClick = { showStartPicker = true }
                    )
                    Text(
                        " - ",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    TimeChip(
                        label = timeFormat.format(Date(endTime)),
                        onClick = { showEndPicker = true }
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("这段时间你做了什么？") },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp),
                    maxLines = 5,
                    shape = RoundedCornerShape(8.dp)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(text, startTime, endTime) },
                enabled = text.isNotBlank() && startTime < endTime
            ) { Text("保存") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )

    // 起始时间选择器
    if (showStartPicker) {
        val cal = Calendar.getInstance().apply { timeInMillis = startTime }
        val pickerState = rememberTimePickerState(
            initialHour = cal.get(Calendar.HOUR_OF_DAY),
            initialMinute = cal.get(Calendar.MINUTE),
            is24Hour = true
        )
        TimePickerDialog(
            title = "选择起始时间",
            onDismiss = { showStartPicker = false },
            onConfirm = {
                val newStart = Calendar.getInstance().apply {
                    timeInMillis = startTime
                    set(Calendar.HOUR_OF_DAY, pickerState.hour)
                    set(Calendar.MINUTE, pickerState.minute)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }.timeInMillis
                // 起始时间不能早于上条记录结束时间
                if (newStart >= minStartTime && newStart < endTime) {
                    startTime = newStart
                }
                showStartPicker = false
            },
            content = { TimePicker(state = pickerState) }
        )
    }

    // 结束时间选择器
    if (showEndPicker) {
        val cal = Calendar.getInstance().apply { timeInMillis = endTime }
        val pickerState = rememberTimePickerState(
            initialHour = cal.get(Calendar.HOUR_OF_DAY),
            initialMinute = cal.get(Calendar.MINUTE),
            is24Hour = true
        )
        TimePickerDialog(
            title = "选择结束时间",
            onDismiss = { showEndPicker = false },
            onConfirm = {
                val newEnd = Calendar.getInstance().apply {
                    timeInMillis = endTime
                    set(Calendar.HOUR_OF_DAY, pickerState.hour)
                    set(Calendar.MINUTE, pickerState.minute)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }.timeInMillis
                // 结束时间必须晚于起始时间，且不能超过当前时间
                if (newEnd > startTime && newEnd <= System.currentTimeMillis()) {
                    endTime = newEnd
                }
                showEndPicker = false
            },
            content = { TimePicker(state = pickerState) }
        )
    }
}

@Composable
private fun TimeChip(label: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(6.dp),
        color = MaterialTheme.colorScheme.primaryContainer
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun TimePickerDialog(
    title: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    content: @Composable () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontWeight = FontWeight.Bold) },
        text = { content() },
        confirmButton = {
            Button(onClick = onConfirm) { Text("确定") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditDialog(
    record: LifeRecord,
    minStartTime: Long,
    maxEndTime: Long,
    onDismiss: () -> Unit,
    onConfirm: (String, Long, Long) -> Unit
) {
    var text by remember { mutableStateOf(record.content) }
    var startTime by remember { mutableStateOf(record.startTime) }
    var endTime by remember { mutableStateOf(record.timestamp) }
    var showStartPicker by remember { mutableStateOf(false) }
    var showEndPicker by remember { mutableStateOf(false) }

    val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("编辑记录", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TimeChip(
                        label = timeFormat.format(Date(startTime)),
                        onClick = { showStartPicker = true }
                    )
                    Text(
                        " - ",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    TimeChip(
                        label = timeFormat.format(Date(endTime)),
                        onClick = { showEndPicker = true }
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("修改记录内容") },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp),
                    maxLines = 5,
                    shape = RoundedCornerShape(8.dp)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(text, startTime, endTime) },
                enabled = text.isNotBlank() && startTime < endTime
            ) { Text("保存") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )

    if (showStartPicker) {
        val cal = Calendar.getInstance().apply { timeInMillis = startTime }
        val pickerState = rememberTimePickerState(
            initialHour = cal.get(Calendar.HOUR_OF_DAY),
            initialMinute = cal.get(Calendar.MINUTE),
            is24Hour = true
        )
        TimePickerDialog(
            title = "选择起始时间",
            onDismiss = { showStartPicker = false },
            onConfirm = {
                val newStart = Calendar.getInstance().apply {
                    timeInMillis = startTime
                    set(Calendar.HOUR_OF_DAY, pickerState.hour)
                    set(Calendar.MINUTE, pickerState.minute)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }.timeInMillis
                if (newStart >= minStartTime && newStart < endTime) {
                    startTime = newStart
                }
                showStartPicker = false
            },
            content = { TimePicker(state = pickerState) }
        )
    }

    if (showEndPicker) {
        val cal = Calendar.getInstance().apply { timeInMillis = endTime }
        val pickerState = rememberTimePickerState(
            initialHour = cal.get(Calendar.HOUR_OF_DAY),
            initialMinute = cal.get(Calendar.MINUTE),
            is24Hour = true
        )
        TimePickerDialog(
            title = "选择结束时间",
            onDismiss = { showEndPicker = false },
            onConfirm = {
                val newEnd = Calendar.getInstance().apply {
                    timeInMillis = endTime
                    set(Calendar.HOUR_OF_DAY, pickerState.hour)
                    set(Calendar.MINUTE, pickerState.minute)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }.timeInMillis
                if (newEnd > startTime && newEnd <= maxEndTime) {
                    endTime = newEnd
                }
                showEndPicker = false
            },
            content = { TimePicker(state = pickerState) }
        )
    }
}
