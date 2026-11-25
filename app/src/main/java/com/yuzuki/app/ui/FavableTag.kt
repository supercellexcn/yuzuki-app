package com.yuzuki.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yuzuki.app.ui.theme.WebColors
import com.yuzuki.app.ui.theme.pillTextStyle

/** 标签胶囊的圆角（详情页 / 收藏页共用同一枚令牌）。 */
internal val TagShape = RoundedCornerShape(8.dp)

/**
 * 声优 / 类型 / 社团标签 + 内嵌收藏加减号：右侧 22 宽自成正方区域，
 * 左侧 1px 渐隐分隔线（web `.fav-toggle::before`）。
 *
 * 详情页与收藏页共用这一份：点胶囊主体 = 按该条件筛选，点右端 = 收藏 / 取消收藏。
 * [foreground] 让社团胶囊可以用「主题色底 + onPrimary 字」，其余沿用白色字。
 */
@Composable
internal fun FavableTag(
    text: String,
    category: String,
    background: Color,
    favored: Boolean,
    onOpen: () -> Unit,
    onToggle: () -> Unit,
    foreground: Color = Color.White,
) {
    // 自定义标签（含内置「全年龄」）与 web `.tag-custom` 一致：深绿底 + 薄荷绿字 + 深绿描边
    val custom = category == CUSTOM_TAG
    val textColor = if (custom) WebColors.CustomTagFg else foreground
    Row(
        modifier = Modifier
            .height(22.dp)
            .clip(TagShape)
            .background(if (custom) WebColors.CustomTagBg else background)
            .then(if (custom) Modifier.border(1.dp, WebColors.CustomTagBorder, TagShape) else Modifier)
            .clickable(onClick = onOpen),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text,
            style = pillTextStyle(12.sp),
            color = textColor,
            maxLines = 1,
            modifier = Modifier.padding(start = 7.dp, end = 6.dp),
        )
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .width(22.dp)
                .clickable(onClick = onToggle),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .width(1.dp)
                    .fillMaxHeight(0.62f),
            ) {
                drawRect(
                    brush = Brush.verticalGradient(
                        listOf(
                            Color.White.copy(alpha = 0f),
                            Color.White.copy(alpha = 0.4f),
                            Color.White.copy(alpha = 0.4f),
                            Color.White.copy(alpha = 0f),
                        ),
                    ),
                )
            }
            Text(
                if (favored) "−" else "+",
                style = pillTextStyle(12.sp, FontWeight.Bold),
                color = textColor,
            )
        }
    }
}
