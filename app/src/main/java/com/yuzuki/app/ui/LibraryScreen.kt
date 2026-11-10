package com.yuzuki.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.foundation.lazy.items as lazyItems
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yuzuki.app.data.AppContainer
import com.yuzuki.app.data.FilterCond
import com.yuzuki.app.data.Work
import com.yuzuki.app.data.WorksResponse
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

/** 页面级间距（.content padding: 14px 20px 24px / .grid gap: 16px）。 */
private const val PAGE_PAD_H = 20
private const val PAGE_PAD_TOP = 14
private const val PAGE_PAD_BOTTOM = 24
private const val GRID_GAP = 16

@Composable
fun LibraryScreen(
    onOpenWork: (String) -> Unit,
    onOpenDrawer: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val gridState = rememberLazyStaggeredGridState()
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
                        // 按 rj 去重：排序键相同（added_at 并列）时跨页可能重复，
                        // 重复 key 会让瀑布流错位/跳动
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

    // 启动恢复 / 其它入口改了查询词时，输入框跟随（否则会出现「搜索词生效但框里空白」）
    LaunchedEffect(LibraryState.query) {
        if (LibraryState.query != searchText) searchText = LibraryState.query
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
    // 悬浮控件的「回到顶部」
    LaunchedEffect(DockState.scrollTopTicket) {
        if (DockState.scrollTopTicket > 0) gridState.animateScrollToItem(0)
    }
    LaunchedEffect(Unit) { FavoritesState.refresh() }

    val columns = if (LocalConfiguration.current.screenWidthDp >= 600) 3 else 2

    // 瀑布流：可见最大下标 + 1 即「已看到的作品数」（末位 footer 的 index 会被截断到 items.size）
    val shouldLoadMore by remember {
        derivedStateOf {
            val last = gridState.layoutInfo.visibleItemsInfo.maxOfOrNull { it.index } ?: -1
            val seen = (last + 1).coerceIn(0, items.size)
            PagingLogic.shouldLoadMore(
                seen, items.size, total, loading, endReached, LibraryState.randomMode,
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
            else -> LazyVerticalStaggeredGrid(
                columns = StaggeredGridCells.Fixed(columns),
                state = gridState,
                contentPadding = PaddingValues(
                    start = PAGE_PAD_H.dp,
                    end = PAGE_PAD_H.dp,
                    top = if (hasFilter) 12.dp else PAGE_PAD_TOP.dp,
                    bottom = PAGE_PAD_BOTTOM.dp,
                ),
                horizontalArrangement = Arrangement.spacedBy(GRID_GAP.dp),
                verticalItemSpacing = GRID_GAP.dp,
                modifier = Modifier.weight(1f),
            ) {
                // 小红书式瀑布流：两列各自按卡片自然高度向下排，彼此互不影响
                items(items, key = { it.rjCode }) { work ->
                    WorkCard(
                        work = work,
                        imageMode = LibraryState.imageMode,
                        cardBg = cardBg,
                        metaBg = metaBg,
                        genreBg = genreBg,
                        actorBg = actorBg,
                        onOpen = { onOpenWork(work.rjCode) },
                        modifier = Modifier.fillMaxWidth(),
                        onCond = { cond -> LibraryState.addCond(cond) },
                    )
                }
                item(span = StaggeredGridItemSpan.FullLine, key = "footer") {
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
