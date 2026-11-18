package com.yuzuki.app.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yuzuki.app.ui.theme.WebColors
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * 作品信息流顶端下拉：左半屏切展示形态，右半屏换随机推荐。
 *
 * 交互是「下拉出提示条 → 松手才生效」：拖动时信息流整体下移、顶端露出一条空白区，
 * 里面是「箭头 + 松开以…」的提示；越过门槛箭头翻转朝上，此时松手才真正执行。
 * 没到门槛就松手会弹回去，所以不会出现「拖到一半突然换了一屏」的突兀感。
 */
enum class FeedPullSide { NONE, Left, Right }

/** 纯判定逻辑，不依赖 Android / Compose，JVM 单测可直接断言。 */
object FeedPull {
    /** 纵向位移至少是横向累计的多少倍，才算一次「下拉」而不是斜着滑列表。 */
    const val DOMINANCE = 1.4f

    /** 手指位移 → 露出高度的阻尼：内容跟手但不跟满，拉一点就能看全提示。 */
    const val DAMPING = 0.7f

    /** 露出高度到这个值就算「松手即生效」（≈64dp）。 */
    const val TRIGGER_DP = 64f

    /** 露出高度上限，再多拉也不会把信息流推出屏幕（≈132dp）。 */
    const val MAX_DP = 132f

    /** 当前露出高度（px）：跟手 + 阻尼 + 封顶，反向拉回 0。 */
    fun offsetPx(sumY: Float, maxPx: Float, damping: Float = DAMPING): Float =
        (sumY * damping).coerceIn(0f, maxPx)

    /** 越过门槛 = 松手会执行；同时决定提示箭头朝上还是朝下。 */
    fun armed(revealedPx: Float, thresholdPx: Float): Boolean =
        thresholdPx > 0f && revealedPx >= thresholdPx

    /** 拖动中途横向位移占了主导（用户在横滑翻页 / 拉侧栏）→ 放弃这次下拉。 */
    fun cancelOnHorizontal(sumY: Float, absXAcc: Float): Boolean =
        absXAcc > sumY * 1.2f

    /** 落点在屏幕哪一半：左半 = 切展示形态，右半 = 随机推荐；宽度未知时不判定。 */
    fun side(downX: Float, widthPx: Float): FeedPullSide = when {
        widthPx <= 0f -> FeedPullSide.NONE
        downX < widthPx / 2f -> FeedPullSide.Left
        else -> FeedPullSide.Right
    }
}

/**
 * 挂在信息流容器上：拖出提示条期间**消费**位移（信息流不再滚动、也不做系统的 overscroll 拉伸），
 * 只在列表停在顶部时起手；其余情况下完全放行，不影响正常滑动与横滑翻页。
 *
 * @param atTop     列表是否停在顶部；不满足时整段手势都不参与
 * @param onDrag    拖动中回调（露出高度 px、落点侧），用于实时驱动提示条
 * @param onRelease 松手回调，由调用方按自己的门槛判定是否执行
 * @param onCancel  手势被判定为横滑而放弃
 */
fun Modifier.feedPullGesture(
    atTop: () -> Boolean,
    onDrag: (revealedPx: Float, side: FeedPullSide) -> Unit,
    onRelease: (revealedPx: Float, side: FeedPullSide) -> Unit,
    onCancel: () -> Unit,
): Modifier = pointerInput(Unit) {
    val startSlop = 6.dp.toPx()
    val maxPx = FeedPull.MAX_DP.dp.toPx()
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        if (!atTop()) return@awaitEachGesture
        val side = FeedPull.side(down.position.x, size.width.toFloat())
        if (side == FeedPullSide.NONE) return@awaitEachGesture

        var lastX = down.position.x
        var lastY = down.position.y
        var sumY = 0f
        var absX = 0f
        var dragging = false
        var cancelled = false

        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            val change = event.changes.firstOrNull { it.id == down.id } ?: break
            if (!change.pressed) break
            val dy = change.position.y - lastY
            val dx = change.position.x - lastX
            lastX = change.position.x
            lastY = change.position.y
            sumY += dy
            absX += abs(dx)

            if (!dragging) {
                // 起手要「明显向下」：横滑翻页 / 右滑拉侧栏都不会被当成下拉
                if (sumY > startSlop && sumY > absX * FeedPull.DOMINANCE) dragging = true
            }
            if (dragging) {
                if (FeedPull.cancelOnHorizontal(sumY, absX)) {
                    cancelled = true
                    break
                }
                // 吃掉位移：信息流不再跟着滚，也不会出现系统的 overscroll 拉伸
                change.consume()
                onDrag(FeedPull.offsetPx(sumY, maxPx), side)
            }
        }

        when {
            cancelled -> onCancel()
            dragging -> onRelease(FeedPull.offsetPx(sumY, maxPx), side)
        }
    }
}

/**
 * 顶端露出的提示条：一条空白区 + 居中的「箭头 + 松开以…」。
 *
 * 箭头默认朝下（继续拉），越过门槛弹簧翻成朝上（可以松手）；文字随露出高度淡入。
 *
 * `reveal` 用 lambda 传进来是刻意的：高度在**布局阶段**读、透明度在**绘制阶段**读，
 * 拖动时不会每帧重组整个信息流，只有「是否越过门槛」这一件事会触发重组。
 */
@Composable
fun FeedPullHint(
    side: FeedPullSide,
    reveal: () -> Float,
    thresholdPx: Float,
    modifier: Modifier = Modifier,
) {
    val text = when (side) {
        FeedPullSide.Right -> "松开以获取随机推荐"
        else -> "松开以切换展示模式"
    }
    val armed by remember { derivedStateOf { FeedPull.armed(reveal(), thresholdPx) } }
    val rotation by animateFloatAsState(
        targetValue = if (armed) 180f else 0f,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessLow),
        label = "feedPullArrow",
    )
    val tint = if (armed) MaterialTheme.colorScheme.primary else WebColors.Muted

    Box(
        modifier = modifier
            .clipToBounds()
            .layout { measurable, constraints ->
                val h = reveal().roundToInt().coerceIn(0, constraints.maxHeight)
                val placeable = measurable.measure(
                    Constraints.fixed(constraints.maxWidth, h),
                )
                layout(placeable.width, h) { placeable.place(0, 0) }
            },
        contentAlignment = Alignment.Center,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.graphicsLayer {
                alpha = if (thresholdPx <= 0f) 0f else (reveal() / thresholdPx).coerceIn(0f, 1f)
            },
        ) {
            // ChevronRight 转 90° 即朝下；armed 时再多转 180° 变成朝上
            ChevronRightIcon(tint, Modifier.size(14.dp).rotate(90f + rotation))
            Text(
                text = text,
                fontSize = 12.sp,
                color = tint,
                maxLines = 1,
                modifier = Modifier.padding(start = 6.dp),
            )
        }
    }
}
