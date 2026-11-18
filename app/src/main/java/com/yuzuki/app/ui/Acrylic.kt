package com.yuzuki.app.ui

import android.content.Context
import android.os.Build
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.CompositingStrategy
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.roundToInt

/**
 * 亚克力毛玻璃（底部导航用）。
 *
 * 取样：内容根容器在**同一次绘制里录两份** ——
 * 1. 正常画面：直接 `drawContent()`，不走任何中间层；
 * 2. 栏身条：再画一遍 `drawContent()`，缩到 1/8 分辨率、只留「导航栏身后那一段」录进小层。
 * 导航栏把这份小层放大 8 倍画出来 —— 双线性插值就是磨砂，等价于一次强盒式模糊；
 * 有 RenderEffect 的机器再叠一层轻模糊让糊得更均匀。
 *
 * 为什么必须「录两份」而不是「复用同一份层」：`GraphicsLayer` 背后是 `RenderNode`，
 * 同一个 node 在本帧被第二个目标再次 `drawLayer` 时不会得到像素（第一次绘制已占用它），
 * 于是取样层为空、栏身只剩半透明底色 —— 这正是前几版「有透明、没磨砂」的原因。
 * 这里每个层都只被画一次，因此取样一定有内容。
 *
 * 降采样不依赖任何平台特性（Android 8 起都有磨砂）；`RenderEffect` 只是可选加强。
 */
val AcrylicBlurSupported: Boolean
    get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

/** 降采样倍数：越大越糊，8 约等于 24dp 的盒式模糊。 */
private const val DOWNSCALE = 8f

private const val DIAG_NAME = "acrylic.log"

private fun diagFile(context: Context): File =
    (context.getExternalFilesDir(null) ?: context.filesDir).resolve(DIAG_NAME)

/** 头几次 + 每 300 次落盘，排障用；稳定后可整段删掉。 */
private class DiagGate {
    private val count = AtomicInteger(0)
    fun allow(): Boolean {
        val n = count.incrementAndGet()
        return n <= 3 || n % 300 == 0
    }
}

private fun diag(file: File, site: String, message: String) {
    runCatching {
        if (file.exists() && file.length() > 96 * 1024) return
        file.appendText("$site ${System.currentTimeMillis()} $message\n")
    }
}

/** 导航栏取样用的内容小层。 */
class AcrylicBackdrop internal constructor(internal val strip: GraphicsLayer)

@Composable
fun rememberAcrylicBackdrop(): AcrylicBackdrop {
    val layer = rememberGraphicsLayer()
    return remember(layer) { AcrylicBackdrop(layer) }
}

/**
 * 挂在「要被透视的内容」根容器上。
 *
 * @param barHeight 导航栏高度：决定录哪一段（内容底部这一段就是栏身身后那一条）
 */
@Composable
fun Modifier.acrylicSource(backdrop: AcrylicBackdrop, barHeight: Dp): Modifier {
    val context = LocalContext.current
    val file = remember(context) { diagFile(context) }
    val gate = remember { DiagGate() }
    val barPx = with(LocalDensity.current) { barHeight.toPx() }

    return drawWithContent {
        // ① 正常画面：直接把内容画到当前画布，不经过任何中间层
        this.drawContent()

        // ② 栏身条：再画一遍内容，缩到 1/8 录进小层（这一层只由导航栏画一次）
        val w = size.width
        val h = size.height
        if (w > 0f && h > 0f && barPx > 0f) {
            val sw = (w / DOWNSCALE).roundToInt().coerceAtLeast(1)
            val sh = (barPx / DOWNSCALE).roundToInt().coerceAtLeast(1)
            // drawContent() 在这里是第二次调用：Compose 允许，但如果哪天不允许，
            // 也不能把整个页面拖垮 —— 失败就退化成「只透不糊」，并落盘留证。
            val ok = runCatching {
                backdrop.strip.record(this, layoutDirection, IntSize(sw, sh)) {
                    scale(1f / DOWNSCALE, 1f / DOWNSCALE, pivot = Offset.Zero) {
                        // 上移 (内容高 − 栏高)：只留底部那一条
                        translate(top = -(h - barPx)) { this@drawWithContent.drawContent() }
                    }
                }
            }.isSuccess
            if (gate.allow()) {
                diag(
                    file,
                    "source",
                    "ok=$ok draw=${w.toInt()}x${h.toInt()} bar=$barPx small=${sw}x$sh " +
                        "layer=${backdrop.strip.size}",
                )
            }
        }
    }
}

/**
 * 导航栏的亚克力底：磨砂取样 + 深色半透明 + 顶部 1px 高光。
 *
 * @param tint        压在被模糊内容上的深色，越不透明越看不出磨砂
 * @param blurEnabled 播放器页自带整屏模糊背景，那一页不必再取样
 */
@Composable
fun AcrylicBarSurface(
    backdrop: AcrylicBackdrop,
    tint: Color,
    modifier: Modifier = Modifier,
    blurRadius: Dp = 14.dp,
    blurEnabled: Boolean = true,
) {
    val context = LocalContext.current
    val file = remember(context) { diagFile(context) }
    val gate = remember { DiagGate() }
    val softRadiusPx = with(LocalDensity.current) { blurRadius.toPx() }

    SideEffect {
        backdrop.strip.compositingStrategy = CompositingStrategy.Offscreen
        backdrop.strip.renderEffect = if (blurEnabled && AcrylicBlurSupported) {
            BlurEffect(softRadiusPx, softRadiusPx, TileMode.Clamp)
        } else {
            null
        }
    }

    Box(
        modifier = modifier.drawBehind {
            val strip = backdrop.strip.size
            if (blurEnabled && strip.width > 0 && strip.height > 0) {
                // 放大 8 倍画回来：双线性插值 = 磨砂
                clipRect {
                    scale(DOWNSCALE, DOWNSCALE, pivot = Offset.Zero) { drawLayer(backdrop.strip) }
                }
                if (gate.allow()) {
                    diag(file, "bar", "draw strip=${strip.width}x${strip.height} bar=${size.width}x${size.height}")
                }
            }
            // 深色半透明：透 = 看得见信息流，糊 = 上面那层磨砂
            drawRect(tint)
            // 顶部一条高光：玻璃边缘
            drawRect(Color(0x1FFFFFFF), size = Size(size.width, 1.dp.toPx()))
        },
    )
}
