package com.yuzuki.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.window.Popup
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.yuzuki.app.data.AppContainer
import com.yuzuki.app.data.FilterCond
import com.yuzuki.app.data.MediaFile
import com.yuzuki.app.data.Work
import com.yuzuki.app.playback.PlaybackController
import com.yuzuki.app.playback.RepeatMode
import com.yuzuki.app.ui.theme.LocalAppearance
import com.yuzuki.app.ui.theme.WebColors
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * 全屏播放器。信息区沿用 web `FullscreenPlayer.vue` 的结构（去掉顶部把手），
 * 控件区按用户提供的播放器视觉稿重排：
 *   ① 图标行：收藏星 / 详情(i) / 播放列表 …… 顺序随机 / 循环
 *   ② 进度行：左侧已播时间 · 白色细轨道 + 圆点 · 右侧总时长
 *   ③ 主按钮行：音量 / 上一曲 / 快退 / 播放 / 快进 / 下一曲 / 均衡器（预留）
 */
private val ChipShape = RoundedCornerShape(4.dp)

@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
fun FullscreenPlayer(
    onClose: () -> Unit,
    onOpenWork: (String) -> Unit,
    onApplyCond: (FilterCond) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by PlaybackController.state.collectAsState()
    val item = state.current
    val scope = rememberCoroutineScope()
    val appearance = LocalAppearance.current
    val accent = MaterialTheme.colorScheme.primary
    val accentFg = MaterialTheme.colorScheme.onPrimary
    // .fs-scrim = var(--panel)
    val scrim = WebColors.panel(appearance.navColor, appearance.panelAlpha)
    val panelSolid = WebColors.panel(appearance.navColor, 1f)
    val genreBg = WebColors.tagBg(appearance.tagColor, appearance.genreTagAlpha)
    val actorBg = WebColors.tagBg(appearance.actorColor, appearance.actorTagAlpha)
    val seekSeconds = AppContainer.settings.current.seekSeconds

    var seekPreview by remember { mutableStateOf<Float?>(null) }
    var playlistVisible by remember { mutableStateOf(false) }
    var volumeVisible by remember { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current
    // 打开播放列表 / 切歌时滚到当前文件，冷启动恢复后也能直接看到上次那一首。
    // 键取「当前文件」而不是 queueIndex / queue.size：手动排序后下标会变，
    // 若按下标触发，排完序列表会被立刻拽回当前曲目。
    val playlistState = rememberLazyListState()
    LaunchedEffect(playlistVisible, state.current?.file?.id) {
        if (playlistVisible && state.queue.isNotEmpty()) {
            playlistState.scrollToItem(state.queueIndex.coerceIn(0, state.queue.lastIndex))
        }
    }

    // 快照恢复出来的 Work 只带标题 / 社团 / 声优，系列与类型标签是空的；
    // 这里按 rj 补一次详情，热启动（列表里带着完整 Work）不会重复请求。
    var detailWork by remember(item?.rj) { mutableStateOf<Work?>(null) }
    LaunchedEffect(item?.rj) {
        val rj = item?.rj ?: return@LaunchedEffect
        val known = item?.work
        if (known != null && known.tags.isNotEmpty() && known.series.isNotBlank()) return@LaunchedEffect
        detailWork = AppContainer.api.work(rj).getOrNull()?.work
    }
    val work = detailWork ?: item?.work
    val workFav = item != null && FavoritesState.workRjs.contains(item.rj)

    // 播放页是横向 Pager 的一页：翻页手势由 Pager 统一接管（跟手），
    // 页面自身不再识别横滑，避免和 Pager 抢手势。
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(WebColors.PageBg),
    ) {
        // 底色：外观设置的面板色兜底（封面未加载 / 无曲目时可见）
        Box(modifier = Modifier.fillMaxSize().background(scrim))

        // ---- 背景：当前封面裁剪放大 + 模糊 + 压暗 ----
        // 纯装饰层，不承载任何文字与控件，因此不影响内容清晰度。
        if (item != null) {
            RemoteImage(
                url = AppContainer.api.coverUrl(item.rj),
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = 1.3f
                        scaleY = 1.3f
                    }
                    // Rectangle（默认）而不是 Unbounded：Unbounded 会让模糊采样画出图层边界，
                    // 在 Pager 里就糊到相邻的库页 / 历史页，看起来像一条外来遮罩。
                    // 这里封面已按 1.3 倍放大，边缘裁剪不可见，所以裁掉是安全的。
                    .blur(30.dp, edgeTreatment = BlurredEdgeTreatment.Rectangle),
                contentScale = ContentScale.Crop,
                placeholder = Color.Transparent,
            )
            // 顶部略暗、中部透出封面、底部压到接近全黑，保证信息区与控件区文字可读
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0f to Color(0x99000000),
                            0.45f to Color(0x73000000),
                            1f to Color(0xE6000000),
                        )
                    ),
            )
        }

        // 背景（封面模糊 + 渐变）铺满整块内容区，一直延伸到状态栏后面；
        // 控件与文字在这一层用 statusBarsPadding 避让，做到"背景沉浸、内容不挡"。
        Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
            // ---- 封面：1:1 正方形，宽度撑满，居中，无上下色块 ----
            BoxWithConstraints(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                if (item == null) {
                    Text("未开始播放", fontSize = 16.sp, color = WebColors.Muted)
                } else {
                    val side = minOf(maxWidth, maxHeight)
                    RemoteImage(
                        url = AppContainer.api.coverUrl(item.rj),
                        modifier = Modifier
                            .size(side)
                            .clip(RoundedCornerShape(6.dp)),
                        contentScale = ContentScale.Crop,
                        placeholder = Color.Transparent,
                    )
                }
            }

            if (item != null) {
                // ---- .fs-info：标题 → 社团 → 系列 → 声优 → 类型（顺序固定）----
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        work?.title?.ifBlank { item.rj } ?: item.rj,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = WebColors.Text,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    // 当前播放的文件名：队列里换曲 / 冷启动恢复都要能一眼看出在放哪个文件
                    Text(
                        text = item.file.filename,
                        fontSize = 12.sp,
                        color = WebColors.Muted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (work != null && work.circle.isNotBlank()) {
                        val circleFav = FavoritesState.circles.contains(work.circle)
                        Row(
                            modifier = Modifier
                                .clip(ChipShape)
                                .clickable { applyFilter(onApplyCond, onClose, FilterCond("circle", work.circle)) }
                                .padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text("社团", fontSize = 14.sp, color = WebColors.Muted)
                            Text(
                                work.circle,
                                fontSize = 14.sp,
                                color = WebColors.Text,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false),
                            )
                            Box(
                                modifier = Modifier
                                    .size(22.dp)
                                    .clip(ChipShape)
                                    .background(accent)
                                    .clickable {
                                        scope.launch {
                                            FavoritesState.toggleWithToast("circle", work.circle)
                                        }
                                    },
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    if (circleFav) "−" else "+",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = accentFg,
                                )
                            }
                        }
                    }

                    // 系列：与社团同样一行，点击加入筛选（系列没有独立收藏，故不带 + 按钮）
                    if (work != null && work.series.isNotBlank()) {
                        Row(
                            modifier = Modifier
                                .clip(ChipShape)
                                .clickable { applyFilter(onApplyCond, onClose, FilterCond("series", work.series)) }
                                .padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text("系列", fontSize = 14.sp, color = WebColors.Muted)
                            Text(
                                work.series,
                                fontSize = 14.sp,
                                color = WebColors.Text,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false),
                            )
                        }
                    }

                    if (work != null && work.actors.isNotEmpty()) {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            work.actors.take(6).forEach { actor ->
                                FavChip(
                                    text = actor,
                                    category = "",
                                    background = actorBg,
                                    favored = FavoritesState.actors.contains(actor),
                                    onToggleFav = { scope.launch { FavoritesState.toggleWithToast("actor", actor) } },
                                    onClick = { applyFilter(onApplyCond, onClose, FilterCond("actor", actor)) },
                                )
                            }
                        }
                    }

                    if (work != null && work.tags.isNotEmpty()) {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            work.tags.take(8).forEach { tag ->
                                FavChip(
                                    text = tag.name,
                                    category = tag.category,
                                    background = genreBg,
                                    favored = FavoritesState.tags.contains(tag.name),
                                    onToggleFav = {
                                        scope.launch {
                                            FavoritesState.toggleWithToast("tag", tag.name, tag.category)
                                        }
                                    },
                                    onClick = { applyFilter(onApplyCond, onClose, FilterCond("tag", tag.name)) },
                                )
                            }
                        }
                    }
                }

                // ---- 控件区（按视觉稿）----
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    // ① 图标行：收藏星 / 详情 / 播放列表 …… 顺序随机 / 循环
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(22.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            IconTap(onClick = {
                                scope.launch { FavoritesState.toggleWorkWithToast(item.rj) }
                            }) {
                                FavoriteStarIcon(
                                    favored = workFav,
                                    tint = if (workFav) accent else Color.White,
                                    modifier = Modifier.size(24.dp),
                                )
                            }
                            IconTap(onClick = { onOpenWork(item.rj) }) {
                                InfoCircleIcon(Color.White, Modifier.size(24.dp))
                            }
                            IconTap(onClick = { playlistVisible = true }) {
                                PlaylistIcon(Color.White, Modifier.size(24.dp))
                            }
                        }
                        Spacer(Modifier.weight(1f))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(22.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            // 随机：开 / 关，开启时变色并在底部提示
                            IconTap(onClick = {
                                PlaybackController.toggleShuffle()
                                AppToast.show(
                                    if (PlaybackController.state.value.shuffle) "随机播放已开启"
                                    else "随机播放已关闭",
                                )
                            }) {
                                ShuffleIcon(
                                    tint = if (state.shuffle) accent else Color.White,
                                    modifier = Modifier.size(24.dp),
                                )
                            }
                            // 循环只有两态：顺序播放 ↔ 单曲循环。
                            // 切换不改颜色，只靠右下角有没有「1」区分单曲循环。
                            IconTap(onClick = {
                                PlaybackController.cycleRepeat()
                                AppToast.show(
                                    when (PlaybackController.state.value.repeat) {
                                        RepeatMode.ONE -> "单曲循环"
                                        RepeatMode.ALL -> "列表循环"
                                        RepeatMode.OFF -> "顺序播放"
                                    },
                                )
                            }) {
                                Box {
                                    RepeatIcon(Color.White, Modifier.size(24.dp))
                                    if (state.repeat == RepeatMode.ONE) {
                                        Text(
                                            "1",
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White,
                                            modifier = Modifier
                                                .align(Alignment.BottomEnd)
                                                .offset(x = 3.dp, y = 2.dp),
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // ② 进度行：已播时间 · 轨道 · 总时长
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // 注意：不能用 coerceAtLeast(1) 兜底 —— duration 无效时会变成 1ms，
                        // 进度条比例乘出来几乎是 0，点了后面就跳回开头
                        val duration = state.durationMs.coerceAtLeast(0L)
                        val value = seekPreview ?: state.positionMs.toFloat().coerceAtMost(duration.toFloat())
                        Text(fmtTime(value.toLong()), fontSize = 13.sp, color = Color.White)
                        ProgressSlider(
                            progress = if (duration > 0f) (value / duration).coerceIn(0f, 1f) else 0f,
                            modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
                            onChange = { ratio ->
                                if (duration > 0f) seekPreview = ratio * duration
                            },
                            onCommit = { ratio ->
                                // 只传比例，时长由 player 提供：状态里的 duration 可能瞬时无效
                                PlaybackController.seekToRatio(ratio)
                                seekPreview = null
                            },
                        )
                        Text(fmtTime(state.durationMs), fontSize = 13.sp, color = Color.White)
                    }

                    // ③ 主按钮行：音量 / 上一曲 / 快退 / 播放 / 快进 / 下一曲 / 均衡器
                    Box(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            CenterCell {
                                Box {
                                    IconTap(onClick = { volumeVisible = !volumeVisible }) {
                                        VolumeIcon(
                                            on = state.volume > 0f,
                                            tint = if (volumeVisible) accent else Color.White,
                                            modifier = Modifier.size(22.dp),
                                        )
                                    }
                                    // 真·弹出框：使用 Popup 绘制在独立窗口里，
                                    // 不参与布局，因此不会把上下控件顶位移。
                                    if (volumeVisible) {
                                        Popup(
                                            popupPositionProvider = AboveAnchorProvider,
                                            onDismissRequest = { volumeVisible = false },
                                            properties = PopupProperties(focusable = true),
                                        ) {
                                            VolumePane(
                                                value = state.volume,
                                                onChange = { PlaybackController.setVolume(it) },
                                                background = panelSolid,
                                            )
                                        }
                                    }
                                }
                            }
                            CenterCell {
                                IconTap(onClick = { PlaybackController.prev() }) {
                                    PrevIcon(Color.White, Modifier.size(26.dp))
                                }
                            }
                            CenterCell {
                                IconTap(onClick = { PlaybackController.seekBy(-seekSeconds) }) {
                                    RewindIcon(Color.White, Modifier.size(26.dp))
                                }
                            }
                            CenterCell {
                                IconTap(size = 54.dp, onClick = { PlaybackController.toggle() }) {
                                    if (state.playing) {
                                        PauseIcon(Color.White, Modifier.size(38.dp))
                                    } else {
                                        PlayIcon(Color.White, Modifier.size(38.dp))
                                    }
                                }
                            }
                            CenterCell {
                                IconTap(onClick = { PlaybackController.seekBy(seekSeconds) }) {
                                    ForwardIcon(Color.White, Modifier.size(26.dp))
                                }
                            }
                            CenterCell {
                                IconTap(onClick = { PlaybackController.next() }) {
                                    NextIcon(Color.White, Modifier.size(26.dp))
                                }
                            }
                            // 均衡器入口：暂不接功能，留给后续 EQ
                            CenterCell {
                                IconTap(onClick = { }) {
                                    EqualizerIcon(Color.White, Modifier.size(22.dp))
                                }
                            }
                        }
                    }
                }
            } else {
                Text(
                    "从作品详情或播放列表开始播放",
                    fontSize = 14.sp,
                    color = WebColors.Muted,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 20.dp),
                )
            }
        }

        // ---- 播放列表（底部 55%，顶部圆角 12）----
        if (playlistVisible && item != null) {
            // 拖拽排序：拖动期间只在本地镜像里换位，松手才一次性提交给播放器，
            // 避免每越过一行就 moveMediaItem 一次（播放器会反复重建 timeline）。
            val dragQueue = remember { mutableStateListOf<MediaFile>().apply { addAll(state.queue) } }
            var dragId by remember { mutableStateOf<Long?>(null) }
            var dragOffset by remember { mutableFloatStateOf(0f) }
            // 被拖行的视觉中心（列表坐标系）。靠它驱动边缘自动滚动，所以必须独立存一份：
            // 滚动后会补偿 dragOffset，center 因此保持不变，滚动速率才稳定。
            var dragCenter by remember { mutableFloatStateOf(0f) }
            val density = LocalDensity.current
            val edgePx = with(density) { 56.dp.toPx() }
            val rollStepPx = with(density) { 9.dp.toPx() }
            // 队列被外部换掉（切作品 / 自动切歌重建）时同步本地镜像；
            // 拖动中不动，否则正在拖的那一行会被从手下抽走
            LaunchedEffect(state.queue, dragId) {
                if (dragId == null && dragQueue.map { it.id } != state.queue.map { it.id }) {
                    dragQueue.clear()
                    dragQueue.addAll(state.queue)
                }
            }
            // 拖动中的一步：夹住位移 → 中心越过哪一行就换到哪一行。
            // 位移夹在列表可视区内是必须的：跑出 viewport 会被 LazyColumn 裁掉甚至回收，
            // 行一消失手势就跟着断（用户看到的就是「刚拖到顶上就弹回原位」）。
            // 贴住上下边之后不再外移，改由下面的滚动循环把前后内容露出来。
            fun settle(id: Long) {
                val info = playlistState.layoutInfo
                val visible = info.visibleItemsInfo
                val dragged = visible.firstOrNull { it.key == id } ?: return
                val minOffset = (info.viewportStartOffset - dragged.offset).toFloat()
                val maxOffset = (info.viewportEndOffset - dragged.size - dragged.offset).toFloat()
                dragOffset = dragOffset.coerceIn(minOf(minOffset, maxOffset), maxOf(minOffset, maxOffset))
                val center = dragged.offset + dragged.size / 2f + dragOffset
                dragCenter = center
                val target = visible.firstOrNull {
                    it.key != id && center >= it.offset && center < it.offset + it.size
                } ?: return
                val fromIndex = dragQueue.indexOfFirst { it.id == id }
                val toIndex = target.index
                if (fromIndex < 0 || toIndex !in dragQueue.indices || fromIndex == toIndex) return
                dragQueue.add(toIndex, dragQueue.removeAt(fromIndex))
                // 换位后这一行的布局位置变了，位移补回来，手指下面的行才不会跳
                dragOffset += (dragged.offset - target.offset).toFloat()
            }
            // 边缘自动滚动：行贴住列表上下边（或手指已移到列表外的标题栏）时，
            // 按越界深度每帧滚一点；滚完补偿位移让行继续跟手，再重算一次落点，
            // 否则「按住不动只看列表滚」时上面的行会直接从被拖行上方越过去。
            LaunchedEffect(dragId) {
                val id = dragId ?: return@LaunchedEffect
                while (true) {
                    withFrameNanos { }
                    val info = playlistState.layoutInfo
                    if (info.visibleItemsInfo.none { it.key == id }) break
                    val step = DragAutoScroll.step(
                        center = dragCenter,
                        viewportStart = info.viewportStartOffset,
                        viewportEnd = info.viewportEndOffset,
                        edge = edgePx,
                        maxStep = rollStepPx,
                    )
                    if (step == 0f) continue
                    val scrolled = playlistState.scrollBy(step)
                    if (scrolled != 0f) {
                        dragOffset += scrolled
                        settle(id)
                    }
                }
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0x80000000))
                    .clickable { playlistVisible = false },
            )
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .fillMaxHeight(0.55f)
                    .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
                    .background(panelSolid)
                    .border(1.dp, WebColors.Border, RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "播放列表",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = WebColors.Text,
                        modifier = Modifier.weight(1f),
                    )
                    Box(
                        modifier = Modifier.size(32.dp).clip(CircleShape).clickable { playlistVisible = false },
                        contentAlignment = Alignment.Center,
                    ) {
                        ClearIcon(WebColors.Muted, Modifier.size(15.dp))
                    }
                }
                Box(Modifier.fillMaxWidth().height(1.dp).background(WebColors.Border))
                LazyColumn(
                    state = playlistState,
                    modifier = Modifier.fillMaxWidth().weight(1f).padding(horizontal = 8.dp, vertical = 6.dp),
                ) {
                    items(dragQueue, key = { it.id }) { file ->
                        val playing = file.id == item.file.id
                        val dragging = dragId == file.id
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                // 被拖起来的那一行画在其它行之上，并随手指上下位移
                                .zIndex(if (dragging) 1f else 0f)
                                .graphicsLayer { translationY = if (dragging) dragOffset else 0f }
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    when {
                                        // 拖动中必须是不透明底，否则会透出被它盖住的下面几行
                                        dragging -> panelSolid
                                        playing -> accent.copy(alpha = 0.2f)
                                        else -> Color.Transparent
                                    }
                                )
                                .combinedClickable(
                                    onClick = {
                                        PlaybackController.play(item.rj, file, item.work, state.queue)
                                        playlistVisible = false
                                    },
                                    // 长按本身不改状态，只是让「长按后松手」不再触发 onClick；
                                    // 真正进入拖拽由下面的拖拽检测器负责
                                    onLongClick = {},
                                )
                                .pointerInput(file.id) {
                                    detectDragGesturesAfterLongPress(
                                        onDragStart = {
                                            dragId = file.id
                                            dragOffset = 0f
                                            settle(file.id)
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        },
                                        onDrag = { change, amount ->
                                            change.consume()
                                            val id = dragId
                                            if (id != null) {
                                                dragOffset += amount.y
                                                settle(id)
                                            }
                                        },
                                        onDragEnd = {
                                            PlaybackController.reorderQueue(dragQueue.toList())
                                            dragId = null
                                            dragOffset = 0f
                                        },
                                        onDragCancel = {
                                            // 手势被打断（系统手势 / 来电）时也提交已排好的顺序：
                                            // 行早就换到新位置了，还原回去反而像「白拖一场」
                                            PlaybackController.reorderQueue(dragQueue.toList())
                                            dragId = null
                                            dragOffset = 0f
                                        },
                                    )
                                }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            // 左侧图标：平时是音符（视频为 ▶），进入拖拽后变成列表图标 —— 提示可以上下拖
                            Box(modifier = Modifier.size(20.dp), contentAlignment = Alignment.Center) {
                                if (dragging) {
                                    PlaylistIcon(accent, Modifier.size(16.dp))
                                } else {
                                    Text(
                                        if (file.playable && file.kind == "video") "▶" else "♪",
                                        fontSize = 13.sp,
                                        color = WebColors.Muted,
                                    )
                                }
                            }
                            Text(
                                file.filename,
                                fontSize = 14.sp,
                                color = if (playing) accent else WebColors.Text,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun applyFilter(onApplyCond: (FilterCond) -> Unit, onClose: () -> Unit, cond: FilterCond) {
    onApplyCond(cond)
    onClose()
}

/** 播放器里的纯图标按钮：无边框、无底色，只有一个 ≥44dp 的触控区。 */
@Composable
private fun IconTap(
    size: Dp = 44.dp,
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = Modifier.size(size).clip(CircleShape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

/** 主按钮行的一格：等分宽度，图标居中。 */
@Composable
private fun RowScope.CenterCell(content: @Composable () -> Unit) {
    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
        content()
    }
}

/** 进度条：白色细轨道 + 白色圆点，点击 / 拖动都能跳转。 */
@Composable
private fun ProgressSlider(
    progress: Float,
    modifier: Modifier = Modifier,
    onChange: (Float) -> Unit,
    onCommit: (Float) -> Unit,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(32.dp)
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    val ratio = (offset.x / size.width).coerceIn(0f, 1f)
                    onChange(ratio)
                    onCommit(ratio)
                }
            }
            .pointerInput(Unit) {
                var lastX = 0f
                detectHorizontalDragGestures(
                    onDragStart = { offset ->
                        lastX = offset.x
                        onChange((offset.x / size.width).coerceIn(0f, 1f))
                    },
                    onHorizontalDrag = { change, _ ->
                        lastX = change.position.x
                        onChange((change.position.x / size.width).coerceIn(0f, 1f))
                    },
                    onDragEnd = { onCommit((lastX / size.width).coerceIn(0f, 1f)) },
                    onDragCancel = { onCommit((lastX / size.width).coerceIn(0f, 1f)) },
                )
            },
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val cy = size.height / 2f
            val trackH = 4.dp.toPx()
            val knobR = 9.dp.toPx()
            val usable = (size.width - knobR * 2).coerceAtLeast(1f)
            val x = knobR + usable * progress.coerceIn(0f, 1f)

            drawRoundRect(
                color = Color(0x59FFFFFF),
                topLeft = Offset(knobR, cy - trackH / 2),
                size = Size(usable, trackH),
                cornerRadius = CornerRadius(trackH / 2),
            )
            drawRoundRect(
                color = Color.White,
                topLeft = Offset(knobR, cy - trackH / 2),
                size = Size((x - knobR).coerceAtLeast(0f), trackH),
                cornerRadius = CornerRadius(trackH / 2),
            )
            drawCircle(color = Color.White, radius = knobR, center = Offset(x, cy))
        }
    }
}

