package com.yuzuki.app.ui

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.items as staggeredItems
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yuzuki.app.data.AppContainer
import com.yuzuki.app.data.FilterCond
import com.yuzuki.app.data.PlayRecord
import com.yuzuki.app.data.Work
import com.yuzuki.app.data.WorkTag
import com.yuzuki.app.ui.theme.LocalAppearance
import com.yuzuki.app.ui.theme.ShapePill
import com.yuzuki.app.ui.theme.WebColors
import com.yuzuki.app.ui.theme.pillTextStyle
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId

/**
 * 我的收藏（底部导航「收藏」Tab，2026-11-25 起）。
 *
 * - 四个切换胶囊：选中 = 主题色底 + onPrimary 文字，未选中 = 控件底 + 描边，
 *   解决旧版「看不出当前在哪个 Tab」。
 * - 作品：**完全跟随库页当前的展示逻辑**（2026-11-25 用户要求）：库页是列表就一行一个 `WorkRow`，
 *   是详情就两列 `WorkCard`，是封面模式就只显示封面（同一套列数 / 间距 / 统一封面高度算法）。
 * - 标签 / 社团 / 声优：与详情页同一套 `FavableTag` 胶囊，点主体按条件筛选，点右端取消收藏；
 *   社团没有独立配色设置，用主题色底 + onPrimary 字。
 * - 列表按 `FavoritesState` 实时过滤：取消收藏后该项立即从列表消失，不用重新请求。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FavoritesScreen(
    onOpenWork: (String) -> Unit,
    onApplyCond: (FilterCond) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    var tab by remember { mutableStateOf(0) }
    var works by remember { mutableStateOf<List<Work>>(emptyList()) }
    var tags by remember { mutableStateOf<List<WorkTag>>(emptyList()) }
    var circles by remember { mutableStateOf<List<String>>(emptyList()) }
    var actors by remember { mutableStateOf<List<String>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }
    var loaded by remember { mutableStateOf(false) }

    suspend fun reload() {
        AppContainer.library.favorites().fold(
            onSuccess = { fav ->
                // 先同步全局收藏态再落列表：否则首帧会按空集合过滤，闪一下「暂无收藏」
                FavoritesState.refresh()
                works = fav.works
                tags = fav.tags
                circles = fav.circles
                actors = fav.actors
                loaded = true
                error = null
            },
            onFailure = { error = it.message },
        )
    }
    LaunchedEffect(Unit) { if (!loaded) reload() }

    val appearance = LocalAppearance.current
    val accent = MaterialTheme.colorScheme.primary
    val onAccent = MaterialTheme.colorScheme.onPrimary
    val cardBg = WebColors.panel(appearance.navColor, appearance.panelAlpha)
    val metaBg = WebColors.cardMeta(appearance.navColor)
    val genreBg = WebColors.tagBg(appearance.tagColor, appearance.genreTagAlpha)
    val actorBg = WebColors.tagBg(appearance.actorColor, appearance.actorTagAlpha)

    // 全局收藏态为空（首次加载未回来 / 刷新失败）时不过滤，避免把已拿到的列表过滤成空
    val visibleWorks = if (FavoritesState.workRjs.isEmpty()) works
    else works.filter { FavoritesState.workRjs.contains(it.rjCode) }
    val visibleTags = if (FavoritesState.tags.isEmpty()) tags
    else tags.filter { FavoritesState.tags.contains(it.name) }
    val visibleCircles = if (FavoritesState.circles.isEmpty()) circles
    else circles.filter { FavoritesState.circles.contains(it) }
    val visibleActors = if (FavoritesState.actors.isEmpty()) actors
    else actors.filter { FavoritesState.actors.contains(it) }

    Column(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, top = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("我的收藏", fontSize = 22.5.sp, fontWeight = FontWeight.Bold, color = WebColors.Text)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("作品", "标签", "社团", "声优").forEachIndexed { index, label ->
                    FavTabPill(
                        label = label,
                        active = tab == index,
                        accent = accent,
                        onAccent = onAccent,
                        onClick = { tab = index },
                    )
                }
            }
        }

        val fail = error
        when {
            fail != null && works.isEmpty() -> StateBox(
                message = "加载失败：$fail",
                modifier = Modifier.fillMaxSize().padding(20.dp),
                onRetry = { scope.launch { reload() } },
            )
            tab == 0 -> {
                if (visibleWorks.isEmpty()) {
                    FavoritesEmpty(loaded)
                } else if (LibraryState.view == LibraryView.List) {
                    // 与库页列表模式完全一致：整片圆角容器 + 行内 1px 分隔
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 20.dp)
                            .clip(RoundedCornerShape(14.dp)),
                    ) {
                        LazyColumn(modifier = Modifier.fillMaxSize()) {
                            itemsIndexed(visibleWorks, key = { _, work -> work.rjCode }) { index, work ->
                                WorkRow(
                                    work = work,
                                    rowBg = cardBg,
                                    actorBg = actorBg,
                                    onOpen = { onOpenWork(work.rjCode) },
                                    onCond = onApplyCond,
                                    showDivider = index != visibleWorks.lastIndex,
                                )
                            }
                        }
                    }
                } else {
                    // 详情 / 封面模式与库页同参数：列数跟屏宽、gap 16、左右 20、顶 14、底 24；
                    // 封面模式下按整数列宽算统一封面高度，避免两列 1px 落差在瀑布流里累积。
                    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                        val columns =
                            if (LocalConfiguration.current.screenWidthDp >= 600) 3 else 2
                        val density = LocalDensity.current
                        val coverHeight = if (LibraryState.imageMode) {
                            val gapPx = with(density) { 16.dp.roundToPx() }
                            val padPx = with(density) { 20.dp.roundToPx() }
                            val cellPx =
                                (constraints.maxWidth - padPx * 2 - gapPx * (columns - 1)) / columns
                            with(density) { cellPx.toDp() }
                        } else {
                            null
                        }
                        LazyVerticalStaggeredGrid(
                            columns = StaggeredGridCells.Fixed(columns),
                            contentPadding = PaddingValues(
                                start = 20.dp, end = 20.dp, top = 14.dp, bottom = 24.dp,
                            ),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalItemSpacing = 16.dp,
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            staggeredItems(visibleWorks, key = { it.rjCode }) { work ->
                                WorkCard(
                                    work = work,
                                    imageMode = LibraryState.imageMode,
                                    cardBg = cardBg,
                                    metaBg = metaBg,
                                    genreBg = genreBg,
                                    actorBg = actorBg,
                                    onOpen = { onOpenWork(work.rjCode) },
                                    modifier = Modifier.fillMaxWidth(),
                                    onCond = onApplyCond,
                                    coverHeight = coverHeight,
                                )
                            }
                        }
                    }
                }
            }
            else -> {
                val entries = when (tab) {
                    1 -> visibleTags.map { FavTagEntry(it.name, it.category, "tag") }
                    2 -> visibleCircles.map { FavTagEntry(it, "", "circle") }
                    else -> visibleActors.map { FavTagEntry(it, "", "actor") }
                }
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 24.dp),
                ) {
                    if (entries.isEmpty()) {
                        FavoritesEmpty(loaded)
                    } else {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            entries.forEach { entry ->
                                FavableTag(
                                    text = entry.value,
                                    category = entry.category,
                                    background = when (entry.type) {
                                        "tag" -> genreBg
                                        "actor" -> actorBg
                                        else -> accent
                                    },
                                    foreground = if (entry.type == "circle") onAccent else Color.White,
                                    favored = true,
                                    onOpen = { onApplyCond(FilterCond(entry.type, entry.value)) },
                                    onToggle = {
                                        scope.launch {
                                            FavoritesState.toggleWithToast(
                                                entry.type, entry.value, entry.category,
                                            )
                                        }
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** 收藏页的标签条目：value + 标签 category（自定义标签配色要用）+ 收藏类型。 */
private data class FavTagEntry(val value: String, val category: String, val type: String)

