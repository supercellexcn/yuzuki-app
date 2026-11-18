package com.yuzuki.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 底部导航（库 / 播放器 / 历史）。
 *
 * 视觉稿是纯黑底，这里改成**亚克力毛玻璃**：栏身半透明，信息流从栏后穿过去，
 * 栏身内侧做一层模糊取样（见 [AcrylicBarSurface]），选中项整组变紫、无胶囊底。
 * 图标形状按视觉稿自绘（滑块 / 圆靶 / 时钟回退），不是 Home / PlayArrow / List。
 */
enum class MainTab { Library, Player, History }

/** 导航栏高度。页面底部要让出这一段（[LocalTabBarInset]），否则最后一项会被栏压住。 */
val TabBarHeight = 65.dp

/** 栏身底色：黑里带一点紫味，55% 不透明 —— 透得出信息流，磨砂感也还在。 */
private val TabBarTint = Color(0x8C0A0A12)
private val TabSelected = Color(0xFFA888F8)
private val TabUnselected = Color(0xFF8E8E9A)

@Composable
fun MainNavigationBar(
    selected: MainTab,
    onSelect: (MainTab) -> Unit,
    backdrop: AcrylicBackdrop,
    /** 播放器页自带整屏模糊背景，那一页不必再取样 */
    blurEnabled: Boolean = true,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxWidth().height(TabBarHeight)) {
        AcrylicBarSurface(
            backdrop = backdrop,
            tint = TabBarTint,
            blurEnabled = blurEnabled,
            modifier = Modifier.fillMaxSize(),
        )
        Row(
            modifier = Modifier.fillMaxSize(),
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
