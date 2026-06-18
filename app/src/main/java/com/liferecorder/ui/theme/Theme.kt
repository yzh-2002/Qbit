package com.liferecorder.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF4A90D9),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD6E4FF),
    onPrimaryContainer = Color(0xFF001B3E),
    secondary = Color(0xFF6B7B94),
    secondaryContainer = Color(0xFFD7E3F8),
    surface = Color(0xFFFAFBFF),
    surfaceVariant = Color(0xFFE1E6F0),
    background = Color(0xFFF5F7FB),
)

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFFAAC7FF),
    onPrimary = Color(0xFF002F65),
    primaryContainer = Color(0xFF17458F),
    onPrimaryContainer = Color(0xFFD6E4FF),
    secondary = Color(0xFFBBC7DB),
    secondaryContainer = Color(0xFF3B4858),
    surface = Color(0xFF111418),
    surfaceVariant = Color(0xFF43474E),
    background = Color(0xFF111418),
)

@Composable
fun LifeRecorderTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        // Android 12+ 支持动态取色
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context)
            else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography(),
        content = content
    )
}
