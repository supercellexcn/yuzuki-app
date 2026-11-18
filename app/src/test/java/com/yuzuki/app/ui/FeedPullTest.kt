package com.yuzuki.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 信息流顶端下拉的纯判定：阻尼露出高度、松手门槛、横向取消、左右半屏落点，
 * 以及随机模式的出口（右半屏越过门槛悬停 3 秒退出）。
 * 手势本体与动画不参与单测，这里只钉住「露出多少」「算不算到位」这些数值规则。
 */
class FeedPullTest {

    private val maxPx = 350f       // ≈132dp @ 2.68x
    private val thresholdPx = 170f // ≈64dp @ 2.68x

    @Test
    fun `露出高度按阻尼跟手并封顶`() {
        // 手指下拉 100px → 露出 70px（阻尼 0.7）
        assertEquals(70f, FeedPull.offsetPx(sumY = 100f, maxPx = maxPx), 0.01f)
        // 拉过头也不超过上限
        assertEquals(maxPx, FeedPull.offsetPx(sumY = 10_000f, maxPx = maxPx), 0.01f)
        // 手指反向（往上）露出一律为 0，不会出现负高度
        assertEquals(0f, FeedPull.offsetPx(sumY = -200f, maxPx = maxPx), 0.01f)
    }

    @Test
    fun `越过门槛才算松手即生效`() {
        assertFalse(FeedPull.armed(revealedPx = thresholdPx - 1f, thresholdPx = thresholdPx))
        assertTrue(FeedPull.armed(revealedPx = thresholdPx, thresholdPx = thresholdPx))
        assertTrue(FeedPull.armed(revealedPx = thresholdPx + 40f, thresholdPx = thresholdPx))
        // 门槛为 0（未测量）时不判定为生效
        assertFalse(FeedPull.armed(revealedPx = 300f, thresholdPx = 0f))
    }

    @Test
    fun `拖动中途转为横滑则取消`() {
        // 一路向下：不取消
        assertFalse(FeedPull.cancelOnHorizontal(sumY = 200f, absXAcc = 20f))
        // 横向累计超过纵向的 1.2 倍：说明用户在横滑翻页 / 拉侧栏
        assertTrue(FeedPull.cancelOnHorizontal(sumY = 100f, absXAcc = 150f))
    }

    @Test
    fun `左右半屏落点`() {
        assertEquals(FeedPullSide.Left, FeedPull.side(downX = 100f, widthPx = 1080f))
        assertEquals(FeedPullSide.Right, FeedPull.side(downX = 900f, widthPx = 1080f))
        // 正中间算右半（严格小于才归左）
        assertEquals(FeedPullSide.Right, FeedPull.side(downX = 540f, widthPx = 1080f))
        assertEquals(FeedPullSide.NONE, FeedPull.side(downX = 100f, widthPx = 0f))
    }

    @Test
    fun `右半屏下拉进入随机并累加批次号`() {
        val before = LibraryState.randomEpoch
        val first = LibraryState.pullRandom()
        assertTrue("第一次应是「新进入随机」", first)
        assertTrue(LibraryState.randomMode)
        assertEquals(before + 1, LibraryState.randomEpoch)

        // 已在随机模式：再拉一次仍是换一批，批次号继续加，页面据此重新请求
        val second = LibraryState.pullRandom()
        assertFalse("已在随机模式时不算新进入", second)
        assertTrue(LibraryState.randomMode)
        assertEquals(before + 2, LibraryState.randomEpoch)
    }

    @Test
    fun `只有右半屏越过门槛且在随机模式才进入悬停计时`() {
        assertTrue(FeedPull.holdEligible(FeedPullSide.Right, dragging = true, armed = true, enabled = true))
        // 左半屏是切展示形态，没有退出语义
        assertFalse(FeedPull.holdEligible(FeedPullSide.Left, dragging = true, armed = true, enabled = true))
        // 还没拖到门槛 / 还没开始拖
        assertFalse(FeedPull.holdEligible(FeedPullSide.Right, dragging = true, armed = false, enabled = true))
        assertFalse(FeedPull.holdEligible(FeedPullSide.Right, dragging = false, armed = true, enabled = true))
        // 常规列表里右半屏下拉是「进入随机」，不该被当成退出
        assertFalse(FeedPull.holdEligible(FeedPullSide.Right, dragging = true, armed = true, enabled = false))
        assertFalse(FeedPull.holdEligible(FeedPullSide.NONE, dragging = true, armed = true, enabled = true))
    }

    @Test
    fun `悬停读秒按整秒递减到 0 触发`() {
        // 起手立刻显示 3，整秒 1s / 2s 后变 2 / 1，满 3s 变 0
        assertEquals(3, FeedPull.holdSecondsLeft(elapsedMs = 0L))
        assertEquals(3, FeedPull.holdSecondsLeft(elapsedMs = 1L))
        assertEquals(3, FeedPull.holdSecondsLeft(elapsedMs = 999L))
        assertEquals(2, FeedPull.holdSecondsLeft(elapsedMs = 1_000L))
        assertEquals(2, FeedPull.holdSecondsLeft(elapsedMs = 1_999L))
        assertEquals(1, FeedPull.holdSecondsLeft(elapsedMs = 2_000L))
        assertEquals(1, FeedPull.holdSecondsLeft(elapsedMs = 2_999L))
        assertEquals(0, FeedPull.holdSecondsLeft(elapsedMs = 3_000L))
        // 表停了也不会变成负数
        assertEquals(0, FeedPull.holdSecondsLeft(elapsedMs = 5_000L))
        // 自定义门槛同样按整秒算
        assertEquals(2, FeedPull.holdSecondsLeft(elapsedMs = 500L, holdMs = 2_000L))
        assertEquals(0, FeedPull.holdSecondsLeft(elapsedMs = 2_000L, holdMs = 2_000L))
    }

    @Test
    fun `退出随机只生效一次`() {
        LibraryState.exitRandom()
        LibraryState.pullRandom()
        assertTrue(LibraryState.randomMode)

        assertTrue("在随机模式里退出成功", LibraryState.exitRandom())
        assertFalse(LibraryState.randomMode)
        // 已在常规列表：再次退出不生效，避免重复提示
        assertFalse(LibraryState.exitRandom())
    }
}
