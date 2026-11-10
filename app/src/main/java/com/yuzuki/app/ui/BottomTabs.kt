package com.yuzuki.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.yuzuki.app.ui.theme.ShapeContainer
import com.yuzuki.app.ui.theme.ShapePill

/** 手机原生底部导航：3 个 Tab，图标 + 文字，48dp 以上触控区，整体圆角容器。 */
enum class MainTab { Library, Favorites, History }

val TabBarHeight = 76.dp

@Composable
fun MainNavigationBar(
    selected: MainTab,
    onSelect: (MainTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        shape = ShapeContainer,
        color = MaterialTheme.colorScheme.surfaceContainer,
        contentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = 3.dp,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().height(64.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MainTabItem(MainTab.Library, Icons.Filled.Home, "媒体库", selected, onSelect)
            MainTabItem(MainTab.Favorites, Icons.Filled.Favorite, "收藏", selected, onSelect)
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
    val tint = if (active) MaterialTheme.colorScheme.onPrimaryContainer
    else MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        modifier = Modifier
            .weight(1f)
            .fillMaxHeight()
            .padding(horizontal = 6.dp, vertical = 4.dp)
            .clip(ShapePill)
            .background(if (active) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
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
