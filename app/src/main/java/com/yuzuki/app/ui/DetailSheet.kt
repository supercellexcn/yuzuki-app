package com.yuzuki.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.yuzuki.app.ui.theme.LocalAppearance
import com.yuzuki.app.ui.theme.WebColors
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

/** 顶部两角圆滑过渡；底部贴着内容区底边，所以不圆角。 */
private val SheetShape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)

/**
 * 框顶到详情顶栏的留白：这段空隙露的是**被压暗的底层内容**（库页 / 播放器），
 * 才看得出是个浮起来的弹出框，而不是又换了一个新界面。
 */
private val SheetTopGap = 56.dp

/** 底层压暗度：够暗到不抢框内内容，又留着形影能认出后面是什么。 */
private val ScrimColor = Color(0x99000000)

/** 动画时长：进场 / 回弹 / 收回。收回统一 0.25s（下滑、点空白、返回键一致）。 */
private const val OPEN_MS = 220
private const val REBOUND_MS = 200
private const val CLOSE_MS = 250

/** 起手门槛：小于这个位移仍算轻点 / 抖动。 */
private val SheetDragSlop = 6.dp

/** 顶部抓手（iPhone 式）：36×5dp 胶囊，居中贴在框顶，占 18dp 高度。 */
private val GrabberWidth = 36.dp
private val GrabberHeight = 5.dp
private val GrabberArea = 18.dp

/**
 * 顶部下拉抓手：只做视觉提示，**不加任何点击 / 拖动逻辑** —— 下滑关框由整块框的手势层接管
 * （手势层覆盖框内全部区域，包括这条胶囊），所以抓住它往下拉与抓住别处行为一致。
 * 贴在平移层里的 Column 顶部，跟着框一起滑；不放进详情内容，否则会跟着列表滚走。
 */
