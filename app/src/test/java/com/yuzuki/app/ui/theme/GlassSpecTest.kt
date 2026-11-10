package com.yuzuki.app.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GlassSpecTest {
    @Test
    fun flame分档与web端一致() {
        assertEquals(GlassSpec.Flame(0, "none"), GlassSpec.flame(null))
        assertEquals(GlassSpec.Flame(1, "gray"), GlassSpec.flame(500))
        assertEquals(GlassSpec.Flame(1, "yellow"), GlassSpec.flame(1500))
        assertEquals(GlassSpec.Flame(2, "yellow"), GlassSpec.flame(4000))
        assertEquals(GlassSpec.Flame(3, "yellow"), GlassSpec.flame(7000))
        assertEquals(GlassSpec.Flame(1, "red"), GlassSpec.flame(12000))
        assertEquals(GlassSpec.Flame(3, "red"), GlassSpec.flame(18000))
        assertEquals(GlassSpec.Flame(1, "rainbow"), GlassSpec.flame(20000))
    }

    @Test
    fun 卡片列数按尺寸与屏宽计算() {
        assertEquals(3, GlassSpec.columns("small", 160, 360))
        assertEquals(2, GlassSpec.columns("medium", 160, 360))
        assertEquals(1, GlassSpec.columns("large", 160, 360))
        assertEquals(4, GlassSpec.columns("custom", 200, 800))
        assertTrue(GlassSpec.columns("small", 160, 100) >= 1)
    }

    @Test
    fun 透明度与动画时长映射() {
        assertEquals(0.85f, GlassSpec.alpha(85), 0.0001f)
        assertEquals(1f, GlassSpec.alpha(200), 0.0001f)
        assertEquals(0f, GlassSpec.alpha(-3), 0.0001f)
        assertEquals(250, GlassSpec.animationMs(0.25))
        assertEquals(0, GlassSpec.animationMs(-1.0))
        assertEquals(1000, GlassSpec.animationMs(3.0))
    }

    @Test
    fun 过暗主题色自动提亮且保持合法hex() {
        val bright = GlassSpec.brighten("#000000")
        assertTrue(bright.startsWith("#"))
        assertEquals(7, bright.length)
        val rgb = GlassSpec.parseHex(bright)
        assertNotNull(rgb)
        assertTrue(rgb!!.first > 0 || rgb.second > 0 || rgb.third > 0)
        assertFalse(GlassSpec.brighten("not-a-color") == "not-a-color")
    }
}
