package com.yuzuki.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DetailSheetDragTest {
    private val slop = 12f

    // ---- 是否接管手势 ----
    @Test
    fun 列表在顶部且纵向下拉过门槛才接管() {
        assertTrue(DetailSheetDrag.canStart(dragY = 40f, dragX = 4f, slop = slop, atTop = true))
    }

    @Test
    fun 列表没到顶时不接管交给列表自己滚() {
        assertFalse(DetailSheetDrag.canStart(dragY = 200f, dragX = 0f, slop = slop, atTop = false))
    }

    @Test
    fun 位移未过起手门槛不算下拉() {
        assertFalse(DetailSheetDrag.canStart(dragY = 10f, dragX = 0f, slop = slop, atTop = true))
    }

    @Test
    fun 横向位移更大时不接管() {
        assertFalse(DetailSheetDrag.canStart(dragY = 30f, dragX = 60f, slop = slop, atTop = true))
        assertFalse(DetailSheetDrag.canStart(dragY = 30f, dragX = -60f, slop = slop, atTop = true))
    }

    @Test
    fun 关闭动画进行中不再接管() {
        assertFalse(
            DetailSheetDrag.canStart(
                dragY = 100f, dragX = 0f, slop = slop, atTop = true, interactive = false,
            ),
        )
    }

    // ---- 提前吃掉事件的判定 ----
    @Test
    fun 只截获明确向下的拉动() {
        // 下拉超过半个门槛且纵向占主导：吃掉，别让列表起 overscroll
        assertTrue(DetailSheetDrag.shouldCapture(dragY = 20f, dragX = 2f, slop = slop))
        // 向上滑必须放行，否则列表滚不动（0.1.70 的回归点）
        assertFalse(DetailSheetDrag.shouldCapture(dragY = -20f, dragX = 2f, slop = slop))
        assertFalse(DetailSheetDrag.shouldCapture(dragY = -1f, dragX = 40f, slop = slop))
        // 横滑放行，相关推荐照常滚
        assertFalse(DetailSheetDrag.shouldCapture(dragY = 8f, dragX = 30f, slop = slop))
        // 位移太小不截获，点击 / 轻点不受影响
        assertFalse(DetailSheetDrag.shouldCapture(dragY = 3f, dragX = 0f, slop = slop))
    }

    // ---- 跟手位移 ----
    @Test
    fun 跟手位移扣掉门槛且夹在零到框高之间() {
        // 起手那一段被门槛吃掉，画面不跳
        assertEquals(0f, DetailSheetDrag.offsetOf(dragY = slop, slop = slop, heightPx = 800f), 0.001f)
        assertEquals(38f, DetailSheetDrag.offsetOf(dragY = 50f, slop = slop, heightPx = 800f), 0.001f)
        // 往上拖不会出现负位移把框顶出界
        assertEquals(0f, DetailSheetDrag.offsetOf(dragY = -300f, slop = slop, heightPx = 800f), 0.001f)
        // 往下拖到底也就停在框高
        assertEquals(800f, DetailSheetDrag.offsetOf(dragY = 5000f, slop = slop, heightPx = 800f), 0.001f)
        // 高度还没量到时不动
        assertEquals(0f, DetailSheetDrag.offsetOf(dragY = 300f, slop = slop, heightPx = 0f), 0.001f)
    }

    // ---- 松手判定 ----
    @Test
    fun 拉过四分之一框高就关() {
        val h = 800f
        assertFalse(DetailSheetDrag.shouldDismiss(h * 0.25f, h))
        assertTrue(DetailSheetDrag.shouldDismiss(h * 0.25f + 1f, h))
        assertTrue(DetailSheetDrag.shouldDismiss(h, h))
    }

    @Test
    fun 高度为零时不判定为关闭() {
        assertFalse(DetailSheetDrag.shouldDismiss(500f, 0f))
    }
}
