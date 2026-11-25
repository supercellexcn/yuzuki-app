package com.yuzuki.app.ui.theme

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Easing
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * 底部弹出层的统一令牌（2026-11-25 收口）。
 *
 * 应用内一共三处「从下往上滑出的框」：作品详情、播放列表、抽屉里的筛选弹层。
 * 以前它们各写各的 —— 详情 24dp / 播放列表 12dp / 筛选走 M3 默认 28dp，白条 64×5 与 M3 默认 32×4 并存，
 * 竖向动画只有详情弹框写了时长。现在全部读这里，**不要再在页面里硬编码圆角、白条尺寸或时长**。
 *
 * 取值来源（用户 2026-11-25 定）：圆角沿用播放列表的 12dp，白条沿用详情弹框的 64×5dp。
 */
object SheetTokens {
    /** 顶部圆角：详情 / 播放列表 / 筛选弹层共用。底部贴着内容区底边，所以只圆上面两角。 */
    val CornerRadius = 12.dp

    /** 顶部抓手（白条）宽度：三处一致。 */
    val GrabberWidth = 64.dp

    /** 白条本体高度。 */
    val GrabberHeight = 5.dp

    /**
     * 白条占位高度：白条居中放在这条带子里。
     * 它同时是「按下就一定能下拉关闭」的热区 —— 内容没滚到顶部也能靠它关框。
     */
    val GrabberArea = 18.dp

    /** 起手门槛：位移小于它仍算轻点 / 手指抖动，不接管手势。 */
    val DragSlop = 6.dp

    /** 松手判定：拉过框高的这个比例就关，否则弹回。 */
    const val DISMISS_FRACTION = 0.25f

    /** 进场（屏幕下方 → 就位）。 */
    const val OPEN_MS = 220

    /** 未过关闭门槛时弹回。 */
    const val REBOUND_MS = 200

    /** 收回（下滑 / 点框外空白 / 返回键 / 点 ✕，全部走这 250ms）。 */
    const val CLOSE_MS = 250

    /** 底层压暗：够暗到不抢框内内容，又留着形影能认出后面是什么。 */
    val Scrim = Color(0x99000000)
}

/** 弹出层顶部两角圆角。 */
val SheetShape = RoundedCornerShape(topStart = SheetTokens.CornerRadius, topEnd = SheetTokens.CornerRadius)

/** 弹出层滑动动画的缓动，与时长一起构成统一的手感。 */
val SheetEasing: Easing = FastOutSlowInEasing
