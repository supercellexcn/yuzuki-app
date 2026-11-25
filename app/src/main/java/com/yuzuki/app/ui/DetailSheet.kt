package com.yuzuki.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.yuzuki.app.ui.theme.SheetTokens

/**
 * 框顶到「原详情顶栏底边」的留白：这段空隙露的是**被压暗的底层内容**（库页 / 播放器），
 * 才看得出是个浮起来的弹出框，而不是又换了一个新界面。
 */
private val SheetTopGap = 56.dp

/**
 * 已删除的详情顶栏占位：原 `WebTopBar` = 56dp 栏 + 1dp 分割线 = 57dp。
 *
 * 2026-11-25 顶栏按用户要求去掉后，这 57dp 仍然留在框上方（同样露被压暗的底层内容），
 * 弹出框的顶边与高度跟有顶栏时一模一样 —— 用户明确要「恢复高度，不是恢复顶栏」。
 * 别再把这 57dp 加回来当顶栏，也不要删掉它让框变高。
 */
private val RemovedTopBarSpace = 57.dp

/** 框顶到容器顶的实际距离 = 原留白 + 已删顶栏占位。 */
private val SheetTopOffset = SheetTopGap + RemovedTopBarSpace

/**
 * 详情弹出框（2026-11-25 起；同日收口到 [SheetPanel]）。
 *
 * 结构：一块从下方滑上来的圆角框，只盖住顶栏以下的内容区，不盖底部 Tab。
 * 圆角、白条、进场 / 回弹 / 收回时长全部读 `ui/theme/SheetTokens.kt`，
 * 与播放列表、抽屉筛选弹层保持一致，**不要在这里再写一份**。
 *
 * 手势：
 * - 按住顶部白条（[SheetTokens.GrabberArea]）往下拉：任何滚动位置都能关框；
 * - 框内列表在顶部时继续下拉 = 关框（跟手位移，过 1/4 框高松手就关）；
 *   列表没到顶时完全不接管，正常浏览文件。
 * - 横向：不响应任何功能（相关推荐自己那一行滚它的）。
 *
 * 关闭统一走 [SheetPanel] 的带滑出动画的 close（点框外空白、下滑、系统返回键）；
 * 框内的图片查看器是独立 Dialog 窗口，它的 BackHandler 后注册、优先级更高，
 * 所以看图时返回只关查看器。
 */
@Composable
fun DetailSheet(
    atTop: () -> Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    /** 框顶到容器顶的距离。默认 [SheetTopOffset]；覆盖层会把状态栏高度叠进来。 */
    topInset: Dp = SheetTopOffset,
    content: @Composable ColumnScope.() -> Unit,
) {
    SheetPanel(
        onDismiss = onDismiss,
        modifier = modifier,
        topInset = topInset,
        dragZoneHeight = SheetTokens.GrabberArea,
        atTop = atTop,
        grabber = true,
    ) { _ ->
        content()
    }
}

/**
 * 作品详情覆盖层：底层压暗 + 详情弹出框。
 *
 * **没有顶栏**（2026-11-25 按用户要求去掉）：详情只是一块浮在内容上的弹出框，
 * 框顶那 [SheetTopOffset] 的留白透出被压暗的底层页面（库 / 播放器 / 收藏 / 历史），
 * 关框靠下滑、点框外空白或系统返回键。压暗层由 [SheetPanel] 画（跟着位移淡入淡出），
 * 这里不再铺一层静态黑。
 */
@Composable
fun WorkDetailOverlay(
    rj: String,
    onDismiss: () -> Unit,
    onOpenWork: (String) -> Unit,
    onOpenLibrary: () -> Unit,
    onOpenPlayer: () -> Unit,
) {
    // 列表状态放在弹框外面：下滑关闭要读「是否已在顶部」，内容重组时也不能丢滚动位置
    val listState = rememberLazyListState()
    // 压暗层由 SheetPanel 画，必须铺到状态栏后面（跟之前的静态压暗一致）；
    // 所以整层不再 statusBarsPadding，改成把状态栏高度并进框顶留白 ——
    // 框内容的位置与「整层让开状态栏」时一模一样，只是黑得更彻底。
    val statusBarDp = with(LocalDensity.current) {
        WindowInsets.statusBars.getTop(this).toDp()
    }
    Box(Modifier.fillMaxSize()) {
        DetailSheet(
            atTop = {
                listState.firstVisibleItemIndex == 0 &&
                    listState.firstVisibleItemScrollOffset == 0
            },
            onDismiss = onDismiss,
            topInset = SheetTopOffset + statusBarDp,
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
