package com.yuzuki.app.ui.theme

import androidx.compose.ui.graphics.Color
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * web 端 `useAppearance.ts` + `App.vue :root` 的配色公式移植。
 *
 * 与网页端保持完全一致的原因：网页的层次是靠「半透明底色逐层叠加」堆出来的
 * （顶栏 = 主题色 60% 叠页面底、卡片 = 面板色 60% 叠页面底、信息区 = 主题色 26% 叠卡片…），
 * 任何一处手算合成都会产生偏差。这里同样保留 alpha，交给渲染层按 SRC_OVER 混合。
 *
 * 注意：这里的半透明只用于**底色**，所有文字仍使用与面板色对比度足够的前景，
 * 不会出现网页端毛玻璃那种「文字压在模糊壁上」的清晰度问题。
 */
object WebColors {
    // ---- 页面与文字（App.vue :root）----
    val PageBg = Color(0xFF121417)
    val Border = Color(0xFF2A2E37)
    val Text = Color(0xFFE8EBF0)
    val Muted = Color(0xFF9AA3B0)

    /** Element Plus 浅色主题正文字色（已选条件标签用它）。 */
    val ElTextPrimary = Color(0xFF303133)

    /** 面板基色与主题色混入比例（PANEL_BASE / PANEL_TINT）。 */
    private val PanelBase = intArrayOf(27, 30, 36)
    private const val PanelTint = 0.22f

    /** 控件底色（--control-rgb）。 */
    private val ControlBase = intArrayOf(38, 43, 51)

    /** 卡片下半部分：主题色 26% 叠加（CARD_META_ALPHA）。 */
    private const val CardMetaAlpha = 0.26f

    /** 强调色作为前景时的最低对比度（WCAG AA）。 */
    private const val MinContrast = 4.5

    /** 深色 UI 上选深色前景时用的颜色（--accent-fg 兜底）。 */
    private val FgDark = intArrayOf(11, 14, 18)

    fun panel(navHex: String?, alpha: Float): Color {
        val nav = parse(navHex)
        val c = if (nav == null) PanelBase else mixRgb(PanelBase, nav, PanelTint)
        return rgba(c[0], c[1], c[2], alpha)
    }

    fun cardMeta(navHex: String?): Color {
        val nav = parse(navHex) ?: return Color.Transparent
        return rgba(nav[0], nav[1], nav[2], CardMetaAlpha)
    }

    fun control(alpha: Float): Color = rgba(ControlBase[0], ControlBase[1], ControlBase[2], alpha)

    /** 顶栏底色：主题色 × 面板透明度（--topbar-color）。 */
    fun topbar(navHex: String?, alpha: Float): Color {
        val nav = parse(navHex) ?: return panel(null, alpha)
        return rgba(nav[0], nav[1], nav[2], alpha)
    }

    fun tagBg(hex: String, alpha: Float): Color {
        val c = parse(hex) ?: return Color.Transparent
        return rgba(c[0], c[1], c[2], alpha)
    }

    /** 强调色：原色对比度不足时才沿原色相提亮（readableAccent）。 */
    fun accent(navHex: String?): Color {
        val rgb = parse(navHex) ?: return Color(0xFFA78BFA)
        val onSoft = { c: IntArray -> contrast(c, mixRgb(PanelBase, c, 0.2f)) }
        if (onSoft(rgb) >= MinContrast) return rgba(rgb[0], rgb[1], rgb[2], 1f)
        var k = 0.05f
        while (k < 1f) {
            val candidate = mixRgb(rgb, intArrayOf(255, 255, 255), k)
            if (onSoft(candidate) >= MinContrast) return rgba(candidate[0], candidate[1], candidate[2], 1f)
            k += 0.05f
        }
        return Color.White
    }

    /** 强调色块上的前景：黑 / 白里对比度更高的那个（foregroundOn）。 */
    fun foregroundOn(bg: Color): Color {
        val rgb = intArrayOf(
            (bg.red * 255).roundToInt(),
            (bg.green * 255).roundToInt(),
            (bg.blue * 255).roundToInt(),
        )
        return if (contrast(intArrayOf(255, 255, 255), rgb) >= contrast(FgDark, rgb)) {
            Color.White
        } else {
            rgba(FgDark[0], FgDark[1], FgDark[2], 1f)
        }
    }

    fun mixRgb(a: IntArray, b: IntArray, k: Float): IntArray = intArrayOf(
        (a[0] * (1 - k) + b[0] * k).roundToInt(),
        (a[1] * (1 - k) + b[1] * k).roundToInt(),
        (a[2] * (1 - k) + b[2] * k).roundToInt(),
    )

    /** Color → #rrggbb（web 的 toHex）。 */
    fun toHex(c: Color): String = "#%02X%02X%02X".format(
        (c.red * 255).roundToInt().coerceIn(0, 255),
        (c.green * 255).roundToInt().coerceIn(0, 255),
        (c.blue * 255).roundToInt().coerceIn(0, 255),
    )

    private fun rgba(r: Int, g: Int, b: Int, a: Float): Color =
        Color(r / 255f, g / 255f, b / 255f, a)

    private fun parse(hex: String?): IntArray? {
        val value = hex ?: return null
        val rgb = GlassSpec.parseHex(value) ?: return null
        return intArrayOf(rgb.first, rgb.second, rgb.third)
    }

    private fun linear(c: Int): Double {
        val v = c / 255.0
        return if (v <= 0.03928) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4)
    }

    private fun luminance(c: IntArray): Double =
        0.2126 * linear(c[0]) + 0.7152 * linear(c[1]) + 0.0722 * linear(c[2])

    private fun contrast(a: IntArray, b: IntArray): Double {
        val la = luminance(a)
        val lb = luminance(b)
        return (maxOf(la, lb) + 0.05) / (minOf(la, lb) + 0.05)
    }
}
