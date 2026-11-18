package com.yuzuki.app.data

import com.yuzuki.app.ui.theme.Appearance
import com.yuzuki.app.ui.theme.WebColors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 本机外观：色值规范化、非法回退、透明度夹紧、映射到 [Appearance]（不依赖 Android）。 */
class AppearancePrefsTest {

    @Test
    fun `默认外观与改造前的取值一致`() {
        val p = AppearancePrefs.Default
        assertEquals("#A78BFA", p.navColor)
        assertEquals("#7BC86C", p.actorColor)
        assertEquals("#F2A65A", p.tagColor)
        assertEquals(62, p.panelOpacity)
        assertEquals(70, p.controlOpacity)
        assertEquals(100, p.actorTagOpacity)
        assertEquals(30, p.genreTagOpacity)

        val a = Appearance.from(p)
        assertEquals("#A78BFA", a.navColor)
        assertEquals("#7BC86C", a.actorColor)
        assertEquals("#F2A65A", a.tagColor)
        assertEquals(0.62f, a.panelAlpha, 0.0001f)
        assertEquals(0.62f, a.filelistAlpha, 0.0001f)
        assertEquals(0.7f, a.controlAlpha, 0.0001f)
        assertEquals(1f, a.actorTagAlpha, 0.0001f)
        assertEquals(0.3f, a.genreTagAlpha, 0.0001f)
    }

    @Test
    fun `色值规范化补井号并转大写`() {
        assertEquals("#A78BFA", normalizeHex("#a78bfa"))
        assertEquals("#A78BFA", normalizeHex("a78bfa"))
        assertEquals("#A78BFA", normalizeHex("  #A78bFa  "))
    }

    @Test
    fun `非法色值回退默认色`() {
        assertEquals(AppearancePrefs.DefaultNav, normalizeHex(""))
        assertEquals(AppearancePrefs.DefaultNav, normalizeHex("#12345"))
        assertEquals(AppearancePrefs.DefaultNav, normalizeHex("#GGGGGG"))
        assertEquals(AppearancePrefs.DefaultActor, normalizeHex("紫", AppearancePrefs.DefaultActor))
        // fallback 自己也不合法时仍回退默认紫，不会返回坏值
        assertEquals(AppearancePrefs.DefaultNav, normalizeHex("bad", "worse"))
    }

    @Test
    fun `isValidHex 只认 6 位十六进制`() {
        assertTrue(isValidHex("#a78bfa"))
        assertTrue(isValidHex("A78BFA"))
        assertFalse(isValidHex("#A78BF"))
        assertFalse(isValidHex("#A78BFA0"))
        assertFalse(isValidHex("#A78BFZ"))
        assertFalse(isValidHex(""))
    }

    @Test
    fun `透明度越界被夹到 0 到 100`() {
        val p = AppearancePrefs(
            panelOpacity = 150,
            controlOpacity = -20,
            actorTagOpacity = 1000,
            genreTagOpacity = 0,
        ).sanitized()
        assertEquals(100, p.panelOpacity)
        assertEquals(0, p.controlOpacity)
        assertEquals(100, p.actorTagOpacity)
        assertEquals(0, p.genreTagOpacity)
    }

    @Test
    fun `存储里的坏色值读出来是默认色`() {
        val p = AppearancePrefs(navColor = "紫", actorColor = "#12", tagColor = "").sanitized()
        assertEquals(AppearancePrefs.DefaultNav, p.navColor)
        assertEquals(AppearancePrefs.DefaultActor, p.actorColor)
        assertEquals(AppearancePrefs.DefaultTag, p.tagColor)
    }

    @Test
    fun `自定义暗色只提亮强调色不动原色`() {
        val a = Appearance.from(AppearancePrefs(navColor = "#101010"))
        // 原始主题色保留，用于面板混色 / 顶栏底色
        assertEquals("#101010", a.navColor)
        // 强调色按对比度提亮，不再是原始值
        assertNotEquals("#101010", a.themeColor)
        // 默认紫也要按同一套 readableAccent 规则算，保持与网页端一致
        assertEquals(
            WebColors.toHex(WebColors.accent(AppearancePrefs.DefaultNav)),
            Appearance.from(AppearancePrefs.Default).themeColor,
        )
    }

    @Test
    fun `面板与文件列表共用同一透明度`() {
        val a = Appearance.from(AppearancePrefs(panelOpacity = 40))
        assertEquals(0.4f, a.panelAlpha, 0.0001f)
        assertEquals(a.panelAlpha, a.filelistAlpha, 0.0001f)
    }

    @Test
    fun `预设色板全是合法色值`() {
        (AppearancePalette.nav + AppearancePalette.actor + AppearancePalette.tag).forEach {
            assertTrue("非法预设色值：$it", isValidHex(it))
            assertEquals(it.uppercase(), normalizeHex(it))
        }
    }
}
