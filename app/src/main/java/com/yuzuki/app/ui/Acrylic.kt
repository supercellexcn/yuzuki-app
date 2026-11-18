package com.yuzuki.app.ui

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/**
 * 亚克力毛玻璃（底部导航用）。
 *
 * 做法：内容根容器录一份离屏层（画面与不录时完全一致），导航栏只把自己身后那一条
 * 从层里取出来做 `RenderEffect` 模糊，再压一层深色半透明 —— 于是信息流是「透」过去的，
 * 而不是被一块纯黑挡死。
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
 * @param blurEnabled 播放器页自带整屏模糊背景，那一页不必再取样（省一次层绘制）
 */
@Composable
fun AcrylicBarSurface(
    backdrop: AcrylicBackdrop,
    tint: Color,
    modifier: Modifier = Modifier,
    blurRadius: Dp = 22.dp,
    blurEnabled: Boolean = true,
) {
    Box(modifier = modifier) {
        if (blurEnabled && AcrylicBlurSupported) {
            Box(
                Modifier
                    .fillMaxSize()
                    // 栏高只有 65dp，只糊这一条：成本与整屏模糊完全不是一个量级
                    .blur(blurRadius, edgeTreatment = BlurredEdgeTreatment(RectangleShape))
                    .drawBehind {
                        val sourceH = backdrop.layer.size.height
                        if (sourceH <= 0) return@drawBehind
                        // 层里取「栏身后那一条」：把内容整体上移 (内容高 - 栏高)
                        translate(top = -(sourceH - size.height)) {
                            drawLayer(backdrop.layer)
                        }
                    },
            )
        }
        Box(Modifier.fillMaxSize().background(tint))
        // 顶部一条高光：亚克力的「玻璃边缘」，也让内容穿过去时有一个明确的界
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(Color(0x1FFFFFFF)),
        )
    }
}
