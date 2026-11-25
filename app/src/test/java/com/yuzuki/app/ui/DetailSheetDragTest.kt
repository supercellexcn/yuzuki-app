package com.yuzuki.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

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

    // ---- 坐标系必须稳定（2026-11-25 A 方案的回归点）----
    // 弹框用 graphicsLayer.translationY 跟手，而图层平移会参与指针坐标转换：
    // 图层下移多少，交给 pointerInput 的 change.position 就被反向减掉多少。
    // 所以「读手势的节点」绝不能落在被平移的子树里，否则 dragY 与 offsetPx 互为输入。
    @Test
    fun 稳定坐标系下手指停住框也停住且全程单调() {
        val finger = listOf(20f, 40f, 60f, 80f, 100f, 120f, 120f, 120f, 120f)
        val offsets = finger.map { d -> DetailSheetDrag.offsetOf(dragY = d, slop = slop, heightPx = 800f) }
        assertEquals(offsets[5], offsets[8], 0.001f)
        offsets.zipWithNext().forEach { (a, b) ->
            assertTrue("offset 回跳：$a -> $b", b >= a)
        }
    }

    @Test
    fun 手势层跟着位移层移动会形成负反馈回跳() {
        // 旧结构：dragY = 手指位移 − offsetPx，增益 −1，手指停住后 offset 逐帧来回跳 —— 就是「上下抖动」。
        val finger = listOf(20f, 40f, 60f, 80f, 100f, 120f, 120f, 120f, 120f)
        var offset = 0f
        val offsets = ArrayList<Float>(finger.size)
        finger.forEach { d ->
            offset = DetailSheetDrag.offsetOf(dragY = d - offset, slop = slop, heightPx = 800f)
            offsets += offset
        }
        val tail = offsets.takeLast(4)
        val jumps = tail.zipWithNext().count { (a, b) -> abs(b - a) > 0.5f }
        assertTrue("手势不动时 offset 仍在来回摆：$tail", jumps >= 2)
    }
}