/** 切换胶囊：选中整颗换主题色，未选中只有描边 —— 一眼就能看出当前 Tab。 */
@Composable
private fun FavTabPill(
    label: String,
    active: Boolean,
    accent: Color,
    onAccent: Color,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .clip(ShapePill)
            .background(if (active) accent else WebColors.control(0.7f))
            .border(1.dp, if (active) Color.Transparent else WebColors.Border, ShapePill)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 6.dp),
    ) {
        Text(
            label,
            style = pillTextStyle(13.sp, if (active) FontWeight.SemiBold else FontWeight.Normal),
            color = if (active) onAccent else WebColors.Text,
        )
    }
}

@Composable
private fun FavoritesEmpty(loaded: Boolean) {
    Box(
        modifier = Modifier.fillMaxWidth().padding(vertical = 60.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            if (loaded) "暂无收藏" else "加载中…",
            fontSize = 14.sp,
            color = WebColors.Muted,
        )
    }
}

// ---------------------------------------------------------------- 播放历史

/*
 * 播放历史：按 web `views/HistoryView.vue` 在移动视口下的样式复刻。
 * .history gap 12 / .content padding 14 20 24；列表 = 圆角 8 的整块 + 行内分隔线；
 * 卡片 = 复用 WorkCard 的 history 变体（播放时间 / 播放时长替换作品字段）。
 */

