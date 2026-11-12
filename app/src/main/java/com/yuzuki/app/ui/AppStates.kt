package com.yuzuki.app.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.yuzuki.app.data.AppContainer
import com.yuzuki.app.data.FilterCond
import com.yuzuki.app.data.LibraryPrefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

private val libraryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

/**
 * 库页的三种展现形态：
 * - [List]   列表：一行一个作品（小封面 + 标题 + 社团/销量 + 声优）
 * - [Detail] 详情：封面 1:1 + 标题/系列/社团/销量/声优卡片（默认）
 * - [Image]  图片：只显示封面（原来的 M 模式）
 */
enum class LibraryView { List, Detail, Image }

/** 媒体库界面状态：抽屉、顶栏、悬浮按钮与列表共享，避免层层传参。 */
object LibraryState {
    var query by mutableStateOf("")
        private set
    var conds by mutableStateOf<List<FilterCond>>(emptyList())
        private set
    var sort by mutableStateOf("added")
        private set
    var order by mutableStateOf("desc")
        private set
    var view by mutableStateOf(LibraryView.Detail)
    var randomMode by mutableStateOf(false)
    var scrollTopTicket by mutableStateOf(0)
        private set

    fun restore(prefs: LibraryPrefs) {
        query = prefs.q
        conds = prefs.conditions.take(5)
        sort = prefs.sort
        order = prefs.order
    }

    /** 兼容既有渲染代码：只有「图片」形态才只画封面。 */
    val imageMode: Boolean get() = view == LibraryView.Image

    /** 顶栏按钮 / 悬浮 M 键：列表 → 详情 → 图片 → 列表。 */
    fun cycleView() {
        view = when (view) {
            LibraryView.List -> LibraryView.Detail
            LibraryView.Detail -> LibraryView.Image
            LibraryView.Image -> LibraryView.List
        }
    }

    fun updateQuery(value: String) { query = value }

    fun addCond(cond: FilterCond) {
        if (conds.size >= 5 || conds.contains(cond)) return
        conds = conds + cond
        randomMode = false
        persistAsync()
    }

    fun removeCond(cond: FilterCond) {
        conds = conds - cond
        persistAsync()
    }

    fun clearConds() {
        conds = emptyList()
        persistAsync()
    }

    fun updateSort(value: String) {
        sort = value
        randomMode = false
        persistAsync()
    }

    fun toggleOrder() {
        order = if (order == "desc") "asc" else "desc"
        persistAsync()
    }

    fun toggleRandom() { randomMode = !randomMode }
    fun scrollToTop() { scrollTopTicket++ }
    fun home() {
        query = ""
        conds = emptyList()
        randomMode = false
        scrollToTop()
        persistAsync()
    }

    suspend fun persist() {
        AppContainer.prefs.save(LibraryPrefs(conds, query, sort, order))
    }

    private fun persistAsync() {
        libraryScope.launch { runCatching { persist() } }
    }
}

/**
 * 移动端悬浮控件（web `MobileDock.vue`）与页面之间的最小契约：
 * 只广播「回到顶部」，由各页面自己的滚动容器消费。
 */
object DockState {
    var scrollTopTicket by mutableStateOf(0)
        private set

    fun scrollToTop() { scrollTopTicket++ }
}

object WallpaperState {
    var version by mutableStateOf(0L)
        private set

    fun bump() { version = System.currentTimeMillis() }
}

object FavoritesState {
    var workRjs by mutableStateOf<Set<String>>(emptySet())
        private set
    var tags by mutableStateOf<Set<String>>(emptySet())
        private set
    var circles by mutableStateOf<Set<String>>(emptySet())
        private set
    var actors by mutableStateOf<Set<String>>(emptySet())
        private set

    suspend fun refresh() {
        AppContainer.api.favorites().onSuccess { fav ->
            workRjs = fav.works.map { it.rjCode }.toSet()
            tags = fav.tags.map { it.name }.toSet()
            circles = fav.circles.toSet()
            actors = fav.actors.toSet()
        }
    }

    /** 切换作品收藏，返回切换后的状态（true = 已收藏），供提示条判断文案。 */
    /** 返回操作后的收藏态；失败返回 null（状态未变，供 UI 提示失败）。 */
    suspend fun toggleWork(rj: String): Boolean? {
        val has = workRjs.contains(rj)
        val result = if (has) AppContainer.api.removeFavorite("work", rj)
        else AppContainer.api.addFavorite("work", rj)
        if (!result.isSuccess) return null
        workRjs = if (has) workRjs - rj else workRjs + rj
        return !has
    }

    /** 收藏切换 + App 内提示：所有收藏入口（卡片 / 详情 / 播放页）统一走这里，不会漏提示。 */
    suspend fun toggleWorkWithToast(rj: String) {
        AppToast.show(
            when (toggleWork(rj)) {
                null -> "操作失败，请检查网络"
                true -> "已收藏"
                false -> "已取消收藏"
            },
        )
    }

    /** 社团 / 声优 / 标签收藏切换；失败返回 null（状态未变）。 */
    suspend fun toggle(type: String, value: String, extra: String = ""): Boolean? {
        val set = when (type) {
            "tag" -> tags
            "circle" -> circles
            else -> actors
        }
        val has = set.contains(value)
        val result = if (has) AppContainer.api.removeFavorite(type, value)
        else AppContainer.api.addFavorite(type, value, extra)
        if (!result.isSuccess) return null
        val next = if (has) set - value else set + value
        when (type) {
            "tag" -> tags = next
            "circle" -> circles = next
            else -> actors = next
        }
        return !has
    }

    /** 社团 / 声优 / 标签的收藏切换 + App 内提示。 */
    suspend fun toggleWithToast(type: String, value: String, extra: String = "") {
        val label = when (type) {
            "circle" -> "社团"
            "tag" -> "标签"
            else -> "声优"
        }
        AppToast.show(
            when (toggle(type, value, extra)) {
                null -> "操作失败，请检查网络"
                true -> "已收藏$label：$value"
                false -> "已取消收藏$label：$value"
            },
        )
    }
}
