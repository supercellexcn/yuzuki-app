package com.yuzuki.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yuzuki.app.data.AppContainer
import com.yuzuki.app.data.CountItem
import com.yuzuki.app.data.FilterCond
import com.yuzuki.app.data.Work
import com.yuzuki.app.ui.theme.GlassItem
import com.yuzuki.app.ui.theme.GlassSpec
import com.yuzuki.app.ui.theme.ShapeItem
import com.yuzuki.app.ui.theme.ShapePill
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/** 与 web 的 SORT_OPTIONS 完全一致：入库 / 发售 / 销量（标签是两个字，顶栏才放得下）。 */
private val TOP_SORTS = listOf(
    "added" to "入库",
    "release" to "发售",
    "sales" to "销量",
)

private val SHEET_SORTS = listOf(
    "added" to "最近添加",
    "release" to "发售日",
    "sales" to "销量",
    "title" to "标题",
    "updated" to "更新时间",
)

// 卡片配色取自参考图实拍取色（不透明，禁止玻璃半透明叠层）
private val CardMetaColor = Color(0xFF604050)
private val CardBorderColor = Color(0xFF4A3A46)
private val CardTextColor = Color(0xFFF8F8F8)
private val CardSubTextColor = Color(0xFFC9BCC6)
private val CoverBaseColor = Color(0xFF0D0F12)
private val CoverTagColor = Color(0xFF282830)
private val ActorPillColor = Color(0xFF78C868)
private val ActorPillTextColor = Color(0xFF101010)

// 顶栏配色：纯黑背景上的紫黑控件（与底部 Tab 同一套色）
private val BarControlColor = Color(0xFF141119)
private val BarControlBorder = Color(0xFF2A2430)
private val SortActiveBg = Color(0xFF2A2140)
private val CondChipBg = Color(0xFFE9E6EE)
private val CondChipText = Color(0xFF16121C)
private val ClearChipBg = Color(0xFF24242A)

