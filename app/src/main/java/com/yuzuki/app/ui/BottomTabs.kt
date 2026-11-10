package com.yuzuki.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 视觉稿底部导航（图2）：纯黑底 + 顶部分割线，选中项整组变紫，无胶囊底。
 * 图标的形状按视觉稿自绘（滑块 / 圆靶 / 时钟回退），不再是 Home / PlayArrow / List。
 */
enum class MainTab { Library, Player, History }

val TabBarHeight = 65.dp

private val TabBarBackground = Color(0xFF000000)
private val TabBarDivider = Color(0xFF1C1C22)
private val TabSelected = Color(0xFFA888F8)
private val TabUnselected = Color(0xFF8E8E9A)

@Composable
fun MainNavigationBar(
    selected: MainTab,
    onSelect: (MainTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(TabBarDivider),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .background(TabBarBackground),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MainTabItem(MainTab.Library, "库", selected, onSelect)
            MainTabItem(MainTab.Player, "播放器", selected, onSelect)
            MainTabItem(MainTab.History, "历史", selected, onSelect)
        }
    }
}

@Composable
private fun RowScope.MainTabItem(
    tab: MainTab,
    label: String,
    selected: MainTab,
    onSelect: (MainTab) -> Unit,
) {
    val active = selected == tab
    val tint = if (active) TabSelected else TabUnselected
    val interaction = remember { MutableInteractionSource() }
    Column(
        modifier = Modifier
            .weight(1f)
            .fillMaxHeight()
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClick = { onSelect(tab) },
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        TabBarIcon(tab = tab, tint = tint, modifier = Modifier.size(23.dp))
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
            color = tint,
        )
    }
}
