package com.yuzuki.app.ui

import android.os.Build
import android.util.Log
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
import androidx.compose.ui.graphics.layer.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/**
 * 亚克力毛玻璃（底部导航用）。
 *
 * 两步取样：
 * 1. 内容根容器录一份离屏层（画面与不录时完全一致）；
 * 2. 导航栏再建一个小层，把身后那一条**缩到 1/8 分辨率**画进去，再放大 8 倍画回栏身 ——
 *    放大时的双线性插值天然就是磨砂糊感，等价于一次强盒式模糊，且不吃 RenderEffect。
 *
 * 为什么不用 `Modifier.blur` / `RenderEffect` 作主路径：它们在真机上会静默失效（层属性、
 * 修饰符顺序、父层裁剪任何一个不对就毫无效果，且无法从代码侧判定）。降采样方案是纯 2D 变换，
 * 全平台/全版本一致，Android 8 上也有磨砂（这点比 RenderEffect 更强）。
 * 有 RenderEffect 的机器再叠一层轻模糊，让糊得更均匀；没有也不影响。
 *
 * @param tint 压在被模糊内容上的深色，越不透明越看不出磨砂
 * @param blurEnabled 播放器页自带整屏模糊背景，那一页不必再取样（省一次层绘制）
 */
val AcrylicBlurSupported: Boolean
    get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

private const val TAG = "YuzukiAcrylic"

/** 降采样倍数：越大越糊，8 约等于 24dp 的盒式模糊。 */
private const val DOWNSCALE = 8f

/** 一份可供导航栏取样的内容离屏层。 */
class AcrylicBackdrop internal constructor(internal val layer: GraphicsLayer)

@Composable
fun rememberAcrylicBackdrop(): AcrylicBackdrop {
    val layer = rememberGraphicsLayer()
    return remember(layer) { AcrylicBackdrop(layer) }
}

/**
 * 挂到「要被透视的内容」根容器上：录进离屏层后立刻画出来，观感与不用它时一致。
 * 记录下来的是本帧的显示列表，导航栏在同一个 frame 里取样，不会慢一帧。
 */
fun Modifier.acrylicSource(backdrop: AcrylicBackdrop): Modifier = drawWithContent {
    val w = size.width.roundToInt()
    val h = size.height.roundToInt()
    if (w <= 0 || h <= 0) {
        drawContent()
        return@drawWithContent
    }
    // DrawScope 本身就是 Density：直接传它（`density` 属性是 1f/2f/3f 的缩放比，不是 Density）
    backdrop.layer.record(this@drawWithContent, layoutDirection, IntSize(w, h)) {
        this@drawWithContent.drawContent()
    }
    drawLayer(backdrop.layer)
}

/**
 * 导航栏的亚克力底：磨砂取样 + 深色半透明 + 顶部 1px 高光。
 */
@Composable
fun AcrylicBarSurface(
    backdrop: AcrylicBackdrop,
    tint: Color,
    modifier: Modifier = Modifier,
    blurRadius: Dp = 14.dp,
    blurEnabled: Boolean = true,
) {
    val strip = rememberGraphicsLayer()
    val softRadiusPx = with(LocalDensity.current) { blurRadius.toPx() }
    val active = blurEnabled
    val logged = remember { java.util.concurrent.atomic.AtomicBoolean(false) }

    SideEffect {
        strip.compositingStrategy = CompositingStrategy.Offscreen
        // 有 RenderEffect 的机器额外糊一层（糊的是已经降采样的内容，成本很低）；
        // 没有也不影响 —— 主效果来自降采样放大
        strip.renderEffect = if (active && AcrylicBlurSupported) {
            BlurEffect(softRadiusPx, softRadiusPx, TileMode.Clamp)
        } else {
            null
        }
    }

    Box(
        modifier = modifier.drawBehind {
            if (active) {
                val sourceH = backdrop.layer.size.height
                val w = size.width
                val h = size.height
                if (sourceH > 0 && w > 0f && h > 0f) {
                    val sw = (w / DOWNSCALE).roundToInt().coerceAtLeast(1)
                    val sh = (h / DOWNSCALE).roundToInt().coerceAtLeast(1)
                    if (logged.compareAndSet(false, true)) {
                        Log.i(
                            TAG,
                            "strip: srcH=$sourceH barH=${h.toInt()} small=${sw}x$sh " +
                                "api=${Build.VERSION.SDK_INT}",
                        )
                    }
                    // 缩小 1/8 录一条：内容整体上移 (内容高 − 栏高)，只留栏身后那一段
                    strip.record(this, layoutDirection, IntSize(sw, sh)) {
                        scale(1f / DOWNSCALE, 1f / DOWNSCALE, pivot = Offset.Zero) {
                            translate(top = -(sourceH - h)) { drawLayer(backdrop.layer) }
                        }
                    }
                    // 放大 8 倍画回来：双线性插值 = 磨砂
                    clipRect {
                        scale(DOWNSCALE, DOWNSCALE, pivot = Offset.Zero) { drawLayer(strip) }
                    }
                }
            }
            // 深色半透明：透 = 看得见信息流，糊 = 上面那层磨砂，两者叠加才是亚克力
            drawRect(tint)
            // 顶部一条高光：玻璃边缘，也让内容穿过去时有一个明确的界
            drawRect(Color(0x1FFFFFFF), size = Size(size.width, 1.dp.toPx()))
        },
    )
}
