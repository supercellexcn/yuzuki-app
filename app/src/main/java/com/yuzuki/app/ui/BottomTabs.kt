package com.yuzuki.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

/**
 * 视觉稿底部导航：整条铺满、顶部分割线、选中项只用紫色图标 + 文字（无胶囊底）。
 * 播放器占中间 Tab，用于打开常驻的全屏播放器；迷你播放条已移除。
 */
enum class MainTab { Library, Player, History }

val TabBarHeight = 65.dp

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
                .background(MaterialTheme.colorScheme.outlineVariant),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .background(MaterialTheme.colorScheme.surfaceContainerLow),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MainTabItem(MainTab.Library, Icons.Filled.Home, "库", selected, onSelect)
            MainTabItem(MainTab.Player, Icons.Filled.PlayArrow, "播放器", selected, onSelect)
            MainTabItem(MainTab.History, Icons.AutoMirrored.Filled.List, "历史", selected, onSelect)
        }
    }
}

@Composable
private fun RowScope.MainTabItem(
    tab: MainTab,
    icon: ImageVector,
    label: String,
    selected: MainTab,
    onSelect: (MainTab) -> Unit,
) {
    val active = selected == tab
    val tint = if (active) MaterialTheme.colorScheme.primary
    else MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        modifier = Modifier
            .weight(1f)
            .fillMaxHeight()
            .clickable { onSelect(tab) },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = tint,
            modifier = Modifier.size(22.dp),
        )
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = tint)
    }
}
