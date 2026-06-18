package com.liferecorder.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier

@Composable
fun MainScreen(viewModel: MainViewModel) {
    var selectedTab by remember { mutableIntStateOf(0) }

    val tabs = listOf(
        TabItem("今日", Icons.Default.EditNote),
        TabItem("历史", Icons.Default.CalendarMonth),
        TabItem("设置", Icons.Default.Settings),
        TabItem("关于", Icons.Default.Info)
    )

    Scaffold(
        bottomBar = {
            NavigationBar {
                tabs.forEachIndexed { index, tab ->
                    NavigationBarItem(
                        icon = { Icon(tab.icon, contentDescription = tab.label) },
                        label = { Text(tab.label) },
                        selected = selectedTab == index,
                        onClick = {
                            selectedTab = index
                            // 切回日历 tab 时清除选中的详情日期
                            if (index == 1) viewModel.clearSelectedDate()
                        }
                    )
                }
            }
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            when (selectedTab) {
                0 -> TodayScreen(viewModel)
                1 -> HistoryScreen(viewModel)
                2 -> SettingsScreen(viewModel)
                3 -> AboutScreen()
            }
        }
    }
}

private data class TabItem(
    val label: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector
)
