package com.yuzuki.app.ui

import android.content.Context
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items as lazyColumnItems
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.lazy.items as lazyItems
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yuzuki.app.data.FilterCond
import com.yuzuki.app.ui.theme.LocalAppearance
import com.yuzuki.app.ui.theme.ShapeItem
import com.yuzuki.app.ui.theme.ShapePill
import com.yuzuki.app.ui.theme.WebColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.distinctUntilChanged

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

/** SharedPreferences(yuzuki_ui) 里记录库页视图形态的键。 */
private const val KEY_LIBRARY_VIEW = "library_view"

@Composable
fun LibraryScreen(
    vm: LibraryListViewModel,
    gridState: LazyStaggeredGridState,
    listState: LazyListState,
    onOpenWork: (String) -> Unit,
    onOpenDrawer: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val uiPrefs = remember { context.getSharedPreferences("yuzuki_ui", Context.MODE_PRIVATE) }
    var searchText by remember { mutableStateOf(LibraryState.query) }

    /** 条件指纹：只含会改变列表数据的条件；视图形态不改变数据，单独处理。 */
    val fingerprint = ScrollRestore.fingerprint(
        listOf(
            LibraryState.query,
            LibraryState.sort,
            LibraryState.order,
            LibraryState.randomMode.toString(),
            // 随机批次号：「已随机」时再拉一次要换一批，条件没变也得重新请求
            LibraryState.randomEpoch.toString(),
        ) + LibraryState.conds.map { "${it.type}:${it.value}" },
    )
    val currentFingerprint by rememberUpdatedState(fingerprint)

    // 记录「进入页面时」的票值：只响应进入之后的变化，首次组合不再误置顶
    val resetEpochAtEnter = remember { vm.resetEpoch }
    val libraryTopAtEnter = remember { LibraryState.scrollTopTicket }
    val viewAtEnter = remember { LibraryState.view }

    // 其它入口（点标签 / 声优进搜索等）改了查询词时，输入框跟随（否则会出现「搜索词生效但框里空白」）
    LaunchedEffect(LibraryState.query) {
        if (LibraryState.query != searchText) searchText = LibraryState.query
    }

    // 关键词只落到内存（LibraryState.query），不落盘也不上报服务端：
    // 下次冷启动仍是空搜索，也不会把网页端的搜索条件带回来。
    LaunchedEffect(searchText) {
        if (searchText != LibraryState.query) {
            delay(400)
            LibraryState.updateQuery(searchText)
        }
    }

    /**
     * 冷启动 / 条件变化 → 重新拉第一页；从其它界面返回（数据已属于当前条件）→ 什么都不做。
     * 冷启动时 loadedFingerprint == null，必然走一次 reset，首屏作品数量才不会不对。
     */
    LaunchedEffect(fingerprint) {
        if (!ScrollRestore.shouldReset(vm.loadedFingerprint, fingerprint)) return@LaunchedEffect
        // 已有旧数据 = 条件变化（含 A→B→A），必须回顶部；
        // 冷启动 loadedFingerprint == null，本来就在顶部，且数据到位后还会再置顶一次。
        if (ScrollRestore.shouldScrollTopOnReset(vm.loadedFingerprint)) {
            gridState.requestScrollToItem(0)
            listState.requestScrollToItem(0)
        }
        vm.resetConditions()
        vm.requestPage(reset = true, fingerprint = fingerprint)
    }

    // 视图形态（列表 / 详情 / 图片）记忆：与悬浮 M 键共用同一份状态
    LaunchedEffect(Unit) {
        val saved = uiPrefs.getString(KEY_LIBRARY_VIEW, null)
        LibraryView.entries.firstOrNull { it.name == saved }?.let { LibraryState.view = it }
    }
    LaunchedEffect(LibraryState.view) {
        uiPrefs.edit().putString(KEY_LIBRARY_VIEW, LibraryState.view.name).apply()
        // 列表 / 网格的索引语义不同，切换形态时回顶部
        if (LibraryState.view != viewAtEnter) {
            gridState.requestScrollToItem(0)
            listState.requestScrollToItem(0)
        }
    }

    // reset 成功（数据到位）后置顶：冷启动首帧还没有 item，沿用 9f951f3 的行为
    LaunchedEffect(vm.resetEpoch) {
        if (ScrollRestore.shouldConsumeTicket(vm.resetEpoch, resetEpochAtEnter)) {
            gridState.requestScrollToItem(0)
            listState.requestScrollToItem(0)
        }
    }
    // 重复点「库」Tab / LibraryState.home() 的显式回顶
    LaunchedEffect(LibraryState.scrollTopTicket) {
        if (ScrollRestore.shouldConsumeTicket(LibraryState.scrollTopTicket, libraryTopAtEnter)) {
            gridState.requestScrollToItem(0)
            listState.requestScrollToItem(0)
        }
    }
    LaunchedEffect(Unit) { FavoritesState.refresh() }

    // 持续记录滚动位置：ViewModel 存活，主页离开组合后快照仍在
    LaunchedEffect(Unit) {
        snapshotFlow {
            if (LibraryState.view == LibraryView.List) {
                listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset
            } else {
                gridState.firstVisibleItemIndex to gridState.firstVisibleItemScrollOffset
            }
        }.distinctUntilChanged().collect { (index, offset) ->
            vm.saveScroll(currentFingerprint, index, offset)
        }
    }

    val columns = if (LocalConfiguration.current.screenWidthDp >= 600) 3 else 2

    // 列表模式一行一张卡，取行下标；网格模式取两列里最大的下标（footer 的 index 会被截断到 items.size）
    val lastVisible by remember {
        derivedStateOf {
            val raw = if (LibraryState.view == LibraryView.List) {
                listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
            } else {
                gridState.layoutInfo.visibleItemsInfo.maxOfOrNull { it.index } ?: -1
            }
            (raw + 1).coerceIn(0, vm.items.size)
        }
    }
    // 用「可见下标」当 key：滚到底一次、且条件仍成立时也能继续取下一页
    LaunchedEffect(lastVisible, vm.items.size, vm.total, vm.endReached, vm.loading) {
        if (vm.loading || vm.endReached || LibraryState.randomMode) return@LaunchedEffect
        if (PagingLogic.shouldLoadMore(
                lastVisible, vm.items.size, vm.total, vm.loading, vm.endReached, LibraryState.randomMode,
            )
        ) {
            vm.requestPage(reset = false, fingerprint = fingerprint)
        }
    }

    // 返回主页时兜底恢复一次：滚动 state 一般会自行保留，这里防容器重建 / 索引被 clamp。
    // 只在进入页面执行，不能挂在 items.size 上 —— 否则翻页（50→100）会把用户拉回旧位置。
    LaunchedEffect(Unit) {
        if (ScrollRestore.shouldRestore(
                vm.loadedFingerprint, vm.savedFingerprint, fingerprint,
                vm.items.size, vm.savedIndex,
            )
        ) {
            val index = ScrollRestore.clampIndex(vm.savedIndex, vm.items.size)
            if (LibraryState.view == LibraryView.List) {
                listState.requestScrollToItem(index, vm.savedOffset)
            } else {
                gridState.requestScrollToItem(index, vm.savedOffset)
            }
        }
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

    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val triggerPx = with(density) { FeedPull.TRIGGER_DP.dp.toPx() }

    // 顶部下拉：露出高度跟手指走，松手再决定执行还是弹回
    var pullReveal by remember { mutableFloatStateOf(0f) }
    var pullSide by remember { mutableStateOf(FeedPullSide.NONE) }
    // 悬停读秒：0 = 不在计时，其余是「还有几秒退出随机」
    var holdSec by remember { mutableIntStateOf(0) }

    fun settlePull() {
        scope.launch {
            animate(pullReveal, 0f, animationSpec = tween(220)) { v, _ -> pullReveal = v }
        }
    }

    /**
     * 松手生效：左半屏循环切换「列表 → 卡片详情 → 封面模式」，右半屏进入随机 / 换一批。
     */
    fun runPullAction(side: FeedPullSide) {
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        when (side) {
            FeedPullSide.Left -> {
                LibraryState.cycleView()
                AppToast.show(
                    when (LibraryState.view) {
                        LibraryView.Detail -> "卡片详情"
                        LibraryView.Image -> "封面模式"
                        LibraryView.List -> "列表模式"
                    },
                )
            }
            FeedPullSide.Right -> {
                val entered = LibraryState.pullRandom()
                AppToast.show(if (entered) "随机推荐" else "换一批随机推荐")
            }
            FeedPullSide.NONE -> Unit
        }
    }

    val feedPull = Modifier.feedPullGesture(
        atTop = {
            if (LibraryState.view == LibraryView.List) {
                listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0
            } else {
                gridState.firstVisibleItemIndex == 0 && gridState.firstVisibleItemScrollOffset == 0
            }
        },
        onDrag = { reveal, side ->
            pullSide = side
            pullReveal = reveal
        },
        onRelease = { reveal, side ->
            if (FeedPull.armed(reveal, triggerPx)) runPullAction(side)
            settlePull()
        },
        onCancel = ::settlePull,
        // 随机模式没有别的出口：右半屏下拉越过门槛后停住 3 秒退出（松手不再「换一批」）
        holdEnabled = { LibraryState.randomMode },
        onHoldTick = { holdSec = it },
        onHold = {
            holdSec = 0
            if (LibraryState.exitRandom()) {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                AppToast.show("已退出随机推荐")
            }
            settlePull()
        },
    )

    Column(modifier = modifier.fillMaxSize()) {
        LibraryTopBar(
            searchText = searchText,
            onSearchChange = { searchText = it },
            total = vm.total,
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

        // 信息流区域：下拉时整体下移，顶端露出提示条（空白区 + 箭头 + 松开以…）
        Box(modifier = Modifier.weight(1f)) {
            FeedPullHint(
                side = pullSide,
                reveal = { pullReveal },
                thresholdPx = triggerPx,
                exitRandomHint = LibraryState.randomMode,
                holdRemaining = { holdSec },
                modifier = Modifier.align(Alignment.TopCenter),
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .offset { IntOffset(0, pullReveal.roundToInt()) }
                    .then(feedPull),
            ) {
                when {
                    vm.error != null && vm.items.isEmpty() -> StateBox(
                        message = vm.error ?: "加载失败",
                        modifier = Modifier.padding(horizontal = PAGE_PAD_H.dp).fillMaxWidth(),
                        onRetry = { vm.requestPage(reset = true, fingerprint = fingerprint) },
                    )
                    // 列表模式：一行一个作品，整片圆角容器 + 行间 1px 分隔
                    LibraryState.view == LibraryView.List -> Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = PAGE_PAD_H.dp)
                            .clip(RoundedCornerShape(14.dp)),
                    ) {
                        LazyColumn(
                            state = listState,
                            contentPadding = PaddingValues(
                                top = if (hasFilter) 12.dp else PAGE_PAD_TOP.dp,
                                bottom = PAGE_PAD_BOTTOM.dp,
                            ),
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            lazyColumnItems(vm.items, key = { it.rjCode }) { work ->
                                WorkRow(
                                    work = work,
                                    rowBg = cardBg,
                                    actorBg = actorBg,
                                    onOpen = { onOpenWork(work.rjCode) },
                                    onCond = { cond -> LibraryState.addCond(cond) },
                                    showDivider = work.rjCode != vm.items.lastOrNull()?.rjCode,
                                )
                            }
                            item(key = "footer") {
                                LibraryFooter(
                                    loading = vm.loading,
                                    loadMoreError = vm.loadMoreError,
                                    endReached = vm.endReached,
                                    hasItems = vm.items.isNotEmpty(),
                                    hasFilter = hasFilter,
                                    onRetry = { vm.requestPage(reset = false, fingerprint = fingerprint) },
                                )
                            }
                        }
                    }
                    else -> BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                        // 图片模式：卡片全是正方形，高度跟列宽走。但 Fixed 列宽会把除不尽的余数补给前几列
                        // （实测 1280px 屏左列 557 / 右列 556），而瀑布流两列各自累加 y，1px 落差会一路放大。
                        // 这里按整数列宽算一个统一封面高度，两列严格等高。
                        val coverHeight = if (LibraryState.imageMode) {
                            val gapPx = with(density) { GRID_GAP.dp.roundToPx() }
                            val padPx = with(density) { PAGE_PAD_H.dp.roundToPx() }
                            val cellPx =
                                (constraints.maxWidth - padPx * 2 - gapPx * (columns - 1)) / columns
                            with(density) { cellPx.toDp() }
                        } else {
                            null
                        }
                        LazyVerticalStaggeredGrid(
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
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            // 小红书式瀑布流：两列各自按卡片自然高度向下排，彼此互不影响
                            items(vm.items, key = { it.rjCode }) { work ->
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
                                    coverHeight = coverHeight,
                                )
                            }
                            item(span = StaggeredGridItemSpan.FullLine, key = "footer") {
                                LibraryFooter(
                                    loading = vm.loading,
                                    loadMoreError = vm.loadMoreError,
                                    endReached = vm.endReached,
                                    hasItems = vm.items.isNotEmpty(),
                                    hasFilter = hasFilter,
                                    onRetry = { vm.requestPage(reset = false, fingerprint = fingerprint) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}


/** 列表底部：加载中 / 加载失败重试 / 已加载全部 / 空态。 */
@Composable
private fun LibraryFooter(
    loading: Boolean,
    loadMoreError: String?,
    endReached: Boolean,
    hasItems: Boolean,
    hasFilter: Boolean,
    onRetry: () -> Unit,
) {
    Box(
        modifier = Modifier.fillMaxWidth().height(56.dp),
        contentAlignment = Alignment.Center,
    ) {
        when {
            loading -> CircularProgressIndicator(modifier = Modifier.size(28.dp))
            loadMoreError != null -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(loadMoreError, style = MaterialTheme.typography.labelSmall)
                TextButton(onClick = onRetry) { Text("重试") }
            }
            endReached && hasItems -> Text(
                "—— 已经加载所有 ——",
                fontSize = 14.sp,
                color = WebColors.Muted,
            )
            !loading && !hasItems -> Text(
                if (hasFilter) "没有匹配的作品"
                else "暂无作品，请先在「设置」中配置 ASMR 源路径并扫描。",
                fontSize = 15.sp,
                color = WebColors.Muted,
                textAlign = TextAlign.Center,
            )
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
                            // 26dp 的点击区里给到 18dp：箭头 + 三条线在小尺寸下才分得开
                            modifier = Modifier.size(18.dp),
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
