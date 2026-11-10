package com.yuzuki.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * 方案 §5.4 圆角规范：所有框体一律圆角，禁止直角与 <8dp 锐角。
 * WebView 内的框体保持前端 CSS 原值，原生侧不覆盖任何样式。
 */
object YuzukiRadius {
    val Card = 20.dp
    val Container = 16.dp
    val Item = 12.dp
}

val ShapeCard = RoundedCornerShape(YuzukiRadius.Card)
val ShapeContainer = RoundedCornerShape(YuzukiRadius.Container)
val ShapeItem = RoundedCornerShape(YuzukiRadius.Item)

// 全胶囊：用百分比而非 999dp，避免小控件上圆角溢出
val ShapePill = RoundedCornerShape(percent = 50)

val YuzukiShapes = Shapes(
    extraSmall = ShapeItem,
    small = ShapeItem,
    medium = ShapeContainer,
    large = ShapeCard,
    extraLarge = ShapeCard,
)