/** web HistoryView 的 pageSize 固定 20（不跟随设置）。 */
private const val HISTORY_PAGE_SIZE = 20


private const val HISTORY_VIEW_KEY = "yuzuki_history_view"

@Composable
fun HistoryScreen(
    onOpenWork: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val cardState = rememberLazyStaggeredGridState()
    val prefs = remember {
        AppContainer.app.getSharedPreferences("yuzuki_ui", Context.MODE_PRIVATE)
    }
    val appearance = LocalAppearance.current
    val nav = appearance.navColor

    var records by remember { mutableStateOf<List<PlayRecord>>(emptyList()) }
    var total by remember { mutableStateOf(0) }
    var page by remember { mutableStateOf(1) }
    var loading by remember { mutableStateOf(false) }
    var loaded by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var cardView by remember { mutableStateOf(prefs.getString(HISTORY_VIEW_KEY, "list") == "card") }

    val hasMore = total > records.size

    suspend fun load(reset: Boolean) {
        if (loading) return
        if (!reset && loaded && !hasMore) return
        loading = true
        if (reset) error = null
        val target = if (reset) 1 else page
        AppContainer.library.plays(target, HISTORY_PAGE_SIZE).fold(
            onSuccess = { response ->
                // 按 play_id 去重：同一时间戳的相邻记录可能跨页重复
                val seen = records.map { it.playId }.toSet()
                val fresh = response.items.filter { it.playId !in seen }
                records = if (reset) response.items else records + fresh
                total = response.total
                page = target + 1
                loaded = true
            },
            onFailure = { if (reset) error = it.message },
        )
        loading = false
    }

    LaunchedEffect(Unit) { if (!loaded) load(true) }

    // 用「可见下标」当 key：滚到末尾后条件仍成立时也能继续取下一页
    // （旧写法 LaunchedEffect(shouldLoadMore) 在值一直是 true 时不会再次触发，导致停在第一页之后）
    val listLast by remember {
        derivedStateOf { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1 }
    }
    val cardLast by remember {
        derivedStateOf { cardState.layoutInfo.visibleItemsInfo.maxOfOrNull { it.index } ?: -1 }
    }
    LaunchedEffect(cardView, listLast, cardLast, records.size, hasMore, loading) {
        if (loading || !hasMore) return@LaunchedEffect
        val nearEnd = if (cardView) {
            val total = cardState.layoutInfo.totalItemsCount
            total > 0 && cardLast >= total - 3
        } else {
            val total = listState.layoutInfo.totalItemsCount
            total > 0 && listLast >= total - 2
        }
        // 必须开独立协程：本 effect 的 key 含 loading，直接 await load 会被
        // loading=true 引发的重启取消，导致 loading 永远为 true、卡在「加载中…」
        if (nearEnd) scope.launch { load(false) }
    }

    fun setCard(value: Boolean) {
        cardView = value
        prefs.edit().putString(HISTORY_VIEW_KEY, if (value) "card" else "list").apply()
    }

    val metaBg = WebColors.cardMeta(nav)
    val cardBg = WebColors.panel(nav, appearance.panelAlpha)
    val genreBg = WebColors.tagBg(appearance.tagColor, appearance.genreTagAlpha)
    val actorBg = WebColors.tagBg(appearance.actorColor, appearance.actorTagAlpha)

    Column(modifier = modifier.fillMaxSize()) {
        if (cardView) {
            BoxWithConstraints(modifier = Modifier.weight(1f)) {
                // 同库页：图片模式封面统一高度，避免两列 1px 列宽差在瀑布流里一路累积
                val coverHeight = if (LibraryState.imageMode) {
                    val density = LocalDensity.current
                    val gapPx = with(density) { 16.dp.roundToPx() }
                    val padPx = with(density) { 20.dp.roundToPx() }
                    val cellPx = (constraints.maxWidth - padPx * 2 - gapPx) / 2
                    with(density) { cellPx.toDp() }
                } else {
                    null
                }
                LazyVerticalStaggeredGrid(
                    columns = StaggeredGridCells.Fixed(2),
                    state = cardState,
                    contentPadding = PaddingValues(
                        start = 20.dp, end = 20.dp, top = 14.dp, bottom = 24.dp,
                    ),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalItemSpacing = 16.dp,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    item(span = StaggeredGridItemSpan.FullLine, key = "head") {
                        HistoryHeader(total = total, cardView = true, onToggle = { setCard(false) })
                    }
                    if (records.isEmpty()) {
                        item(span = StaggeredGridItemSpan.FullLine, key = "empty") {
                            HistoryEmpty(loading, error)
                        }
                    } else {
                        // 与库页一致：两列瀑布流，卡片高度各自自然
                        staggeredItems(records, key = { "p${it.playId}" }) { record ->
                            WorkCard(
                                work = record.work,
                                imageMode = LibraryState.imageMode,
                                cardBg = cardBg,
                                metaBg = metaBg,
                                genreBg = genreBg,
                                actorBg = actorBg,
                                history = HistoryMeta(
                                    timeLabel = historyTime(record.playedAt, withClock = true),
                                    shortTimeLabel = historyTime(record.playedAt),
                                    durationLabel = historyDuration(record.durationSeconds),
                                ),
                                onOpen = { onOpenWork(record.work.rjCode) },
                                modifier = Modifier.fillMaxWidth(),
                                coverHeight = coverHeight,
                            )
                        }
                        item(span = StaggeredGridItemSpan.FullLine, key = "end") {
                            HistoryEndTip(loading, hasMore, loaded)
                        }
                    }
                }
            }
        } else {
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(
                    start = 20.dp, end = 20.dp, top = 14.dp, bottom = 24.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f),
            ) {
                item(key = "head") {
                    HistoryHeader(total = total, cardView = false, onToggle = { setCard(true) })
                }
                if (records.isEmpty()) {
                    item(key = "empty") { HistoryEmpty(loading, error) }
                } else {
                    // 一行一个 item：记录多时也能虚拟化滚动，翻页靠「可见下标」判断
                    itemsIndexed(records, key = { _, record -> "p${record.playId}" }) { index, record ->
                        val shape = historyRowShape(
                            first = index == 0,
                            last = index == records.lastIndex,
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(shape)
                                .background(cardBg),
                        ) {
                            HistoryRow(
                                record = record,
                                onOpen = { onOpenWork(record.work.rjCode) },
                                showDivider = index != records.lastIndex,
                            )
                        }
                    }
                    item(key = "end") { HistoryEndTip(loading, hasMore, loaded) }
                }
            }
        }
    }
}

