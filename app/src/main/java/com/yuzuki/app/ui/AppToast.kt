package com.yuzuki.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yuzuki.app.ui.theme.ShapePill
import kotlinx.coroutines.delay

/**
 * App 内轻提示（替代系统 Toast）。
 *
 * 系统 Toast 从屏幕最底部弹出、样式固定，和深色全圆角 UI 割裂；
 * 这里用一层 Compose 覆盖层：底部 Tab 上方居中的圆角胶囊，
 * 淡入 120ms → 停留 1s → 淡出 160ms，连续触发时只保留最新一条并重置计时。
 *
 * 用法：任意位置 `AppToast.show("单曲循环")`，宿主 `AppToastHost()`
 * 只需在根布局挂一次（RootScreen 最外层 Box 末尾）。
 */
object AppToast {
    /** 最近一条文案：退出动画期间仍要能读到，所以不做清空。 */
    var text by mutableStateOf("")
        private set

    var visible by mutableStateOf(false)
        private set

    /** 每次 show 自增：作为 LaunchedEffect 的 key，重复触发也会重置 1s 计时。 */
    var ticket by mutableLongStateOf(0L)
        private set

    fun show(message: String) {
        text = message
        visible = true
        ticket++
    }

    fun hide() {
        visible = false
    }
}

@Composable
fun AppToastHost(
    modifier: Modifier = Modifier,
    bottomPadding: Dp = TabBarHeight + 22.dp,
) {
    val visible = AppToast.visible
    val ticket = AppToast.ticket

    LaunchedEffect(ticket) {
        if (!visible) return@LaunchedEffect
        delay(1000)
        // 期间又弹了新的（ticket 变了），交给新的这次负责收尾
        if (AppToast.ticket == ticket) AppToast.hide()
    }

    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(tween(120)),
            exit = fadeOut(tween(160)),
            modifier = Modifier.padding(bottom = bottomPadding),
        ) {
            Box(
                modifier = Modifier
                    .clip(ShapePill)
                    .background(Color(0xF01B1B23))
                    .padding(horizontal = 18.dp, vertical = 9.dp),
            ) {
                Text(AppToast.text, fontSize = 13.sp, color = Color(0xFFF2F2F5))
            }
        }
    }
}
