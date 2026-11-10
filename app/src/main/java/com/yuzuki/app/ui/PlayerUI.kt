package com.yuzuki.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yuzuki.app.data.AppContainer
import com.yuzuki.app.data.FilterCond
import com.yuzuki.app.data.MediaFile
import com.yuzuki.app.data.Work
import com.yuzuki.app.playback.PlayItem
import com.yuzuki.app.playback.PlayerHolder
import com.yuzuki.app.ui.theme.LocalAppearance
import com.yuzuki.app.ui.theme.ShapePill
import com.yuzuki.app.ui.theme.WebColors
import kotlinx.coroutines.launch

/**
 * 全屏播放器：按 web 的 FullscreenPlayer.vue 逐条还原（CSS px = dp）。
 *
 * 与 web 的唯一差异：去掉顶部 36px 的下拉把手（用户明确不要那条小白条），
 * 下滑关闭手势保留，作用在封面与信息区上，控件区不参与。
 */
private val PanelRadius = RoundedCornerShape(12.dp)
private val ChipShape = RoundedCornerShape(4.dp)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FullscreenPlayer(
    onClose: () -> Unit,
    onOpenWork: (String) -> Unit,
    onApplyCond: (FilterCond) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by PlayerHolder.state.collectAsState()
    val item = state.current
    val scope = rememberCoroutineScope()
    val appearance = LocalAppearance.current
    val accent = MaterialTheme.colorScheme.primary
    val accentFg = MaterialTheme.colorScheme.onPrimary
    // .fs-scrim = var(--panel)
    val scrim = WebColors.panel(appearance.navColor, appearance.panelAlpha)
    val genreBg = WebColors.tagBg(appearance.tagColor, appearance.genreTagAlpha)
    val actorBg = WebColors.tagBg(appearance.actorColor, appearance.actorTagAlpha)
    val seekSeconds = AppContainer.settings.current.seekSeconds

    var dragY by remember { mutableStateOf(0f) }
    var seekPreview by remember { mutableStateOf<Float?>(null) }
    var playlistVisible by remember { mutableStateOf(false) }

    val work = item?.work
    val workFav = item != null && FavoritesState.workRjs.contains(item.rj)

    val dragToClose = Modifier.pointerInput(Unit) {
        detectVerticalDragGestures(
            onDragEnd = {
                if (dragY > 100.dp.toPx()) {
                    dragY = 0f
                    onClose()
                } else {
                    dragY = 0f
                }
            },
            onDragCancel = { dragY = 0f },
        ) { _, delta ->
            dragY = (dragY + delta).coerceAtLeast(0f)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(WebColors.PageBg)
            .graphicsLayer { translationY = dragY },
    ) {
        Box(modifier = Modifier.fillMaxSize().background(scrim))

        Column(modifier = Modifier.fillMaxSize()) {
            // ---- .fs-cover：flex 1，居中，contain ----
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .then(dragToClose),
                contentAlignment = Alignment.Center,
            ) {
                if (item == null) {
                    Text("未开始播放", fontSize = 16.sp, color = WebColors.Muted)
                } else {
                    RemoteImage(
                        url = AppContainer.api.coverUrl(item.rj),
                        modifier = Modifier.fillMaxWidth(0.96f).fillMaxHeight(),
                        contentScale = ContentScale.Fit,
                    )
                }
            }

            if (item != null) {
                // ---- .fs-info：padding 4 20 10 / gap 6 ----
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 10.dp)
                        .then(dragToClose),
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
                                            FavoritesState.toggle("circle", work.circle)
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

                    if (work != null && work.actors.isNotEmpty()) {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            work.actors.take(6).forEach { actor ->
                                FavChip(
                                    text = actor,
                                    background = actorBg,
                                    favored = FavoritesState.actors.contains(actor),
                                    onToggleFav = { scope.launch { FavoritesState.toggle("actor", actor) } },
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
                                    background = genreBg,
                                    favored = FavoritesState.tags.contains(tag.name),
                                    onToggleFav = {
                                        scope.launch {
                                            FavoritesState.toggle("tag", tag.name, tag.category)
                                        }
                                    },
                                    onClick = { applyFilter(onApplyCond, onClose, FilterCond("tag", tag.name)) },
                                )
                            }
                        }
                    }
                }

                // ---- .fs-controls：padding 10 20 26 / gap 12 ----
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 26.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    // .fs-row：三个 small 按钮，两端对齐
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        SmallButton(
                            text = "播放列表",
                            border = WebColors.Border,
                            contentColor = WebColors.Text,
                            onClick = { playlistVisible = true },
                        ) { PlaylistIcon(WebColors.Text, Modifier.size(13.dp)) }

                        SmallButton(
                            text = if (workFav) "已收藏" else "收藏",
                            border = if (workFav) Color(0xFFF56C6C) else WebColors.Border,
                            background = if (workFav) Color(0xFFF56C6C) else Color.Transparent,
                            contentColor = if (workFav) Color.White else WebColors.Text,
                            onClick = { item?.let { scope.launch { FavoritesState.toggleWork(it.rj) } } },
                        ) {
                            val tint = if (workFav) Color.White else WebColors.Text
                            if (workFav) {
                                FavoriteStarIcon(true, tint, Modifier.size(13.dp))
                            } else {
                                FavoriteStarIcon(false, tint, Modifier.size(13.dp))
                            }
                        }

                        SmallButton(
                            text = "跳转详情",
                            border = WebColors.Border,
                            contentColor = WebColors.Text,
                            onClick = { item?.let { onOpenWork(it.rj) } },
                        ) { ExternalIcon(WebColors.Text, Modifier.size(13.dp)) }
                    }

                    // 进度条 + 时间
                    Box {
                        val duration = state.durationMs.coerceAtLeast(1L)
                        val value = seekPreview ?: state.positionMs.toFloat().coerceAtMost(duration.toFloat())
                        WebSlider(
                            progress = (value / duration).coerceIn(0f, 1f),
                            accent = accent,
                            onChange = { ratio ->
                                seekPreview = ratio * duration
                            },
                            onCommit = {
                                seekPreview?.let { PlayerHolder.seekTo(it.toLong()) }
                                seekPreview = null
                            },
                        )
                        Row(modifier = Modifier.fillMaxWidth().padding(top = 22.dp)) {
                            Text(fmtTime(state.positionMs), fontSize = 12.sp, color = WebColors.Muted)
                            Spacer(Modifier.weight(1f))
                            Text(fmtTime(state.durationMs), fontSize = 12.sp, color = WebColors.Muted)
                        }
                    }

                    Text(
                        item.file.filename.ifBlank { "未开始播放" },
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = WebColors.Text,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )

                    // 六个圆形按钮：重复 / 上一曲 / 快退 / 播放 / 快进 / 下一曲
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RoundButton(
                            size = 44.dp,
                            active = state.loop,
                            accent = accent,
                            border = WebColors.Border,
                            onClick = { PlayerHolder.toggleLoop() },
                        ) { tint ->
                            RepeatIcon(tint, Modifier.size(17.dp))
                        }
                        Spacer(Modifier.width(8.dp))
                        RoundButton(
                            size = 44.dp,
                            accent = accent,
                            border = WebColors.Border,
                            onClick = { PlayerHolder.prev() },
                        ) { tint ->
                            PrevIcon(tint, Modifier.size(17.dp))
                        }
                        Spacer(Modifier.width(8.dp))
                        RoundButton(
                            size = 44.dp,
                            accent = accent,
                            border = WebColors.Border,
                            onClick = { PlayerHolder.seekBy(-seekSeconds) },
                        ) { tint ->
                            Text("-$seekSeconds", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = tint)
                        }
                        Spacer(Modifier.width(8.dp))
                        // 主按钮：46 圆，强调色底
                        Box(
                            modifier = Modifier
                                .size(46.dp)
                                .clip(CircleShape)
                                .background(accent)
                                .clickable { PlayerHolder.toggle() },
                            contentAlignment = Alignment.Center,
                        ) {
                            if (state.playing) {
                                PauseIcon(accentFg, Modifier.size(19.dp))
                            } else {
                                PlayIcon(accentFg, Modifier.size(19.dp))
                            }
                        }
                        Spacer(Modifier.width(8.dp))
                        RoundButton(
                            size = 44.dp,
                            accent = accent,
                            border = WebColors.Border,
                            onClick = { PlayerHolder.seekBy(seekSeconds) },
                        ) { tint ->
                            Text("+$seekSeconds", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = tint)
                        }
                        Spacer(Modifier.width(8.dp))
                        RoundButton(
                            size = 44.dp,
                            accent = accent,
                            border = WebColors.Border,
                            onClick = { PlayerHolder.next() },
                        ) { tint ->
                            NextIcon(tint, Modifier.size(17.dp))
                        }
                    }

                    // 音量
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Box(modifier = Modifier.size(18.dp), contentAlignment = Alignment.Center) {
                            VolumeIcon(state.volume > 0f, WebColors.Muted, Modifier.size(18.dp))
                        }
                        WebSlider(
                            progress = state.volume.coerceIn(0f, 1f),
                            accent = accent,
                            modifier = Modifier.weight(1f),
                            onChange = { PlayerHolder.setVolume(it) },
                            onCommit = {},
                        )
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

        // ---- 播放列表（pl-panel：底部 55%，顶部圆角 12）----
        if (playlistVisible && item != null) {
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
                    .background(WebColors.panel(appearance.navColor, 1f))
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
                LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f).padding(horizontal = 8.dp, vertical = 6.dp)) {
                    items(state.queue, key = { it.id }) { file ->
                        val playing = file.id == item.file.id
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (playing) accent.copy(alpha = 0.2f) else Color.Transparent)
                                .clickable {
                                    PlayerHolder.play(item.rj, file, item.work, state.queue)
                                    playlistVisible = false
                                }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Text(
                                if (file.playable && file.kind == "video") "▶" else "♪",
                                fontSize = 13.sp,
                                color = WebColors.Muted,
                            )
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

/** el-button size=small：高 24 / padding 0 11 / radius 12 / 1px 边框 / 12px。 */
@Composable
private fun SmallButton(
    text: String,
    border: Color,
    background: Color = Color.Transparent,
    contentColor: Color = WebColors.Text,
    onClick: () -> Unit,
    icon: @Composable () -> Unit,
) {
    Row(
        modifier = Modifier
            .height(24.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(background)
            .border(1.dp, border, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        icon()
        Text(text, fontSize = 12.sp, color = contentColor, maxLines = 1)
    }
}

/** .fbtn：44 圆 / 1px 边框；active 时强调色边框 + 淡底。 */
@Composable
private fun RoundButton(
    size: androidx.compose.ui.unit.Dp,
    accent: Color,
    border: Color,
    active: Boolean = false,
    onClick: () -> Unit,
    content: @Composable (Color) -> Unit,
) {
    val tint = if (active) accent else WebColors.Text
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(if (active) accent.copy(alpha = 0.2f) else Color.Transparent)
            .border(1.dp, if (active) accent else border, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        content(tint)
    }
}

/** 标签内的 +/- 区块（.fav-toggle：24 宽，与文字用 1px 竖线分隔）。 */
@Composable
private fun FavChip(
    text: String,
    background: Color,
    favored: Boolean,
    onToggleFav: () -> Unit,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .height(24.dp)
            .clip(ChipShape)
            .background(background)
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text,
            fontSize = 12.sp,
            color = Color.White,
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
                color = Color.White.copy(alpha = 0.85f),
            )
        }
    }
}

