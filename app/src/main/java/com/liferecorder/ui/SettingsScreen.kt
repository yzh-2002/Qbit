package com.liferecorder.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.liferecorder.data.QuietRule
import com.liferecorder.data.SettingsManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: MainViewModel) {
    val interval by viewModel.intervalMinutes.collectAsStateWithLifecycle()
    val rules by viewModel.quietRules.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshingHolidays.collectAsStateWithLifecycle()
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    val exportResult by viewModel.exportResult.collectAsStateWithLifecycle()
    val importResult by viewModel.importResult.collectAsStateWithLifecycle()

    var showIntervalPicker by remember { mutableStateOf(false) }
    var showRuleEditor by remember { mutableStateOf(false) }
    var editingRule by remember { mutableStateOf<QuietRule?>(null) }
    var showThemePicker by remember { mutableStateOf(false) }

    // SAF 文件选择器：导出
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        uri?.let { viewModel.exportData(it) }
    }

    // SAF 文件选择器：导入
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { viewModel.importData(it) }
    }

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
            // ===== 外观设置 =====
            item {
                Text(
                    "外观设置",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
            }

            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showThemePicker = true },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.DarkMode,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("主题模式", fontWeight = FontWeight.Medium)
                            Text(
                                themeModeName(themeMode),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            themeModeName(themeMode),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

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
                Text(
                    "提醒不会从当前时刻开始计算，而是固定在每天 ${getAlignExample(interval)} 等时间点触发",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 8.dp, top = 4.dp)
                )
            }

            // ===== 电池优化白名单 =====
            item {
                BatteryOptimizationCard()
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

            // ===== AI 配置 =====
            item {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "AI 配置",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
            }

            item {
                AiConfigCard()
            }

            // ===== 数据管理 =====
            item {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "数据管理",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
            }

            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { exportLauncher.launch(viewModel.getExportFileName()) },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.FileUpload,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("导出数据", fontWeight = FontWeight.Medium)
                            Text(
                                "将记录和规则导出为 JSON 文件",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { importLauncher.launch(arrayOf("application/json")) },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.FileDownload,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("导入数据", fontWeight = FontWeight.Medium)
                            Text(
                                "从 JSON 备份文件恢复数据",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
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

        if (showThemePicker) {
            ThemePickerDialog(
                currentMode = themeMode,
                onDismiss = { showThemePicker = false },
                onSelect = {
                    viewModel.setThemeMode(it)
                    showThemePicker = false
                }
            )
        }

        // 导出结果提示
        exportResult?.let { msg ->
            AlertDialog(
                onDismissRequest = { viewModel.clearExportResult() },
                title = { Text("导出数据") },
                text = { Text(msg) },
                confirmButton = {
                    TextButton(onClick = { viewModel.clearExportResult() }) { Text("确定") }
                }
            )
        }

        // 导入结果提示
        importResult?.let { msg ->
            AlertDialog(
                onDismissRequest = { viewModel.clearImportResult() },
                title = { Text("导入数据") },
                text = { Text(msg) },
                confirmButton = {
                    TextButton(onClick = { viewModel.clearImportResult() }) { Text("确定") }
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
private fun BatteryOptimizationCard() {
    val context = LocalContext.current
    val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    var isIgnoring by remember {
        mutableStateOf(powerManager.isIgnoringBatteryOptimizations(context.packageName))
    }

    // 用户从系统设置返回后自动刷新状态
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                isIgnoring = powerManager.isIgnoringBatteryOptimizations(context.packageName)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = if (isIgnoring) CardDefaults.cardColors()
        else CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                if (isIgnoring) Icons.Default.CheckCircle else Icons.Default.BatteryAlert,
                contentDescription = null,
                tint = if (isIgnoring) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.error
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("电池优化", fontWeight = FontWeight.Medium)
                Text(
                    if (isIgnoring) "已关闭电池优化，提醒更可靠"
                    else "建议关闭电池优化，避免提醒被系统拦截",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (!isIgnoring) {
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = {
                        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                            data = Uri.parse("package:${context.packageName}")
                        }
                        context.startActivity(intent)
                        // 延迟刷新状态（用户操作后回到页面会 recompose）
                        isIgnoring = powerManager.isIgnoringBatteryOptimizations(context.packageName)
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    ),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text("去设置", style = MaterialTheme.typography.labelMedium)
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
    val presets = listOf(
        1 to "1 分钟（仅测试）",
        30 to "30 分钟",
        60 to "1 小时"
    )

    val units = listOf("分钟", "小时")
    val unitMultipliers = listOf(1, 60)

    // 自定义输入状态
    var useCustom by remember { mutableStateOf(
        currentInterval !in presets.map { it.first }
    ) }
    var customNumber by remember { mutableStateOf(
        when {
            currentInterval >= 60 && currentInterval % 60 == 0 -> (currentInterval / 60).toString()
            else -> currentInterval.toString()
        }
    ) }
    var selectedUnitIndex by remember { mutableIntStateOf(
        when {
            currentInterval >= 60 && currentInterval % 60 == 0 -> 1
            else -> 0
        }
    ) }

    val customMinutes = (customNumber.toIntOrNull() ?: 0) * unitMultipliers[selectedUnitIndex]
    val isCustomValid = (customNumber.toIntOrNull() ?: 0) in 1..60

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("选择提醒间隔", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                // 预设选项
                presets.forEach { (minutes, label) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                useCustom = false
                                onSelect(minutes)
                            }
                            .padding(vertical = 10.dp, horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = !useCustom && currentInterval == minutes,
                            onClick = {
                                useCustom = false
                                onSelect(minutes)
                            }
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(label, style = MaterialTheme.typography.bodyLarge)
                    }
                }

                // 自定义选项
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { useCustom = true }
                        .padding(vertical = 10.dp, horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = useCustom,
                        onClick = { useCustom = true }
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text("自定义", style = MaterialTheme.typography.bodyLarge)
                }

                // 自定义输入区域
                if (useCustom) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = customNumber,
                            onValueChange = { input ->
                                // 只允许输入数字，最多2位
                                if (input.isEmpty() || (input.all { it.isDigit() } && input.length <= 2)) {
                                    customNumber = input
                                }
                            },
                            modifier = Modifier.weight(1f),
                            label = { Text("1-60") },
                            singleLine = true,
                            shape = RoundedCornerShape(8.dp)
                        )

                        // 单位选择
                        units.forEachIndexed { index, unit ->
                            FilterChip(
                                selected = selectedUnitIndex == index,
                                onClick = { selectedUnitIndex = index },
                                label = { Text(unit) }
                            )
                        }
                    }

                    if (customNumber.isNotEmpty() && !isCustomValid) {
                        Text(
                            "请输入 1-60 之间的数字",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(start = 8.dp, top = 4.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {
            if (useCustom) {
                Button(
                    onClick = { onSelect(customMinutes) },
                    enabled = isCustomValid
                ) { Text("确定") }
            }
        },
        dismissButton = {
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
        minutes >= 60 && minutes % 60 == 0 -> "${minutes / 60} 小时"
        minutes < 60 -> "${minutes} 分钟"
        else -> "${minutes / 60} 小时 ${minutes % 60} 分钟"
    }
}

private fun getAlignExample(intervalMinutes: Int): String {
    // 生成前几个对齐时间点作为示例
    val examples = mutableListOf<String>()
    var totalMinutes = 0
    while (examples.size < 3 && totalMinutes < 1440) {
        totalMinutes += intervalMinutes
        val h = totalMinutes / 60
        val m = totalMinutes % 60
        if (h < 24) {
            examples.add(String.format("%d:%02d", h, m))
        }
    }
    return examples.joinToString("、")
}

private fun applyModeText(mode: Int): String {
    return when (mode) {
        0 -> "每天"
        1 -> "仅工作日（周一至周五，含调休补班）"
        2 -> "仅周末（含法定节假日）"
        else -> "未知"
    }
}

private fun themeModeName(mode: Int): String {
    return when (mode) {
        SettingsManager.THEME_LIGHT -> "光态"
        SettingsManager.THEME_DARK -> "暗态"
        else -> "跟随系统"
    }
}

@Composable
private fun AiConfigCard() {
    val context = LocalContext.current
    val settings = remember { SettingsManager.getInstance(context) }
    var baseUrl by remember { mutableStateOf(settings.aiBaseUrl.value) }
    var apiKey by remember { mutableStateOf(settings.aiApiKey.value) }
    var model by remember { mutableStateOf(settings.aiModel.value) }
    var expanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded },
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("API 配置", fontWeight = FontWeight.Medium)
                    Text(
                        if (apiKey.isNotBlank()) "已配置 · ${model}" else "未配置，点击展开",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (expanded) {
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = baseUrl,
                    onValueChange = {
                        baseUrl = it
                        settings.setAiBaseUrl(it)
                    },
                    label = { Text("API Base URL") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(8.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = {
                        apiKey = it
                        settings.setAiApiKey(it)
                    },
                    label = { Text("API Key") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(8.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = model,
                    onValueChange = {
                        model = it
                        settings.setAiModel(it)
                    },
                    label = { Text("模型名称") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(8.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "支持 OpenAI 兼容 API（如 DeepSeek、Moonshot 等）",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun ThemePickerDialog(
    currentMode: Int,
    onDismiss: () -> Unit,
    onSelect: (Int) -> Unit
) {
    val options = listOf(
        SettingsManager.THEME_FOLLOW_SYSTEM to "跟随系统",
        SettingsManager.THEME_LIGHT to "光态",
        SettingsManager.THEME_DARK to "暗态"
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("选择主题模式", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                options.forEach { (mode, label) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(mode) }
                            .padding(vertical = 12.dp, horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = currentMode == mode,
                            onClick = { onSelect(mode) }
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
