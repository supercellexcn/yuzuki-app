package com.yuzuki.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 信息流顶端下拉的纯判定：门槛、方向占比、左右半屏落点。
 * 手势本体（pointerInput）不参与单测，这里只钉住「什么时候算一次下拉」「落到哪一半」。
 */
class FeedPullTest {

    private val slop = 150f   // ≈56dp @ 2.68x

    @Test
    fun `纵向下拉过门槛才触发`() {
        assertTrue(FeedPull.triggered(sumY = 200f, absXAcc = 10f, thresholdPx = slop))
        // 差一点点
        assertFalse(FeedPull.triggered(sumY = 140f, absXAcc = 0f, thresholdPx = slop))
    }

    @Test
    fun `向上拉不触发`() {
        assertFalse(FeedPull.triggered(sumY = -400f, absXAcc = 0f, thresholdPx = slop))
    }

    @Test
    fun `斜着滑列表不触发`() {
        // 纵向过了门槛，但横向累计同样大：这是斜滑，不是下拉
        assertFalse(FeedPull.triggered(sumY = 300f, absXAcc = 260f, thresholdPx = slop))
        // 横滑翻页 / 右滑拉侧栏
        assertFalse(FeedPull.triggered(sumY = 160f, absXAcc = 300f, thresholdPx = slop))
    }

    @Test
    fun `左右半屏落点`() {
        assertEquals(FeedPullSide.Left, FeedPull.side(downX = 100f, widthPx = 1080f))
        assertEquals(FeedPullSide.Right, FeedPull.side(downX = 900f, widthPx = 1080f))
        // 正中间算右半（与「过半才算右边」的直觉一致：严格小于才归左）
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
