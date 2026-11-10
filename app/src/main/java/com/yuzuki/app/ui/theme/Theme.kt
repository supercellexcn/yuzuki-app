package com.yuzuki.app.ui.theme

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

/**
 * 配色与网页端完全对齐（App.vue :root + useAppearance.ts）：
 * 页面底 #121417、面板 = 面板基色混 22% 主题色 × 面板透明度、
 * 控件底 = rgb(38,43,51) × 控件透明度、强调色 = 主题色（对比度不足时提亮）。
 */
@Composable
fun YuzukiTheme(
    appearance: Appearance = Appearance.Default,
    content: @Composable () -> Unit,
) {
    val nav = appearance.navColor
    val accent = composeColor(appearance.themeColor, 0xFFA78BFA)
    val panel = WebColors.panel(nav, appearance.panelAlpha)
    val control = WebColors.control(appearance.controlAlpha)
    val accentFg = WebColors.foregroundOn(accent)
    val scheme = darkColorScheme(
        primary = accent,
        onPrimary = accentFg,
        primaryContainer = accent.copy(alpha = 0.2f),
        onPrimaryContainer = WebColors.Text,
        secondary = WebColors.tagBg(appearance.tagColor, 1f),
        onSecondary = Color(0xFF101010),
        secondaryContainer = control,
        onSecondaryContainer = WebColors.Text,
        tertiary = WebColors.tagBg(appearance.actorColor, 1f),
        onTertiary = Color(0xFF101010),
        background = WebColors.PageBg,
        onBackground = WebColors.Text,
        surface = panel,
        onSurface = WebColors.Text,
        surfaceVariant = control,
        onSurfaceVariant = WebColors.Muted,
        surfaceContainerLowest = Color(0xFF0D0E11),
        surfaceContainerLow = control,
        surfaceContainer = panel,
        surfaceContainerHigh = WebColors.panel(nav, 0.85f),
        surfaceContainerHighest = WebColors.panel(nav, 0.95f),
        outline = WebColors.Border,
        outlineVariant = WebColors.Border,
        error = Color(0xFFFFB4AB),
        onError = Color(0xFF2A1113),
    )
    CompositionLocalProvider(LocalAppearance provides appearance) {
        MaterialTheme(
            colorScheme = scheme,
            shapes = YuzukiShapes,
        ) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background,
                contentColor = MaterialTheme.colorScheme.onBackground,
                content = content,
            )
        }
    }
}
