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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items as lazyItems
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yuzuki.app.data.AppContainer
import com.yuzuki.app.data.CountItem
import com.yuzuki.app.data.FilterCond
import com.yuzuki.app.data.Work
import com.yuzuki.app.data.WorksResponse
import com.yuzuki.app.ui.theme.GlassItem
import com.yuzuki.app.ui.theme.GlassSpec
import com.yuzuki.app.ui.theme.LocalAppearance
import com.yuzuki.app.ui.theme.ShapeItem
import com.yuzuki.app.ui.theme.ShapePill
import com.yuzuki.app.ui.theme.WebColors
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/*
 * 本文件所有尺寸都是 web 端 CSS 的 px 原值 —— 网页在 412px 视口下的布局
 * 与 Android 412dp 屏一一对应，因此 CSS px 可直接当作 dp 使用，不做任何换算。
 */

/** 与 web 的 SORT_OPTIONS 完全一致（标签是两个字，排序胶囊才装得下）。 */
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

/** 卡片圆角 10 / 封面圆角 9（web 的 .work-card / .cover）。 */
private val CardShape = RoundedCornerShape(10.dp)

/** 页面级间距（.content padding: 14px 20px 24px / .grid gap: 16px）。 */
private const val PAGE_PAD_H = 20
private const val PAGE_PAD_TOP = 14
private const val PAGE_PAD_BOTTOM = 24
private const val GRID_GAP = 16

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun LibraryScreen(
    onOpenWork: (String) -> Unit,
    onOpenDrawer: () -> Unit,
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
    LaunchedEffect(LibraryState.sheetTicket) { if (LibraryState.sheetTicket > 0) sheetVisible = true }
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

    val appearance = LocalAppearance.current
    val nav = appearance.navColor
    val topbarColor = WebColors.topbar(nav, appearance.panelAlpha)
    val controlBg = WebColors.control(appearance.controlAlpha)
    val cardBg = WebColors.panel(nav, appearance.panelAlpha)
    val metaBg = WebColors.cardMeta(nav)
    val genreBg = WebColors.tagBg(appearance.tagColor, appearance.genreTagAlpha)
    val actorBg = WebColors.tagBg(appearance.actorColor, appearance.actorTagAlpha)

    val columns = if (LocalConfiguration.current.screenWidthDp >= 600) 3 else 2
    val hasFilter = searchText.isNotBlank() || LibraryState.conds.isNotEmpty()

    Column(modifier = modifier.fillMaxSize()) {
        LibraryTopBar(
            searchText = searchText,
            onSearchChange = { searchText = it },
            total = total,
            onOpenDrawer = onOpenDrawer,
            topbarColor = topbarColor,
            controlBg = controlBg,
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
                modifier = Modifier.padding(horizontal = PAGE_PAD_H.dp).fillMaxWidth(),
                onRetry = { requestPage(reset = true) },
            )
            else -> LazyVerticalGrid(
                columns = GridCells.Fixed(columns),
                state = gridState,
                contentPadding = PaddingValues(
                    start = PAGE_PAD_H.dp,
                    end = PAGE_PAD_H.dp,
                    top = if (hasFilter) 12.dp else PAGE_PAD_TOP.dp,
                    bottom = PAGE_PAD_BOTTOM.dp,
                ),
                horizontalArrangement = Arrangement.spacedBy(GRID_GAP.dp),
                verticalArrangement = Arrangement.spacedBy(GRID_GAP.dp),
                modifier = Modifier.weight(1f),
            ) {
                gridItems(items, key = { it.rjCode }) { work ->
                    WorkCard(
                        work = work,
                        imageMode = LibraryState.imageMode,
                        cardBg = cardBg,
                        metaBg = metaBg,
                        genreBg = genreBg,
                        actorBg = actorBg,
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
                                fontSize = 14.sp,
                                color = WebColors.Muted,
                            )
                            !loading && items.isEmpty() -> Text(
                                if (hasFilter) "没有匹配的作品"
                                else "暂无作品，请先在「设置」中配置 ASMR 源路径并扫描。",
                                fontSize = 15.sp,
                                color = WebColors.Muted,
                                textAlign = TextAlign.Center,
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
// .topbar: min-height 56 / padding 8 10 / gap 6 / bg topbar-color / border-bottom 1px
// .top-btn: 32x32 圆形 + 1px 边框
// .topbar-controls: height 38 / gap 6；搜索胶囊 padding 0 10 / radius 999
// 排序胶囊: padding 0 5 / gap 2；sort-tab 28 高 padding 0 7 12px；order-toggle 26x26

@Composable
private fun LibraryTopBar(
    searchText: String,
    onSearchChange: (String) -> Unit,
    total: Int,
    onOpenDrawer: () -> Unit,
    topbarColor: Color,
    controlBg: Color,
) {
    Column(modifier = Modifier.fillMaxWidth().background(topbarColor)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            // 菜单按钮：32 × 32 圆形 + 1px 边框，点击打开 web 式侧栏抽屉
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .border(1.dp, WebColors.Border, CircleShape)
                    .clickable(onClick = onOpenDrawer),
                contentAlignment = Alignment.Center,
            ) {
                MenuIcon(WebColors.Text, Modifier.size(18.dp))
            }

            Row(
                modifier = Modifier.weight(1f).height(38.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                SearchPill(
                    value = searchText,
                    onValueChange = onSearchChange,
                    placeholder = when {
                        LibraryState.randomMode -> "在${total}个随机推荐中搜索"
                        total > 0 -> "在${total}个作品中搜索"
                        else -> "搜索标题 / RJ号 / 声优 / 标签"
                    },
                    background = controlBg,
                    modifier = Modifier.weight(1f),
                )

                Row(
                    modifier = Modifier
                        .height(38.dp)
                        .clip(ShapePill)
                        .background(controlBg)
                        .border(1.dp, WebColors.Border, ShapePill)
                        .padding(horizontal = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    TOP_SORTS.forEach { (key, label) ->
                        SortTabButton(
                            label = label,
                            active = LibraryState.sort == key,
                            onClick = { LibraryState.updateSort(key) },
                        )
                    }
                    Box(
                        modifier = Modifier
                            .padding(start = 4.dp)
                            .size(26.dp)
                            .clip(ShapePill)
                            .clickable { LibraryState.toggleOrder() },
                        contentAlignment = Alignment.Center,
                    ) {
                        SortOrderIcon(
                            descending = LibraryState.order == "desc",
                            tint = WebColors.Text,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
            }
        }
        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(WebColors.Border))
    }
}

/** 搜索胶囊：web 的 el-input 没有放大镜图标，只有 placeholder 与清空 ×。 */
@Composable
private fun SearchPill(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    background: Color,
    modifier: Modifier = Modifier,
) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = TextStyle(color = WebColors.Text, fontSize = 12.sp),
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        modifier = modifier,
        decorationBox = { inner ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(ShapePill)
                    .background(background)
                    .border(1.dp, WebColors.Border, ShapePill)
                    .padding(start = 10.dp, end = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                    if (value.isEmpty()) {
                        Text(
                            text = placeholder,
                            fontSize = 12.sp,
                            color = WebColors.Muted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    inner()
                }
                if (value.isNotEmpty()) {
                    Box(
                        modifier = Modifier.size(24.dp).clip(CircleShape).clickable { onValueChange("") },
                        contentAlignment = Alignment.Center,
                    ) {
                        ClearIcon(WebColors.Muted, Modifier.size(12.dp))
                    }
                }
            }
        },
    )
}

/** sort-tab：28 高 / padding 0 7 / 12px；选中 = 强调色底 + 深色文字 + 600。 */
@Composable
private fun SortTabButton(label: String, active: Boolean, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val accent = MaterialTheme.colorScheme.primary
    Box(
        modifier = Modifier
            .height(28.dp)
            .clip(ShapePill)
            .background(if (active) accent else Color.Transparent)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 7.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
            color = if (active) MaterialTheme.colorScheme.onPrimary else WebColors.Text,
        )
    }
}

// ---------------------------------------------------------------- 已选条件
// .status-row: gap 12；el-tag: 24 高 / padding 0 9 / 12px / 白 35% 底 + 深灰字

@Composable
private fun ActiveCondRow(
    query: String,
    conds: List<FilterCond>,
    onRemoveQuery: () -> Unit,
    onRemoveCond: (FilterCond) -> Unit,
    onClearAll: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = PAGE_PAD_H.dp, end = PAGE_PAD_H.dp, top = 14.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        LazyRow(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
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
                .height(24.dp)
                .clip(ShapeItem)
                .border(1.dp, WebColors.Border, ShapeItem)
                .clickable(onClick = onClearAll)
                .padding(horizontal = 11.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text("清除筛选", fontSize = 12.sp, color = WebColors.Muted)
        }
    }
}

@Composable
private fun ActiveCondChip(text: String, onRemove: () -> Unit) {
    Row(
        modifier = Modifier
            .height(24.dp)
            .clip(ShapeItem)
            .background(Color(0x59FFFFFF))
            .clickable(onClick = onRemove)
            .padding(start = 9.dp, end = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, fontSize = 12.sp, color = WebColors.ElTextPrimary, maxLines = 1)
        Spacer(Modifier.width(4.dp))
        ClearIcon(WebColors.ElTextPrimary, Modifier.size(10.dp))
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
// .work-card: radius 10 / border 1px / bg panel
// .cover: 1:1；.fav-star: 30x30 @ 6,6（svg 22）
// .cover-tags: padding 14 6 6 / gap 4 / 渐变到 rgba(0,0,0,.72)
// .meta: padding 10 12 12 / gap 6 / bg card-meta

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WorkCard(
    work: Work,
    imageMode: Boolean,
    cardBg: Color,
    metaBg: Color,
    genreBg: Color,
    actorBg: Color,
    onOpen: () -> Unit,
    onCond: (FilterCond) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val fav = FavoritesState.workRjs.contains(work.rjCode)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(CardShape)
            .background(cardBg)
            .border(1.dp, WebColors.Border, CardShape)
            .clickable(onClick = onOpen),
    ) {
        // clipToBounds 等价于 web 的 overflow:hidden —— 标签再多也不会溢出到信息区
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .background(Color(0xFF0D0F12))
                .clipToBounds(),
        ) {
            RemoteImage(
                url = if (work.coverUrl.startsWith("http")) work.coverUrl
                else AppContainer.api.authedUrl(work.coverUrl),
                modifier = Modifier.fillMaxSize(),
            )

            if (work.tags.isNotEmpty() || imageMode) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(64.dp)
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, Color(0xB8000000)),
                            ),
                        ),
                )
            }

            if (work.tags.isNotEmpty()) {
                FlowRow(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .padding(start = 6.dp, end = 6.dp, top = 14.dp, bottom = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    work.tags.forEach { tag ->
                        CoverTagChip(tag.name, genreBg) { onCond(FilterCond("tag", tag.name)) }
                    }
                }
            }

            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(6.dp)
                    .size(30.dp)
                    .clip(CircleShape)
                    .clickable { scope.launch { FavoritesState.toggleWork(work.rjCode) } },
                contentAlignment = Alignment.Center,
            ) {
                FavoriteStarIcon(
                    favored = fav,
                    tint = if (fav) MaterialTheme.colorScheme.primary else Color.White,
                    modifier = Modifier.size(22.dp),
                )
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
                        fontSize = 11.sp,
                        modifier = Modifier.align(Alignment.TopEnd).padding(10.dp),
                    )
                }
                Text(
                    work.rjCode,
                    fontSize = 11.sp,
                    color = WebColors.Text,
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(6.dp)
                        .background(Color(0xFF282830), ShapeItem)
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
        }

        if (!imageMode) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(metaBg)
                    .padding(start = 12.dp, end = 12.dp, top = 10.dp, bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    work.title.ifBlank { work.rjCode },
                    fontSize = 15.sp,
                    lineHeight = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = WebColors.Text,
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
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        work.actors.take(4).forEach { actor ->
                            ActorChip(actor, actorBg) { onCond(FilterCond("actor", actor)) }
                        }
                    }
                }
            }
        }
    }
}

/** .row: flex / gap 8 / font-size 13；.label 用 muted，.value 用正文色。 */
@Composable
private fun CardInfoRow(label: String, value: String, onClick: (() -> Unit)? = null) {
    Row(
        modifier = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, fontSize = 13.sp, color = WebColors.Muted, maxLines = 1)
        Text(
            value.ifBlank { "—" },
            fontSize = 13.sp,
            color = WebColors.Text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** .cover-tag: 11px / padding 3 9 / radius 999 / 主题标签色 × 标签透明度。 */
@Composable
private fun CoverTagChip(text: String, background: Color, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(ShapePill)
            .background(background)
            .clickable(onClick = onClick)
            .padding(horizontal = 9.dp, vertical = 3.dp),
    ) {
        Text(
            text,
            fontSize = 11.sp,
            lineHeight = 13.sp,
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** .chip: 12px / padding 3 10 / radius 999；声优标签用独立配色。 */
@Composable
private fun ActorChip(text: String, background: Color, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(ShapePill)
            .background(background)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 3.dp),
    ) {
        Text(
            text,
            fontSize = 12.sp,
            lineHeight = 14.sp,
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
