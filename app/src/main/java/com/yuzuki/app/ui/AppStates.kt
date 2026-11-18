package com.yuzuki.app.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.yuzuki.app.data.AppContainer
import com.yuzuki.app.data.FilterCond

/**
 * 库页的三种展现形态：
 * - [List]   列表：一行一个作品（小封面 + 标题 + 社团/销量 + 声优）
 * - [Detail] 详情：封面 1:1 + 标题/系列/社团/销量/声优卡片（默认）
 * - [Image]  图片：只显示封面（原来的 M 模式）
 */
enum class LibraryView { List, Detail, Image }

/** 媒体库界面状态：抽屉、顶栏、悬浮按钮与列表共享，避免层层传参。
 *
 * 搜索关键词 / 筛选条件 / 排序 **只在本次会话内存中**：不落盘、不读写服务端的 `/api/prefs`，
 * 冷启动恒为「空关键词 + 无筛选 + 默认排序」。这样既不会记住 App 自己上次的条件，
 * 也不会把网页端保存的搜索条件带进来（或反过来覆盖掉网页端的）。
 */
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

    /**
     * 随机批次号：已处于随机模式时再要一批随机，条件本身没变，
     * 靠这个计数参与列表条件指纹让页面重新拉数据。
     */
    var randomEpoch by mutableStateOf(0)
        private set
    var scrollTopTicket by mutableStateOf(0)
        private set

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
    }

    fun removeCond(cond: FilterCond) {
        conds = conds - cond
    }

    fun clearConds() {
        conds = emptyList()
    }

    fun updateSort(value: String) {
        sort = value
        randomMode = false
    }

    fun toggleOrder() {
        order = if (order == "desc") "asc" else "desc"
    }

    fun toggleRandom() { randomMode = !randomMode }

    /**
     * 信息流右半屏下拉：「随机推荐」。
     * 未在随机模式 → 进入随机并拉一批；已在随机模式 → 换一批（randomEpoch 参与条件指纹）。
     * 返回 true 表示这次是「新进入随机」，供提示文案区分。
     */
    fun pullRandom(): Boolean {
        val entered = !randomMode
        randomMode = true
        randomEpoch++
        return entered
    }

    fun scrollToTop() { scrollTopTicket++ }
    fun home() {
        query = ""
        conds = emptyList()
        randomMode = false
        scrollToTop()
    }
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
