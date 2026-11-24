package com.yuzuki.app.ui.theme

import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit

/**
 * 标签 / 胶囊内的单行文字样式。
 *
 * Compose 的 `Text` 默认 `includeFontPadding = true`：按字体的 top / bottom 指标在上下补白，
 * 而 top 留白通常比 bottom 多 0.2em 左右，11–14sp 的胶囊文字整体偏下 1–2dp，看起来就是「没上下居中」。
 * 封面标签、声优 chip 原先还把 `lineHeight` 压得比字号更小（11sp 配 12sp、12sp 配 13sp），
 * 字形被行盒挤住，偏移更明显。
 *
 * 这里关掉字体留白、也不再压行高：文字高度交给字体自然行高，
 * 由容器的上下对称内距 / 居中排布保证真正居中。
 */
fun pillTextStyle(fontSize: TextUnit, fontWeight: FontWeight? = null): TextStyle = TextStyle(
    fontSize = fontSize,
    fontWeight = fontWeight,
    platformStyle = PlatformTextStyle(includeFontPadding = false),
)
