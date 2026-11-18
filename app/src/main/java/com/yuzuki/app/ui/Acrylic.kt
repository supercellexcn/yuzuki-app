package com.yuzuki.app.ui

import android.os.Build
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.clipRect
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
 * 做法：内容根容器录一份离屏层（画面与不录时完全一致），导航栏只把自己**身后那一条**
 * 从层里取出来、录进一个「栏高 + 多一条」的小层，给这个小层挂 `BlurEffect` 再画回来，
 * 最后压一层深色半透明 —— 于是信息流是「透过磨砂玻璃」看见的，而不是被一块纯黑挡死。
 *
 * 多录一条（一个模糊半径的高度）是为了让模糊在栏身顶端有真实像素可采，不会出现一条硬边。
 *
 * 限制：`BlurEffect` 需要 Android 12（API 31）的 RenderEffect。更低版本没有等价能力，
 * 退化成「透而不糊」的深色半透明（仍有透视，只是没有磨砂），不会崩、也不会闪。
 */
val AcrylicBlurSupported: Boolean
    get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

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
 * 导航栏的亚克力底：模糊取样 + 深色半透明 + 顶部 1px 高光。
 *
 * @param tint        压在被模糊内容上的深色，越不透明越看不出磨砂
 * @param blurEnabled 播放器页自带整屏模糊背景，那一页不必再取样（省一次层绘制）
 */
@Composable
fun AcrylicBarSurface(
    backdrop: AcrylicBackdrop,
    tint: Color,
    modifier: Modifier = Modifier,
    blurRadius: Dp = 26.dp,
    blurEnabled: Boolean = true,
) {
    // 栏身自己的一条离屏层：只有栏高大小，模糊成本与整屏模糊不是一个量级
    val strip = rememberGraphicsLayer()
    val radiusPx = with(LocalDensity.current) { blurRadius.toPx() }
    val active = blurEnabled && AcrylicBlurSupported

    // 效果挂在层上（而不是 Modifier.blur），这样「取样层」与「被模糊的层」是同一个，
    // 不受修饰符顺序 / 父节点绘制方式影响
    SideEffect {
        strip.renderEffect = if (active) BlurEffect(radiusPx, radiusPx, TileMode.Clamp) else null
    }

    Box(
        modifier = modifier.drawBehind {
            if (active) {
                val sourceH = backdrop.layer.size.height
                val w = size.width.roundToInt()
                val h = size.height.roundToInt()
                val pad = radiusPx.roundToInt()
                if (sourceH > 0 && w > 0 && h > 0) {
                    // 栏身后那一条 + 顶端多留一个模糊半径，模糊才有源可采
                    val layerH = h + pad
                    strip.record(this, layoutDirection, IntSize(w, layerH)) {
                        translate(top = -(sourceH - layerH).toFloat()) { drawLayer(backdrop.layer) }
                    }
                    clipRect {
                        translate(top = -pad.toFloat()) { drawLayer(strip) }
                    }
                }
            }
            // 深色半透明：透 = 看得见信息流，糊 = 上面那层模糊，两者叠加才是亚克力
            drawRect(tint)
            // 顶部一条高光：玻璃边缘，也让内容穿过去时有一个明确的界
            drawRect(Color(0x1FFFFFFF), size = Size(size.width, 1.dp.toPx()))
        },
    )
}
