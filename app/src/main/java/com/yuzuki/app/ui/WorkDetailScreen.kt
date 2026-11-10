package com.yuzuki.app.ui

import android.util.Base64
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.yuzuki.app.data.AppContainer
import com.yuzuki.app.data.FilterCond
import com.yuzuki.app.data.MediaFile
import com.yuzuki.app.data.Work
import com.yuzuki.app.data.WorkDetail
import com.yuzuki.app.playback.PlaybackController
import com.yuzuki.app.ui.theme.LocalAppearance
import com.yuzuki.app.ui.theme.WebColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Request

/*
 * 作品详情页：按 web `views/PlayerView.vue` + `components/FileList.vue` 的 CSS 1:1 复刻。
 * 网页在 412px 视口下的 px 值直接当 dp 用（与 LibraryScreen 同一套换算约定）。
 */

/** .header: radius 14 / padding 16 / gap 24（≤640px 竖排）。 */
private val HeaderShape = RoundedCornerShape(14.dp)

/** .file-list: radius 14 / border 1px / overflow hidden。 */
private val FileListShape = RoundedCornerShape(14.dp)

/** .cover: radius 10。 */
private val CoverShape = RoundedCornerShape(10.dp)

/** 标签圆角（web 为 4px，按项目「框体一律圆角」规范取 8）。 */
private val TagShape = RoundedCornerShape(8.dp)

private fun MediaFile.dirOf(): String = relPath.substringBeforeLast('/', "")

private fun MediaFile.depth(): Int = (relPath.split('/').size - 1).coerceAtLeast(0)

/** FileList.formatSize：B / KB / MB（1 位小数）。 */
private fun formatSize(n: Long): String = when {
    n < 1024 -> "$n B"
    n < 1024 * 1024 -> "%.1f KB".format(n / 1024.0)
    else -> "%.1f MB".format(n / 1024.0 / 1024.0)
}

/** FileList.icon：文件类型 → 线性图标名。 */
private fun kindIcon(kind: String): String = when (kind) {
    "audio" -> "music"
    "video" -> "video"
    "image" -> "image"
    else -> "file"
}

/** FileList.actionLabel：图片「查看」、音视频「播放」。 */
private fun actionLabel(kind: String): String = when (kind) {
    "image" -> "查看"
    "audio", "video" -> "播放"
    else -> ""
}