/** 列表整片首行/末行圆角，中间行直角（等价整片圆角容器）。 */
private fun historyRowShape(first: Boolean, last: Boolean) = RoundedCornerShape(
    topStart = if (first) 14.dp else 0.dp,
    topEnd = if (first) 14.dp else 0.dp,
    bottomStart = if (last) 14.dp else 0.dp,
    bottomEnd = if (last) 14.dp else 0.dp,
)

/** .head：标题 + 共 N 段 + 列表/卡片切换。 */
@Composable
private fun HistoryHeader(total: Int, cardView: Boolean, onToggle: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            // web 是未设样式的 h2 → 1.5em / 700
            Text("播放历史", fontSize = 22.5.sp, fontWeight = FontWeight.Bold, color = WebColors.Text)
            Text(
                "共 $total 段",
                fontSize = 13.sp,
                color = WebColors.Muted,
                modifier = Modifier.padding(start = 6.dp),
            )
        }
        // el-radio-group size=small：列表 / 卡片
        Row(
            modifier = Modifier
                .height(24.dp)
                .clip(RoundedCornerShape(12.dp))
                .border(1.dp, WebColors.Border, RoundedCornerShape(12.dp)),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            HistoryViewTab("列表", !cardView, onToggle = { if (cardView) onToggle() })
            HistoryViewTab("卡片", cardView, onToggle = { if (!cardView) onToggle() })
        }
    }
}

