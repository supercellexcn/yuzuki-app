package com.yuzuki.app.ui

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
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.yuzuki.app.data.AppContainer
import com.yuzuki.app.data.FilterCond
import com.yuzuki.app.data.PlayRecord
import com.yuzuki.app.data.Work
import com.yuzuki.app.ui.theme.GlassItem
import com.yuzuki.app.ui.theme.GlassPill
import com.yuzuki.app.ui.theme.GlassSurface
import com.yuzuki.app.ui.theme.ShapeCard
import com.yuzuki.app.ui.theme.ShapeItem
import kotlinx.coroutines.launch

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

@Composable
fun HistoryScreen(
    onOpenWork: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    var records by remember { mutableStateOf<List<PlayRecord>>(emptyList()) }
    var total by remember { mutableStateOf(0) }
    var page by remember { mutableStateOf(1) }
    var loading by remember { mutableStateOf(false) }
    var cardView by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    val pageSize = AppContainer.settings.current.pageSize

    suspend fun load(target: Int, reset: Boolean) {
        if (loading) return
        loading = true
        AppContainer.library.plays(target, pageSize).fold(
            onSuccess = {
                records = if (reset) it.items else records + it.items
                total = it.total
                page = target
                error = null
            },
            onFailure = { error = it.message },
        )
        loading = false
    }
    LaunchedEffect(Unit) { load(1, true) }

    Column(modifier = modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("播放历史", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            GlassPill(onClick = { cardView = !cardView }) {
                Text(if (cardView) "卡片" else "列表", style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp))
            }
        }
        if (error != null && records.isEmpty()) {
            StateBox(message = error ?: "加载失败", modifier = Modifier.fillMaxSize(),
                onRetry = { scope.launch { load(1, true) } })
            return@Column
        }
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(records, key = { it.playId }) { record ->
                GlassSurface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = ShapeCard,
                    onClick = { onOpenWork(record.work.rjCode) },
                ) {
                    Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (cardView) {
                            RemoteImage(
                                url = AppContainer.api.coverUrl(record.work.rjCode),
                                modifier = Modifier.size(64.dp).clip(ShapeItem),
                            )
                        }
                        Column(modifier = Modifier.padding(start = 10.dp).weight(1f)) {
                            Text(record.work.title, style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                "${record.playedAt.take(16).replace('T', ' ')} · ${formatSeconds(record.durationSeconds)} · ${record.groupCount} 个文件",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                if (record == records.lastOrNull() && !loading && records.size < total) {
                    LaunchedEffect(Unit) { load(page + 1, false) }
                }
            }
            if (records.isEmpty() && !loading) {
                item { StateBox(message = "暂无播放记录", modifier = Modifier.height(160.dp)) }
            }
        }
    }
}
