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
    var imageMode by mutableStateOf(false)
    var randomMode by mutableStateOf(false)
    var scrollTopTicket by mutableStateOf(0)
        private set

    fun restore(prefs: LibraryPrefs) {
        query = prefs.q
        conds = prefs.conditions.take(5)
        sort = prefs.sort
        order = prefs.order
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

    suspend fun toggleWork(rj: String) {
        val has = workRjs.contains(rj)
        val result = if (has) AppContainer.api.removeFavorite("work", rj)
        else AppContainer.api.addFavorite("work", rj)
        if (result.isSuccess) {
            workRjs = if (has) workRjs - rj else workRjs + rj
        }
    }

    suspend fun toggle(type: String, value: String, extra: String = ""): Boolean {
        val set = when (type) {
            "tag" -> tags
            "circle" -> circles
            else -> actors
        }
        val has = set.contains(value)
        val result = if (has) AppContainer.api.removeFavorite(type, value)
        else AppContainer.api.addFavorite(type, value, extra)
        if (result.isSuccess) {
            val next = if (has) set - value else set + value
            when (type) {
                "tag" -> tags = next
                "circle" -> circles = next
                else -> actors = next
            }
        }
        return !has
    }
}
