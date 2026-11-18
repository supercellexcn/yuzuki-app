package com.yuzuki.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 圆盘侧栏的纯几何：面板宽度随屏宽自适应、4 个圆等分且不小于触控下限、
 * 圆组跟随起手高度并在上下边界被钳制。
 * 圆盘的实际绘制不参与单测，这里只钉住「多宽」「多大」「多高」这些数值规则。
 */
class DrawerGeometryTest {

    @Test
    fun `面板宽度随屏宽按比例缩放并封顶`() {
        // 360dp 屏 → 295.2dp（360 × 0.82）
        assertEquals(295.2f, DrawerGeometry.widthDp(360f), 0.01f)
        // 412dp 屏算出 337.8，被上限压回 320
        assertEquals(320f, DrawerGeometry.widthDp(412f), 0.01f)
        // 极窄屏不会窄过下限
        assertEquals(240f, DrawerGeometry.widthDp(240f), 0.01f)
    }

    @Test
    fun `4 个圆等分可用宽度且不小于触控下限`() {
        // 295.2 面板：扣左右 18 与 3 段 10 的间距 → (295.2 - 36 - 30) / 4
        assertEquals(57.3f, DrawerGeometry.circleDp(295.2f, 18f, 10f), 0.01f)
        // 面板再窄也不低于 44dp，否则点不准
        assertEquals(DrawerGeometry.MIN_CIRCLE_DP, DrawerGeometry.circleDp(240f, 18f, 10f), 0.01f)
        // 直径必须装得下 4 列 + 间距，不能溢出面板
        val width = 295.2f
        val circle = DrawerGeometry.circleDp(width, 18f, 10f)
        assertTrue(4 * circle + 3 * 10f + 2 * 18f <= width + 0.01f)
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