@Composable
private fun HistoryViewTab(label: String, active: Boolean, onToggle: () -> Unit) {
    Box(
        modifier = Modifier
            .height(22.dp)
            .background(if (active) MaterialTheme.colorScheme.primary else WebColors.control(0.7f))
            .clickable(onClick = onToggle)
            .padding(horizontal = 11.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            fontSize = 12.sp,
            color = if (active) MaterialTheme.colorScheme.onPrimary else WebColors.Text,
        )
    }
}

/** 单行历史记录：44 封面 + 标题/副行 + 右侧时间。 */
@Composable
private fun HistoryRow(record: PlayRecord, onOpen: () -> Unit, showDivider: Boolean) {
    Column(modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0xFF0D0F12)),
                contentAlignment = Alignment.Center,
            ) {
                if (record.work.coverUrl.isNotBlank()) {
                    RemoteImage(
                        url = AppContainer.api.coverUrl(record.work.rjCode),
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Text(record.work.rjCode, fontSize = 9.sp, color = WebColors.Muted, maxLines = 1)
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    record.work.title.ifBlank { record.work.rjCode },
                    fontSize = 14.sp,
                    color = WebColors.Text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    historySubLine(record),
                    fontSize = 12.sp,
                    color = WebColors.Muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                if (record.groupCount > 1) {
                    Text(
                        "本次 ${record.groupCount} 个文件",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(bottom = 2.dp),
                    )
                }
                Text(
                    historyTime(record.playedAt, withClock = true),
                    fontSize = 12.sp,
                    color = WebColors.Muted,
                )
            }
        }
        if (showDivider) {
            Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(WebColors.Border))
        }
    }
}

/** .sub：社团 · 销量 · 播放 N 次 · 时长 45m */
private fun historySubLine(record: PlayRecord): String {
    val parts = mutableListOf<String>()
    if (record.work.circle.isNotBlank()) parts += record.work.circle
    record.work.salesCount?.let { parts += "销量 ${"%,d".format(it)}" }
    if (record.work.playCount > 0) parts += "播放 ${record.work.playCount} 次"
    if (record.durationSeconds > 0) parts += "时长 ${historyDuration(record.durationSeconds)}"
    return parts.joinToString(" · ")
}

@Composable
private fun HistoryEmpty(loading: Boolean, error: String?) {
    Box(
        modifier = Modifier.fillMaxWidth().padding(vertical = 60.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = when {
                error != null -> "加载失败：$error"
                loading -> "加载中…"
                else -> "暂无播放记录"
            },
            fontSize = 14.sp,
            color = WebColors.Muted,
            textAlign = TextAlign.Center,
        )
    }
}

/** .end-tip：min-height 40 / 居中 / 13px；加载中用强调色。 */
@Composable
private fun HistoryEndTip(loading: Boolean, hasMore: Boolean, loaded: Boolean) {
    Box(
        modifier = Modifier.fillMaxWidth().height(40.dp).padding(top = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        when {
            loading && hasMore -> Text("加载中…", fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
            loaded && !hasMore -> Text("—— 已经加载所有 ——", fontSize = 13.sp, color = WebColors.Muted)
        }
    }
}

/** fmtTime：本地时区 yyyy-MM-dd HH:mm / fmtShortTime：两位年份。 */
private fun historyTime(iso: String, withClock: Boolean = false): String {
    if (iso.isBlank()) return ""
    val date = runCatching {
        Instant.parse(iso).atZone(ZoneId.systemDefault())
    }.getOrNull()
    if (date == null) {
        val raw = iso.take(16).replace('T', ' ')
        return if (withClock) raw else raw.take(10)
    }
    val y = if (withClock) date.year.toString().padStart(4, '0')
    else (date.year % 100).toString().padStart(2, '0')
    val head = "$y-%02d-%02d".format(date.monthValue, date.dayOfMonth)
    return if (withClock) "$head %02d:%02d".format(date.hour, date.minute) else head
}

/** fmtDuration：1h30m / 45m / 30s */
private fun historyDuration(sec: Int): String {
    val total = sec.coerceAtLeast(0)
    val h = total / 3600
    val m = (total % 3600) / 60
    return when {
        h > 0 -> if (m > 0) "${h}h${m}m" else "${h}h"
        m > 0 -> "${m}m"
        else -> "${total}s"
    }
}