/** 音量弹条内容：竖排滑块 + 百分比（由 Popup 承载，不参与页面布局）。 */
@Composable
private fun VolumePane(
    value: Float,
    onChange: (Float) -> Unit,
    background: Color,
) {
    Column(
        modifier = Modifier
            .width(46.dp)
            .clip(RoundedCornerShape(23.dp))
            .background(background)
            .border(1.dp, WebColors.Border, RoundedCornerShape(23.dp))
            .padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        VerticalVolumeSlider(
            value = value,
            onChange = onChange,
            modifier = Modifier.width(30.dp).height(118.dp),
        )
        Text(
            "${(value.coerceIn(0f, 1f) * 100).roundToInt()}",
            fontSize = 11.sp,
            color = WebColors.Muted,
        )
    }
}

@Composable
private fun VerticalVolumeSlider(
    value: Float,
    onChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    onChange(1f - (offset.y / size.height).coerceIn(0f, 1f))
                }
            }
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragStart = { offset ->
                        onChange(1f - (offset.y / size.height).coerceIn(0f, 1f))
                    },
                    onVerticalDrag = { change, _ ->
                        onChange(1f - (change.position.y / size.height).coerceIn(0f, 1f))
                    },
                )
            },
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val cx = size.width / 2f
            val trackW = 4.dp.toPx()
            val knobR = 7.dp.toPx()
            val top = knobR
            val bottom = size.height - knobR
            val y = bottom - (bottom - top) * value.coerceIn(0f, 1f)

            drawRoundRect(
                color = Color(0x59FFFFFF),
                topLeft = Offset(cx - trackW / 2, top),
                size = Size(trackW, (bottom - top).coerceAtLeast(1f)),
                cornerRadius = CornerRadius(trackW / 2),
            )
            drawRoundRect(
                color = Color.White,
                topLeft = Offset(cx - trackW / 2, y),
                size = Size(trackW, (bottom - y).coerceAtLeast(0f)),
                cornerRadius = CornerRadius(trackW / 2),
            )
            drawCircle(color = Color.White, radius = knobR, center = Offset(cx, y))
        }
    }
}

