package com.yuzuki.app.ui

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlin.math.abs

/**
 * 作品信息流顶端下拉手势：左半屏切视图形态，右半屏换随机推荐。
 *
 * 只有「列表停在顶部」时才有意义 —— 顶部再往下拉本来就没有内容可露出来，
 * 正好把这段无效空白变成快捷手势。
 */
enum class FeedPullSide { NONE, Left, Right }

/** 纯判定逻辑，不依赖 Android / Compose，JVM 单测可直接断言。 */
object FeedPull {
    /** 纵向下拉门槛（dp）：比列表的 touchSlop 大得多，轻轻一抖不会换模式。 */
    const val TRIGGER_DP = 56f

    /** 纵向位移至少是横向累计的多少倍，才算一次「下拉」而不是斜着滑列表。 */
    const val DOMINANCE = 1.4f

    /** 是否已经够格触发：向下、过门槛、且明显偏纵向（向上拉 / 横滑都不触发）。 */
    fun triggered(sumY: Float, absXAcc: Float, thresholdPx: Float): Boolean =
        sumY > thresholdPx && sumY > absXAcc * DOMINANCE

    /** 落点在屏幕哪一半：左半 = 切视图，右半 = 随机推荐；宽度未知时不判定。 */
    fun side(downX: Float, widthPx: Float): FeedPullSide = when {
        widthPx <= 0f -> FeedPullSide.NONE
        downX < widthPx / 2f -> FeedPullSide.Left
        else -> FeedPullSide.Right
    }
}

/**
 * 挂在信息流根容器上：Initial pass 旁路观察（只读坐标、不消费事件），
 * 因此列表照常滚动，也不会和 Pager 的横滑翻页抢手势。
 *
 * @param atTop  列表是否停在顶部；不满足时整段手势都不参与
 * @param onPull 一次手势最多触发一次
 */
fun Modifier.feedPullAtTop(
    atTop: () -> Boolean,
    onPull: (FeedPullSide) -> Unit,
): Modifier = pointerInput(Unit) {
    val thresholdPx = FeedPull.TRIGGER_DP.dp.toPx()
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        if (!atTop()) return@awaitEachGesture
        val widthPx = size.width.toFloat()
        var lastX = down.position.x
        var lastY = down.position.y
        var sumY = 0f
        var absX = 0f
        var fired = false
        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            val change = event.changes.firstOrNull { it.id == down.id } ?: break
            if (!change.pressed) break
            sumY += change.position.y - lastY
            absX += abs(change.position.x - lastX)
            lastX = change.position.x
            lastY = change.position.y
            if (!fired && FeedPull.triggered(sumY, absX, thresholdPx)) {
                fired = true
                val side = FeedPull.side(down.position.x, widthPx)
                if (side != FeedPullSide.NONE) onPull(side)
            }
        }
    }
}
