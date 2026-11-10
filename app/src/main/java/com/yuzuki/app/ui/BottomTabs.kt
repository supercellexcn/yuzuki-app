package com.yuzuki.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.yuzuki.app.R
import com.yuzuki.app.ui.theme.ShapeContainer
import com.yuzuki.app.ui.theme.ShapePill
import com.yuzuki.app.web.WebTab

/** 底部 Tab 栏占位高度；WebView 用同等 padding 避让，二者不重叠（方案 §5.1）。 */
val TabBarHeight = 56.dp

@Composable
fun BottomTabs(
    selected: WebTab,
    onTabClick: (WebTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(TabBarHeight)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Surface(
            shape = ShapeContainer,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 3.dp,
            modifier = Modifier.fillMaxSize(),
        ) {
            Row(
                modifier = Modifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                TabItem(R.string.tab_library, WebTab.LIBRARY, selected, onTabClick)
                TabItem(R.string.tab_player, WebTab.PLAYER, selected, onTabClick)
                TabItem(R.string.tab_history, WebTab.HISTORY, selected, onTabClick)
            }
        }
    }
}

@Composable
private fun RowScope.TabItem(
    labelRes: Int,
    tab: WebTab,
    selected: WebTab,
    onTabClick: (WebTab) -> Unit,
) {
    val active = selected == tab
    Box(
        modifier = Modifier
            .weight(1f)
            .padding(horizontal = 8.dp, vertical = 6.dp)
            .clip(ShapePill)
            .background(
                if (active) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                else Color.Transparent,
            )
            .clickable { onTabClick(tab) },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(labelRes),
            style = MaterialTheme.typography.labelLarge,
            color = if (active) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
    }
}
