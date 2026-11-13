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

/**
 * 全屏播放器（Pager 第 1 页）与覆盖层（详情 / 收藏 / 设置 / 改密）的互斥规则。
 *
 * 背景：播放器只是 Pager 的一页，而详情等是画在 Pager 之上的覆盖层。
 * 在详情里点文件播放时只 setFullscreen(true) 是不够的 —— 覆盖层仍然盖住播放器页，
 * 界面看起来毫无反应；反过来，播放器展开期间任何 nav 变化都不能把 Pager 拽回别的板块，
 * 否则「播放页 → 详情 → 点文件」会被 nav 同步再拉回库页。
 */
object PlayerNav {
    /** 覆盖层还开着（当前不在库页）就必须先收起它，否则播放器页永远被盖住。 */
    fun shouldCloseOverlay(currentIsLibrary: Boolean): Boolean = !currentIsLibrary

    /** nav 变化 → Pager 归位：全屏播放器展开时跳过，避免与「进播放器」的翻页互相拉扯。 */
    fun shouldSyncPager(fullscreen: Boolean): Boolean = !fullscreen
}

/**
 * 图片查看器横滑的「松手判定」：是完成翻页，还是弹簧回弹。
 *
 * 手感对齐系统横滑：位移过半自然翻页，打得快（速度够）即使位移小也翻页，
 * 两者都不满足就回弹。纯数值逻辑，JVM 可直接断言。
 */
object ViewerSwipe {
    /** 位移超过屏宽 25% 即视为用户已经划过一半，松手就完成翻页。 */
    const val SLIDE_FRACTION = 0.25f

    /** 快速甩动阈值（px/s）：位移不大但甩得够快也翻页。 */
    const val FLING_VELOCITY = 900f

    const val NONE = 0

    /** 手指向左滑（offset 为负）→ 看下一张。 */
    const val NEXT = 1
    const val PREV = -1

    /**
     * @param offsetPx 松手瞬间的跟手位移（左负右正）
     * @param velocity 松手瞬间的速度（px/s，左负右正）
     * @param pagePx   一页宽度（屏宽），用于把相对阈值换成像素
     */
    fun decide(offsetPx: Float, velocity: Float, pagePx: Float): Int {
        if (pagePx <= 0f) return NONE
        val threshold = pagePx * SLIDE_FRACTION
        when {
            offsetPx <= -threshold -> return NEXT
            offsetPx >= threshold -> return PREV
        }
        // 速度方向必须与已产生的位移同向，避免「反向甩」把回弹误判成翻页
        return when {
            velocity <= -FLING_VELOCITY && offsetPx <= 0f -> NEXT
            velocity >= FLING_VELOCITY && offsetPx >= 0f -> PREV
            else -> NONE
        }
    }

    /** 首尾循环取相邻下标；单张图返回 null（不渲染邻居、也不翻页）。 */
    fun neighbor(index: Int, dir: Int, count: Int): Int? = when {
        count <= 1 -> null
        dir > 0 -> (index + 1) % count
        dir < 0 -> (index - 1 + count) % count
        else -> null
    }
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
