package com.yuzuki.app.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.yuzuki.app.data.AppContainer
import com.yuzuki.app.data.FilterCond
import com.yuzuki.app.data.LibraryFilterPrefs

/**
 * 库页的三种展现形态：
 * - [List]   列表：一行一个作品（小封面 + 标题 + 社团/销量 + 声优）
 * - [Detail] 详情：封面 1:1 + 标题/系列/社团/销量/声优卡片（默认）
 * - [Image]  图片：只显示封面（原来的 M 模式）
 */
enum class LibraryView { List, Detail, Image }

/** 媒体库界面状态：抽屉、顶栏、悬浮按钮与列表共享，避免层层传参。
 *
 * 搜索关键词 / 筛选条件 / 排序 **记在本机**（DataStore `app_library_filter`，见 [LibraryFilterPrefs]）：
 * 冷启动由 `AppViewModel.enterMain` 读回上次的条件；改条件时经 [persistHook] 回写。
 * 服务端 `/api/prefs` 一概不读不写 —— 网页端自己的记忆不受影响，App 也不会把网页端的条件带进来。
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
     * 条件落盘钩子：进主界面前由 `AppViewModel` 挂上（本机 DataStore）。
     * 默认 null —— 单测里不触达任何存储，也不会碰到未初始化的 `AppContainer`。
     */
    var persistHook: ((LibraryFilterPrefs) -> Unit)? = null

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

    fun updateQuery(value: String) {
        query = value
        persist()
    }

    fun addCond(cond: FilterCond) {
        if (conds.size >= LibraryFilterPrefs.MAX_CONDS || conds.contains(cond)) return
        conds = conds + cond
        randomMode = false
        persist()
    }

    fun removeCond(cond: FilterCond) {
        conds = conds - cond
        persist()
    }

    fun clearConds() {
        conds = emptyList()
        persist()
    }

    fun updateSort(value: String) {
        sort = value
        randomMode = false
        persist()
    }

    fun toggleOrder() {
        order = if (order == "desc") "asc" else "desc"
        persist()
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

    /**
     * 退出随机：回到当前搜索 / 筛选 / 排序对应的常规列表（条件本身不变）。
     * 返回 false 表示本来就不在随机模式，调用方据此决定要不要弹提示。
     */
    fun exitRandom(): Boolean {
        if (!randomMode) return false
        randomMode = false
        return true
    }

    fun scrollToTop() { scrollTopTicket++ }

    /** 当前条件的快照，交给 [persistHook] 落盘。 */
    fun snapshot(): LibraryFilterPrefs = LibraryFilterPrefs(query, conds, sort, order)

    /**
     * 冷启动恢复上次条件（只来自本机存储）：随机模式属于临时状态，恢复时一律回到常规列表。
     */
    fun restore(prefs: LibraryFilterPrefs) {
        val clean = prefs.sanitized()
        query = clean.query
        conds = clean.conds
        sort = clean.sort
        order = clean.order
        randomMode = false
    }

    private fun persist() {
        persistHook?.invoke(snapshot())
    }

    fun home() {
        query = ""
        conds = emptyList()
        randomMode = false
        persist()
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
