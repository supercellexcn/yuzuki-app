package com.yuzuki.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// 原生壳只承载 Tab 栏与若干提示卡片；媒体库/播放器外观完全由 WebView 内前端渲染
private val YuzukiDark = darkColorScheme(
    primary = Color(0xFFB69AF6),
    onPrimary = Color(0xFF201830),
    secondary = Color(0xFF8E7CC3),
    background = Color(0xFF141018),
    onBackground = Color(0xFFF2ECF8),
    surface = Color(0xFF211B28),
    onSurface = Color(0xFFF2ECF8),
    surfaceVariant = Color(0xFF2C2434),
    onSurfaceVariant = Color(0xFFD9CFE6),
    outline = Color(0xFF4A4056),
    error = Color(0xFFFF8A80),
)

@Composable
fun YuzukiTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = YuzukiDark,
        shapes = YuzukiShapes,
        content = content,
    )
}
