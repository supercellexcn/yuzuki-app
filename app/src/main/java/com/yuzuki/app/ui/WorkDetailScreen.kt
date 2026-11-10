package com.yuzuki.app.ui

import android.util.Base64
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.unit.dp
import com.yuzuki.app.data.AppContainer
import com.yuzuki.app.data.MediaFile
import com.yuzuki.app.data.Work
import com.yuzuki.app.data.WorkDetail
import com.yuzuki.app.playback.PlayerHolder
import com.yuzuki.app.ui.theme.GlassItem
import com.yuzuki.app.ui.theme.GlassPill
import com.yuzuki.app.ui.theme.GlassSurface
import com.yuzuki.app.ui.theme.ShapeCard
import com.yuzuki.app.ui.theme.ShapeItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Request

private fun MediaFile.dirOf(): String = relPath.substringBeforeLast('/', "")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkDetailScreen(
    rj: String,
    onBack: () -> Unit,
    onOpenWork: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    var detail by remember { mutableStateOf<WorkDetail?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var recommendations by remember { mutableStateOf<List<Work>>(emptyList()) }
    var expanded by remember { mutableStateOf<Set<String>>(emptySet()) }
    var viewerIndex by remember { mutableStateOf<Int?>(null) }
    var message by remember { mutableStateOf<String?>(null) }

    suspend fun reload() {
        AppContainer.library.work(rj).fold(
            onSuccess = { detail = it },
            onFailure = { error = it.message ?: "加载失败" },
        )
        if (AppContainer.settings.current.recommendationEnabled) {
            AppContainer.api.recommendations(rj).onSuccess { recommendations = it }
        }
    }

    LaunchedEffect(rj) { reload() }

    // 图片查看器打开时，系统返回先关查看器，不直接退出作品
    BackHandler(enabled = viewerIndex != null) { viewerIndex = null }

    val data = detail
    val fav = FavoritesState.workRjs.contains(rj)
    val files = data?.files.orEmpty()
    val images = files.filter { it.kind == "image" && !it.isDir }
    val grouped = files.filter { !it.isDir && (it.playable || it.kind == "image") }
        .groupBy { it.dirOf() }

    Column(modifier = modifier.fillMaxSize()) {
        TopAppBar(
            title = {
                Text(
                    data?.work?.title ?: "作品详情",
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                }
            },
            actions = {
                IconButton(onClick = { scope.launch { FavoritesState.toggleWork(rj) } }) {
                    Icon(
                        imageVector = if (fav) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                        contentDescription = if (fav) "取消收藏" else "收藏",
                        tint = if (fav) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurface,
                    )
                }
            },
            windowInsets = WindowInsets(0, 0, 0, 0),
        )

        if (data == null) {
            StateBox(
                message = error ?: "正在加载作品详情…",
                loading = error == null,
                modifier = Modifier.weight(1f).fillMaxWidth().padding(16.dp),
                onRetry = { scope.launch { reload() } },
            )
            return@Column
        }

        val work = data.work

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                GlassSurface(modifier = Modifier.fillMaxWidth(), shape = ShapeCard) {
                    Row(modifier = Modifier.padding(12.dp)) {
                        RemoteImage(
                            url = AppContainer.api.coverUrl(rj),
                            modifier = Modifier
                                .width(110.dp)
                                .aspectRatio(0.75f)
                                .clip(ShapeItem),
                        )
                        Column(modifier = Modifier.padding(start = 12.dp).weight(1f)) {
                            Text(work.title, style = MaterialTheme.typography.titleMedium, maxLines = 3)
                            Text(
                                work.rjCode,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            listOf(work.circle, work.series, work.releaseDate)
                                .filter { it.isNotBlank() }
                                .forEach {
                                    Text(
                                        it,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            Text(
                                "销量 ${work.salesCount ?: 0} · 播放 ${work.playCount}",
                                style = MaterialTheme.typography.labelSmall,
                            )
                            if (work.actors.isNotEmpty()) {
                                Text(
                                    work.actors.joinToString("、"),
                                    style = MaterialTheme.typography.labelSmall,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }
            if (work.tags.isNotEmpty()) {
                item {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(work.tags, key = { it.name }) { tag ->
                            val marked = FavoritesState.tags.contains(tag.name)
                            GlassPill(onClick = {
                                scope.launch { FavoritesState.toggle("tag", tag.name, tag.category) }
                            }) {
                                Text(
                                    "${if (marked) "−" else "+"} ${tag.name}",
                                    style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                )
                            }
                        }
                    }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("circle" to work.circle, "actor" to work.actors.firstOrNull().orEmpty())
                        .filter { it.second.isNotBlank() }
                        .forEach { (type, value) ->
                            val set = if (type == "circle") FavoritesState.circles else FavoritesState.actors
                            GlassPill(onClick = {
                                scope.launch { FavoritesState.toggle(type, value) }
                            }) {
                                Text(
                                    "${if (set.contains(value)) "−" else "+"} $value",
                                    style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                )
                            }
                        }
                }
            }
            if (recommendations.isNotEmpty()) {
                item { Text("相关推荐", style = MaterialTheme.typography.titleSmall) }
                item {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(recommendations, key = { it.rjCode }) { rec ->
                            GlassSurface(
                                modifier = Modifier.size(width = 120.dp, height = 150.dp),
                                shape = ShapeCard,
                                onClick = { onOpenWork(rec.rjCode) },
                            ) {
                                Column {
                                    RemoteImage(
                                        url = AppContainer.api.coverUrl(rec.rjCode),
                                        modifier = Modifier.fillMaxWidth().height(100.dp),
                                    )
                                    Text(
                                        rec.title,
                                        style = MaterialTheme.typography.labelSmall,
                                        maxLines = 2,
                                        modifier = Modifier.padding(6.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }
            item { Text("文件列表", style = MaterialTheme.typography.titleSmall) }
            grouped.forEach { (dir, files) ->
                item(key = "dir_$dir") {
                    GlassItem(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            expanded = if (expanded.contains(dir)) expanded - dir else expanded + dir
                        },
                    ) {
                        Text(
                            "${if (expanded.contains(dir)) "▾" else "▸"} ${dir.ifBlank { "根目录" }} (${files.size})",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                        )
                    }
                }
                if (expanded.contains(dir)) {
                    items(files, key = { it.id }) { file ->
                        FileRow(
                            rj = rj,
                            work = work,
                            file = file,
                            queue = files.filter { it.playable },
                            onImage = { viewerIndex = images.indexOfFirst { it.id == file.id }.takeIf { it >= 0 } },
                            onMessage = { message = it },
                        )
                    }
                }
            }
            message?.let {
                item {
                    Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }

    val index = viewerIndex
    if (index != null && images.isNotEmpty()) {
        ImageViewer(
            images = images,
            index = index,
            rj = rj,
            onIndexChange = { viewerIndex = it },
            onClose = { viewerIndex = null },
            onMessage = { message = it },
            onReload = { scope.launch { reload() } },
        )
    }
}

@Composable
private fun FileRow(
    rj: String,
    work: Work,
    file: MediaFile,
    queue: List<MediaFile>,
    onImage: () -> Unit,
    onMessage: (String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val downloaded = AppContainer.offline.hasAudio(rj, file.id)
    GlassItem(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 3.dp),
        onClick = {
            when {
                file.kind == "image" -> onImage()
                file.playable -> PlayerHolder.play(rj, file, work, queue)
            }
        },
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                when (file.kind) {
                    "audio" -> "♪"
                    "video" -> "▶"
                    "image" -> "▣"
                    else -> "·"
                },
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(end = 8.dp),
            )
            Text(file.filename, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
            if (file.playable) {
                GlassPill(onClick = {
                    scope.launch {
                        AppContainer.offline.downloadAudio(AppContainer.api.mediaUrl(rj, file.id), rj, file.id)
                            .fold(
                                onSuccess = { onMessage("已下载 ${file.filename}") },
                                onFailure = { onMessage("下载失败：${it.message}") },
                            )
                    }
                }) {
                    Text(
                        if (downloaded) "已缓存" else "下载",
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun ImageViewer(
    images: List<MediaFile>,
    index: Int,
    rj: String,
    onIndexChange: (Int) -> Unit,
    onClose: () -> Unit,
    onMessage: (String) -> Unit,
    onReload: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val file = images[index.coerceIn(0, images.lastIndex)]
    var menu by remember { mutableStateOf(false) }
    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        GlassSurface(modifier = Modifier.fillMaxSize(), shape = ShapeCard) {
            Box(modifier = Modifier.fillMaxSize()) {
                RemoteImage(
                    url = AppContainer.api.mediaUrl(rj, file.id),
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp)
                        .pointerInput(Unit) {
                            detectTapGestures(onLongPress = { menu = true })
                        },
                    contentScale = ContentScale.Fit,
                )
                Row(
                    modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter).padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onClose, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                    Text("${index + 1}/${images.size}", style = MaterialTheme.typography.bodyMedium)
                    GlassPill(onClick = { menu = true }) {
                        Text("操作", modifier = Modifier.padding(12.dp))
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth().align(Alignment.BottomCenter).padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = { onIndexChange((index - 1 + images.size) % images.size) }) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "上一张")
                    }
                    Text("${index + 1} / ${images.size}", style = MaterialTheme.typography.labelMedium)
                    IconButton(onClick = { onIndexChange((index + 1) % images.size) }) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "下一张")
                    }
                }
                if (menu) {
                    GlassSurface(modifier = Modifier.align(Alignment.Center), shape = ShapeCard) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("图片操作", style = MaterialTheme.typography.titleSmall)
                            GlassPill(onClick = {
                                menu = false
                                scope.launch {
                                    AppContainer.api.setWorkCover(rj, file.id)
                                        .onSuccess { onMessage("封面已更新"); onReload() }
                                        .onFailure { onMessage("设置封面失败") }
                                }
                            }) { Text("设为作品封面", modifier = Modifier.padding(12.dp)) }
                            GlassPill(onClick = {
                                menu = false
                                scope.launch {
                                    uploadWallpaper(rj, file, "mobile")
                                        .onSuccess { onMessage("移动壁纸已更新"); WallpaperState.bump() }
                                        .onFailure { onMessage("壁纸上传失败：${it.message}") }
                                }
                            }) { Text("设为移动壁纸", modifier = Modifier.padding(12.dp)) }
                            GlassPill(onClick = {
                                menu = false
                                scope.launch {
                                    uploadWallpaper(rj, file, "desktop")
                                        .onSuccess { onMessage("桌面壁纸已更新") }
                                        .onFailure { onMessage("壁纸上传失败：${it.message}") }
                                }
                            }) { Text("设为桌面壁纸", modifier = Modifier.padding(12.dp)) }
                            GlassPill(onClick = { menu = false }) { Text("取消", modifier = Modifier.padding(12.dp)) }
                        }
                    }
                }
            }
        }
    }
}

private suspend fun uploadWallpaper(rj: String, file: MediaFile, kind: String): Result<Unit> = withContext(Dispatchers.IO) {
    runCatching {
        val url = AppContainer.api.mediaUrl(rj, file.id)
        val request = Request.Builder().url(url).get().build()
        val bytes = AppContainer.http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("HTTP ${response.code}")
            response.body?.bytes() ?: error("空响应")
        }
        val ext = file.filename.substringAfterLast('.', "jpg").lowercase()
        val base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
        AppContainer.api.uploadImage("/api/settings/wallpaper/$kind", base64, ext).getOrThrow()
    }
}
