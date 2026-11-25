package com.yuzuki.app.ui

import kotlin.math.abs

/**
 * 详情弹出框「下滑关闭」的判定（纯函数，JVM 可测）。
 *
 * 手势分层规则：框内主体是一个 LazyColumn（作品头卡 / 文件树 / 相关推荐），
 * 所以下拉**只有列表已经在顶部时**才归弹框，否则一律交还给列表做正常浏览。
 * 一旦接管就只认纵向下拉，横向位移不作任何响应（相关推荐的横滑走自己的行内滚动）。
 */
object DetailSheetDrag {
    /** 松手时已拉过框高的这个比例就关，否则弹回。 */
    const val DISMISS_FRACTION = 0.25f

    /**
     * 是否接管本轮手势。
     *
     * @param dragY 手指相对按下点的纵向位移（下为正）
     * @param dragX 手指相对按下点的横向位移
     * @param slop  起手门槛（约 6dp），低于它仍算「轻点 / 微小抖动」
     * @param atTop 框内列表是否已经停在顶部
     * @param interactive 弹框是否还能响应（关闭动画进行中就不接了）
     */
    fun canStart(
        dragY: Float,
        dragX: Float,
        slop: Float,
        atTop: Boolean,
        interactive: Boolean = true,
    ): Boolean {
        if (!interactive || !atTop) return false
        if (dragY <= slop) return false
        // 纵向下拉必须明显大于横向位移：横滑是「相关推荐」那一行的事
        return dragY > abs(dragX)
    }

    /** 跟手位移：扣掉起手门槛（起手不跳），并夹在 0..框高之间（不允许往上顶出界）。 */
    fun offsetOf(dragY: Float, slop: Float, heightPx: Float): Float {
        if (heightPx <= 0f) return 0f
        return (dragY - slop).coerceIn(0f, heightPx)
    }

    /** 松手：拉过四分之一就关，否则弹回。 */
    fun shouldDismiss(offsetPx: Float, heightPx: Float): Boolean =
        heightPx > 0f && offsetPx > heightPx * DISMISS_FRACTION
}