@Composable
fun WorkDetailScreen(
    rj: String,
    onBack: () -> Unit,
    onOpenWork: (String) -> Unit,
    onOpenLibrary: () -> Unit,
    onOpenDrawer: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val playState by PlaybackController.state.collectAsState()
    val appearance = LocalAppearance.current
    val nav = appearance.navColor

    var detail by remember(rj) { mutableStateOf<WorkDetail?>(null) }
    var error by remember(rj) { mutableStateOf<String?>(null) }
    var recommendations by remember(rj) { mutableStateOf<List<Work>>(emptyList()) }
    var recLoading by remember(rj) { mutableStateOf(false) }
    var expanded by remember(rj) { mutableStateOf<Set<String>>(emptySet()) }
    var viewerIndex by remember { mutableStateOf<Int?>(null) }
    var viewerFiles by remember { mutableStateOf<List<MediaFile>>(emptyList()) }
    var message by remember { mutableStateOf<String?>(null) }
    var coverVersion by remember { mutableLongStateOf(0L) }

    suspend fun reload() {
        error = null
        AppContainer.library.work(rj).fold(
            onSuccess = { detail = it },
            onFailure = {
                detail = null
                error = it.message ?: "加载失败"
            },
        )
        if (AppContainer.settings.current.recommendationEnabled) {
            recLoading = true
            AppContainer.api.recommendations(rj)
                .onSuccess { recommendations = it }
                .onFailure { recommendations = emptyList() }
            recLoading = false
        }
    }

    LaunchedEffect(rj) { reload() }
    LaunchedEffect(DockState.scrollTopTicket) {
        if (DockState.scrollTopTicket > 0) listState.animateScrollToItem(0)
    }
    // 图片查看器打开时，系统返回先关查看器，不直接退出作品
    BackHandler(enabled = viewerIndex != null) { viewerIndex = null }

    val data = detail
    val work = data?.work
    val files = data?.files.orEmpty()
    val fav = FavoritesState.workRjs.contains(rj)
    // 图片查看器：只在「被点图片的同一目录」内左右切换（web openImage）
    val images = files.filter { !it.isDir && it.kind == "image" }
    val queue = files.filter { it.playable }

    val panelBg = WebColors.panel(nav, appearance.panelAlpha)
    val filelistBg = WebColors.panel(nav, appearance.filelistAlpha)
    val genreBg = WebColors.tagBg(appearance.tagColor, appearance.genreTagAlpha)
    val actorBg = WebColors.tagBg(appearance.actorColor, appearance.actorTagAlpha)
    val accent = MaterialTheme.colorScheme.primary

    Column(modifier = modifier.fillMaxSize()) {
        WebTopBar(onOpenDrawer = onOpenDrawer)

        if (data == null || work == null) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(top = 60.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // .empty { padding: 60px 0; text-align: center; color: var(--muted) }
                Text(
                    text = error?.let { "加载失败：$it" } ?: "加载中…",
                    fontSize = 15.sp,
                    color = WebColors.Muted,
                )
                if (error != null) {
                    Text(
                        "点击重试",
                        fontSize = 15.sp,
                        color = accent,
                        modifier = Modifier.clickable { scope.launch { reload() } },
                    )
                }
            }
            return@Column
        }

        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            // .content padding: 14px 20px 24px；.player-view gap: 16
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item(key = "back") {
                // .back { margin-bottom: 4 }
                Box(modifier = Modifier.padding(bottom = 4.dp)) {
                    WebBackButton(onClick = onBack)
                }
            }

            item(key = "header") {
                HeaderCard(
                    work = work,
                    coverUrl = AppContainer.api.coverUrl(rj) + if (coverVersion > 0) "&v=$coverVersion" else "",
                    favored = fav,
                    panelBg = panelBg,
                    actorBg = actorBg,
                    genreBg = genreBg,
                    onToggleWorkFav = { scope.launch { FavoritesState.toggleWork(rj) } },
                    onCond = { cond ->
                        LibraryState.addCond(cond)
                        onOpenLibrary()
                    },
                    onToggleFav = { type, value, extra ->
                        scope.launch { FavoritesState.toggle(type, value, extra) }
                    },
                )
            }

            item(key = "files_title") { WebSectionTitle("文件（${files.size}）") }

            item(key = "files") {
                FileListBlock(
                    files = files,
                    expanded = expanded,
                    playingId = playState.current?.file?.id,
                    filelistBg = filelistBg,
                    accent = accent,
                    onToggleDir = { dir ->
                        expanded = if (expanded.contains(dir)) expanded - dir else expanded + dir
                    },
                    onOpenImage = { file ->
                        // 只在「被点图片的同一目录」内左右切换（web openImage）
                        val list = images.filter { it.dirOf() == file.dirOf() }
                        viewerFiles = list
                        viewerIndex = list.indexOfFirst { it.id == file.id }.coerceAtLeast(0)
                    },
                    onOpenFile = { file ->
                        PlaybackController.play(rj, file, work, queue)
                        PlaybackController.setFullscreen(true)
                    },
                    onImageAction = { file, kind -> scope.launch { runImageAction(rj, file, kind, onCover = { coverVersion = System.currentTimeMillis() }, onMessage = { message = it }) } },
                )
            }

            if (AppContainer.settings.current.recommendationEnabled) {
                item(key = "rec_title") { WebSectionTitle("相关推荐") }
                item(key = "rec") {
                    RecBlock(
                        works = recommendations,
                        loading = recLoading,
                        cardWidth = AppContainer.settings.current.cardWidth.dp,
                        onOpen = onOpenWork,
                        onCond = { cond ->
                            LibraryState.addCond(cond)
                            onOpenLibrary()
                        },
                        cardBg = panelBg,
                        metaBg = WebColors.cardMeta(nav),
                        genreBg = genreBg,
                        actorBg = actorBg,
                    )
                }
            }

            message?.let { text ->
                item(key = "msg") {
                    Text(text, fontSize = 12.sp, color = accent)
                }
            }
        }
    }

    val index = viewerIndex
    if (index != null && viewerFiles.isNotEmpty()) {
        ImageViewer(
            images = viewerFiles,
            index = index,
            rj = rj,
            onIndexChange = { viewerIndex = it },
            onClose = { viewerIndex = null },
            onMessage = { message = it },
            onReload = { scope.launch { reload() } },
        )
    }
}

