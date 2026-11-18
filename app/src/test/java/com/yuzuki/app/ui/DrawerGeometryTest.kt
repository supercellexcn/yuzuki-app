package com.yuzuki.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 浮动圆控件的纯几何：滑入基准宽度、4 个圆等分屏宽并限制直径、圆组宽度、
 * 以及圆组跟随起手高度并在上下边界被钳制。
 * 圆的实际绘制不参与单测，这里只钉住「多宽」「多大」「多高」这些数值规则。
 */
class DrawerGeometryTest {

    @Test
    fun `滑入基准宽度按屏宽比例`() {
        // 360dp 屏 → 288dp（360 × 0.8）
        assertEquals(288f, DrawerGeometry.swipeDp(360f), 0.01f)
        // 极窄屏按 240dp 兜底，避免滑一点点就全开
        assertEquals(192f, DrawerGeometry.swipeDp(240f), 0.01f)
        assertEquals(192f, DrawerGeometry.swipeDp(200f), 0.01f)
    }

    @Test
    fun `4 个圆等分屏宽`() {
        // 360 屏：扣左右 20 与 3 段 12 的间距 → (360 - 40 - 36) / 4
        assertEquals(71f, DrawerGeometry.circleDp(360f), 0.01f)
        // 320 屏
        assertEquals(61f, DrawerGeometry.circleDp(320f), 0.01f)
        // 大屏封顶，不能无限变大
        assertEquals(DrawerGeometry.MAX_CIRCLE_DP, DrawerGeometry.circleDp(412f), 0.01f)
        // 极窄屏保底，否则点不准
        assertEquals(DrawerGeometry.MIN_CIRCLE_DP, DrawerGeometry.circleDp(240f), 0.01f)
    }

    @Test
    fun `常规屏 4 个圆不会溢出屏宽`() {
        val screen = 360f
        val circle = DrawerGeometry.circleDp(screen)
        val group = DrawerGeometry.groupWidthDp(circle)
        assertEquals(4 * circle + 3 * 12f, group, 0.01f)
        assertTrue(group + 2 * DrawerGeometry.PAD_DP <= screen + 0.01f)
        // 圆直径必须容得下圆内的图标 + 小字
        assertTrue(circle >= 56f)
    }

    @Test
    fun `圆组以起手高度为中心`() {
        // 可用高度 800、组高 200、留白 12 → 起手在正中时，顶边 = 800/2 - 200/2
        assertEquals(300f, DrawerGeometry.groupTopPx(0.5f, 800f, 200f, 12f), 0.01f)
        // 从上往下 1/4 处起手 → 200 - 100
        assertEquals(100f, DrawerGeometry.groupTopPx(0.25f, 800f, 200f, 12f), 0.01f)
    }

    @Test
    fun `起手太靠上或太靠下时圆组被钳制`() {
        // 起手在屏幕最上沿：顶边不能为负，压回留白处
        assertEquals(12f, DrawerGeometry.groupTopPx(0f, 800f, 200f, 12f), 0.01f)
        // 起手在屏幕最下沿：底边不能超出，顶边顶到 800 - 200 - 12
        assertEquals(588f, DrawerGeometry.groupTopPx(1f, 800f, 200f, 12f), 0.01f)
        // 越界输入（比例 <0 / >1）按边界处理
        assertEquals(12f, DrawerGeometry.groupTopPx(-0.5f, 800f, 200f, 12f), 0.01f)
        assertEquals(588f, DrawerGeometry.groupTopPx(1.5f, 800f, 200f, 12f), 0.01f)
    }

    @Test
    fun `可用高度装不下圆组时退回顶部留白`() {
        // 横屏 / 小窗：可用高度比圆组还矮，不能算出负数顶边
        assertEquals(12f, DrawerGeometry.groupTopPx(0.5f, 150f, 200f, 12f), 0.01f)
    }
}
