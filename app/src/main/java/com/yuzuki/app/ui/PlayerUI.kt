package com.yuzuki.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.yuzuki.app.data.AppContainer
import com.yuzuki.app.playback.PlayerHolder
import com.yuzuki.app.ui.theme.GlassItem
import com.yuzuki.app.ui.theme.GlassPill
import com.yuzuki.app.ui.theme.GlassSurface
import com.yuzuki.app.ui.theme.LocalAppearance
import com.yuzuki.app.ui.theme.ShapeCard
import com.yuzuki.app.ui.theme.ShapeItem

@Composable
private fun ControlButton(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    GlassPill(modifier = modifier, onClick = onClick) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
        )
    }
}

@Composable
fun MiniPlayer(
    modifier: Modifier = Modifier,
    onExpand: () -> Unit,
) {
    val state by PlayerHolder.state.collectAsState()
    val item = state.current ?: return
    val progress = if (state.durationMs > 0) {
        state.positionMs.toFloat() / state.durationMs.toFloat()
    } else 0f

    GlassSurface(
        modifier = modifier
            .fillMaxWidth()
            .height(64.dp),
        shape = ShapeCard,
        onClick = onExpand,
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(modifier = Modifier.size(48.dp)) {
                RemoteImage(
                    url = AppContainer.api.coverUrl(item.rj),
                    modifier = Modifier.fillMaxSize(),
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.file.filename.ifBlank { item.work?.title ?: item.rj },
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = item.work?.actors?.joinToString("、")?.takeIf { it.isNotBlank() }
                        ?: item.work?.circle ?: item.rj,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.25f)),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(progress.coerceIn(0f, 1f))
                            .height(3.dp)
                            .background(MaterialTheme.colorScheme.primary),
                    )
                }
            }
            ControlButton(if (state.playing) "暂停" else "播放") { PlayerHolder.toggle() }
            ControlButton("下一曲") { PlayerHolder.next() }
            ControlButton("全屏") { onExpand() }
        }
    }
}

@Composable
fun FullscreenPlayer(
    onClose: () -> Unit,
    onOpenWork: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by PlayerHolder.state.collectAsState()
    val appearance = LocalAppearance.current
    val item = state.current
    var dragY by remember { mutableStateOf(0f) }
    var seekPreview by remember { mutableStateOf<Float?>(null) }
    val seekSeconds = AppContainer.settings.current.seekSeconds

    GlassSurface(
        modifier = modifier
            .fillMaxSize()
            .systemBarsPadding()
            .graphicsLayer { translationY = dragY }
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragEnd = { dragY = 0f },
                    onDragCancel = { dragY = 0f },
                ) { _, delta ->
                    dragY = (dragY + delta).coerceAtLeast(0f)
                    if (dragY > 320f) onClose()
                }
            },
        shape = ShapeCard,
        alpha = appearance.panelAlpha,
        blur = appearance.panelBlur,
    ) {
        WallpaperBackground()
        Box(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Row(modifier = Modifier.fillMaxWidth()) {
                    ControlButton("下滑退出") { onClose() }
                    Box(modifier = Modifier.weight(1f))
                    ControlButton("回到作品") { item?.let { onOpenWork(it.rj) } }
                }

                if (item == null) {
                    StateBox(message = "暂无播放", modifier = Modifier.weight(1f))
                    return@Column
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    RemoteImage(
                        url = AppContainer.api.coverUrl(item.rj),
                        modifier = Modifier.fillMaxSize().padding(12.dp),
                        contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                    )
                }

                Text(
                    text = item.file.filename.ifBlank { item.work?.title ?: item.rj },
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                )
                Text(
                    text = item.work?.title ?: item.rj,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )

                val duration = state.durationMs.coerceAtLeast(1L)
                val sliderValue = seekPreview ?: state.positionMs.toFloat().coerceAtMost(duration.toFloat())
                Slider(
                    value = sliderValue,
                    onValueChange = { seekPreview = it },
                    onValueChangeFinished = {
                        seekPreview?.let { PlayerHolder.seekTo(it.toLong()) }
                        seekPreview = null
                    },
                    valueRange = 0f..duration.toFloat(),
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(modifier = Modifier.fillMaxWidth()) {
                    Text(formatDuration(state.positionMs), style = MaterialTheme.typography.labelSmall)
                    Box(modifier = Modifier.weight(1f))
                    Text(formatDuration(state.durationMs), style = MaterialTheme.typography.labelSmall)
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ControlButton("上一曲") { PlayerHolder.prev() }
                    ControlButton("-$seekSeconds") { PlayerHolder.seekBy(-seekSeconds) }
                    ControlButton(if (state.playing) "暂停" else "播放") { PlayerHolder.toggle() }
                    ControlButton("+$seekSeconds") { PlayerHolder.seekBy(seekSeconds) }
                    ControlButton("下一曲") { PlayerHolder.next() }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ControlButton(if (state.loop) "循环：开" else "循环：关") { PlayerHolder.toggleLoop() }
                    Text("音量", style = MaterialTheme.typography.labelSmall)
                    Slider(
                        value = state.volume,
                        onValueChange = { PlayerHolder.setVolume(it) },
                        modifier = Modifier.weight(1f),
                    )
                }

                Text("播放列表（当前目录）", style = MaterialTheme.typography.labelMedium)
                LazyColumn(modifier = Modifier.height(140.dp).fillMaxWidth()) {
                    items(state.queue, key = { it.id }) { file ->
                        val active = file.id == item.file.id
                        GlassItem(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 3.dp),
                            onClick = { PlayerHolder.play(item.rj, file, item.work, state.queue) },
                        ) {
                            Text(
                                text = file.filename,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (active) MaterialTheme.colorScheme.primary else Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}
