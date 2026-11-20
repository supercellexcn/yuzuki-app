package com.yuzuki.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 图片查看器缩放 / 平移的数值规则（双击放大、捏合边界、Fit 后尺寸）。
 * 只断言纯函数，不依赖 Android / Compose。
 */
class ViewerZoomTest {

    @Test
    fun `倍率夹在自适应与上限之间`() {
        assertEquals(ViewerZoom.MIN_SCALE, ViewerZoom.clampScale(0.2f), 1e-4f)
        assertEquals(ViewerZoom.MAX_SCALE, ViewerZoom.clampScale(9f), 1e-4f)
        assertEquals(2f, ViewerZoom.clampScale(2f), 1e-4f)
        // 异常值（手势算出 NaN / Infinity）回落到自适应，不能让画面直接消失
        assertEquals(ViewerZoom.MIN_SCALE, ViewerZoom.clampScale(Float.NaN), 1e-4f)
        assertEquals(ViewerZoom.MAX_SCALE, ViewerZoom.clampScale(Float.POSITIVE_INFINITY), 1e-4f)
    }

    @Test
    fun `只有明显放大才算已放大`() {
        assertFalse(ViewerZoom.isZoomed(1f))
        // 捏合松手停在 1.005 不该挡住翻页
        assertFalse(ViewerZoom.isZoomed(1.005f))
        assertTrue(ViewerZoom.isZoomed(1.02f))
        assertTrue(ViewerZoom.isZoomed(ViewerZoom.DOUBLE_TAP_SCALE))
    }

    @Test
    fun `双击在自适应与放大之间切换`() {
        assertEquals(ViewerZoom.DOUBLE_TAP_SCALE, ViewerZoom.doubleTapTarget(1f), 1e-4f)
        assertEquals(ViewerZoom.MIN_SCALE, ViewerZoom.doubleTapTarget(2.5f), 1e-4f)
        // 已经比双击档更大时，双击仍然是还原
        assertEquals(ViewerZoom.MIN_SCALE, ViewerZoom.doubleTapTarget(4f), 1e-4f)
    }

    @Test
    fun `双击放大的平移量让点击点停在原处`() {
        // 自适应、未平移时点右侧 (100, 50)：放大 2.5 档后视图要往左移 1.5 倍点击位置
        val (x, y) = ViewerZoom.panKeeping(100f, 50f, 0f, 0f, 1f, 2.5f)
        assertEquals(-150f, x, 1e-3f)
        assertEquals(-75f, y, 1e-3f)

        // 该点的屏幕位置在缩放前后必须一致：screen = pan + u * scale，u = (p - pan) / scale
        val u = (100f - 0f) / 1f
        assertEquals(100f, x + u * 2.5f, 1e-3f)

        // 再双击还原：同一公式把平移带回 0
        val (bx, by) = ViewerZoom.panKeeping(100f, 50f, x, y, 2.5f, 1f)
        assertEquals(0f, bx, 1e-3f)
        assertEquals(0f, by, 1e-3f)
    }

    @Test
    fun `以中心点缩放不需要平移`() {
        val (x, y) = ViewerZoom.panKeeping(0f, 0f, 0f, 0f, 1f, 3f)
        assertEquals(0f, x, 1e-4f)
        assertEquals(0f, y, 1e-4f)
    }

    @Test
    fun `平移边界按 Fit 后尺寸计算`() {
        // 竖图 Fit 后在 1080×1920 屏上显示为 960×1920：横向多出的部分才是可拖范围
        assertEquals(0f, ViewerZoom.panLimit(1080f, 960f, 1f), 1e-4f)
        // 960 × 1.25 = 1200，左右各多出 60
        assertEquals(60f, ViewerZoom.panLimit(1080f, 960f, 1.25f), 1e-4f)
        // 高度已经铺满：放大 2 倍后上下各可拖半屏
        assertEquals(960f, ViewerZoom.panLimit(1920f, 1920f, 2f), 1e-4f)
        // 内容比视口小（理论上不该出现）也不允许反向拖动
        assertEquals(0f, ViewerZoom.panLimit(1080f, 200f, 1f), 1e-4f)
    }

    @Test
    fun `平移量被夹在边界内`() {
        assertEquals(0f, ViewerZoom.clampPan(120f, 0f), 1e-4f)
        assertEquals(100f, ViewerZoom.clampPan(120f, 100f), 1e-4f)
        assertEquals(-100f, ViewerZoom.clampPan(-120f, 100f), 1e-4f)
        assertEquals(80f, ViewerZoom.clampPan(80f, 100f), 1e-4f)
    }

    @Test
    fun `Fit 后尺寸按短边对齐`() {
        // 竖向图 720×1440 进 1080×1920：受高度限制，按 1.333 缩放
        val (w1, h1) = ViewerZoom.fittedSize(1080f, 1920f, 720f, 1440f)
        assertEquals(960f, w1, 1e-3f)
        assertEquals(1920f, h1, 1e-3f)
        // 横向图 1440×720：受宽度限制
        val (w2, h2) = ViewerZoom.fittedSize(1080f, 1920f, 1440f, 720f)
        assertEquals(1080f, w2, 1e-3f)
        assertEquals(540f, h2, 1e-3f)
        // 尺寸未知（首帧还没解码完）时退化为铺满，边界按视口算
        val (w3, h3) = ViewerZoom.fittedSize(1080f, 1920f, 0f, 0f)
        assertEquals(1080f, w3, 1e-3f)
        assertEquals(1920f, h3, 1e-3f)
    }
}
