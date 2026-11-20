package com.yuzuki.app.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class ReorderSlideTest {
    private val h = 42f

    @Test
    fun 往上拖时中间的行往下让位() {
        // 3 → 1：1、2 两行整体下移一格，被拖行自己不动（由手势位移负责）
        assertEquals(0f, ReorderSlide.shiftOf(0, 3, 1, h), 0.001f)
        assertEquals(h, ReorderSlide.shiftOf(1, 3, 1, h), 0.001f)
        assertEquals(h, ReorderSlide.shiftOf(2, 3, 1, h), 0.001f)
        assertEquals(0f, ReorderSlide.shiftOf(3, 3, 1, h), 0.001f)
        assertEquals(0f, ReorderSlide.shiftOf(4, 3, 1, h), 0.001f)
    }

    @Test
    fun 往下拖时中间的行往上让位() {
        // 1 → 3：2、3 两行整体上移一格
        assertEquals(0f, ReorderSlide.shiftOf(0, 1, 3, h), 0.001f)
        assertEquals(0f, ReorderSlide.shiftOf(1, 1, 3, h), 0.001f)
        assertEquals(-h, ReorderSlide.shiftOf(2, 1, 3, h), 0.001f)
        assertEquals(-h, ReorderSlide.shiftOf(3, 1, 3, h), 0.001f)
        assertEquals(0f, ReorderSlide.shiftOf(4, 1, 3, h), 0.001f)
    }

    @Test
    fun 相邻换一位时只有对方让位() {
        assertEquals(h, ReorderSlide.shiftOf(2, 3, 2, h), 0.001f)
        assertEquals(0f, ReorderSlide.shiftOf(1, 3, 2, h), 0.001f)
        assertEquals(-h, ReorderSlide.shiftOf(2, 1, 2, h), 0.001f)
        assertEquals(0f, ReorderSlide.shiftOf(3, 1, 2, h), 0.001f)
    }

    @Test
    fun 落点没变或行高未知时不让位() {
        repeat(5) { i ->
            assertEquals(0f, ReorderSlide.shiftOf(i, 2, 2, h), 0.001f)
            assertEquals(0f, ReorderSlide.shiftOf(i, 2, 4, 0f), 0.001f)
        }
    }
}
