package com.yuzuki.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
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
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.yuzuki.app.data.AppContainer
import com.yuzuki.app.data.CountItem
import com.yuzuki.app.data.FilterCond
import com.yuzuki.app.data.Work
import com.yuzuki.app.data.WorksResponse
import com.yuzuki.app.ui.theme.GlassItem
import com.yuzuki.app.ui.theme.GlassSpec
import com.yuzuki.app.ui.theme.ShapeCard
import com.yuzuki.app.ui.theme.ShapeItem
import com.yuzuki.app.ui.theme.ShapePill
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

private val SORTS = listOf(
    "added" to "最近添加",
    "release" to "发售日",
    "sales" to "销量",
    "title" to "标题",
    "updated" to "更新时间",
)

// 作品卡片配色取自参考图实拍取色（不透明，禁止半透明叠层）
private val CardSurfaceColor = Color(0xFF604050)
private val CardTextColor = Color(0xFFF8F8F8)
private val CardSubTextColor = Color(0xFFD8D0D8)
private val CoverTagColor = Color(0xFF282830)
private val ActorPillColor = Color(0xFF78C868)
private val ActorPillTextColor = Color(0xFF101010)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    onOpenWork: (String) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenFavorites: () -> Unit,
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
    var favoritesOnly by remember { mutableStateOf(false) }
    var sheetVisible by remember { mutableStateOf(false) }
    var menuVisible by remember { mutableStateOf(false) }
    var expandedGroup by remember { mutableStateOf<String?>(null) }
    var counts by remember { mutableStateOf<Map<String, List<CountItem>>>(emptyMap()) }

    /**
     * 分页加载在 scope 里执行，不放在随 loading 变化而销毁的 LaunchedEffect 内；
     * generation 丢弃过期响应，finally 保证状态复位 —— 修复「永远停在首页 50 条」。
     * 「我的收藏」不走分页接口，直接读 GET /api/favorites 并在本地套用搜索 / 筛选 / 排序。
     */
    fun requestPage(reset: Boolean) {
        val running = loadJob
        if (running != null && running.isActive) {
            if (!reset) return
            running.cancel()
        }
        val gen = ++generation
        val target = if (reset) 1 else page + 1
        val favMode = favoritesOnly
        loadJob = scope.launch {
            loading = true
            if (reset) error = null
            loadMoreError = null
            try {
                val result = if (favMode) {
                    AppContainer.library.favorites().map { fav ->
                        val list = filterFavorites(
                            fav.works, LibraryState.query, LibraryState.conds,
                            LibraryState.sort, LibraryState.order,
                        )
                        WorksResponse(list, list.size, 1, list.size.coerceAtLeast(1))
                    }
                } else if (LibraryState.randomMode) {
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
                        items = if (reset || favMode) response.items else items + response.items
                        total = response.total
                        page = response.page
                        endReached = favMode || LibraryState.randomMode || PagingLogic.endReached(
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
        LibraryState.order, LibraryState.randomMode, favoritesOnly,
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
        HomeSearchBar(
            value = searchText,
            onValueChange = { searchText = it },
            menuVisible = menuVisible,
            onMenuToggle = { menuVisible = !menuVisible },
            onMenuDismiss = { menuVisible = false },
            onOpenSettings = onOpenSettings,
            onOpenFavorites = onOpenFavorites,
            onOpenChangePassword = onOpenChangePassword,
            onChangeServer = onChangeServer,
            onLogout = onLogout,
        )

        if (LibraryState.conds.isNotEmpty()) {
            LazyRow(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
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

        FilterRow(
            favoritesOnly = favoritesOnly,
            onAll = { favoritesOnly = false },
            onFavorites = { favoritesOnly = true },
        )

        SectionHeader(
            title = if (favoritesOnly) "我的收藏" else "全部作品",
            count = if (favoritesOnly) items.size else total,
            sortLabel = sortLabel(LibraryState.sort, LibraryState.order),
            onSort = { sheetVisible = true },
        )

        when {
            error != null && items.isEmpty() -> StateBox(
                message = error ?: "加载失败",
                modifier = Modifier.weight(1f).fillMaxWidth().padding(16.dp),
                onRetry = { requestPage(reset = true) },
            )
            else -> LazyVerticalGrid(
                columns = GridCells.Fixed(columns),
                state = gridState,
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
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
                        modifier = Modifier.fillMaxWidth().height(56.dp),
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

private fun sortLabel(sort: String, order: String): String {
    val label = SORTS.firstOrNull { it.first == sort }?.second ?: "最近添加"
    return "$label ${if (order == "desc") "↓" else "↑"}"
}

/** 收藏列表不走 /api/works，本地复刻同一套搜索 / 条件 / 排序语义。 */
private fun filterFavorites(
    works: List<Work>,
    query: String,
    conds: List<FilterCond>,
    sort: String,
    order: String,
): List<Work> {
    val q = query.trim()
    val filtered = works.filter { work ->
        val matchQuery = q.isBlank() || work.title.contains(q, true) ||
            work.rjCode.contains(q, true) || work.circle.contains(q, true) ||
            work.actors.any { it.contains(q, true) }
        val matchConds = conds.all { cond ->
            when (cond.type) {
                "tag" -> work.tags.any { it.name == cond.value }
                "circle" -> work.circle == cond.value
                "actor" -> work.actors.contains(cond.value)
                "series" -> work.series == cond.value
                else -> true
            }
        }
        matchQuery && matchConds
    }
    val sorted = filtered.sortedBy { work ->
        when (sort) {
            "release" -> work.releaseDate
            "title" -> work.title
            "updated" -> work.updatedAt
            "sales" -> (work.salesCount ?: 0).toString().padStart(10, '0')
            else -> work.addedAt
        }
    }
    return if (order == "asc") sorted else sorted.asReversed()
}

@Composable
private fun HomeSearchBar(
    value: String,
    onValueChange: (String) -> Unit,
    menuVisible: Boolean,
    onMenuToggle: () -> Unit,
    onMenuDismiss: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenFavorites: () -> Unit,
    onOpenChangePassword: () -> Unit,
    onChangeServer: () -> Unit,
    onLogout: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = { Text("搜索作品、社团、声优…", style = MaterialTheme.typography.bodyMedium) },
        singleLine = true,
        shape = ShapeCard,
        leadingIcon = {
            Icon(
                Icons.Filled.Search,
                contentDescription = null,
                tint = colors.onSurfaceVariant,
            )
        },
        trailingIcon = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (value.isNotEmpty()) {
                    IconButton(onClick = { onValueChange("") }) {
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = "清除搜索",
                            tint = colors.onSurfaceVariant,
                        )
                    }
                }
                Box {
                    IconButton(onClick = onMenuToggle) {
                        Icon(
                            Icons.Filled.MoreVert,
                            contentDescription = "更多",
                            tint = colors.onSurfaceVariant,
                        )
                    }
                    DropdownMenu(expanded = menuVisible, onDismissRequest = onMenuDismiss) {
                        DropdownMenuItem(
                            text = { Text("设置") },
                            onClick = { onMenuDismiss(); onOpenSettings() },
                        )
                        DropdownMenuItem(
                            text = { Text("收藏管理") },
                            onClick = { onMenuDismiss(); onOpenFavorites() },
                        )
                        DropdownMenuItem(
                            text = { Text("修改密码") },
                            onClick = { onMenuDismiss(); onOpenChangePassword() },
                        )
                        DropdownMenuItem(
                            text = { Text("服务地址") },
                            onClick = { onMenuDismiss(); onChangeServer() },
                        )
                        DropdownMenuItem(
                            text = { Text("退出登录") },
                            onClick = { onMenuDismiss(); onLogout() },
                        )
                    }
                }
            }
        },
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = colors.onSurface,
            unfocusedTextColor = colors.onSurface,
            focusedContainerColor = colors.surfaceContainerLow,
            unfocusedContainerColor = colors.surfaceContainerLow,
            focusedBorderColor = colors.outlineVariant,
            unfocusedBorderColor = colors.outlineVariant,
            cursorColor = colors.primary,
            focusedPlaceholderColor = colors.onSurfaceVariant,
            unfocusedPlaceholderColor = colors.onSurfaceVariant,
            focusedLeadingIconColor = colors.onSurfaceVariant,
            unfocusedLeadingIconColor = colors.onSurfaceVariant,
            focusedTrailingIconColor = colors.onSurfaceVariant,
            unfocusedTrailingIconColor = colors.onSurfaceVariant,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

@Composable
private fun FilterRow(
    favoritesOnly: Boolean,
    onAll: () -> Unit,
    onFavorites: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        FilterPill("全部", !favoritesOnly, onAll)
        FilterPill("我的收藏", favoritesOnly, onFavorites)
    }
}

@Composable
private fun FilterPill(text: String, selected: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier
            .height(38.dp)
            .clip(ShapePill)
            .clickable(onClick = onClick),
        shape = ShapePill,
        color = if (selected) colors.primaryContainer else colors.surfaceContainerLow,
        contentColor = if (selected) colors.onPrimaryContainer else colors.onSurfaceVariant,
        border = BorderStroke(
            1.dp,
            if (selected) colors.primary else colors.outline,
        ),
    ) {
        Box(
            modifier = Modifier.fillMaxHeight().padding(horizontal = 18.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(text, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun SectionHeader(
    title: String,
    count: Int,
    sortLabel: String,
    onSort: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            "%02d".format(count),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 8.dp),
        )
        Spacer(modifier = Modifier.weight(1f))
        Text(
            sortLabel,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .clip(ShapeItem)
                .clickable(onClick = onSort)
                .padding(horizontal = 8.dp, vertical = 8.dp),
        )
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
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(ShapeCard)
            .background(CardSurfaceColor)
            .clickable(onClick = onOpen),
    ) {
        Box(modifier = Modifier.fillMaxWidth().aspectRatio(1f)) {
            RemoteImage(
                url = if (work.coverUrl.startsWith("http")) work.coverUrl
                else AppContainer.api.authedUrl(work.coverUrl),
                modifier = Modifier.fillMaxSize().clip(ShapeCard),
            )
            // 收藏星标移到封面左上角，触控区 48dp；未收藏白星，已收藏紫圆底白星
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .size(48.dp)
                    .clip(ShapePill)
                    .clickable { scope.launch { FavoritesState.toggleWork(work.rjCode) } },
                contentAlignment = Alignment.Center,
            ) {
                if (fav) {
                    Surface(
                        modifier = Modifier.size(30.dp),
                        shape = ShapePill,
                        color = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Filled.Star,
                                contentDescription = "取消收藏",
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                } else {
                    Icon(
                        Icons.Filled.Star,
                        contentDescription = "收藏",
                        tint = CardTextColor,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
            // 类型标签叠在封面上：不透明底，避免半透明叠层糊住封面
            if (work.tags.isNotEmpty()) {
                LazyRow(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    lazyItems(work.tags.take(6)) { tag -> CoverTagChip(tag.name) }
                }
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
                        modifier = Modifier.align(Alignment.TopEnd).padding(12.dp),
                    )
                }
                Text(
                    work.rjCode,
                    style = MaterialTheme.typography.labelSmall,
                    color = CardTextColor,
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(6.dp)
                        .background(CoverTagColor, ShapeItem)
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
        }
        if (!imageMode) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(
                    work.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = CardTextColor,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                CardInfoRow("系列", work.series)
                CardInfoRow("社团", work.circle)
                CardInfoRow("销量", work.salesCount?.let { "%,d".format(it) } ?: "—")
                if (work.actors.isNotEmpty()) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        work.actors.take(2).forEach { actor ->
                            ActorChip(actor, Modifier.weight(1f, fill = false))
                        }
                        if (work.actors.size > 2) {
                            Text(
                                "+${work.actors.size - 2}",
                                style = MaterialTheme.typography.labelSmall,
                                color = CardSubTextColor,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CardInfoRow(label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = CardSubTextColor,
            modifier = Modifier.width(32.dp),
        )
        Text(
            value.ifBlank { "—" },
            style = MaterialTheme.typography.labelSmall,
            color = CardTextColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun CoverTagChip(text: String) {
    Surface(
        shape = ShapePill,
        color = CoverTagColor,
        contentColor = CardTextColor,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .widthIn(max = 88.dp)
                .padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}

@Composable
private fun ActorChip(text: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = ShapePill,
        color = ActorPillColor,
        contentColor = ActorPillTextColor,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .widthIn(max = 108.dp)
                .padding(horizontal = 12.dp, vertical = 5.dp),
        )
    }
}
