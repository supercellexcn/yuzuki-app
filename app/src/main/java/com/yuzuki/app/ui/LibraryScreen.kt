package com.yuzuki.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.yuzuki.app.data.AppContainer
import com.yuzuki.app.data.Work
import com.yuzuki.app.ui.theme.GlassPill
import com.yuzuki.app.ui.theme.GlassSpec
import com.yuzuki.app.ui.theme.GlassSurface
import com.yuzuki.app.ui.theme.LocalAppearance
import com.yuzuki.app.ui.theme.ShapeCard
import com.yuzuki.app.ui.theme.ShapeItem
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private val SORTS = listOf(
    "added" to "入库时间",
    "release" to "发售日",
    "sales" to "销量",
    "title" to "标题",
    "updated" to "更新时间",
)

@Composable
fun LibraryScreen(
    onOpenDrawer: () -> Unit,
    onOpenWork: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val appearance = LocalAppearance.current
    val gridState = rememberLazyGridState()
    var items by remember { mutableStateOf<List<Work>>(emptyList()) }
    var total by remember { mutableStateOf(0) }
    var page by remember { mutableStateOf(1) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var searchText by remember { mutableStateOf(LibraryState.query) }
    var sortMenu by remember { mutableStateOf(false) }
    var dockRight by rememberSaveable { mutableStateOf(true) }

    LaunchedEffect(searchText) {
        if (searchText != LibraryState.query) {
            delay(400)
            LibraryState.updateQuery(searchText)
            LibraryState.persist()
        }
    }

    suspend fun loadPage(target: Int, reset: Boolean) {
        if (loading) return
        loading = true
        error = null
        val pageSize = AppContainer.settings.current.pageSize
        val result = if (LibraryState.randomMode) {
            AppContainer.api.randomWorks(
                LibraryState.query, LibraryState.conds, pageSize * 3,
            ).map { com.yuzuki.app.data.WorksResponse(it, it.size, 1, it.size) }
        } else {
            AppContainer.library.works(
                q = LibraryState.query,
                conds = LibraryState.conds,
                sort = LibraryState.sort,
                order = LibraryState.order,
                page = target,
                pageSize = pageSize,
            )
        }
        result.fold(
            onSuccess = { response ->
                items = if (reset) response.items else items + response.items
                total = response.total
                page = target
            },
            onFailure = { error = it.message ?: "加载失败" },
        )
        loading = false
    }

    LaunchedEffect(
        LibraryState.query, LibraryState.conds, LibraryState.sort,
        LibraryState.order, LibraryState.randomMode,
    ) {
        loadPage(1, true)
    }
    LaunchedEffect(LibraryState.scrollTopTicket) { gridState.scrollToItem(0) }
    LaunchedEffect(Unit) { FavoritesState.refresh() }

    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            // 顶栏：汉堡 + 搜索胶囊 + 排序胶囊
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                GlassPill(onClick = onOpenDrawer) {
                    Text("≡", style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp))
                }
                OutlinedTextField(
                    value = searchText,
                    onValueChange = { searchText = it },
                    placeholder = { Text("搜索 · 共 $total 部") },
                    singleLine = true,
                    shape = ShapeCard,
                    modifier = Modifier.weight(1f),
                )
                Box {
                    GlassPill(onClick = { sortMenu = true }) {
                        Text(
                            SORTS.firstOrNull { it.first == LibraryState.sort }?.second ?: "排序",
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        )
                    }
                    DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                        SORTS.forEach { (key, label) ->
                            DropdownMenuItem(
                                text = { Text(label) },
                                onClick = { LibraryState.updateSort(key); sortMenu = false },
                            )
                        }
                        DropdownMenuItem(
                            text = { Text(if (LibraryState.order == "desc") "降序 ↓" else "升序 ↑") },
                            onClick = { LibraryState.toggleOrder(); sortMenu = false },
                        )
                    }
                }
            }

            // 筛选条件（最多 5 个，AND）
            if (LibraryState.conds.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    LibraryState.conds.forEach { cond ->
                        FilterChip(
                            selected = true,
                            onClick = { LibraryState.removeCond(cond) },
                            label = {
                                Text("${cond.type}:${cond.value}  ×", style = MaterialTheme.typography.labelSmall)
                            },
                            shape = RoundedCornerShape(percent = 50),
                        )
                    }
                }
            }

            when {
                error != null && items.isEmpty() -> StateBox(
                    message = error ?: "加载失败",
                    modifier = Modifier.fillMaxSize().padding(16.dp),
                    onRetry = { scope.launch { loadPage(1, true) } },
                )
                else -> {
                    val columns = GlassSpec.columns(
                        appearance.cardSize, appearance.cardWidth,
                        LocalConfiguration.current.screenWidthDp - 24,
                    )
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(columns),
                        state = gridState,
                        contentPadding = PaddingValues(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.weight(1f),
                    ) {
                        items(items.size) { index ->
                            WorkCard(
                                work = items[index],
                                imageMode = LibraryState.imageMode,
                                onOpen = { onOpenWork(items[index].rjCode) },
                            )
                            if (index == items.lastIndex && !LibraryState.randomMode && !loading && items.size < total) {
                                LaunchedEffect(Unit) { loadPage(page + 1, false) }
                            }
                        }
                        if (loading) {
                            item {
                                Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                                }
                            }
                        }
                        if (!loading && items.isEmpty() && error == null) {
                            item { StateBox(message = "没有符合条件的作品", modifier = Modifier.height(160.dp)) }
                        }
                    }
                }
            }
        }

        MobileDock(
            dockRight = dockRight,
            onSideChange = { dockRight = it },
            modifier = Modifier.align(if (dockRight) Alignment.CenterEnd else Alignment.CenterStart),
        )
    }
}

