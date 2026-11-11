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

/**
 * 库页「返回时保留滚动位置」的纯判定逻辑，不依赖 Android / Compose，便于 JVM 单测断言。
 *
 * 关键约束：冷启动（loadedFingerprint == null）必须重新拉第一页。
 * 若为了「返回复用」把首次组合的 reset 跳掉，主界面首帧不会有数据，
 * 「在 N 个作品中搜索」的数量就会不对（历史上 5cc8e04 修过同类问题）。
 */
object ScrollRestore {
    /** 长度前缀拼接，查询词里出现分隔符也不会造成指纹碰撞。 */
    fun fingerprint(parts: List<String>): String =
        parts.joinToString("") { "${it.length}:$it|" }

    /** 重新拉第一页：没有可复用数据，或条件指纹已变化。 */
    fun shouldReset(loadedFingerprint: String?, currentFingerprint: String): Boolean =
        loadedFingerprint != currentFingerprint

    /** reset 时是否回顶部：已有旧数据 = 条件变化；null = 冷启动（本来就在顶部）。 */
    fun shouldScrollTopOnReset(loadedFingerprint: String?): Boolean = loadedFingerprint != null

    /**
     * 底部「库」Tab 被点时是否回顶：只有「本来就在库页、且不是从全屏播放器返回」才算
     * 用户主动重复点当前 Tab。播放器是叠加层，库页从未离开组合，此时回顶会立刻被消费，
     * 表现为「点播放器再返回就跳到最上面」。
     */
    fun shouldScrollTopOnLibraryTab(fromPlayer: Boolean, isLibraryRoute: Boolean): Boolean =
        !fromPlayer && isLibraryRoute

    /** 置顶票只在「进入页面之后发生变化」时消费，首次组合不消费。 */
    fun shouldConsumeTicket(current: Int, atEnter: Int): Boolean = current != atEnter

    /** 恢复快照：数据属于当前条件、列表非空、快照属于同一条件且下标有效。 */
    fun shouldRestore(
        loadedFingerprint: String?,
        savedFingerprint: String?,
        currentFingerprint: String,
        itemCount: Int,
        savedIndex: Int,
    ): Boolean = loadedFingerprint == currentFingerprint &&
        savedFingerprint == currentFingerprint &&
        itemCount > 0 && savedIndex > 0 && savedIndex < itemCount

    /** 上界取 itemCount - 1：等于 itemCount 会落到 footer 上，位置会偏一格。 */
    fun clampIndex(savedIndex: Int, itemCount: Int): Int =
        savedIndex.coerceIn(0, (itemCount - 1).coerceAtLeast(0))
}
