package com.yuzuki.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color

private val Background = Color(0xFF141018)
private val Surface = Color(0xFF211B28)
private val OnSurface = Color(0xFFF2ECF8)

@Composable
fun YuzukiTheme(
    appearance: Appearance = Appearance.Default,
    content: @Composable () -> Unit,
) {
    val primary = composeColor(appearance.themeColor)
    val scheme = darkColorScheme(
        primary = primary,
        onPrimary = Color(0xFF201830),
        secondary = composeColor(appearance.actorColor, 0xFF8E7CC3),
        tertiary = composeColor(appearance.tagColor, 0xFF6E8EC9),
        background = Background,
        onBackground = OnSurface,
        surface = Surface,
        onSurface = OnSurface,
        surfaceVariant = Color(0xFF2C2434),
        onSurfaceVariant = Color(0xFFD9CFE6),
        outline = Color(0xFF4A4056),
        error = Color(0xFFFF8A80),
    )
    CompositionLocalProvider(LocalAppearance provides appearance) {
        MaterialTheme(
            colorScheme = scheme,
            shapes = YuzukiShapes,
            content = content,
        )
    }
}