@Composable
private fun WorkCard(
    work: Work,
    imageMode: Boolean,
    onOpen: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val appearance = LocalAppearance.current
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
                GlassPill(
                    modifier = Modifier.align(Alignment.TopStart).padding(6.dp),
                    onClick = { scope.launch { FavoritesState.toggleWork(work.rjCode) } },
                ) {
                    Text(
                        if (fav) "★" else "☆",
                        color = if (fav) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                    )
                }
                if (imageMode) {
                    val flame = GlassSpec.flame(work.salesCount)
                    if (flame.count > 0) {
                        GlassPill(modifier = Modifier.align(Alignment.TopEnd).padding(6.dp)) {
                            Text(
                                "▲".repeat(flame.count),
                                color = when (flame.kind) {
                                    "red" -> Color(0xFFFF6B6B)
                                    "yellow" -> Color(0xFFFFD166)
                                    "rainbow" -> Color(0xFFB69AF6)
                                    else -> Color.LightGray
                                },
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            )
                        }
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
                Row(
                    modifier = Modifier.align(Alignment.BottomEnd).padding(6.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    work.tags.take(2).forEach {
                        Chip(text = it.name, color = androidx.compose.ui.graphics.Color(0xFF6E8EC9),
                            alpha = appearance.genreTagAlpha)
                    }
                }
            }
            if (!imageMode) {
                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(work.title, style = MaterialTheme.typography.bodyMedium, maxLines = 2,
                        overflow = TextOverflow.Ellipsis)
                    Text(
                        listOf(work.series, work.circle).filter { it.isNotBlank() }.joinToString(" · "),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        listOfNotNull(
                            work.salesCount?.let { "销量 $it" },
                            "播放 ${work.playCount}",
                        ).joinToString(" · "),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (work.actors.isNotEmpty()) {
                        Text(work.actors.joinToString("、"), style = MaterialTheme.typography.labelSmall,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

/** 46×166 竖向悬浮按钮组：拖动 + 吸附到最近一侧 + 记住位置。 */
@Composable
private fun MobileDock(
    dockRight: Boolean,
    onSideChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    var offsetY by rememberSaveable { mutableStateOf(0f) }
    GlassSurface(
        modifier = modifier
            .width(46.dp)
            .height(166.dp)
            .offset { IntOffset(0, offsetY.roundToInt()) }
            .pointerInput(Unit) {
                detectDragGestures { _, drag ->
                    offsetY += drag.y
                    if (drag.x > 60f) onSideChange(false)
                    if (drag.x < -60f) onSideChange(true)
                }
            }
            .clip(RoundedCornerShape(999.dp)),
        shape = RoundedCornerShape(999.dp),
        alpha = 0.75f,
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceEvenly,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            DockButton("首页") { LibraryState.home() }
            DockButton("T") { LibraryState.scrollToTop() }
            DockButton("M") { LibraryState.imageMode = !LibraryState.imageMode }
            DockButton("R") { LibraryState.toggleRandom() }
        }
    }
}

@Composable
private fun DockButton(label: String, onClick: () -> Unit) {
    GlassPill(onClick = onClick) {
        Text(label, style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
    }
}
