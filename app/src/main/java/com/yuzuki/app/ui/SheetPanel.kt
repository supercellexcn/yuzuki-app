package com.yuzuki.app.ui

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.yuzuki.app.ui.theme.LocalAppearance
import com.yuzuki.app.ui.theme.SheetEasing
import com.yuzuki.app.ui.theme.SheetShape
import com.yuzuki.app.ui.theme.SheetTokens
import com.yuzuki.app.ui.theme.WebColors
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * 顶部抓手（白条）：详情 / 播放列表共用，尺寸与颜色读 [SheetTokens]。
 *
 * 只做视觉提示，**不自带手势** —— 下拉关闭由 [SheetPanel] 的手势层按「顶部带子」热区统一接管，
 * 所以抓住它往下拉与抓住标题栏 / 内容（已滚到顶部时）行为完全一致。
 */
@Composable
fun SheetGrabber() {
    Box(
        modifier = Modifier.fillMaxWidth().height(SheetTokens.GrabberArea),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .width(SheetTokens.GrabberWidth)
                .height(SheetTokens.GrabberHeight)
                .clip(RoundedCornerShape(SheetTokens.GrabberHeight / 2))
                .background(WebColors.Muted.copy(alpha = 0.5f)),
        )
    }
}

/**
 * 统一的底部弹出层（2026-11-25 收口）。
 *
 * 应用内三处「从下往上滑出」的框里，作品详情与播放列表都用它；抽屉筛选弹层走 M3 的
 * ModalBottomSheet（有独立窗口 / 焦点 / 输入法处理），只复用 [SheetShape] 与 [SheetGrabber]。
 *
 * 动画统一读 [SheetTokens]：进场 [SheetTokens.OPEN_MS]、弹回 [SheetTokens.REBOUND_MS]、
 * 收回 [SheetTokens.CLOSE_MS]，缓动 [SheetEasing]；压暗层跟随位移淡入淡出。
 *
 * 手势分层：
 * - 顶部 [dragZoneHeight] 这条带子（白条 + 标题栏）**任何时候**按住下拉都能关，不看内容滚动位置；
 * - 带子以外的区域由 [atTop] 决定：内容在顶部才接管，否则整轮放行给列表滚动；
 * - 一旦接管只认纵向下拉，横向位移一律放弃本轮（相关推荐 / 横向标签自己滚自己的）。
 *
 * 关闭统一走 [close]（带滑出动画），系统返回键也走它。
 */
