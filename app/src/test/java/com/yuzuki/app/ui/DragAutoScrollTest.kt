package com.yuzuki.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DragAutoScrollTest {
    // 列表可视区 0..1000，边缘感应区 60，每帧最多 9
    private fun step(center: Float) = DragAutoScroll.step(
        center = center,
        viewportStart = 0,
        viewportEnd = 1000,
        edge = 60f,
        maxStep = 9f,
    )

    @Test
    fun 中间区域不滚动() {
        assertEquals(0f, step(500f), 0.001f)
        assertEquals(0f, step(60f), 0.001f)
        assertEquals(0f, step(940f), 0.001f)
    }

    @Test
    fun 贴顶部向上滚且贴得越紧越快() {
        // 负值 = 回看前面的内容，拖到顶部正是要露出上面被挡住的曲目
        assertTrue(step(30f) < 0f)
        assertTrue(step(5f) < step(30f))
        // 封顶：越界再深也不会超过每帧上限
        assertEquals(-9f, step(0f), 0.001f)
        assertEquals(-9f, step(-500f), 0.001f)
    }

    @Test
    fun 贴底部向下滚() {
        assertTrue(step(970f) > 0f)
        assertTrue(step(995f) > step(970f))
        assertEquals(9f, step(1000f), 0.001f)
        assertEquals(9f, step(5000f), 0.001f)
    }

    @Test
    fun 感应区为零或上限为零时不滚() {
        assertEquals(0f, DragAutoScroll.step(0f, 0, 1000, edge = 0f, maxStep = 9f), 0.001f)
        assertEquals(0f, DragAutoScroll.step(0f, 0, 1000, edge = 60f, maxStep = 0f), 0.001f)
    }
}