// ---------------------------------------------------------------- 顶部信息区

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HeaderCard(
    work: Work,
    coverUrl: String,
    favored: Boolean,
    panelBg: Color,
    actorBg: Color,
    genreBg: Color,
    onToggleWorkFav: () -> Unit,
    onToggleFav: (String, String, String) -> Unit,
    onCond: (FilterCond) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(HeaderShape)
            .background(panelBg)
            .border(1.dp, WebColors.Border, HeaderShape)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        // .cover: 160×160（≤640px）/ radius 10 / 底色 #0d0f12 / FavoriteStar 6,6 30×30
        Box(
            modifier = Modifier
                .size(160.dp)
                .clip(CoverShape)
                .background(Color(0xFF0D0F12)),
        ) {
            if (work.coverUrl.isNotBlank()) {
                RemoteImage(url = coverUrl, modifier = Modifier.fillMaxSize())
            } else {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(work.rjCode, fontSize = 15.sp, color = WebColors.Muted)
                }
            }
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(6.dp)
                    .size(30.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onToggleWorkFav),
                contentAlignment = Alignment.Center,
            ) {
                FavoriteStarIcon(
                    favored = favored,
                    tint = if (favored) MaterialTheme.colorScheme.primary else Color.White,
                    modifier = Modifier.size(22.dp),
                )
            }
        }

        // .info: gap 8；标题 margin-bottom 6 → 用 padding 补 6
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = work.title.ifBlank { work.rjCode },
                fontSize = 24.sp,
                lineHeight = 30.sp,
                fontWeight = FontWeight.Bold,
                color = WebColors.Text,
                modifier = Modifier.padding(bottom = 6.dp),
            )

            if (work.circle.isNotBlank()) {
                MetaRow("社团") {
                    Text(
                        work.circle,
                        fontSize = 15.sp,
                        color = WebColors.Text,
                        modifier = Modifier.clickable { onCond(FilterCond("circle", work.circle)) },
                    )
                    // 社团后的方形 +/- 标签：22×22 主题色
                    Box(
                        modifier = Modifier
                            .size(22.dp)
                            .clip(TagShape)
                            .background(MaterialTheme.colorScheme.primary)
                            .clickable { onToggleFav("circle", work.circle, "") },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            if (FavoritesState.circles.contains(work.circle)) "−" else "+",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                    }
                }
            }

            if (work.releaseDate.isNotBlank()) {
                MetaRow("发售日") {
                    Text(work.releaseDate, fontSize = 15.sp, color = WebColors.Text)
                }
            }

            if (work.series.isNotBlank()) {
                MetaRow("系列") {
                    Text(
                        work.series,
                        fontSize = 15.sp,
                        color = WebColors.Text,
                        modifier = Modifier.clickable { onCond(FilterCond("series", work.series)) },
                    )
                }
            }

            if (work.salesCount != null) {
                MetaRow("销量") {
                    Text("%,d".format(work.salesCount), fontSize = 15.sp, color = WebColors.Text)
                }
            }

            if (work.actors.isNotEmpty()) {
                MetaRow("声优") {
                    work.actors.forEach { actor ->
                        FavableTag(
                            text = actor,
                            background = actorBg,
                            favored = FavoritesState.actors.contains(actor),
                            onOpen = { onCond(FilterCond("actor", actor)) },
                            onToggle = { onToggleFav("actor", actor, "") },
                        )
                    }
                }
            }

            if (work.tags.isNotEmpty()) {
                MetaRow("类型") {
                    work.tags.forEach { tag ->
                        FavableTag(
                            text = tag.name,
                            background = genreBg,
                            favored = FavoritesState.tags.contains(tag.name),
                            onOpen = { onCond(FilterCond("tag", tag.name)) },
                            onToggle = { onToggleFav("tag", tag.name, tag.category) },
                        )
                    }
                }
            }
        }
    }
}