@Composable
private fun SheetGrabber() {
    Box(
        modifier = Modifier.fillMaxWidth().height(GrabberArea),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .width(GrabberWidth)
                .height(GrabberHeight)
                .clip(RoundedCornerShape(GrabberHeight / 2))
                .background(WebColors.Muted.copy(alpha = 0.5f)),
        )
    }
}

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
        val scrimInteraction = remember { MutableInteractionSource() }

        // 进场 / 回弹 / 收回都写同一个 offsetPx，统一经这一条 Job 调度：
        // 启动前先取消上一个，避免「回弹还没跑完又按下拖动」时动画与手势交替写值（又是抖动）。
        val slideJob = remember { mutableStateOf<Job?>(null) }

        suspend fun slideTo(targetPx: Float, durationMs: Int) {
            animate(
                initialValue = offsetPx,
                targetValue = targetPx,
                animationSpec = tween(durationMs, easing = FastOutSlowInEasing),
            ) { v, _ -> offsetPx = v }
        }

        fun launchSlide(targetPx: Float, durationMs: Int, then: (() -> Unit)? = null) {
            slideJob.value?.cancel()
            slideJob.value = scope.launch {
                slideTo(targetPx, durationMs)
                then?.invoke()
            }
        }

        fun requestClose() {
            if (closing) return
            closing = true
            launchSlide(sheetHeightPx, CLOSE_MS) { dismissNow() }
        }

        // 出场：从屏幕下方滑到就位
        LaunchedEffect(Unit) { launchSlide(0f, OPEN_MS) }

        BackHandler(enabled = !closing) { requestClose() }

        Box(Modifier.fillMaxSize()) {
            // 框外空白（顶栏下面那一段）：点它走同一条收回动画，而不是瞬间消失
            Box(
                Modifier
                    .fillMaxSize()
                    .clickable(
                        interactionSource = scrimInteraction,
                        indication = null,
                        onClick = { requestClose() },
                    ),
            )
            // 手势层必须站在**被平移的那层外面**（2026-11-25 A 方案）。
            // graphicsLayer 的 translationY 会参与指针坐标转换：图层往下挪多少，交给
            // pointerInput 的 change.position 就被反向减掉多少。手势若和被平移的层同处一棵子树，
            // dragY = 手指位移 − offsetPx，而 offsetPx 又由 dragY 算出 —— 增益 −1 的闭环：
            // 手指一停 offset 就逐帧来回跳（上下抖动），匀速拖动也只跟到手指的一半。
            // 0.1.70 / 0.1.71 / 0.1.72 都在改「什么时候消费事件」，从未动过坐标系，所以没修掉。
            // 这里的 padding 是固定布局偏移（不含 offsetPx），坐标系稳定；同时把命中区收在框内，
            // 顶部那 [SheetTopGap] 空隙仍归下层空白层，不会误吃关闭点击。
            Box(Modifier.fillMaxSize().padding(top = SheetTopGap)) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            val halfSlop = viewConfiguration.touchSlop * 0.5f
                            awaitEachGesture {
                                val down = awaitFirstDown(
                                    requireUnconsumed = false,
                                    pass = PointerEventPass.Initial,
                                )
                                // 列表已在顶部时，只截获「明确往下拉」的那几帧；
                                // 向上滑 / 横滑立刻放弃本轮，列表照常滚、相关推荐照常横滑。
                                val capturing = atTopNow() && !closing
                                var dragging = false
                                var abandoned = false
                                while (true) {
                                    val event = awaitPointerEvent(PointerEventPass.Initial)
                                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                    val dragY = change.position.y - down.position.y
                                    val dragX = change.position.x - down.position.x
                                    if (!change.pressed) break
                                    if (!dragging) {
                                        if (!abandoned && DetailSheetDrag.canStart(
                                                dragY = dragY,
                                                dragX = dragX,
                                                slop = slopPx,
                                                atTop = capturing,
                                                interactive = !closing,
                                            )
                                        ) {
                                            // 接管瞬间掐掉还在跑的回弹 / 进场动画，别让两处同时写 offsetPx
                                            slideJob.value?.cancel()
                                            dragging = true
                                        } else {
                                            // 位移够大才判方向：够大且不是「往下」就整轮放行
                                            if (abs(dragY) + abs(dragX) > halfSlop) {
                                                if (dragY > abs(dragX)) {
                                                    if (capturing && !abandoned) change.consume()
                                                } else {
                                                    abandoned = true
                                                }
                                            }
                                            continue
                                        }
                                    }
                                    offsetPx = DetailSheetDrag.offsetOf(
                                        dragY = dragY,
                                        slop = slopPx,
                                        heightPx = sheetHeightPx,
                                    )
                                    change.consume()
                                }
                                if (dragging) {
                                    if (DetailSheetDrag.shouldDismiss(offsetPx, sheetHeightPx)) {
                                        requestClose()
                                    } else {
                                        launchSlide(0f, REBOUND_MS)
                                    }
                                }
                            }
                        },
                ) {
                    // 平移单独一层：只改图层矩阵，不再和「圆角裁剪 + 底色」挤在同一个 modifier 链上；
                    // 位移按整数像素对齐，否则图层纹理落在半个像素上，文字会被双线性采样糊掉
                    // （快速拖动时看起来就是「抖到模糊」）。
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer { translationY = offsetPx.roundToInt().toFloat() },
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(SheetShape)
                                .background(sheetBg),
                        ) {
                            SheetGrabber()
                            content()
                        }
                    }
                }
            }
        }
    }
}

/**
 * 作品详情覆盖层：底层压暗 + 原有顶栏 + 详情弹出框。
 *
 * 这一层**不铺不透明页面底色**：框顶到顶栏之间那 [SheetTopGap] 的空隙要露出被压暗的
 * 库页 / 播放器，看着才像浮起来的弹出框。顶栏保留（详情页不响应侧栏手势，这里是唯一入口）；
 * 返回按钮已经去掉，关框靠下滑 / 系统返回键 / 点框外空白。
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
    Box(Modifier.fillMaxSize()) {
        // 只做压暗：点空白的处理在 DetailSheet 内部（同一层空白），保证也走 0.25s 收回动画
        Box(Modifier.fillMaxSize().background(ScrimColor))
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            // 顶栏自带半透明底色，下面垫一层页底色，避免压暗层与底层内容透上来干扰文字
            Box(Modifier.fillMaxWidth().background(WebColors.PageBg)) {
                WebTopBar(onOpenDrawer = onOpenDrawer)
            }
            // 撑开顶栏以下的全部高度：weight 落在这一层显式 Box 上，弹框自己再 fillMaxSize
            Box(Modifier.weight(1f).fillMaxWidth()) {
                DetailSheet(
                    atTop = {
                        listState.firstVisibleItemIndex == 0 &&
                            listState.firstVisibleItemScrollOffset == 0
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
}
