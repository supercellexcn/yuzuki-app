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
    val primary = composeColor(appearance.themeColor)
    val scheme = darkColorScheme(
        primary = primary,
        onPrimary = Color(0xFF1B1425),
        primaryContainer = composeColor(appearance.themeColor, 0xFF4A3B6B),
        onPrimaryContainer = Color(0xFFF2ECF8),
        secondary = Color(0xFFB9C2D0),
        onSecondary = Color(0xFF1B2028),
        secondaryContainer = Color(0xFF363C48),
        onSecondaryContainer = Color(0xFFEDF0F6),
        tertiary = composeColor(appearance.tagColor, 0xFF6E8EC9),
        onTertiary = Color(0xFF151C26),
        background = Color(0xFF101318),
        onBackground = Color(0xFFE6E9F0),
        surface = Color(0xFF161A21),
        onSurface = Color(0xFFE6E9F0),
        surfaceVariant = Color(0xFF2A303B),
        onSurfaceVariant = Color(0xFFC2C8D4),
        surfaceContainerLowest = Color(0xFF11151B),
        surfaceContainerLow = Color(0xFF1C212A),
        surfaceContainer = Color(0xFF20262F),
        surfaceContainerHigh = Color(0xFF2A313C),
        surfaceContainerHighest = Color(0xFF353D49),
        outline = Color(0xFF565E6C),
        outlineVariant = Color(0xFF3A424E),
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