/** el-slider 的等价实现：6px 轨道 + 20px 圆形滑块，点击 / 拖动都能跳转。 */
@Composable
private fun WebSlider(
    progress: Float,
    accent: Color,
    modifier: Modifier = Modifier,
    onChange: (Float) -> Unit,
    onCommit: () -> Unit,
) {
    var widthPx by remember { mutableStateOf(1f) }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(24.dp)
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    onChange((offset.x / size.width).coerceIn(0f, 1f))
                    onCommit()
                }
            }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = { offset ->
                        onChange((offset.x / size.width).coerceIn(0f, 1f))
                    },
                    onHorizontalDrag = { change, _ ->
                        onChange((change.position.x / size.width).coerceIn(0f, 1f))
                    },
                    onDragEnd = { onCommit() },
                    onDragCancel = { onCommit() },
                )
            },
    ) {
        androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
            widthPx = size.width
            val cy = size.height / 2f
            val trackH = 6.dp.toPx()
            val knobR = 10.dp.toPx()
            val usable = size.width - knobR * 2
            val x = knobR + usable * progress.coerceIn(0f, 1f)

            drawRoundRect(
                color = Color(0x33FFFFFF),
                topLeft = Offset(knobR, cy - trackH / 2),
                size = Size(usable, trackH),
                cornerRadius = CornerRadius(trackH / 2),
            )
            drawRoundRect(
                color = accent,
                topLeft = Offset(knobR, cy - trackH / 2),
                size = Size((x - knobR).coerceAtLeast(0f), trackH),
                cornerRadius = CornerRadius(trackH / 2),
            )
            drawCircle(color = Color.White, radius = knobR, center = Offset(x, cy))
        }
        Spacer(Modifier.width(0.dp))
    }
}

/** 与 web 的 fmt() 一致：m:ss。 */
private fun fmtTime(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    val m = total / 60
    val s = total % 60
    return "%d:%02d".format(m, s)
}
