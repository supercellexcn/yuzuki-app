package com.yuzuki.app.ui

import com.yuzuki.app.ui.theme.SheetTokens
import kotlin.math.abs

/**
 * 底部弹出层「下滑关闭」判定（纯函数，JVM 可测）。
 *
 * 详情弹框、播放列表共用这一套规则（2026-11-25 收口，原 `DetailSheetDrag`）：
 * - **顶部带子**（白条 / 标题栏，见 [SheetTokens.GrabberArea]）按下就能下拉关闭，
 *   不管框内内容有没有滚到顶部；
 * - 带子以外的区域仍然要求内容已在顶部才接管，否则正常交还给列表滚动；
 * - 一旦接管就只认纵向下拉，横向位移不作任何响应（相关推荐 / 横向标签走自己的行内滚动）。
 */
object SheetDrag {
    /** 松手时已拉过框高的这个比例就关，否则弹回。 */
    const val DISMISS_FRACTION = SheetTokens.DISMISS_FRACTION

    /**
     * 是否接管本轮手势。
     *
     * @param dragY 手指相对按下点的纵向位移（下为正）
     * @param dragX 手指相对按下点的横向位移
     * @param slop  起手门槛（约 6dp），低于它仍算「轻点 / 微小抖动」
     * @param atTop 框内列表是否已经停在顶部
     * @param interactive 弹框是否还能响应（关闭动画进行中就不接了）
     * @param fromZone 按下点是否落在顶部带子里：是的话不看 [atTop]，任何滚动位置都能拉
     */
    fun canStart(
        dragY: Float,
        dragX: Float,
        slop: Float,
        atTop: Boolean,
        interactive: Boolean = true,
        fromZone: Boolean = false,
    ): Boolean {
        if (!interactive) return false
        if (dragY <= slop) return false
        // 纵向下拉必须明显大于横向位移：横滑是「相关推荐」那一行的事
        if (dragY <= abs(dragX)) return false
        return fromZone || atTop
    }

    /** 跟手位移：扣掉起手门槛（起手不跳），并夹在 0..框高之间（不允许往上顶出界）。 */
    fun offsetOf(dragY: Float, slop: Float, heightPx: Float): Float {
        if (heightPx <= 0f) return 0f
        return (dragY - slop).coerceIn(0f, heightPx)
    }

    /**
     * 还没到起手门槛时要不要先吃掉事件。
     *
     * 只有「明确是往下拉」才吃：否则列表会先接到一两帧 move 起 overscroll 拉伸，
     * 回弹叠在跟手位移上就是抖动 + 边缘阴影。向上滑 / 横向滑一律不吃，
     * 让列表正常滚动、让相关推荐正常横滑。
     */
    fun shouldCapture(dragY: Float, dragX: Float, slop: Float): Boolean =
        dragY > slop && dragY > abs(dragX)

    /** 松手：拉过四分之一就关，否则弹回。 */
    fun shouldDismiss(offsetPx: Float, heightPx: Float): Boolean =
        heightPx > 0f && offsetPx > heightPx * DISMISS_FRACTION
}