/** .meta-row：15px / gap 8 / 可换行。 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MetaRow(label: String, content: @Composable () -> Unit) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(label, fontSize = 15.sp, color = WebColors.Muted)
        content()
    }
}

/**
 * 声优 / 类型标签 + 内嵌收藏加减号：右侧 22 宽自成正方区域，
 * 左侧 1px 渐隐分隔线（web .fav-toggle::before）。
 */
@Composable
private fun FavableTag(
    text: String,
    background: Color,
    favored: Boolean,
    onOpen: () -> Unit,
    onToggle: () -> Unit,
) {
    Row(
        modifier = Modifier
            .height(22.dp)
            .clip(TagShape)
            .background(background)
            .clickable(onClick = onOpen),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text,
            fontSize = 12.sp,
            color = Color.White,
            maxLines = 1,
            modifier = Modifier.padding(start = 7.dp, end = 6.dp),
        )
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .width(22.dp)
                .clickable(onClick = onToggle),
            contentAlignment = Alignment.Center,
        ) {
            androidx.compose.foundation.Canvas(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .width(1.dp)
                    .fillMaxHeight(0.62f),
            ) {
                drawRect(
                    brush = Brush.verticalGradient(
                        listOf(
                            Color.White.copy(alpha = 0f),
                            Color.White.copy(alpha = 0.4f),
                            Color.White.copy(alpha = 0.4f),
                            Color.White.copy(alpha = 0f),
                        ),
                    ),
                )
            }
            Text(
                if (favored) "−" else "+",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
            )
        }
    }
}