private val CardShape = RoundedCornerShape(12.dp)

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
                val result = if (LibraryState.randomMode) {
                    val size = PagingLogic.pageSize(AppContainer.settings.current.pageSize)
                    AppContainer.api.randomWorks(LibraryState.query, LibraryState.conds, size * 3)
                        .map { com.yuzuki.app.data.WorksResponse(it, it.size, 1, it.size) }
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
                        items = if (reset || LibraryState.randomMode) response.items else items + response.items
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
    val hasFilter = searchText.isNotBlank() || LibraryState.conds.isNotEmpty()

    Column(modifier = modifier.fillMaxSize()) {
        LibraryTopBar(
            searchText = searchText,
            onSearchChange = { searchText = it },
            total = total,
            menuVisible = menuVisible,
            onMenuVisible = { menuVisible = it },
            onOpenFilter = { sheetVisible = true },
            onImageMode = { LibraryState.imageMode = !LibraryState.imageMode },
            onRandom = { LibraryState.toggleRandom() },
            onScrollTop = { LibraryState.scrollToTop() },
            onOpenFavorites = onOpenFavorites,
            onOpenSettings = onOpenSettings,
            onOpenChangePassword = onOpenChangePassword,
            onChangeServer = onChangeServer,
            onLogout = onLogout,
        )

        if (hasFilter) {
            ActiveCondRow(
                query = searchText,
                conds = LibraryState.conds,
                onRemoveQuery = {
                    searchText = ""
                    LibraryState.updateQuery("")
                },
                onRemoveCond = { LibraryState.removeCond(it) },
                onClearAll = {
                    searchText = ""
                    LibraryState.updateQuery("")
                    LibraryState.clearConds()
                },
            )
        }

        when {
            error != null && items.isEmpty() -> StateBox(
                message = error ?: "加载失败",
                modifier = Modifier.weight(1f).fillMaxWidth().padding(12.dp),
                onRetry = { requestPage(reset = true) },
            )
            else -> LazyVerticalGrid(
                columns = GridCells.Fixed(columns),
                state = gridState,
                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f),
            ) {
                gridItems(items, key = { it.rjCode }) { work ->
                    WorkCard(
                        work = work,
                        imageMode = LibraryState.imageMode,
                        onOpen = { onOpenWork(work.rjCode) },
                        onCond = { cond -> LibraryState.addCond(cond) },
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
                                "—— 已经加载所有 ——",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            !loading && items.isEmpty() -> Text(
                                if (hasFilter) "没有匹配的作品" else "暂无作品，请先在「设置」中配置 ASMR 源路径并扫描。",
                                fontSize = 13.sp,
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

private fun sortTitle(sort: String, order: String): String {
    val label = SHEET_SORTS.firstOrNull { it.first == sort }?.second ?: "最近添加"
    return "$label ${if (order == "desc") "↓" else "↑"}"
}

// ---------------------------------------------------------------- 顶栏

@Composable
private fun LibraryTopBar(
    searchText: String,
    onSearchChange: (String) -> Unit,
    total: Int,
    menuVisible: Boolean,
    onMenuVisible: (Boolean) -> Unit,
    onOpenFilter: () -> Unit,
    onImageMode: () -> Unit,
    onRandom: () -> Unit,
    onScrollTop: () -> Unit,
    onOpenFavorites: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenChangePassword: () -> Unit,
    onChangeServer: () -> Unit,
    onLogout: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 10.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(ShapeItem)
                    .background(BarControlColor)
                    .border(1.dp, BarControlBorder, ShapeItem)
                    .clickable { onMenuVisible(true) },
                contentAlignment = Alignment.Center,
            ) {
                MenuIcon(MaterialTheme.colorScheme.onSurface, Modifier.size(18.dp))
            }
            DropdownMenu(expanded = menuVisible, onDismissRequest = { onMenuVisible(false) }) {
                DropdownMenuItem(
                    text = { Text("图片模式") },
                    onClick = { onMenuVisible(false); onImageMode() },
                    trailingIcon = { Text(if (LibraryState.imageMode) "开" else "关") },
                )
                DropdownMenuItem(
                    text = { Text("随机推荐") },
                    onClick = { onMenuVisible(false); onRandom() },
                    trailingIcon = { Text(if (LibraryState.randomMode) "开" else "关") },
                )
                DropdownMenuItem(
                    text = { Text("回到顶部") },
                    onClick = { onMenuVisible(false); onScrollTop() },
                )
                HorizontalDivider()
                DropdownMenuItem(
                    text = { Text("收藏管理") },
                    onClick = { onMenuVisible(false); onOpenFavorites() },
                )
                DropdownMenuItem(
                    text = { Text("设置") },
                    onClick = { onMenuVisible(false); onOpenSettings() },
                )
                DropdownMenuItem(
                    text = { Text("修改密码") },
                    onClick = { onMenuVisible(false); onOpenChangePassword() },
                )
                DropdownMenuItem(
                    text = { Text("服务地址") },
                    onClick = { onMenuVisible(false); onChangeServer() },
                )
                DropdownMenuItem(
                    text = { Text("退出登录") },
                    onClick = { onMenuVisible(false); onLogout() },
                )
            }
        }

        SearchField(
            value = searchText,
            onValueChange = onSearchChange,
            placeholder = when {
                LibraryState.randomMode -> "在${total}个随机推荐中搜索"
                total > 0 -> "在${total}个作品中搜索"
                else -> "搜索标题 / RJ号 / 声优 / 标签"
            },
            modifier = Modifier.weight(1f),
        )

        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            TOP_SORTS.forEach { (key, label) ->
                SortTabButton(
                    label = label,
                    active = LibraryState.sort == key,
                    onClick = { LibraryState.updateSort(key) },
                )
            }
        }

        CircleIconButton(onClick = { LibraryState.toggleOrder() }) {
            SortOrderIcon(
                descending = LibraryState.order == "desc",
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(18.dp),
            )
        }

        CircleIconButton(onClick = onOpenFilter) {
            FunnelIcon(MaterialTheme.colorScheme.onSurface, Modifier.size(19.dp))
        }
    }
}

@Composable
private fun SearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = TextStyle(color = colors.onSurface, fontSize = 13.sp),
        cursorBrush = SolidColor(colors.primary),
        modifier = modifier,
        decorationBox = { inner ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp)
                    .clip(ShapePill)
                    .background(BarControlColor)
                    .border(1.dp, BarControlBorder, ShapePill)
                    .padding(start = 12.dp, end = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SearchIcon(colors.onSurfaceVariant, Modifier.size(16.dp))
                Spacer(Modifier.width(7.dp))
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                    if (value.isEmpty()) {
                        Text(
                            text = placeholder,
                            fontSize = 12.sp,
                            color = colors.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    inner()
                }
                if (value.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(ShapePill)
                            .clickable { onValueChange("") },
                        contentAlignment = Alignment.Center,
                    ) {
                        ClearIcon(colors.onSurfaceVariant, Modifier.size(13.dp))
                    }
                }
            }
        },
    )
}

