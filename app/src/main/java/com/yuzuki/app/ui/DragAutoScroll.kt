package com.yuzuki.app.ui

/**
 * 播放列表拖动排序时的「边缘自动滚动」速率计算（纯函数，JVM 可测）。
 *
 * 规则：被拖行的中心进入列表上下边缘感应区 [edge] 后按越界深度线性加速，
 * 越贴边滚得越快；离开感应区返回 0。返回值与 `LazyListState.scrollBy` 同向 —
 * **正值 = 往下看后续内容，负值 = 回看前面的内容**（拖到顶部要露上面的行，所以是负值）。
 */
object DragAutoScroll {
    fun step(
        center: Float,
        viewportStart: Int,
        viewportEnd: Int,
        edge: Float,
        maxStep: Float,
    ): Float {
        if (edge <= 0f || maxStep <= 0f) return 0f
        val overTop = (viewportStart + edge) - center
        if (overTop > 0f) return -maxStep * (overTop / edge).coerceIn(0f, 1f)
        val overBottom = center - (viewportEnd - edge)
        if (overBottom > 0f) return maxStep * (overBottom / edge).coerceIn(0f, 1f)
        return 0f
    }
}
