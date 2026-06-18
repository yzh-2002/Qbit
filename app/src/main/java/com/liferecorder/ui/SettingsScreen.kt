package com.liferecorder.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.liferecorder.data.QuietRule

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: MainViewModel) {
    val interval by viewModel.intervalMinutes.collectAsStateWithLifecycle()
    val rules by viewModel.quietRules.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshingHolidays.collectAsStateWithLifecycle()

    var showIntervalPicker by remember { mutableStateOf(false) }
    var showRuleEditor by remember { mutableStateOf(false) }
    var editingRule by remember { mutableStateOf<QuietRule?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("设置", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ===== 提醒设置 =====
            item {
                Text(
                    "提醒设置",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
            }

            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showIntervalPicker = true },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Timer,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("提醒间隔", fontWeight = FontWeight.Medium)
                            Text(
                                "每 ${formatInterval(interval)} 提醒一次",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            formatInterval(interval),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // ===== 节假日设置 =====
            item {
                Text(
                    "节假日设置",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            "数据来源：国务院办公厅官方发布",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            "当前仅支持中国大陆节假日",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = { viewModel.refreshHolidays() },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !isRefreshing
                        ) {
                            if (isRefreshing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("刷新中...")
                            } else {
                                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("刷新节假日数据")
                            }
                        }
                    }
                }
            }

            // ===== 免打扰规则 =====
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "免打扰规则",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                    TextButton(onClick = {
                        editingRule = null
                        showRuleEditor = true
                    }) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("添加规则")
                    }
                }
            }

            if (rules.isEmpty()) {
                item {
                    Text(
                        "暂无免打扰规则，点击上方添加",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }
            }

            items(rules, key = { it.id }) { rule ->
                QuietRuleCard(
                    rule = rule,
                    onToggle = { viewModel.toggleQuietRule(rule) },
                    onEdit = {
                        editingRule = rule
                        showRuleEditor = true
                    },
                    onDelete = { viewModel.deleteQuietRule(rule) }
                )
            }
        }

        // 弹窗
        if (showIntervalPicker) {
            IntervalPickerDialog(
                currentInterval = interval,
                onDismiss = { showIntervalPicker = false },
                onSelect = {
                    viewModel.setInterval(it)
                    showIntervalPicker = false
                }
            )
        }

        if (showRuleEditor) {
            RuleEditorDialog(
                rule = editingRule,
                onDismiss = { showRuleEditor = false },
                onSave = { rule ->
                    if (editingRule != null) {
                        viewModel.updateQuietRule(rule)
                    } else {
                        viewModel.addQuietRule(rule)
                    }
                    showRuleEditor = false
                }
            )
        }
    }
}

@Composable
private fun QuietRuleCard(
    rule: QuietRule,
    onToggle: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onEdit),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = rule.name,
                        fontWeight = FontWeight.Medium,
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "${formatTime(rule.startHour, rule.startMinute)} - ${formatTime(rule.endHour, rule.endMinute)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = applyModeText(rule.applyMode),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Switch(
                        checked = rule.enabled,
                        onCheckedChange = { onToggle() }
                    )
                    if (!showDeleteConfirm) {
                        IconButton(
                            onClick = { showDeleteConfirm = true },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = "删除",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            if (showDeleteConfirm) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("确认删除该规则？", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.error)
                    Spacer(modifier = Modifier.width(8.dp))
                    TextButton(onClick = { showDeleteConfirm = false }) { Text("取消") }
                    TextButton(onClick = onDelete) { Text("删除", color = MaterialTheme.colorScheme.error) }
                }
            }
        }
    }
}

