package com.yuzuki.app.ui

import com.yuzuki.app.ui.theme.SheetTokens
import kotlin.math.abs

/**
 * 底部弹出层「下滑关闭」判定（纯函数 / 纯状态，JVM 可测）。
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
     * 手势位移的锚点（0.2.4 修「接管瞬间跳一段」）。
     *
     * **不能拿「按下点」当锚点**：接管条件是「这一帧列表恰好停在顶部」，而列表可能是在本段手势
     * 中途才滚到顶的 —— 手指此前已经走了一大段，累计位移是个大数；一接管就把这个大数灌进
     * `offsetPx`，框就会瞬间往下跳一大段（先上划浏览内容、再下滑到顶时最明显）。
     *
     * 所以位移只算「当前这一段向下运动」：手指每次回升（`y` 比上一帧小）就把锚点挪到当前位置、
     * 累计清零；接管那一刻再 [anchorAt] 一次，位移严格从 0 起算。
     */
    class Run(y0: Float) {
        private var anchorY = y0
        private var lastY = y0

        /** 本段向下运动累计位移。 */
        var downRun = 0f
            private set

        /** 同一段里的横向累计，用来判断「是下拉还是横滑」。 */
        var crossRun = 0f
            private set

        /** 喂一帧：返回本段向下运动的累计位移。手指回升即重开一段。 */
        fun track(y: Float, dx: Float): Float {
            if (y < lastY) {
                anchorY = y
                downRun = 0f
                crossRun = 0f
            } else {
                downRun = y - anchorY
                crossRun += dx
            }
            lastY = y
            return downRun
        }

        /** 接管这一刻调用：把锚点钉在当前手指位置，位移从 0 起算（不留历史位移）。 */
        fun anchorAt(y: Float) {
            anchorY = y
            lastY = y
            downRun = 0f
            crossRun = 0f
        }

        /** 跟手位移：从锚点算起，夹在 0..框高之间（不允许往上顶出界）。 */
        fun offsetOf(y: Float, heightPx: Float): Float {
            if (heightPx <= 0f) return 0f
            return (y - anchorY).coerceIn(0f, heightPx)
        }
    }

    /**
     * 是否接管本轮手势。
     *
     * @param dragY **本段**向下运动的累计位移（[Run.downRun]，不是从按下点算的总位移）
     * @param dragX 同一段里的横向累计位移
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

    /**
     * 还没接管时，这一帧要不要先替列表吃掉。
     *
     * 只有「明确是往下拉」才吃：否则列表会先接到一两帧 move 起 overscroll 拉伸，
     * 回弹叠在跟手位移上就是抖动 + 边缘阴影。向上滑 / 横向滑一律不吃，
     * 让列表正常滚动、让相关推荐正常横滑。
     */
    fun pullFrame(dy: Float, dx: Float): Boolean = dy > 0f && dy > abs(dx)

    /** 松手：拉过四分之一就关，否则弹回。 */
    fun shouldDismiss(offsetPx: Float, heightPx: Float): Boolean =
        heightPx > 0f && offsetPx > heightPx * DISMISS_FRACTION
}
