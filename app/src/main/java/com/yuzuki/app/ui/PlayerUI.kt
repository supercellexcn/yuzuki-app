package com.yuzuki.app.ui

import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.yuzuki.app.data.AppContainer
import com.yuzuki.app.playback.PlayerHolder
import com.yuzuki.app.ui.theme.GlassItem
import com.yuzuki.app.ui.theme.GlassSurface
import com.yuzuki.app.ui.theme.ShapeCard
import com.yuzuki.app.ui.theme.ShapePill

@Composable
private fun ControlButton(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    FilledTonalButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = 48.dp),
        shape = ShapePill,
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Text(text = label, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
fun FullscreenPlayer(
    onClose: () -> Unit,
    onOpenWork: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by PlayerHolder.state.collectAsState()
    val item = state.current
    var dragY by remember { mutableStateOf(0f) }
    var seekPreview by remember { mutableStateOf<Float?>(null) }
    val seekSeconds = AppContainer.settings.current.seekSeconds

    GlassSurface(
        modifier = modifier
            .fillMaxSize()
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
    ) {
        Box(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onClose, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "收起播放器")
                    }
                    Text(
                        "正在播放",
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.weight(1f).padding(start = 8.dp),
                    )
                    TextButton(onClick = { item?.let { onOpenWork(it.rj) } }) { Text("回到作品") }
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
                                color = if (active) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurface,
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
