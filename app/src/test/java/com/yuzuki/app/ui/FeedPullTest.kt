package com.yuzuki.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 信息流顶端下拉的纯判定：阻尼露出高度、松手门槛、横向取消、左右半屏落点。
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
}
