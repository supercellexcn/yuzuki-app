package com.yuzuki.app.ui.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.roundToInt

/**
 * 断言配色公式与网页端 useAppearance.ts 完全一致。
 * 期望值不是手写的，而是用同一套算法在脚本里独立复算得到（主题色 #a78bfa）。
 * alpha 由 Compose 以 8bit 存储，故用 0.01 容差比较。
 */
class WebColorsTest {

    private fun channel(c: Color): Triple<Int, Int, Int> = Triple(
        (c.red * 255).roundToInt(),
        (c.green * 255).roundToInt(),
        (c.blue * 255).roundToInt(),
    )

    @Test
    fun panelMixesThemeColorLikeWeb() {
        // mix((27,30,36), (167,139,250), 0.22) = (58,54,83)
        assertEquals(Triple(58, 54, 83), channel(WebColors.panel("#a78bfa", 1f)))
    }

    @Test
    fun panelFallsBackToBaseColorWithoutTheme() {
        assertEquals(Triple(27, 30, 36), channel(WebColors.panel(null, 1f)))
    }

    @Test
    fun controlUsesWebRgbTimesOpacity() {
        val c = WebColors.control(0.35f)
        assertEquals(Triple(38, 43, 51), channel(c))
        assertEquals(0.35f, c.alpha, 0.01f)
    }

    @Test
    fun cardMetaIsThemeColorOverlay() {
        val c = WebColors.cardMeta("#a78bfa")
        assertEquals(Triple(167, 139, 250), channel(c))
        assertEquals(0.26f, c.alpha, 0.01f)
    }

    @Test
    fun accentBrightensOnlyWhenContrastIsLow() {
        // #a78bfa 在 accent-soft 上只有 4.368:1，低于 AA 的 4.5，提亮到 k=0.05
        assertEquals("#AB91FA", WebColors.toHex(WebColors.accent("#a78bfa")))
        // 本来就够亮的主题色保持原样
        assertEquals("#FFFFFF", WebColors.toHex(WebColors.accent("#ffffff")))
    }

    @Test
    fun foregroundOnAccentPrefersDark() {
        // 白 2.57:1 / 深 7.52:1
        assertEquals("#0B0E12", WebColors.toHex(WebColors.foregroundOn(WebColors.accent("#a78bfa"))))
    }

    @Test
    fun tagColorsKeepThemeChannelAndOpacity() {
        val genre = WebColors.tagBg("#f2a65a", 0.3f)
        assertEquals(Triple(242, 166, 90), channel(genre))
        assertEquals(0.3f, genre.alpha, 0.01f)
        val actor = WebColors.tagBg("#7bc86c", 1f)
        assertEquals(Triple(123, 200, 108), channel(actor))
        assertEquals(1f, actor.alpha, 0.01f)
    }

    @Test
    fun topbarUsesThemeColorTimesPanelOpacity() {
        val bar = WebColors.topbar("#a78bfa", 0.6f)
        assertEquals(Triple(167, 139, 250), channel(bar))
        assertEquals(0.6f, bar.alpha, 0.01f)
    }

    @Test
    fun pageTokensMatchWebRootVariables() {
        assertEquals("#121417", WebColors.toHex(WebColors.PageBg))
        assertEquals("#E8EBF0", WebColors.toHex(WebColors.Text))
        assertEquals("#9AA3B0", WebColors.toHex(WebColors.Muted))
        assertEquals("#2A2E37", WebColors.toHex(WebColors.Border))
        assertTrue(WebColors.accent("#a78bfa").alpha > 0.99f)
    }
}