/** 标签内的 +/- 区块（.fav-toggle：24 宽，与文字用 1px 竖线分隔）。 */
@Composable
private fun FavChip(
    text: String,
    category: String,
    background: Color,
    favored: Boolean,
    onToggleFav: () -> Unit,
    onClick: () -> Unit,
) {
    // 自定义标签（含内置「全年龄」）与 web `.tag-custom` 一致：深绿底 + 薄荷绿字 + 深绿描边
    val custom = category == CUSTOM_TAG
    Row(
        modifier = Modifier
            .height(24.dp)
            .clip(ChipShape)
            .background(if (custom) WebColors.CustomTagBg else background)
            .then(if (custom) Modifier.border(1.dp, WebColors.CustomTagBorder, ChipShape) else Modifier)
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text,
            fontSize = 12.sp,
            color = if (custom) WebColors.CustomTagFg else Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 8.dp, end = 2.dp),
        )
        Box(
            modifier = Modifier
                .width(24.dp)
                .height(24.dp)
                .clickable(onClick = onToggleFav),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                if (favored) "−" else "+",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = if (custom) WebColors.CustomTagFg.copy(alpha = 0.85f) else Color.White.copy(alpha = 0.85f),
            )
        }
    }
}

/** 把弹层放在锚点正上方居中（越界时贴边）。 */
private val AboveAnchorProvider = object : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val x = anchorBounds.center.x - popupContentSize.width / 2
        val y = anchorBounds.top - popupContentSize.height - 12
        return IntOffset(
            x.coerceIn(0, (windowSize.width - popupContentSize.width).coerceAtLeast(0)),
            y.coerceAtLeast(0),
        )
    }
}

/** 与 web 的 fmt() 一致：m:ss。 */
private fun fmtTime(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    val m = total / 60
    val s = total % 60
    return "%d:%02d".format(m, s)
}
