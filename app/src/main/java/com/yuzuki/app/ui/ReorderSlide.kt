package com.yuzuki.app.ui

/**
 * 播放列表拖动排序的「让位」几何（纯函数，JVM 可测）。
 *
 * 拖动期间队列顺序一个字都不改，只是把被拖行 [from] 走过的那些行整体挪一个行高，
 * 视觉上像卡片让位；松手时再一次性把 [from] 搬到 [to]。
 * 这样 LazyColumn 全程不需要重新布局，也就没有可抖的东西。
 */
object ReorderSlide {
    /**
     * 某一行的纵向让位（正 = 往下让，负 = 往上让）。
     * - 被拖行自己返回 0：它由手势位移负责，不参与让位；
     * - 往上拖（to < from）：[to] 到 [from] 之间的行整体往下让一行的位置；
     * - 往下拖（to > from）：(from, to] 之间的行整体往上让。
     */
    fun shiftOf(index: Int, from: Int, to: Int, rowHeight: Float): Float = when {
        index == from || from == to || rowHeight <= 0f -> 0f
        from < to && index in (from + 1)..to -> -rowHeight
        to < from && index in to until from -> rowHeight
        else -> 0f
    }
}
