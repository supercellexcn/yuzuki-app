package com.yuzuki.app.ui

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yuzuki.app.data.AppContainer
import com.yuzuki.app.data.FilterCond
import com.yuzuki.app.data.PlayRecord
import com.yuzuki.app.data.Work
import com.yuzuki.app.ui.theme.GlassItem
import com.yuzuki.app.ui.theme.GlassPill
import com.yuzuki.app.ui.theme.GlassSurface
import com.yuzuki.app.ui.theme.LocalAppearance
import com.yuzuki.app.ui.theme.ShapeCard
import com.yuzuki.app.ui.theme.ShapeItem
import com.yuzuki.app.ui.theme.WebColors
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId

@Composable
fun FavoritesScreen(
    onOpenWork: (String) -> Unit,
    onApplyCond: (FilterCond) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    var tab by remember { mutableStateOf(0) }
    var works by remember { mutableStateOf<List<Work>>(emptyList()) }
    var tags by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    var circles by remember { mutableStateOf<List<String>>(emptyList()) }
    var actors by remember { mutableStateOf<List<String>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }

    suspend fun reload() {
        AppContainer.library.favorites().fold(
            onSuccess = { fav ->
                works = fav.works
                tags = fav.tags.map { it.name to it.category }
                circles = fav.circles
                actors = fav.actors
                FavoritesState.refresh()
            },
            onFailure = { error = it.message },
        )
    }
    LaunchedEffect(Unit) { reload() }

    Column(modifier = modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("我的收藏", style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("作品", "标签", "社团", "声优").forEachIndexed { index, label ->
                GlassPill(onClick = { tab = index }) {
                    Text(label, style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp))
                }
            }
        }
        if (error != null) {
            StateBox(message = error ?: "加载失败", modifier = Modifier.fillMaxSize(),
                onRetry = { scope.launch { reload() } })
            return@Column
        }
        when (tab) {
            0 -> LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f),
            ) {
                gridItems(works, key = { it.rjCode }) { work ->
                    GlassSurface(
                        modifier = Modifier.height(220.dp),
                        shape = ShapeCard,
                        onClick = { onOpenWork(work.rjCode) },
                    ) {
                        Column {
                            RemoteImage(
                                url = AppContainer.api.coverUrl(work.rjCode),
                                modifier = Modifier.fillMaxWidth().height(150.dp).clip(ShapeItem),
                            )
                            Text(work.title, style = MaterialTheme.typography.bodySmall, maxLines = 2,
                                modifier = Modifier.padding(8.dp))
                        }
                    }
                }
            }
            else -> {
                val values: List<Pair<String, String>> = when (tab) {
                    1 -> tags
                    2 -> circles.map { it to "" }
                    else -> actors.map { it to "" }
                }
                val type = when (tab) { 1 -> "tag"; 2 -> "circle"; else -> "actor" }
                if (values.isEmpty()) {
                    StateBox(message = "暂无收藏", modifier = Modifier.fillMaxSize())
                } else {
                    LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(values) { (value, extra) ->
                            GlassItem(
                                modifier = Modifier.fillMaxWidth(),
                                onClick = { onApplyCond(FilterCond(type, value)) },
                            ) {
                                Row(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
                                    Text(value, style = MaterialTheme.typography.bodyMedium,
                                        modifier = Modifier.weight(1f))
                                    GlassPill(onClick = {
                                        scope.launch { FavoritesState.toggle(type, value, extra); reload() }
                                    }) {
                                        Text("移除", style = MaterialTheme.typography.labelSmall,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
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

private val HistoryListShape = RoundedCornerShape(8.dp)

private const val HISTORY_VIEW_KEY = "yuzuki_history_view"

@Composable
fun HistoryScreen(
    onOpenWork: (String) -> Unit,
    onBackToLibrary: () -> Unit,
    onOpenDrawer: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val gridState = rememberLazyGridState()
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
    LaunchedEffect(DockState.scrollTopTicket) {
        if (DockState.scrollTopTicket > 0) {
            if (cardView) gridState.animateScrollToItem(0) else listState.animateScrollToItem(0)
        }
    }

    // 列表模式下所有行都在同一个 item 里，用「是否滚到列表末尾」判断翻页
    val shouldLoadMore by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            !cardView && hasMore && !loading && last >= info.totalItemsCount - 2
        }
    }
    LaunchedEffect(shouldLoadMore) { if (shouldLoadMore) load(false) }

    val shouldLoadMoreGrid by remember {
        derivedStateOf {
            val info = gridState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            cardView && hasMore && !loading && last >= info.totalItemsCount - 4
        }
    }
    LaunchedEffect(shouldLoadMoreGrid) { if (shouldLoadMoreGrid) load(false) }

    fun setCard(value: Boolean) {
        cardView = value
        prefs.edit().putString(HISTORY_VIEW_KEY, if (value) "card" else "list").apply()
    }

    val metaBg = WebColors.cardMeta(nav)
    val cardBg = WebColors.panel(nav, appearance.panelAlpha)
    val genreBg = WebColors.tagBg(appearance.tagColor, appearance.genreTagAlpha)
    val actorBg = WebColors.tagBg(appearance.actorColor, appearance.actorTagAlpha)

    Column(modifier = modifier.fillMaxSize()) {
        WebTopBar(onOpenDrawer = onOpenDrawer)

        if (cardView) {
            LazyVerticalGrid(
                state = gridState,
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.weight(1f),
            ) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Box(modifier = Modifier.padding(bottom = 4.dp)) {
                        WebBackButton(onClick = onBackToLibrary)
                    }
                }
                item(span = { GridItemSpan(maxLineSpan) }) {
                    HistoryHeader(total = total, cardView = true, onToggle = { setCard(false) })
                }
                if (records.isEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) { HistoryEmpty(loading, error) }
                }
                gridItems(records, key = { "p${it.playId}" }) { record ->
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
                    )
                }
                if (records.isNotEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) { HistoryEndTip(loading, hasMore, loaded) }
                }
            }
        } else {
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f),
            ) {
                item(key = "back") {
                    Box(modifier = Modifier.padding(bottom = 4.dp)) {
                        WebBackButton(onClick = onBackToLibrary)
                    }
                }
                item(key = "head") {
                    HistoryHeader(total = total, cardView = false, onToggle = { setCard(true) })
                }
                if (records.isEmpty()) {
                    item(key = "empty") { HistoryEmpty(loading, error) }
                } else {
                    item(key = "rows") {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(HistoryListShape)
                                .border(1.dp, WebColors.Border, HistoryListShape),
                        ) {
                            records.forEachIndexed { index, record ->
                                HistoryRow(
                                    record = record,
                                    onOpen = { onOpenWork(record.work.rjCode) },
                                    showDivider = index != records.lastIndex,
                                )
                            }
                        }
                    }
                    item(key = "end") { HistoryEndTip(loading, hasMore, loaded) }
                }
            }
        }
    }
}

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