@Composable
private fun SortTabButton(label: String, active: Boolean, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .height(28.dp)
            .clip(ShapePill)
            .background(if (active) SortActiveBg else Color.Transparent)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 7.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
            color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun CircleIconButton(onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .size(30.dp)
            .clip(ShapePill)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

// ---------------------------------------------------------------- 已选条件

@Composable
private fun ActiveCondRow(
    query: String,
    conds: List<FilterCond>,
    onRemoveQuery: () -> Unit,
    onRemoveCond: (FilterCond) -> Unit,
    onClearAll: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 10.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        LazyRow(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (query.isNotBlank()) {
                item { ActiveCondChip("搜索: $query", onRemoveQuery) }
            }
            lazyItems(conds, key = { "${it.type}:${it.value}" }) { cond ->
                ActiveCondChip("${typeLabel(cond.type)}: ${cond.value}") { onRemoveCond(cond) }
            }
        }
        Box(
            modifier = Modifier
                .height(28.dp)
                .clip(ShapePill)
                .background(ClearChipBg)
                .clickable(onClick = onClearAll)
                .padding(horizontal = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text("清除筛选", fontSize = 12.sp, color = Color(0xFFC8C8D2))
        }
    }
}

@Composable
private fun ActiveCondChip(text: String, onRemove: () -> Unit) {
    Row(
        modifier = Modifier
            .height(28.dp)
            .clip(ShapePill)
            .background(CondChipBg)
            .clickable(onClick = onRemove)
            .padding(start = 11.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, fontSize = 12.sp, color = CondChipText, maxLines = 1)
        Spacer(Modifier.width(5.dp))
        ClearIcon(Color(0xFF5A5460), Modifier.size(10.dp))
    }
}

private fun typeLabel(type: String): String = when (type) {
    "circle" -> "社团"
    "series" -> "系列"
    "actor" -> "声优"
    else -> "标签"
}

// ---------------------------------------------------------------- 筛选面板（保留原实现）

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
            Text(
                "排序与筛选 · ${sortTitle(LibraryState.sort, LibraryState.order)}",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onClose) { Text("完成") }
        }

        Text("排序", style = MaterialTheme.typography.titleSmall)
        SHEET_SORTS.forEach { (key, label) ->
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

// ---------------------------------------------------------------- 卡片

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WorkCard(
    work: Work,
    imageMode: Boolean,
    onOpen: () -> Unit,
    onCond: (FilterCond) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val fav = FavoritesState.workRjs.contains(work.rjCode)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(CardShape)
            .background(CardMetaColor)
            .border(1.dp, CardBorderColor, CardShape)
            .clickable(onClick = onOpen),
    ) {
        Box(modifier = Modifier.fillMaxWidth().aspectRatio(1f).background(CoverBaseColor)) {
            RemoteImage(
                url = if (work.coverUrl.startsWith("http")) work.coverUrl
                else AppContainer.api.authedUrl(work.coverUrl),
                modifier = Modifier.fillMaxSize(),
            )

            // 封面底部渐隐：与 web 一致，仅用于托住类型标签，不涉及玻璃主题
            if (work.tags.isNotEmpty() || imageMode) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(72.dp)
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, Color(0xB3000000)),
                            ),
                        ),
                )
            }

            // 收藏星标：左上角，未收藏空心白星 / 已收藏紫色实心
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .size(44.dp)
                    .clip(ShapePill)
                    .clickable { scope.launch { FavoritesState.toggleWork(work.rjCode) } },
                contentAlignment = Alignment.Center,
            ) {
                FavoriteStarIcon(
                    favored = fav,
                    tint = if (fav) MaterialTheme.colorScheme.primary else Color(0xFFF2F0F6),
                    modifier = Modifier.size(21.dp),
                )
            }

            if (work.tags.isNotEmpty()) {
                // 与 web 的 flex-wrap 一致：标签最多铺两行，超出裁掉，避免封面被标签吃掉
                FlowRow(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .padding(horizontal = 6.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    maxLines = 2,
                ) {
                    work.tags.take(6).forEach { tag ->
                        CoverTagChip(tag.name) { onCond(FilterCond("tag", tag.name)) }
                    }
                }
            }

            if (imageMode) {
                val flame = GlassSpec.flame(work.salesCount)
                if (flame.count > 0) {
                    Text(
                        "▲".repeat(flame.count),
                        color = when (flame.kind) {
                            "red" -> Color(0xFFF05A4B)
                            "yellow" -> Color(0xFFF2C14E)
                            "rainbow" -> Color(0xFFB69AF6)
                            else -> Color(0xFF9AA3B0)
                        },
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.align(Alignment.TopEnd).padding(10.dp),
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
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Text(
                    work.title.ifBlank { work.rjCode },
                    fontSize = 15.sp,
                    lineHeight = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = CardTextColor,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (work.series.isNotBlank()) {
                    CardInfoRow("系列", work.series) { onCond(FilterCond("series", work.series)) }
                }
                if (work.circle.isNotBlank()) {
                    CardInfoRow("社团", work.circle) { onCond(FilterCond("circle", work.circle)) }
                }
                if (work.salesCount != null) {
                    CardInfoRow("销量", "%,d".format(work.salesCount))
                }
                if (work.playCount > 0) {
                    CardInfoRow("播放", "%,d".format(work.playCount))
                }
                if (work.actors.isNotEmpty()) {
                    FlowRow(
                        modifier = Modifier.fillMaxWidth().padding(top = 3.dp),
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                        verticalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                        work.actors.take(4).forEach { actor ->
                            ActorChip(actor) { onCond(FilterCond("actor", actor)) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CardInfoRow(label: String, value: String, onClick: (() -> Unit)? = null) {
    Row(
        modifier = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            fontSize = 12.sp,
            color = CardSubTextColor,
            modifier = Modifier.width(30.dp),
        )
        Text(
            value.ifBlank { "—" },
            fontSize = 12.sp,
            color = CardTextColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun CoverTagChip(text: String, onClick: () -> Unit) {
    Surface(
        shape = ShapePill,
        color = CoverTagColor,
        contentColor = CardTextColor,
        onClick = onClick,
    ) {
        Text(
            text,
            fontSize = 11.sp,
            lineHeight = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .widthIn(max = 88.dp)
                .padding(horizontal = 9.dp, vertical = 4.dp),
        )
    }
}

@Composable
private fun ActorChip(text: String, onClick: () -> Unit) {
    Surface(
        shape = ShapePill,
        color = ActorPillColor,
        contentColor = ActorPillTextColor,
        onClick = onClick,
    ) {
        Text(
            text,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .widthIn(max = 108.dp)
                .padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}
