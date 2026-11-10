package com.yuzuki.app.ui

/**
 * 纯 Kotlin 导航栈与分页判定：不依赖 Android / Compose，便于 JVM 单测直接断言。
 * 修复点：栈为空时 backOr 仍会回到指定主页，任何页面都不会出现「返回点了没反应」。
 */
class NavStack<T>(initial: T) {
    var current: T = initial
        private set
    private val stack = ArrayDeque<T>()

    fun go(route: T): Boolean {
        if (current == route) return false
        stack.addLast(current)
        current = route
        return true
    }

    /** Tab 切换：清空历史，避免跨 Tab 的返回链。 */
    fun replace(route: T) {
        stack.clear()
        current = route
    }

    fun back(): Boolean {
        val previous = stack.removeLastOrNull() ?: return false
        current = previous
        return true
    }

    fun backOr(home: T): Boolean {
        if (back()) return true
        if (current != home) {
            replace(home)
            return true
        }
        return false
    }

    val canBack: Boolean get() = stack.isNotEmpty()
}

object PagingLogic {
    /** 后端 /api/works 把 pageSize clamp 到 1..200，前端必须同步，否则 offset 会跳页漏数据。 */
    const val MAX_PAGE_SIZE = 200

    /** 距列表末尾剩余多少项时预取下一页。 */
    const val PREFETCH = 6

    fun pageSize(requested: Int): Int = requested.coerceIn(1, MAX_PAGE_SIZE)

    fun shouldLoadMore(
        lastVisible: Int,
        loaded: Int,
        total: Int,
        loading: Boolean,
        endReached: Boolean,
        randomMode: Boolean,
    ): Boolean = !loading && !endReached && !randomMode && loaded > 0 &&
        loaded < total && lastVisible >= loaded - PREFETCH

    fun endReached(loaded: Int, total: Int, returned: Int, effectivePageSize: Int): Boolean =
        returned <= 0 || loaded >= total || returned < effectivePageSize
}
