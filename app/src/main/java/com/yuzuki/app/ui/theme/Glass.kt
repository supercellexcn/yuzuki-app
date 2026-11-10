package com.yuzuki.app.ui.theme

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import com.yuzuki.app.data.AppSettings
import kotlin.math.roundToInt

/** 纯 Kotlin 外观计算，JVM 单测可直接断言（不依赖 Android/Compose）。 */
object GlassSpec {
    data class Flame(val count: Int, val kind: String)

    /** web 端图片模式销量火焰分档：≤1000 灰1；1000-2999 黄1；3000-5999 黄2；6000-9999 黄3；10000+ 红1起每3000+1最多3；≥19999 彩色大火。 */
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

    /** 主题色过暗时按 WCAG 对比度沿原色相自动提亮（向白混合，保持色相）。 */
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

data class Appearance(
    val themeColor: String = "#B69AF6",
    val actorColor: String = "#8E7CC3",
    val tagColor: String = "#6E8EC9",
    val actorTagAlpha: Float = 0.8f,
    val genreTagAlpha: Float = 0.8f,
    val panelAlpha: Float = 0.85f,
    val filelistAlpha: Float = 0.85f,
    val controlAlpha: Float = 0.85f,
    val panelBlur: Float = 0f,
    val tagBlur: Float = 0f,
    val wallpaperBlur: Float = 0f,
    val wallpaperEnabled: Boolean = true,
    val wallpaperVersion: Long = 0L,
    val animationMs: Int = 250,
    val cardSize: String = "medium",
    val cardWidth: Int = 160,
) {
    companion object {
        fun from(s: AppSettings) = Appearance(
            themeColor = GlassSpec.brighten(s.themeColorNav.takeIf { it.isNotBlank() } ?: "#B69AF6"),
            actorColor = s.themeColorActor.takeIf { it.isNotBlank() } ?: "#8E7CC3",
            tagColor = s.themeColorTag.takeIf { it.isNotBlank() } ?: "#6E8EC9",
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

        val Default = Appearance()
    }
}

val LocalAppearance = compositionLocalOf { Appearance.Default }

fun composeColor(hex: String, fallback: Long = 0xFFB69AF6): Color =
    GlassSpec.parseHex(hex)?.let { (r, g, b) -> Color(0xFF000000 or (r.toLong() shl 16) or (g.toLong() shl 8) or b.toLong()) }
        ?: Color(fallback)

/** 所有框体的唯一玻璃质感出口：半透明底 + 多段高光渐变 + 1px 内侧高光描边 + 柔和外投影 + 圆角。 */
@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    shape: Shape = ShapeContainer,
    alpha: Float = LocalAppearance.current.panelAlpha,
    blur: Float = LocalAppearance.current.panelBlur,
    highlight: Boolean = true,
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val base = MaterialTheme.colorScheme.surface
    val blurModifier = if (blur > 0.5f && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        Modifier.blur((blur / 6f).dp)
    } else Modifier
    val brush = remember(highlight) {
        if (highlight) Brush.verticalGradient(
            listOf(
                Color.White.copy(alpha = 0.16f),
                Color.White.copy(alpha = 0.05f),
                Color.Transparent,
                Color.Black.copy(alpha = 0.10f),
            ),
        ) else Brush.verticalGradient(
            listOf(Color.White.copy(alpha = 0.06f), Color.Transparent),
        )
    }
    Box(
        modifier = modifier
            .then(blurModifier)
            .clip(shape)
            .background(base.copy(alpha = alpha))
            .background(brush)
            .border(1.dp, Color.White.copy(alpha = 0.16f), shape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        content = content,
    )
}

/** 全胶囊容器：搜索 / 排序 / 按钮 / 标签。 */
@Composable
fun GlassPill(
    modifier: Modifier = Modifier,
    alpha: Float = LocalAppearance.current.controlAlpha,
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit,
) = GlassSurface(
    modifier = modifier,
    shape = ShapePill,
    alpha = alpha,
    blur = 0f,
    onClick = onClick,
    content = content,
)

/** 列表项 / 树节点 / 小卡片。 */
@Composable
fun GlassItem(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit,
) = GlassSurface(
    modifier = modifier,
    shape = ShapeItem,
    alpha = LocalAppearance.current.filelistAlpha,
    blur = 0f,
    highlight = false,
    onClick = onClick,
    content = content,
)

val ShapeDialog = RoundedCornerShape(YuzukiRadius.Card)