@Composable
private fun IntervalPickerDialog(
    currentInterval: Int,
    onDismiss: () -> Unit,
    onSelect: (Int) -> Unit
) {
    val options = listOf(
        1 to "1 分钟（测试）",
        15 to "15 分钟",
        30 to "30 分钟",
        45 to "45 分钟",
        60 to "1 小时",
        90 to "1.5 小时",
        120 to "2 小时",
        180 to "3 小时"
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("选择提醒间隔", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                options.forEach { (minutes, label) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(minutes) }
                            .padding(vertical = 12.dp, horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = currentInterval == minutes,
                            onClick = { onSelect(minutes) }
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(label, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RuleEditorDialog(
    rule: QuietRule?,
    onDismiss: () -> Unit,
    onSave: (QuietRule) -> Unit
) {
    var name by remember { mutableStateOf(rule?.name ?: "") }
    var startHour by remember { mutableIntStateOf(rule?.startHour ?: 0) }
    var startMinute by remember { mutableIntStateOf(rule?.startMinute ?: 0) }
    var endHour by remember { mutableIntStateOf(rule?.endHour ?: 0) }
    var endMinute by remember { mutableIntStateOf(rule?.endMinute ?: 0) }
    var applyMode by remember { mutableIntStateOf(rule?.applyMode ?: 0) }
    var enabled by remember { mutableStateOf(rule?.enabled ?: true) }

    var showStartTimePicker by remember { mutableStateOf(false) }
    var showEndTimePicker by remember { mutableStateOf(false) }

    val isEdit = rule != null
    val title = if (isEdit) "编辑规则" else "新建规则"

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("规则名称") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(8.dp)
                )

                OutlinedCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showStartTimePicker = true }
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("开始时间", modifier = Modifier.weight(1f))
                        Text(
                            formatTime(startHour, startMinute),
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                OutlinedCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showEndTimePicker = true }
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("结束时间", modifier = Modifier.weight(1f))
                        Text(
                            formatTime(endHour, endMinute),
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Text("适用范围", style = MaterialTheme.typography.labelLarge)
                val modes = listOf("每天" to 0, "仅工作日" to 1, "仅周末" to 2)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    modes.forEach { (label, mode) ->
                        FilterChip(
                            selected = applyMode == mode,
                            onClick = { applyMode = mode },
                            label = { Text(label) }
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("启用规则")
                    Switch(checked = enabled, onCheckedChange = { enabled = it })
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        QuietRule(
                            id = rule?.id ?: 0,
                            name = name.ifBlank { "未命名规则" },
                            startHour = startHour,
                            startMinute = startMinute,
                            endHour = endHour,
                            endMinute = endMinute,
                            applyMode = applyMode,
                            enabled = enabled
                        )
                    )
                },
                enabled = name.isNotBlank()
            ) {
                Text("保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )

    if (showStartTimePicker) {
        TimePickerDialog(
            initialHour = startHour,
            initialMinute = startMinute,
            onDismiss = { showStartTimePicker = false },
            onConfirm = { h, m ->
                startHour = h
                startMinute = m
                showStartTimePicker = false
            }
        )
    }

    if (showEndTimePicker) {
        TimePickerDialog(
            initialHour = endHour,
            initialMinute = endMinute,
            onDismiss = { showEndTimePicker = false },
            onConfirm = { h, m ->
                endHour = h
                endMinute = m
                showEndTimePicker = false
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimePickerDialog(
    initialHour: Int,
    initialMinute: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int, Int) -> Unit
) {
    val state = rememberTimePickerState(
        initialHour = initialHour,
        initialMinute = initialMinute,
        is24Hour = true
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("选择时间") },
        text = {
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                TimePicker(state = state)
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(state.hour, state.minute) }) { Text("确定") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

// ===== 工具函数 =====

private fun formatTime(hour: Int, minute: Int): String {
    return String.format("%02d:%02d", hour, minute)
}

private fun formatInterval(minutes: Int): String {
    return when {
        minutes < 60 -> "${minutes} 分钟"
        minutes % 60 == 0 -> "${minutes / 60} 小时"
        else -> "${minutes / 60} 小时 ${minutes % 60} 分钟"
    }
}

private fun applyModeText(mode: Int): String {
    return when (mode) {
        0 -> "每天"
        1 -> "仅工作日（周一至周五，含调休补班）"
        2 -> "仅周末（含法定节假日）"
        else -> "未知"
    }
}