@Composable
fun SheetPanel(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    /** 框顶到容器顶的留白：详情弹框用它把框顶压到原顶栏之下，播放列表为 0。 */
    topInset: Dp = 0.dp,
    /** 框高占「容器高 − topInset」的比例：详情 1f，播放列表 0.55f。 */
    heightFraction: Float = 1f,
    /** 顶部永远可以按住下拉的带子高度（白条 + 标题栏）。 */
    dragZoneHeight: Dp = SheetTokens.GrabberArea,
    /** 带子以外区域是否接管下拉：详情 = 列表是否已在顶部；播放列表同理。 */
    atTop: () -> Boolean = { true },
    scrim: Color = SheetTokens.Scrim,
    /** 是否响应系统返回键关框（详情 / 播放列表都要）。 */
    handleBack: Boolean = true,
    /** 是否画顶部白条。 */
    grabber: Boolean = false,
    content: @Composable ColumnScope.(close: () -> Unit) -> Unit,
) {
    val appearance = LocalAppearance.current
    val sheetBg = WebColors.panel(appearance.navColor, 1f)
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val slopPx = with(density) { SheetTokens.DragSlop.toPx() }
    val insetPx = with(density) { topInset.toPx() }
    val zonePx = with(density) { dragZoneHeight.toPx() }

    // 框高在布局期就能算出来（父高 − 顶部留白，再乘高度比例），不必等 onSizeChanged 回调；
    // 首帧直接把框摆在屏幕下方，再滑到就位，避免「先可见再隐藏」的闪一下，
    // 也不会出现「高度还没量到 → 一直隐藏」的死状态。
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val panelHeightPx =
            ((with(density) { maxHeight.toPx() } - insetPx) * heightFraction).coerceAtLeast(0f)
        var offsetPx by remember { mutableFloatStateOf(panelHeightPx) }
        var closing by remember { mutableStateOf(false) }
        val atTopNow by rememberUpdatedState(atTop)
        val dismissNow by rememberUpdatedState(onDismiss)
        val scrimInteraction = remember { MutableInteractionSource() }
        // 压暗层跟着位移淡入淡出：框滑到哪，底下的黑就到哪，收起时不会留一层黑闪一下
        val scrimAlpha =
            if (panelHeightPx <= 0f) 1f else (1f - offsetPx / panelHeightPx).coerceIn(0f, 1f)

        // 进场 / 回弹 / 收回都写同一个 offsetPx，统一经这一条 Job 调度：
        // 启动前先取消上一个，避免「回弹还没跑完又按下拖动」时动画与手势交替写值（又是抖动）。
        val slideJob = remember { mutableStateOf<Job?>(null) }

        suspend fun slideTo(targetPx: Float, durationMs: Int) {
            animate(
                initialValue = offsetPx,
                targetValue = targetPx,
                animationSpec = tween(durationMs, easing = SheetEasing),
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
            launchSlide(panelHeightPx, SheetTokens.CLOSE_MS) { dismissNow() }
        }

        // 出场：从屏幕下方滑到就位
        LaunchedEffect(Unit) { launchSlide(0f, SheetTokens.OPEN_MS) }

        if (handleBack) BackHandler(enabled = !closing) { requestClose() }

        Box(Modifier.fillMaxSize()) {
            // 压暗层：点框外空白也走同一条收回动画，而不是瞬间消失
            Box(
                Modifier
                    .fillMaxSize()
                    .background(scrim.copy(alpha = scrim.alpha * scrimAlpha))
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
            // 这里的两层布局偏移（topInset 与高度比例）都是固定的、不含 offsetPx，坐标系稳定；
            // 在它里面再套 BoxWithConstraints 是因为面板高度比例也要换算成像素。
            Box(Modifier.fillMaxSize().padding(top = topInset)) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(heightFraction)
                        .align(Alignment.BottomCenter)
                        .pointerInput(panelHeightPx) {
                            awaitEachGesture {
                                val down = awaitFirstDown(
                                    requireUnconsumed = false,
                                    pass = PointerEventPass.Initial,
                                )
                                // 顶部带子（白条 / 标题栏）按下就算「可拉」：内容没滚到顶部也能关框
                                val fromZone = down.position.y <= zonePx
                                var dragging = false
                                var lastY = down.position.y
                                var lastX = down.position.x
                                // 位移锚点只认「当前这一段向下运动」，且在接管那一帧清零。
                                // 拿按下点当锚点会让接管瞬间把「列表还在滚时手指已经走的距离」一次性
                                // 灌进 offsetPx —— 框直接跳一段（先上划浏览、再下滑到顶时最明显）。
                                val run = SheetDrag.Run(down.position.y)
                                while (true) {
                                    val event = awaitPointerEvent(PointerEventPass.Initial)
                                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                    if (!change.pressed) break
                                    val y = change.position.y
                                    val dx = change.position.x - lastX
                                    val dy = y - lastY
                                    lastY = y
                                    lastX = change.position.x
                                    if (!dragging) {
                                        val downRun = run.track(y, dx)
                                        // 每一帧重新判一次（不做「本轮放弃」的粘性标志）：
                                        // 先上划再下滑时，atTop 是在手势中途才变 true 的，必须还能接管；
                                        // 而位移已经是「这一段向下运动」的量，不会带着历史距离。
                                        if (SheetDrag.canStart(
                                                dragY = downRun,
                                                dragX = run.crossRun,
                                                slop = slopPx,
                                                atTop = atTopNow(),
                                                interactive = !closing,
                                                fromZone = fromZone,
                                            )
                                        ) {
                                            // 接管瞬间掐掉还在跑的回弹 / 进场动画，别让两处同时写 offsetPx
                                            slideJob.value?.cancel()
                                            dragging = true
                                            run.anchorAt(y)
                                        } else {
                                            // 明确往下拉才替列表吃掉这几帧，免得列表先 overscroll 拉一下；
                                            // 向上 / 横向一律放行，列表照常滚、相关推荐照常横滑
                                            if ((fromZone || atTopNow()) && SheetDrag.pullFrame(dy, dx)) {
                                                change.consume()
                                            }
                                            continue
                                        }
                                    }
                                    offsetPx = run.offsetOf(y, panelHeightPx)
                                    change.consume()
                                }
                                if (dragging) {
                                    if (SheetDrag.shouldDismiss(offsetPx, panelHeightPx)) {
                                        requestClose()
                                    } else {
                                        launchSlide(0f, SheetTokens.REBOUND_MS)
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
                            if (grabber) SheetGrabber()
                            content(::requestClose)
                        }
                    }
                }
            }
        }
    }
}
