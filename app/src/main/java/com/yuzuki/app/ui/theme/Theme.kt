package com.yuzuki.app.ui.theme

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

/** Material 3 深色配色：不透明容器 + 高对比文字，主题色来自服务端设置。 */
@Composable
fun YuzukiTheme(
    appearance: Appearance = Appearance.Default,
    content: @Composable () -> Unit,
) {
    // 色值取自首页视觉稿实拍取色：纯黑底 + 紫黑面板 + 薰衣草紫强调色。
    val primary = composeColor(appearance.themeColor)
    val scheme = darkColorScheme(
        primary = primary,
        onPrimary = Color(0xFF180C28),
        primaryContainer = Color(0xFF181028),
        onPrimaryContainer = Color(0xFFF8F8F8),
        secondary = Color(0xFFC8B8E8),
        onSecondary = Color(0xFF180C28),
        secondaryContainer = Color(0xFF181828),
        onSecondaryContainer = Color(0xFFEDE6F8),
        tertiary = composeColor(appearance.tagColor, 0xFF8E7CC3),
        onTertiary = Color(0xFF151026),
        background = Color(0xFF000000),
        onBackground = Color(0xFFF8F8F8),
        surface = Color(0xFF0B0A0E),
        onSurface = Color(0xFFF8F8F8),
        surfaceVariant = Color(0xFF181828),
        onSurfaceVariant = Color(0xFFA8A0B8),
        surfaceContainerLowest = Color(0xFF000000),
        surfaceContainerLow = Color(0xFF101010),
        surfaceContainer = Color(0xFF101018),
        surfaceContainerHigh = Color(0xFF181020),
        surfaceContainerHighest = Color(0xFF201828),
        outline = Color(0xFF303030),
        outlineVariant = Color(0xFF282828),
        error = Color(0xFFFFB4AB),
        onError = Color(0xFF2A1113),
    )
    CompositionLocalProvider(LocalAppearance provides appearance) {
        MaterialTheme(
            colorScheme = scheme,
            shapes = YuzukiShapes,
        ) {
            // 统一不透明窗口底色，避免系统默认背景与卡片色冲突
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background,
                contentColor = MaterialTheme.colorScheme.onBackground,
                content = content,
            )
        }
    }
}
