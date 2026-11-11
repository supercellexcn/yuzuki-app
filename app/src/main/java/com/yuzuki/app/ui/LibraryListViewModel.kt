package com.yuzuki.app.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yuzuki.app.data.AppContainer
import com.yuzuki.app.data.Work
import com.yuzuki.app.data.WorksResponse
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * 库页列表数据与分页状态：由 RootScreen 持有（ViewModel），主页分支被移出组合时不会销毁。
 *
 * 冷启动语义保持不变：[loadedFingerprint] 为 null 时 UI 侧必然发起一次 reset 请求，
 * 且请求内的 items / total / page / endReached 赋值与改造前逐行一致 —— 不能因为
 * 「返回时复用数据」而跳过冷启动首屏加载，否则「在 N 个作品中搜索」的数量会不对。
 */
class LibraryListViewModel : ViewModel() {
    var items by mutableStateOf<List<Work>>(emptyList())
        private set
    var total by mutableStateOf(0)
        private set
    var page by mutableStateOf(1)
        private set
    var loading by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var loadMoreError by mutableStateOf<String?>(null)
        private set
    var endReached by mutableStateOf(false)
        private set

    /** 已成功加载的数据所属的条件指纹；null = 没有可复用数据（冷启动 / 条件刚变 / 加载失败）。 */
    var loadedFingerprint by mutableStateOf<String?>(null)
        private set

    /** reset 成功后自增；UI 用「进入页面时的票值」判定是否置顶，避免首次组合误触发。 */
    var resetEpoch by mutableStateOf(0)
        private set

    /** 滚动位置快照，按条件指纹绑定，条件变了就不再恢复。 */
    var savedFingerprint by mutableStateOf<String?>(null)
        private set
    var savedIndex by mutableStateOf(0)
        private set
    var savedOffset by mutableStateOf(0)
        private set

    private var loadJob: Job? = null
    private var generation = 0

    fun saveScroll(fingerprint: String, index: Int, offset: Int) {
        savedFingerprint = fingerprint
        savedIndex = index
        savedOffset = offset
    }

    /** 条件变化时清空旧数据；不清 total，避免首屏「N 个作品」闪成 0（与改造前一致）。 */
    fun resetConditions() {
        loadJob?.cancel()
        loadJob = null
        loading = false
        endReached = false
        loadMoreError = null
        items = emptyList()
        loadedFingerprint = null
    }

    /** 退出登录 / 换服务器：清掉上一个会话的数据，下次进来仍走冷启动。 */
    fun clear() {
        resetConditions()
        generation++
        total = 0
        page = 1
        error = null
        savedFingerprint = null
        savedIndex = 0
        savedOffset = 0
    }

    /**
     * 分页加载：generation 丢弃过期响应，finally 保证状态复位。
     * 逻辑与原先 LibraryScreen.requestPage 一致，只是状态载体换成 ViewModel。
     */
    fun requestPage(reset: Boolean, fingerprint: String) {
        val running = loadJob
        if (running != null && running.isActive) {
            if (!reset) return
            running.cancel()
        }
        val gen = ++generation
        val target = if (reset) 1 else page + 1
        loadJob = viewModelScope.launch {
            loading = true
            if (reset) error = null
            loadMoreError = null
            try {
                val result = if (LibraryState.randomMode) {
                    val size = PagingLogic.pageSize(AppContainer.settings.current.pageSize)
                    AppContainer.api.randomWorks(LibraryState.query, LibraryState.conds, size * 3)
                        .map { WorksResponse(it, it.size, 1, it.size) }
                } else {
                    val size = PagingLogic.pageSize(AppContainer.settings.current.pageSize)
                    AppContainer.library.works(
                        q = LibraryState.query,
                        conds = LibraryState.conds,
                        sort = LibraryState.sort,
                        order = LibraryState.order,
                        page = target,
                        pageSize = size,
                    )
                }
                if (gen != generation) return@launch
                result.fold(
                    onSuccess = { response ->
                        // 按 rj 去重：排序键相同（added_at 并列）时跨页可能重复
                        items = if (reset || LibraryState.randomMode) {
                            response.items
                        } else {
                            val seen = items.mapTo(HashSet()) { it.rjCode }
                            items + response.items.filter { seen.add(it.rjCode) }
                        }
                        total = response.total
                        page = response.page
                        endReached = LibraryState.randomMode || PagingLogic.endReached(
                            items.size, response.total, response.items.size,
                            response.pageSize.coerceAtLeast(1),
                        )
                        error = null
                        loadedFingerprint = fingerprint
                        // 数据到位后再置顶一次：冷启动时首帧还没有 item（沿用 9f951f3 的行为）
                        if (reset) resetEpoch++
                    },
                    onFailure = { cause ->
                        val message = cause.message ?: "加载失败"
                        if (reset) error = message else loadMoreError = message
                    },
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (cause: Exception) {
                if (gen == generation) {
                    val message = cause.message ?: "加载失败"
                    if (reset) error = message else loadMoreError = message
                }
            } finally {
                if (gen == generation) loading = false
            }
        }
    }
}
