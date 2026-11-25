package com.yuzuki.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SheetDragTest {
    private val slop = 12f

    // ---- 是否接管手势 ----
    @Test
    fun 列表在顶部且纵向下拉过门槛才接管() {
        assertTrue(SheetDrag.canStart(dragY = 40f, dragX = 4f, slop = slop, atTop = true))
    }

    @Test
    fun 列表没到顶时不接管交给列表自己滚() {
        assertFalse(SheetDrag.canStart(dragY = 200f, dragX = 0f, slop = slop, atTop = false))
    }

    @Test
    fun 顶部带子按下时任何滚动位置都能拉() {
        // 2026-11-25：按住白条 / 标题栏下拉就能关框，不必先把内容滚回顶部
        assertTrue(
            SheetDrag.canStart(dragY = 40f, dragX = 4f, slop = slop, atTop = false, fromZone = true),
        )
        // 带子里也要过门槛、也要纵向占主导，轻点和横滑照样放行
        assertFalse(
            SheetDrag.canStart(dragY = 10f, dragX = 0f, slop = slop, atTop = false, fromZone = true),
        )
        assertFalse(
            SheetDrag.canStart(dragY = 30f, dragX = 90f, slop = slop, atTop = false, fromZone = true),
        )
    }

    @Test
    fun 位移未过起手门槛不算下拉() {
        assertFalse(SheetDrag.canStart(dragY = 10f, dragX = 0f, slop = slop, atTop = true))
    }

    @Test
    fun 横向位移更大时不接管() {
        assertFalse(SheetDrag.canStart(dragY = 30f, dragX = 60f, slop = slop, atTop = true))
        assertFalse(SheetDrag.canStart(dragY = 30f, dragX = -60f, slop = slop, atTop = true))
    }

    @Test
    fun 关闭动画进行中不再接管() {
        assertFalse(
            SheetDrag.canStart(
                dragY = 100f, dragX = 0f, slop = slop, atTop = true, interactive = false,
            ),
        )
        // 带子也不例外：收回动画跑起来后不再接受新的拖动
        assertFalse(
            SheetDrag.canStart(
                dragY = 100f, dragX = 0f, slop = slop, atTop = false,
                interactive = false, fromZone = true,
            ),
        )
    }

    // ---- 提前吃掉事件的判定 ----
    @Test
    fun 只截获明确向下的拉动() {
        // 逐帧判断：这一帧在往下拉就替列表吃掉，别让它起 overscroll
        assertTrue(SheetDrag.pullFrame(dy = 20f, dx = 2f))
        // 向上滑必须放行，否则列表滚不动（0.1.70 的回归点）
        assertFalse(SheetDrag.pullFrame(dy = -20f, dx = 2f))
        assertFalse(SheetDrag.pullFrame(dy = -1f, dx = 40f))
        // 横滑放行，相关推荐照常滚
        assertFalse(SheetDrag.pullFrame(dy = 8f, dx = 30f))
        // 没位移（手指停住）不吃，点击 / 轻点不受影响
        assertFalse(SheetDrag.pullFrame(dy = 0f, dx = 0f))
    }

    // ---- 跟手位移（锚点式）----
    @Test
    fun 跟手位移从锚点起算且夹在零到框高之间() {
        val run = SheetDrag.Run(1000f)
        // 锚点就是按下点：位移 = 手指位置 − 锚点，不再额外扣门槛（接管帧已经清零）
        assertEquals(0f, run.offsetOf(1000f, 800f), 0.001f)
        assertEquals(38f, run.offsetOf(1038f, 800f), 0.001f)
        // 往锚点上方拖不会出现负位移把框顶出界
        assertEquals(0f, run.offsetOf(700f, 800f), 0.001f)
        // 往下拖到底也就停在框高
        assertEquals(800f, run.offsetOf(9000f, 800f), 0.001f)
        // 高度还没量到时不动
        assertEquals(0f, run.offsetOf(1300f, 0f), 0.001f)
    }

    // ---- 松手判定 ----
    @Test
    fun 拉过四分之一框高就关() {
        val h = 800f
        assertFalse(SheetDrag.shouldDismiss(h * 0.25f, h))
        assertTrue(SheetDrag.shouldDismiss(h * 0.25f + 1f, h))
        assertTrue(SheetDrag.shouldDismiss(h, h))
    }

    @Test
    fun 高度为零时不判定为关闭() {
        assertFalse(SheetDrag.shouldDismiss(500f, 0f))
    }

    // ---- 接管瞬间不能跳一段（0.2.4 的回归点）----
    // 现象：先上划浏览内容，再下滑回顶部、继续下拉，弹框一下跳到很下面。
    // 旧实现拿「按下点」当锚点 + 用「当前帧 atTop」判接管，列表滚到顶那一刻把
    // 手指此前累积的整段距离一次性灌进 offsetPx。
    @Test
    fun 先上划再下滑接管时位移从零起算() {
        val run = SheetDrag.Run(1000f)
        // 上划：锚点跟着手指上移，这一段「向下运动」始终是 0
        assertEquals(0f, run.track(y = 700f, dx = 0f), 0.001f)
        assertEquals(0f, run.track(y = 400f, dx = 0f), 0.001f)
        // 反向下滑 600（这段手势里列表还没到顶，一直不接管）
        assertEquals(600f, run.track(y = 1000f, dx = 0f), 0.001f)
        // 列表终于到顶、接管：锚点钉在当前手指位置 → 位移 0，不跳
        run.anchorAt(1000f)
        assertEquals(0f, run.offsetOf(1000f, 800f), 0.001f)
        // 之后逐帧跟手，步长等于手指步长
        assertEquals(20f, run.offsetOf(1020f, 800f), 0.001f)
        assertEquals(40f, run.offsetOf(1040f, 800f), 0.001f)
    }

    @Test
    fun 列表滚到顶那一帧的累计位移不再等于手指总位移() {
        // 同一段手势：按下 → 上划 300 → 下滑 500（中途到顶）
        val run = SheetDrag.Run(1000f)
        run.track(y = 700f, dx = 0f)          // 上划
        val atTopRun = run.track(y = 1200f, dx = 0f)   // 下滑 500，越过按下点 200
        // 旧实现此时 dragY = 200，接管就会直接跳 200 像素；新实现锚点式跟踪给出的是
        // 「本段向下运动」的 500，随后 anchorAt 清零，所以两处都不会带出历史位移。
        assertEquals(500f, atTopRun, 0.001f)
        run.anchorAt(1200f)
        assertEquals(0f, run.offsetOf(1200f, 800f), 0.001f)
    }

    @Test
    fun 接管前的手指回升会重开一段() {
        val run = SheetDrag.Run(1000f)
        assertEquals(100f, run.track(y = 1100f, dx = 0f), 0.001f)
        // 回升 50：这一段作废，重新计时
        assertEquals(0f, run.track(y = 1050f, dx = 0f), 0.001f)
        assertEquals(10f, run.track(y = 1060f, dx = 0f), 0.001f)
    }

    // ---- 坐标系必须稳定（2026-11-25 A 方案的回归点）----
    // 弹框用 graphicsLayer.translationY 跟手，而图层平移会参与指针坐标转换：
    // 图层下移多少，交给 pointerInput 的 change.position 就被反向减掉多少。
    // 所以「读手势的节点」绝不能落在被平移的子树里，否则 dragY 与 offsetPx 互为输入。
    @Test
    fun 稳定坐标系下手指停住框也停住且全程单调() {
        val run = SheetDrag.Run(0f)
        val finger = listOf(20f, 40f, 60f, 80f, 100f, 120f, 120f, 120f, 120f)
        val offsets = finger.map { y -> run.offsetOf(y, 800f) }
        assertEquals(offsets[5], offsets[8], 0.001f)
        offsets.zipWithNext().forEach { (a, b) ->
            assertTrue("offset 回跳：$a -> $b", b >= a)
        }
    }

    @Test
    fun 手势层跟着位移层移动会跟不到手指() {
        // 旧结构：手指位移要先减掉 offsetPx（增益 −1），手指走 120、框只走 60 —— 跟手只有一半，
        // 且手指停住后 offset 还在原地摆。这就是必须把手势层放在平移层外面的原因。
        val run = SheetDrag.Run(0f)
        val finger = listOf(20f, 40f, 60f, 80f, 100f, 120f, 120f, 120f, 120f)
        val offsets = ArrayList<Float>(finger.size)
        var offset = 0f
        finger.forEach { d ->
            offset = run.offsetOf(d - offset, 800f)
            offsets += offset
        }
        assertTrue("手指 120 只跟到 ${offsets.last()}", offsets.last() <= 120f * 0.6f)
    }
}
