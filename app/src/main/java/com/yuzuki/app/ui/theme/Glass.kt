package com.yuzuki.app.ui.theme

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import com.yuzuki.app.data.AppSettings
import kotlin.math.roundToInt

/** 纯 Kotlin 外观计算，JVM 单测可直接断言（不依赖 Android/Compose）。 */
object GlassSpec {
    data class Flame(val count: Int, val kind: String)

    /** web 端图片模式销量火焰分档。 */
    fun flame(sales: Int?): Flame {
        val v = sales ?: return Flame(0, "none")
        return when {
            v >= 19999 -> Flame(1, "rainbow")
            v >= 10000 -> Flame(minOf(3, 1 + (v - 10000) / 3000), "red")
            v >= 6000 -> Flame(3, "yellow")
            v >= 3000 -> Flame(2, "yellow")
            v >= 1000 -> Flame(1, "yellow")
            else -> Flame(1, "gray")
        }
    }

    fun columns(cardSize: String, cardWidth: Int, availableDp: Int): Int {
        val target = when (cardSize) {
            "small" -> 110
            "large" -> 240
            "custom" -> cardWidth.coerceIn(80, 400)
            else -> 160
        }
        return (availableDp / target).coerceIn(1, 8)
    }

    fun alpha(percent: Int): Float = percent.coerceIn(0, 100) / 100f
    fun blurDp(percent: Int): Float = percent.coerceIn(0, 100) / 4f
    fun animationMs(seconds: Double): Int = (seconds.coerceIn(0.0, 1.0) * 1000).roundToInt()

    /** 主题色过暗时按 WCAG 对比度沿原色相自动提亮。 */
    fun brighten(hex: String, minLuminance: Double = 0.42): String {
        val rgb = parseHex(hex) ?: return "#B69AF6"
        var (r, g, b) = rgb
        var guard = 0
        while (luminance(r, g, b) < minLuminance && guard++ < 12) {
            r += ((255 - r) * 0.12).roundToInt()
            g += ((255 - g) * 0.12).roundToInt()
            b += ((255 - b) * 0.12).roundToInt()
        }
        return "#%02X%02X%02X".format(r, g, b)
    }

    fun parseHex(hex: String): Triple<Int, Int, Int>? {
        val s = hex.trim().removePrefix("#")
        if (s.length != 6) return null
        return runCatching {
            Triple(s.substring(0, 2).toInt(16), s.substring(2, 4).toInt(16), s.substring(4, 6).toInt(16))
        }.getOrNull()
    }

    private fun linear(c: Int): Double {
        val v = c / 255.0
        return if (v <= 0.04045) v / 12.92 else Math.pow((v + 0.055) / 1.055, 2.4)
    }

    private fun luminance(r: Int, g: Int, b: Int): Double =
        0.2126 * linear(r) + 0.7152 * linear(g) + 0.0722 * linear(b)
}

/**
 * 服务端外观设置。配色公式与网页端 `useAppearance.ts` 完全一致：
 * 主题色、面板 / 控件 / 标签透明度都直接参与渲染，保证两端观感一致。
 */
data class Appearance(
    /** 处理后的强调色（原色对比度不足时已按 WCAG 提亮）。 */
    val themeColor: String = "#A78BFA",
    /** 用户原始主题色：面板混色、卡片信息区、顶栏底色都用它。 */
    val navColor: String = "#A78BFA",
    val actorColor: String = "#7BC86C",
    val tagColor: String = "#F2A65A",
    val actorTagAlpha: Float = 1f,
    val genreTagAlpha: Float = 0.3f,
    val panelAlpha: Float = 0.62f,
    val filelistAlpha: Float = 0.62f,
    val controlAlpha: Float = 0.7f,
    val panelBlur: Float = 0f,
    val tagBlur: Float = 0f,
    val wallpaperBlur: Float = 0f,
    val wallpaperEnabled: Boolean = true,
    val wallpaperVersion: Long = 0L,
    val animationMs: Int = 250,
    val cardSize: String = "medium",
    val cardWidth: Int = 180,
) {
    companion object {
        fun from(s: AppSettings): Appearance {
            val nav = s.themeColorNav.takeIf { it.isNotBlank() } ?: "#A78BFA"
            return Appearance(
                // 与 web 的 readableAccent 一致：够亮就保持原色，不够才提亮
                themeColor = WebColors.toHex(WebColors.accent(nav)),
                navColor = nav,
                actorColor = s.themeColorActor.takeIf { it.isNotBlank() } ?: "#7BC86C",
                tagColor = s.themeColorTag.takeIf { it.isNotBlank() } ?: "#F2A65A",
                actorTagAlpha = GlassSpec.alpha(s.actorTagOpacity),
                genreTagAlpha = GlassSpec.alpha(s.genreTagOpacity),
                panelAlpha = GlassSpec.alpha(s.panelOpacity),
                filelistAlpha = GlassSpec.alpha(s.filelistOpacity),
                controlAlpha = GlassSpec.alpha(s.controlOpacity),
                panelBlur = GlassSpec.blurDp(s.panelBlur),
                tagBlur = GlassSpec.blurDp(s.tagBlur),
                wallpaperBlur = GlassSpec.blurDp(s.wallpaperBlur),
                wallpaperEnabled = s.wallpaperMobileEnabled,
                animationMs = GlassSpec.animationMs(s.animationDuration),
                cardSize = s.cardSize,
                cardWidth = s.cardWidth,
            )
        }

        val Default = Appearance()
    }
}

val LocalAppearance = compositionLocalOf { Appearance.Default }

fun composeColor(hex: String, fallback: Long = 0xFFA888F8): Color =
    GlassSpec.parseHex(hex)?.let { (r, g, b) -> Color(0xFF000000 or (r.toLong() shl 16) or (g.toLong() shl 8) or b.toLong()) }
        ?: Color(fallback)

/**
 * Material 3 卡片容器：不透明 surfaceContainerHigh + 圆角，无模糊、无半透明叠层。
 * alpha / blur / highlight 三个参数仅为兼容既有调用点保留，M3 版一律忽略。
 */
@Composable
@Suppress("UNUSED_PARAMETER")
fun GlassSurface(
    modifier: Modifier = Modifier,
    shape: Shape = ShapeContainer,
    alpha: Float = LocalAppearance.current.panelAlpha,
    blur: Float = LocalAppearance.current.panelBlur,
    highlight: Boolean = true,
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    Surface(
        modifier = modifier,
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = 1.dp,
        shadowElevation = 0.dp,
    ) {
        Box(
            modifier = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier,
            content = content,
        )
    }
}

/** 胶囊：按钮 / 搜索 / 排序 / 标签，M3 风格用 secondaryContainer 底色。 */
@Composable
fun GlassPill(
    modifier: Modifier = Modifier,
    alpha: Float = LocalAppearance.current.controlAlpha,
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    Surface(
        modifier = modifier,
        shape = ShapePill,
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        tonalElevation = 0.dp,
    ) {
        Box(
            modifier = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier,
            content = content,
        )
    }
}

/** 列表项 / 树节点：低一档的容器色，保持层级但不牺牲对比度。 */
@Composable
fun GlassItem(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    Surface(
        modifier = modifier,
        shape = ShapeItem,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        contentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = 0.dp,
    ) {
        Box(
            modifier = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier,
            content = content,
        )
    }
}

val ShapeDialog = RoundedCornerShape(YuzukiRadius.Card)
