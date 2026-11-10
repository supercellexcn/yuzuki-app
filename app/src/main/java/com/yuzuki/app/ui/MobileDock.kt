package com.yuzuki.app.ui

import android.content.Context
import android.os.SystemClock
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yuzuki.app.data.AppContainer
import com.yuzuki.app.ui.theme.LocalAppearance
import com.yuzuki.app.ui.theme.ShapePill
import com.yuzuki.app.ui.theme.WebColors
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * 移动端悬浮快捷控件（web `MobileDock.vue`）：46 宽的竖条圆盘 + 4 个 36×36 圆钮。
 *
 * 交互照 web：可拖动、松手吸附到最近的一侧、纵向位置记忆（web 存 localStorage，
 * 这里存 SharedPreferences）；位移 < 5dp 视为点击，拖完 350ms 内吞掉随之而来的点击。
 * 叠放顺序由 RootScreen 保证：内容之上、侧栏抽屉与全屏播放器之下。
 */
private const val DOCK_PREFS = "yuzuki_ui"
private const val KEY_DOCK_SIDE = "yuzuki_mobile_dock_side"
private const val KEY_DOCK_TOP = "yuzuki_mobile_dock_top"

/** web: width 46 / DOCK_H 166 / EDGE 10。 */
private val DockWidth = 46.dp
private val DockHeight = 166.dp
private val DockEdge = 10.dp

@Composable
fun MobileDock(
    onGoHome: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val appearance = LocalAppearance.current
    val prefs = remember { AppContainer.app.getSharedPreferences(DOCK_PREFS, Context.MODE_PRIVATE) }

    val dockWidthPx = with(density) { DockWidth.toPx() }
    val dockHeightPx = with(density) { DockHeight.toPx() }
    val edgePx = with(density) { DockEdge.toPx() }
    val minPx = with(density) { 6.dp.toPx() }
    val thresholdPx = with(density) { 5.dp.toPx() }

    var posX by remember { mutableFloatStateOf(0f) }
    var posY by remember { mutableFloatStateOf(0f) }
    var ready by remember { mutableStateOf(false) }
    var dragging by remember { mutableStateOf(false) }
    var suppressUntil by remember { mutableLongStateOf(0L) }
    var settleTicket by remember { mutableIntStateOf(0) }
    var settleX by remember { mutableFloatStateOf(0f) }
    var settleY by remember { mutableFloatStateOf(0f) }

    // 松手后的吸附动画：position 是两个独立的浮点状态，各自动画到目标点。
    // 拖拽开始时 effect 重启并直接返回，等于取消未跑完的吸附动画。
    LaunchedEffect(settleTicket, dragging) {
        if (settleTicket == 0 || dragging) return@LaunchedEffect
        coroutineScope {
            launch { animate(posX, settleX, animationSpec = tween(300)) { v, _ -> posX = v } }
            launch { animate(posY, settleY, animationSpec = tween(300)) { v, _ -> posY = v } }
        }
    }

    val panelBg = WebColors.panel(appearance.navColor, appearance.panelAlpha)

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val viewW = with(density) { maxWidth.toPx() }
        val viewH = with(density) { maxHeight.toPx() }
        val maxTop = (viewH - dockHeightPx - edgePx).coerceAtLeast(edgePx)
        val maxLeft = (viewW - dockWidthPx - minPx).coerceAtLeast(minPx)

        fun sideX(right: Boolean): Float =
            if (right) (viewW - dockWidthPx - edgePx).coerceAtLeast(edgePx) else edgePx

        // 首次测量后定位：右侧、纵向居中（web: innerHeight*0.5 - DOCK_H/2）
        LaunchedEffect(viewW, viewH) {
            if (ready || viewW <= 0f || viewH <= 0f) return@LaunchedEffect
            val side = prefs.getString(KEY_DOCK_SIDE, "right") ?: "right"
            val saved = prefs.getFloat(KEY_DOCK_TOP, -1f)
            val top = if (saved < 0f) viewH * 0.5f - dockHeightPx / 2f else saved
            posY = top.coerceIn(edgePx, maxTop)
            posX = sideX(side == "right")
            ready = true
        }

        if (!ready) return@BoxWithConstraints

        fun settle() {
            val right = posX + dockWidthPx / 2f >= viewW / 2f
            val targetY = posY.coerceIn(edgePx, maxTop)
            prefs.edit()
                .putString(KEY_DOCK_SIDE, if (right) "right" else "left")
                .putFloat(KEY_DOCK_TOP, targetY)
                .apply()
            settleX = sideX(right)
            settleY = targetY
            settleTicket++
        }

        Column(
            modifier = Modifier
                .offset { IntOffset(posX.roundToInt(), posY.roundToInt()) }
                .width(DockWidth)
                .shadow(14.dp, ShapePill)
                .clip(ShapePill)
                .background(panelBg)
                .border(1.dp, WebColors.Border, ShapePill)
                .padding(4.dp)
                .pointerInput(viewW, viewH) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        var moved = false
                        var total = Offset.Zero
                        var startX = posX
                        var startY = posY
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) break
                            val delta = change.position - change.previousPosition
                            if (delta != Offset.Zero) {
                                total += delta
                                if (!moved && total.getDistance() > thresholdPx) {
                                    moved = true
                                    dragging = true
                                }
                                if (moved) {
                                    change.consume()
                                    posX = (startX + total.x).coerceIn(minPx, maxLeft)
                                    posY = (startY + total.y).coerceIn(edgePx, maxTop)
                                }
                            }
                        }
                        if (moved) {
                            // 拖拽结束后吞掉随之而来的点击（web: suppressClickUntil 350ms）
                            suppressUntil = SystemClock.elapsedRealtime() + 350
                            settle()
                        }
                        dragging = false
                    }
                },
            verticalArrangement = Arrangement.spacedBy(4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            DockButton(active = false) {
                if (!dragging && SystemClock.elapsedRealtime() >= suppressUntil) onGoHome()
            }
            DockButton(label = "T", active = false) {
                if (!dragging && SystemClock.elapsedRealtime() >= suppressUntil) DockState.scrollToTop()
            }
            // 视图切换：列表 → 详情 → 图片，图标随当前形态变化
            DockButton(
                active = LibraryState.view != LibraryView.Detail,
                icon = { tint ->
                    when (LibraryState.view) {
                        LibraryView.List -> ViewListIcon(tint, Modifier.size(18.dp))
                        LibraryView.Detail -> ViewDetailIcon(tint, Modifier.size(18.dp))
                        LibraryView.Image -> ViewImageIcon(tint, Modifier.size(18.dp))
                    }
                },
            ) {
                if (!dragging && SystemClock.elapsedRealtime() >= suppressUntil) {
                    LibraryState.cycleView()
                }
            }
            DockButton(label = "R", active = LibraryState.randomMode) {
                if (!dragging && SystemClock.elapsedRealtime() >= suppressUntil) {
                    LibraryState.toggleRandom()
                    onGoHome()
                }
            }
        }
    }
}

@Composable
private fun DockButton(
    label: String? = null,
    active: Boolean,
    icon: (@Composable (Color) -> Unit)? = null,
    onClick: () -> Unit,
) {
    val tint = if (active) MaterialTheme.colorScheme.onPrimary else WebColors.Text
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(if (active) MaterialTheme.colorScheme.primary else Color.Transparent)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        when {
            icon != null -> icon(tint)
            label == null -> HomeIcon(tint, Modifier.size(18.dp))
            else -> Text(label, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = tint)
        }
    }
}
