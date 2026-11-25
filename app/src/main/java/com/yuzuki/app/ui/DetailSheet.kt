package com.yuzuki.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.yuzuki.app.ui.theme.LocalAppearance
import com.yuzuki.app.ui.theme.WebColors
import kotlinx.coroutines.launch

/** 顶部两角圆滑过渡；底部贴着内容区底边，所以不圆角。 */
private val SheetShape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)

/** 框顶到详情顶栏的留白：露一条底色，弹框才有「浮在页面之上」的层次。 */
private val SheetTopGap = 8.dp

/** 起手门槛：小于这个位移仍算轻点 / 抖动。 */
private val SheetDragSlop = 6.dp

/**
 * 详情弹出框（2026-11-25 起）。
 *
 * 结构：详情页 = 原有顶栏 + 这一块从下方滑上来的圆角框，像播放页的播放列表面板那样
 * 只盖住顶栏以下的内容区，不盖底部 Tab。
 *
 * 手势：
 * - 纵向：框内列表在顶部时继续下拉 = 关框（跟手位移，过 1/4 框高松手就关）；
 *   列表没到顶时完全不接管，正常浏览文件。
 * - 横向：不响应任何功能（相关推荐自己那一行滚它的）。
 *
 * 关闭统一走 [requestClose]（带滑出动画），系统返回键也走它；框内的图片查看器是独立
 * Dialog 窗口，它的 BackHandler 后注册、优先级更高，所以看图时返回只关查看器。
 */
@Composable
fun DetailSheet(
    atTop: () -> Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val appearance = LocalAppearance.current
    val sheetBg = WebColors.panel(appearance.navColor, 1f)
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val slopPx = with(density) { SheetDragSlop.toPx() }
    val gapPx = with(density) { SheetTopGap.toPx() }

    // 框高在布局期就能算出来（父高 − 顶部留白），不必等 onSizeChanged 回调；
    // 首帧直接把框摆在屏幕下方，再滑到就位，避免「先可见再隐藏」的闪一下，
    // 也不会出现「高度还没量到 → 一直隐藏」的死状态。
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val sheetHeightPx = (with(density) { maxHeight.toPx() } - gapPx).coerceAtLeast(0f)
        var offsetPx by remember { mutableFloatStateOf(sheetHeightPx) }
        var closing by remember { mutableStateOf(false) }
        val atTopNow by rememberUpdatedState(atTop)
        val dismissNow by rememberUpdatedState(onDismiss)

        suspend fun slideTo(targetPx: Float, durationMs: Int) {
            animate(
                initialValue = offsetPx,
                targetValue = targetPx,
                animationSpec = tween(durationMs, easing = FastOutSlowInEasing),
            ) { v, _ -> offsetPx = v }
        }

        fun requestClose() {
            if (closing) return
            closing = true
            scope.launch {
                slideTo(sheetHeightPx, 180)
                dismissNow()
            }
        }

        // 出场：从屏幕下方滑到就位
        LaunchedEffect(Unit) { slideTo(0f, 220) }

        BackHandler(enabled = !closing) { requestClose() }

        Box(Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = SheetTopGap)
                    .graphicsLayer { translationY = offsetPx }
                    .clip(SheetShape)
                    .background(sheetBg)
                    .border(1.dp, WebColors.Border, SheetShape)
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            val down = awaitFirstDown(
                                requireUnconsumed = false,
                                pass = PointerEventPass.Initial,
                            )
                            var dragging = false
                            while (true) {
                                val event = awaitPointerEvent(PointerEventPass.Initial)
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                val dragY = change.position.y - down.position.y
                                val dragX = change.position.x - down.position.x
                                if (!change.pressed) break
                                if (!dragging) {
                                    if (DetailSheetDrag.canStart(
                                            dragY = dragY,
                                            dragX = dragX,
                                            slop = slopPx,
                                            atTop = atTopNow(),
                                            interactive = !closing,
                                        )
                                    ) {
                                        dragging = true
                                    } else {
                                        continue
                                    }
                                }
                                offsetPx = DetailSheetDrag.offsetOf(
                                    dragY = dragY,
                                    slop = slopPx,
                                    heightPx = sheetHeightPx,
                                )
                                // 接管之后必须消费，否则列表会同时跟着下拉做 overscroll
                                change.consume()
                            }
                            if (dragging) {
                                if (DetailSheetDrag.shouldDismiss(offsetPx, sheetHeightPx)) {
                                    requestClose()
                                } else {
                                    scope.launch { slideTo(0f, 200) }
                                }
                            }
                        }
                    },
            ) {
                content()
            }
        }
    }
}

/**
 * 作品详情覆盖层：原有顶栏 + 详情弹出框。
 *
 * 顶栏留给侧栏入口（详情页不响应侧栏手势，这里是唯一入口）；返回按钮已经去掉，
 * 关框靠下滑 / 系统返回键。
 */
@Composable
fun WorkDetailOverlay(
    rj: String,
    onDismiss: () -> Unit,
    onOpenWork: (String) -> Unit,
    onOpenLibrary: () -> Unit,
    onOpenDrawer: () -> Unit,
    onOpenPlayer: () -> Unit,
) {
    // 列表状态放在弹框外面：下滑关闭要读「是否已在顶部」，内容重组时也不能丢滚动位置
    val listState = rememberLazyListState()
    Column(Modifier.fillMaxSize()) {
        WebTopBar(onOpenDrawer = onOpenDrawer)
        // 撑开顶栏以下的全部高度：weight 落在这一层显式 Box 上，弹框自己再 fillMaxSize
        Box(Modifier.weight(1f).fillMaxWidth()) {
            DetailSheet(
                atTop = {
                    listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0
                },
                onDismiss = onDismiss,
                modifier = Modifier.fillMaxSize(),
            ) {
                WorkDetailScreen(
                    rj = rj,
                    listState = listState,
                    onOpenWork = onOpenWork,
                    onOpenLibrary = onOpenLibrary,
                    onOpenPlayer = onOpenPlayer,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}
