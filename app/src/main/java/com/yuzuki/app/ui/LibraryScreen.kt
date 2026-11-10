package com.yuzuki.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items as lazyItems
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.yuzuki.app.data.AppContainer
import com.yuzuki.app.data.CountItem
import com.yuzuki.app.data.FilterCond
import com.yuzuki.app.data.Work
import com.yuzuki.app.data.WorksResponse
import com.yuzuki.app.ui.theme.GlassItem
import com.yuzuki.app.ui.theme.GlassSpec
import com.yuzuki.app.ui.theme.GlassSurface
import com.yuzuki.app.ui.theme.ShapeCard
import com.yuzuki.app.ui.theme.ShapeItem
import com.yuzuki.app.ui.theme.ShapePill
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

private val SORTS = listOf(
    "added" to "入库时间",
    "release" to "发售日",
    "sales" to "销量",
    "title" to "标题",
    "updated" to "更新时间",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    onOpenWork: (String) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenChangePassword: () -> Unit,
    onChangeServer: () -> Unit,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val gridState = rememberLazyGridState()
    var items by remember { mutableStateOf<List<Work>>(emptyList()) }
    var total by remember { mutableStateOf(0) }
    var page by remember { mutableStateOf(1) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var loadMoreError by remember { mutableStateOf<String?>(null) }
    var endReached by remember { mutableStateOf(false) }
    var loadJob by remember { mutableStateOf<Job?>(null) }
    var generation by remember { mutableStateOf(0) }
    var searchText by remember { mutableStateOf(LibraryState.query) }
    var searchExpanded by remember { mutableStateOf(false) }
    var sheetVisible by remember { mutableStateOf(false) }
    var menuVisible by remember { mutableStateOf(false) }
    var expandedGroup by remember { mutableStateOf<String?>(null) }
    var counts by remember { mutableStateOf<Map<String, List<CountItem>>>(emptyMap()) }

    /**
     * 分页加载在 scope 里执行，不放在随 loading 变化而销毁的 LaunchedEffect 内；
     * generation 丢弃过期响应，finally 保证状态复位 —— 修复「永远停在首页 50 条」。
     */
    fun requestPage(reset: Boolean) {
        val running = loadJob
        if (running != null && running.isActive) {
            if (!reset) return
            running.cancel()
        }
        val gen = ++generation
        val target = if (reset) 1 else page + 1
        loadJob = scope.launch {
            loading = true
            if (reset) error = null
            loadMoreError = null
            try {
                val size = PagingLogic.pageSize(AppContainer.settings.current.pageSize)
                val result = if (LibraryState.randomMode) {
                    AppContainer.api.randomWorks(LibraryState.query, LibraryState.conds, size * 3)
                        .map { WorksResponse(it, it.size, 1, it.size) }
                } else {
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
                        items = if (reset) response.items else items + response.items
                        total = response.total
                        page = response.page
                        endReached = LibraryState.randomMode || PagingLogic.endReached(
                            items.size, response.total, response.items.size,
                            response.pageSize.coerceAtLeast(1),
                        )
                        error = null
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

    LaunchedEffect(searchText) {
        if (searchText != LibraryState.query) {
            delay(400)
            LibraryState.updateQuery(searchText)
            LibraryState.persist()
        }
    }

    LaunchedEffect(
        LibraryState.query, LibraryState.conds, LibraryState.sort,
        LibraryState.order, LibraryState.randomMode,
    ) {
        loadJob?.cancel()
        loadJob = null
        loading = false
        endReached = false
        loadMoreError = null
        items = emptyList()
        requestPage(reset = true)
    }

    LaunchedEffect(LibraryState.scrollTopTicket) { gridState.scrollToItem(0) }
    LaunchedEffect(Unit) { FavoritesState.refresh() }
    LaunchedEffect(Unit) {
        counts = mapOf(
            "circle" to AppContainer.api.circles().getOrDefault(emptyList()),
            "series" to AppContainer.api.series().getOrDefault(emptyList()),
            "actor" to AppContainer.api.actors().getOrDefault(emptyList()),
            "tag" to AppContainer.api.tags().getOrDefault(emptyList()),
        )
    }

    // 预取判定只依赖状态，加载 Job 不会被重组取消
    val shouldLoadMore by remember {
        derivedStateOf {
            val lastVisible = gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
            PagingLogic.shouldLoadMore(
                lastVisible, items.size, total, loading, endReached, LibraryState.randomMode,
            )
        }
    }
    LaunchedEffect(Unit) {
        snapshotFlow { shouldLoadMore }
            .distinctUntilChanged()
            .collect { if (it) requestPage(reset = false) }
    }

    val columns = if (LocalConfiguration.current.screenWidthDp >= 600) 3 else 2

    Column(modifier = modifier.fillMaxSize()) {
        TopAppBar(
            title = {
                Column {
                    Text("作品库", style = MaterialTheme.typography.titleLarge)
                    Text(
                        if (LibraryState.randomMode) "随机推荐 · ${items.size} 部" else "共 $total 部",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            actions = {
                IconButton(onClick = { searchExpanded = !searchExpanded }) {
                    Icon(Icons.Filled.Search, contentDescription = "搜索")
                }
                TextButton(onClick = { sheetVisible = true }) { Text("筛选") }
                Box {
                    IconButton(onClick = { menuVisible = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "更多")
                    }
                    DropdownMenu(expanded = menuVisible, onDismissRequest = { menuVisible = false }) {
                        DropdownMenuItem(text = { Text("设置") }, onClick = { menuVisible = false; onOpenSettings() })
                        DropdownMenuItem(text = { Text("修改密码") }, onClick = { menuVisible = false; onOpenChangePassword() })
                        DropdownMenuItem(text = { Text("服务地址") }, onClick = { menuVisible = false; onChangeServer() })
                        DropdownMenuItem(text = { Text("退出登录") }, onClick = { menuVisible = false; onLogout() })
                    }
                }
            },
            windowInsets = WindowInsets(0, 0, 0, 0),
        )

        if (searchExpanded || searchText.isNotEmpty()) {
            OutlinedTextField(
                value = searchText,
                onValueChange = { searchText = it },
                placeholder = { Text("搜索标题 / RJ / 社团") },
                singleLine = true,
                shape = ShapePill,
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = {
                    IconButton(onClick = { searchText = ""; searchExpanded = false }) {
                        Icon(Icons.Filled.Close, contentDescription = "清除搜索")
                    }
                },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }

        if (LibraryState.conds.isNotEmpty()) {
            LazyRow(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                lazyItems(LibraryState.conds, key = { "${it.type}:${it.value}" }) { cond ->
                    FilterChip(
                        selected = true,
                        onClick = { LibraryState.removeCond(cond) },
                        label = { Text("${cond.value}  ×", style = MaterialTheme.typography.labelSmall) },
                        shape = ShapePill,
                    )
                }
            }
        }

        when {
            error != null && items.isEmpty() -> StateBox(
                message = error ?: "加载失败",
                modifier = Modifier.weight(1f).fillMaxWidth().padding(16.dp),
                onRetry = { requestPage(reset = true) },
            )
            else -> LazyVerticalGrid(
                columns = GridCells.Fixed(columns),
                state = gridState,
                contentPadding = PaddingValues(12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f),
            ) {
                gridItems(items, key = { it.rjCode }) { work ->
                    WorkCard(
                        work = work,
                        imageMode = LibraryState.imageMode,
                        onOpen = { onOpenWork(work.rjCode) },
                    )
                }
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Box(
                        modifier = Modifier.fillMaxWidth().height(64.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        when {
                            loading -> CircularProgressIndicator(modifier = Modifier.size(28.dp))
                            loadMoreError != null -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(loadMoreError ?: "加载失败", style = MaterialTheme.typography.labelSmall)
                                TextButton(onClick = { requestPage(reset = false) }) { Text("重试") }
                            }
                            endReached && items.isNotEmpty() -> Text(
                                "已加载全部 ${items.size} 部",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            !loading && items.isEmpty() -> Text(
                                "没有符合条件的作品",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }

    if (sheetVisible) {
        ModalBottomSheet(
            onDismissRequest = { sheetVisible = false },
            sheetState = rememberModalBottomSheetState(),
        ) {
            LibrarySheet(
                counts = counts,
                expandedGroup = expandedGroup,
                onToggleGroup = { expandedGroup = if (expandedGroup == it) null else it },
                onCond = { LibraryState.addCond(it); sheetVisible = false },
                onScrollTop = { LibraryState.scrollToTop(); sheetVisible = false },
                onClear = {
                    searchText = ""
                    LibraryState.updateQuery("")
                    LibraryState.clearConds()
                    sheetVisible = false
                },
                onClose = { sheetVisible = false },
            )
        }
    }
}

@Composable
private fun LibrarySheet(
    counts: Map<String, List<CountItem>>,
    expandedGroup: String?,
    onToggleGroup: (String) -> Unit,
    onCond: (FilterCond) -> Unit,
    onScrollTop: () -> Unit,
    onClear: () -> Unit,
    onClose: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("排序与筛选", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            TextButton(onClick = onClose) { Text("完成") }
        }

        Text("排序", style = MaterialTheme.typography.titleSmall)
        SORTS.forEach { (key, label) ->
            val active = LibraryState.sort == key
            GlassItem(modifier = Modifier.fillMaxWidth(), onClick = { LibraryState.updateSort(key) }) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        label,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (active) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                    )
                    if (active) Text("✓", color = MaterialTheme.colorScheme.primary)
                }
            }
        }
        Button(onClick = { LibraryState.toggleOrder() }, shape = ShapePill) {
            Text(if (LibraryState.order == "desc") "当前：降序 ↓" else "当前：升序 ↑")
        }

        Text("视图", style = MaterialTheme.typography.titleSmall)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("仅看封面", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Switch(checked = LibraryState.imageMode, onCheckedChange = { LibraryState.imageMode = it })
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("随机推荐", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Switch(checked = LibraryState.randomMode, onCheckedChange = { LibraryState.toggleRandom() })
        }

        Text("按条件筛选（最多 5 个，当前 ${LibraryState.conds.size}）", style = MaterialTheme.typography.titleSmall)
        listOf(
            "circle" to "社团",
            "series" to "系列",
            "actor" to "声优",
            "tag" to "标签",
        ).forEach { (type, label) ->
            GlassItem(modifier = Modifier.fillMaxWidth(), onClick = { onToggleGroup(type) }) {
                Text(
                    "${if (expandedGroup == type) "▾" else "▸"} $label (${counts[type]?.size ?: 0})",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
                )
            }
            if (expandedGroup == type) {
                counts[type].orEmpty().take(30).forEach { item ->
                    Text(
                        "${item.name}  ${item.count}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(ShapeItem)
                            .clickable { onCond(FilterCond(type, item.name)) }
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                    )
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onScrollTop, shape = ShapePill) { Text("回到顶部") }
            Button(onClick = onClear, shape = ShapePill) { Text("清除筛选") }
        }
        Box(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun WorkCard(
    work: Work,
    imageMode: Boolean,
    onOpen: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val fav = FavoritesState.workRjs.contains(work.rjCode)
    GlassSurface(
        modifier = Modifier.fillMaxWidth(),
        shape = ShapeCard,
        onClick = onOpen,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Box(modifier = Modifier.fillMaxWidth().aspectRatio(0.75f)) {
                RemoteImage(
                    url = if (work.coverUrl.startsWith("http")) work.coverUrl
                    else AppContainer.api.authedUrl(work.coverUrl),
                    modifier = Modifier.fillMaxSize().clip(ShapeItem),
                )
                IconButton(
                    onClick = { scope.launch { FavoritesState.toggleWork(work.rjCode) } },
                    modifier = Modifier.align(Alignment.TopEnd).size(48.dp),
                ) {
                    Icon(
                        imageVector = if (fav) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                        contentDescription = if (fav) "取消收藏" else "收藏",
                        tint = if (fav) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurface,
                    )
                }
                if (imageMode) {
                    val flame = GlassSpec.flame(work.salesCount)
                    if (flame.count > 0) {
                        Text(
                            "▲".repeat(flame.count),
                            color = when (flame.kind) {
                                "red" -> Color(0xFFFF6B6B)
                                "yellow" -> Color(0xFFFFD166)
                                "rainbow" -> Color(0xFFB69AF6)
                                else -> Color.LightGray
                            },
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(8.dp),
                        )
                    }
                    Text(
                        work.rjCode,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(6.dp)
                            .background(MaterialTheme.colorScheme.surfaceContainerHighest, ShapeItem)
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
            }
            if (!imageMode) {
                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        work.title,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        listOfNotNull(
                            work.circle.takeIf { it.isNotBlank() },
                            work.series.takeIf { it.isNotBlank() },
                            "播放 ${work.playCount}",
                        ).joinToString(" · "),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}