// ---------------------------------------------------------------- 文件列表

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FileListBlock(
    files: List<MediaFile>,
    expanded: Set<String>,
    playingId: Long?,
    filelistBg: Color,
    accent: Color,
    onToggleDir: (String) -> Unit,
    onOpenImage: (MediaFile) -> Unit,
    onOpenFile: (MediaFile) -> Unit,
    onImageAction: (MediaFile, String) -> Unit,
) {
    // web isVisible：只有当所有祖先目录都展开时这一行才可见
    val visible = files.filter { file ->
        val ancestors = file.relPath.split('/').dropLast(1)
        ancestors.indices.all { i -> expanded.contains(ancestors.take(i + 1).joinToString("/")) }
    }
    var menuFile by remember { mutableStateOf<MediaFile?>(null) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(FileListShape)
            .background(filelistBg)
            .border(1.dp, WebColors.Border, FileListShape),
    ) {
        if (visible.isEmpty()) {
            Text(
                "没有可显示的文件",
                fontSize = 15.sp,
                color = WebColors.Muted,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            )
        }
        visible.forEachIndexed { index, file ->
            FileRow(
                file = file,
                playing = playingId != null && playingId == file.id,
                expanded = expanded.contains(file.relPath),
                accent = accent,
                showDivider = index != visible.lastIndex,
                onOpen = {
                    when {
                        file.isDir -> onToggleDir(file.relPath)
                        file.kind == "image" -> onOpenImage(file)
                        file.playable -> onOpenFile(file)
                    }
                },
                onLongPress = if (!file.isDir && file.kind == "image") {
                    { menuFile = file }
                } else null,
                onImageAction = { kind ->
                    menuFile = null
                    onImageAction(file, kind)
                },
                menuOpen = menuFile?.id == file.id,
                onDismissMenu = { menuFile = null },
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FileRow(
    file: MediaFile,
    playing: Boolean,
    expanded: Boolean,
    accent: Color,
    showDivider: Boolean,
    onOpen: () -> Unit,
    onLongPress: (() -> Unit)?,
    onImageAction: (String) -> Unit,
    menuOpen: Boolean,
    onDismissMenu: () -> Unit,
) {
    // .file-item: gap 12 / padding 10 14 10 12+20*depth / 15px / 底边 1px
    Box {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(onClick = onOpen, onLongClick = onLongPress)
                .background(if (playing) accent.copy(alpha = 0.2f) else Color.Transparent)
                .padding(
                    start = (12 + file.depth() * 20).dp,
                    end = 14.dp,
                    top = 10.dp,
                    bottom = 10.dp,
                ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(modifier = Modifier.width(22.dp), contentAlignment = Alignment.Center) {
                if (file.isDir) {
                    ChevronRightIcon(
                        tint = WebColors.Muted,
                        modifier = Modifier.size(16.dp).rotate(if (expanded) 90f else 0f),
                    )
                } else {
                    FileKindIcon(
                        kind = kindIcon(file.kind),
                        tint = WebColors.Muted,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (file.isDir) {
                    FolderIcon(
                        tint = WebColors.Muted,
                        modifier = Modifier.size(16.dp).padding(end = 0.dp),
                    )
                    Box(Modifier.width(6.dp))
                }
                Text(
                    file.filename,
                    fontSize = 15.sp,
                    fontWeight = if (file.isDir) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (playing) accent else WebColors.Text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (!file.isDir) {
                Text(formatSize(file.size), fontSize = 13.sp, color = WebColors.Muted)
            }
            val action = if (file.isDir) "" else actionLabel(file.kind)
            if (action.isNotEmpty()) {
                Text(action, fontSize = 13.sp, color = WebColors.Muted)
            }
        }
        DropdownMenu(expanded = menuOpen, onDismissRequest = onDismissMenu) {
            DropdownMenuItem(text = { Text("设为作品封面") }, onClick = { onImageAction("cover") })
            DropdownMenuItem(text = { Text("设为移动壁纸") }, onClick = { onImageAction("mobile") })
            DropdownMenuItem(text = { Text("设为桌面壁纸") }, onClick = { onImageAction("desktop") })
            DropdownMenuItem(text = { Text("取消") }, onClick = onDismissMenu)
        }
        if (showDivider) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(WebColors.Border),
            )
        }
    }
}

// ---------------------------------------------------------------- 相关推荐

@Composable
private fun RecBlock(
    works: List<Work>,
    loading: Boolean,
    cardWidth: androidx.compose.ui.unit.Dp,
    onOpen: (String) -> Unit,
    onCond: (FilterCond) -> Unit,
    cardBg: Color,
    metaBg: Color,
    genreBg: Color,
    actorBg: Color,
) {
    when {
        loading -> RecEmpty("加载中…")
        works.isEmpty() -> RecEmpty("暂无相关推荐，可在「设置」中开启或立即计算推荐。")
        else -> Row(
            // .rec: flex / gap 12 / overflow-x auto
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // 固定卡宽，高度各自自然（不强行等高，避免多留空白）
            works.forEach { work ->
                WorkCard(
                    work = work,
                    imageMode = LibraryState.imageMode,
                    cardBg = cardBg,
                    metaBg = metaBg,
                    genreBg = genreBg,
                    actorBg = actorBg,
                    onOpen = { onOpen(work.rjCode) },
                    modifier = Modifier.width(cardWidth),
                    onCond = onCond,
                )
            }
        }
    }
}

/** .rec-empty: padding 20px 0 / center / muted / 14px。 */
@Composable
private fun RecEmpty(text: String) {
    Box(
        modifier = Modifier.fillMaxWidth().padding(vertical = 20.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, fontSize = 14.sp, color = WebColors.Muted)
    }
}

// ---------------------------------------------------------------- 图片动作 / 查看器

/** 图片长按动作：设为作品封面 / 移动壁纸 / 桌面壁纸（web useImageActions）。 */
private suspend fun runImageAction(
    rj: String,
    file: MediaFile,
    kind: String,
    onCover: () -> Unit,
    onMessage: (String) -> Unit,
) {
    when (kind) {
        "cover" -> AppContainer.api.setWorkCover(rj, file.id)
            .onSuccess {
                onCover()
                onMessage("封面已更新")
            }
            .onFailure { onMessage("设置封面失败：${it.message}") }

        "mobile" -> uploadWallpaper(rj, file, "mobile")
            .onSuccess {
                WallpaperState.bump()
                onMessage("移动壁纸已更新")
            }
            .onFailure { onMessage("壁纸上传失败：${it.message}") }

        "desktop" -> uploadWallpaper(rj, file, "desktop")
            .onSuccess { onMessage("桌面壁纸已更新") }
            .onFailure { onMessage("壁纸上传失败：${it.message}") }
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
        Box(modifier = Modifier.fillMaxSize().background(Color(0xFF0D0F12))) {
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
                    BackArrowIcon(WebColors.Text, Modifier.size(22.dp))
                }
                Text("${index + 1}/${images.size}", fontSize = 15.sp, color = WebColors.Text)
                Text(
                    "操作",
                    fontSize = 14.sp,
                    color = WebColors.Text,
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(WebColors.control(0.7f))
                        .clickable { menu = true }
                        .padding(horizontal = 14.dp, vertical = 9.dp),
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth().align(Alignment.BottomCenter).padding(16.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "上一张",
                    fontSize = 14.sp,
                    color = WebColors.Text,
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(WebColors.control(0.7f))
                        .clickable { onIndexChange((index - 1 + images.size) % images.size) }
                        .padding(horizontal = 14.dp, vertical = 9.dp),
                )
                Text("${index + 1} / ${images.size}", fontSize = 13.sp, color = WebColors.Muted)
                Text(
                    "下一张",
                    fontSize = 14.sp,
                    color = WebColors.Text,
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(WebColors.control(0.7f))
                        .clickable { onIndexChange((index + 1) % images.size) }
                        .padding(horizontal = 14.dp, vertical = 9.dp),
                )
            }
            if (menu) {
                Column(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .clip(RoundedCornerShape(16.dp))
                        .background(WebColors.panel(null, 1f))
                        .border(1.dp, WebColors.Border, RoundedCornerShape(16.dp))
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("图片操作", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = WebColors.Text)
                    ViewerAction("设为作品封面") {
                        menu = false
                        scope.launch {
                            AppContainer.api.setWorkCover(rj, file.id)
                                .onSuccess { onMessage("封面已更新"); onReload() }
                                .onFailure { onMessage("设置封面失败") }
                        }
                    }
                    ViewerAction("设为移动壁纸") {
                        menu = false
                        scope.launch {
                            uploadWallpaper(rj, file, "mobile")
                                .onSuccess { onMessage("移动壁纸已更新"); WallpaperState.bump() }
                                .onFailure { onMessage("壁纸上传失败：${it.message}") }
                        }
                    }
                    ViewerAction("设为桌面壁纸") {
                        menu = false
                        scope.launch {
                            uploadWallpaper(rj, file, "desktop")
                                .onSuccess { onMessage("桌面壁纸已更新") }
                                .onFailure { onMessage("壁纸上传失败：${it.message}") }
                        }
                    }
                    ViewerAction("取消") { menu = false }
                }
            }
        }
    }
}

@Composable
private fun ViewerAction(text: String, onClick: () -> Unit) {
    Text(
        text,
        fontSize = 14.sp,
        color = WebColors.Text,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(WebColors.control(0.7f))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
    )
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
