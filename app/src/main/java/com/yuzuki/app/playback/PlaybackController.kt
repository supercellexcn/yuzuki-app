package com.yuzuki.app.playback

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.yuzuki.app.data.AppContainer
import com.yuzuki.app.data.MediaFile
import com.yuzuki.app.data.Work
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * UI 侧播放控制器（方案 §7 架构图里的 PlaybackController）：
 * 通过 [MediaController] 连接 [PlaybackService]，所有播放指令都下发给服务，
 * Activity 里不持有任何播放器实例，因此页面重建 / 息屏 / 切 App 都不会中断播放。
 *
 * 播放队列以 ExoPlayer playlist 的形式下发，随机 / 循环 / 自动切歌由 ExoPlayer 推进，
 * 这里只是把 controller 的状态投影成 UI 需要的 [PlayState]。
 */
@UnstableApi
object PlaybackController {
    private val main = Handler(Looper.getMainLooper())
    private val _state = MutableStateFlow(PlayState())
    val state: StateFlow<PlayState> = _state

    private var appContext: Context? = null
    private var future: ListenableFuture<MediaController>? = null
    private var controller: MediaController? = null
    private var pendingAction: ((MediaController) -> Unit)? = null
    private var currentWork: Work? = null

    /** 应用级初始化，只保存 Context，不建连接（首次播放或 UI 进场时才 bind 服务）。 */
    fun init(context: Context) {
        if (appContext == null) {
            appContext = context.applicationContext
            main.postDelayed(ticker, 250)
        }
    }

    /** UI 进场调用：提前连上服务，重建 Activity 后仍能直接读到正在播放的曲目。 */
    fun warmUp() = connect()

    // ---- UI 指令 ----

    fun play(rj: String, file: MediaFile, work: Work?, queue: List<MediaFile> = emptyList()) {
        val state = _state.value
        if (state.current?.rj == rj && state.current.file.id == file.id) {
            toggle()
            return
        }
        val playable = QueueMath.playable(queue)
        val list = playable.ifEmpty {
            if (state.current?.rj == rj) state.queue.ifEmpty { listOf(file) } else listOf(file)
        }
        val index = list.indexOfFirst { it.id == file.id }.coerceAtLeast(0)
        currentWork = work
        _state.value = state.copy(
            current = PlayItem(rj, file, work),
            queue = list,
            queueIndex = index,
            playing = true,
            positionMs = 0L,
            durationMs = 0L,
            error = null,
        )
        val items = buildItems(rj, work, list)
        withController { c ->
            c.setMediaItems(items, index, 0L)
            c.prepare()
            c.play()
        }
    }

    fun toggle() {
        if (_state.value.current == null) return
        if (_state.value.playing) pause() else resume()
    }

    fun pause() = withController { it.pause() }

    fun resume() = withController { it.play() }

    fun next() = withController { it.seekToNext() }

    fun prev() = withController { it.seekToPrevious() }

    fun seekTo(positionMs: Long) {
        val target = positionMs.coerceAtLeast(0)
        _state.value = _state.value.copy(positionMs = target)
        withController { it.seekTo(target) }
    }

    fun seekBy(seconds: Int) = withController {
        it.seekTo((it.currentPosition + seconds * 1000L).coerceAtLeast(0))
    }

    fun setVolume(value: Float) {
        val volume = value.coerceIn(0f, 1f)
        _state.value = _state.value.copy(volume = volume)
        withController { it.volume = volume }
    }

    /** 单按钮三态：单曲循环 → 列表循环 → 关闭。 */
    fun cycleRepeat() {
        val next = QueueMath.cycleRepeat(_state.value.repeat)
        _state.value = _state.value.copy(repeat = next)
        withController { it.repeatMode = next.toPlayerRepeatMode() }
    }

    fun toggleShuffle() {
        val enabled = !_state.value.shuffle
        _state.value = _state.value.copy(shuffle = enabled)
        withController { it.shuffleModeEnabled = enabled }
    }

    /** 退出登录 / 清空播放：停止并清队列，媒体通知随之消失。 */
    fun stop() {
        withController {
            it.stop()
            it.clearMediaItems()
        }
        pendingAction = null
        currentWork = null
        _state.value = _state.value.copy(
            current = null,
            queue = emptyList(),
            queueIndex = 0,
            playing = false,
            positionMs = 0L,
            durationMs = 0L,
            fullscreen = false,
            error = null,
        )
    }

    fun setFullscreen(visible: Boolean) {
        _state.value = _state.value.copy(fullscreen = visible)
    }

    fun clearError() {
        _state.value = _state.value.copy(error = null)
    }

    /** 退出登录时彻底断开，避免服务被继续绑定。 */
    fun disconnect() {
        pendingAction = null
        controller?.removeListener(listener)
        controller = null
        future?.let { MediaController.releaseFuture(it) }
        future = null
        currentWork = null
        _state.value = PlayState()
    }

    // ---- 内部 ----

    private fun connect() {
        val context = appContext ?: return
        if (controller != null || future != null) return
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val pending = MediaController.Builder(context, token).buildAsync()
        future = pending
        pending.addListener({
            if (future !== pending) return@addListener
            val connected = runCatching { pending.get() }.getOrNull()
            if (connected == null) {
                future = null
                _state.value = _state.value.copy(playing = false, error = "无法连接播放服务")
                return@addListener
            }
            controller = connected
            connected.addListener(listener)
            syncFromController()
            pendingAction?.let { it(connected) }
            pendingAction = null
        }, ContextCompat.getMainExecutor(context))
    }

    private fun withController(action: (MediaController) -> Unit) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            main.post { withController(action) }
            return
        }
        val ready = controller
        if (ready != null) action(ready) else {
            pendingAction = action
            connect()
        }
    }

    private val listener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            val c = controller
            _state.value = _state.value.copy(
                playing = isPlaying,
                positionMs = c?.currentPosition?.coerceAtLeast(0) ?: _state.value.positionMs,
            )
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_READY) syncFromController()
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            syncCurrent()
        }

        override fun onRepeatModeChanged(repeatMode: Int) {
            _state.value = _state.value.copy(repeat = repeatMode.toRepeatMode())
        }

        override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
            _state.value = _state.value.copy(shuffle = shuffleModeEnabled)
        }

        override fun onVolumeChanged(volume: Float) {
            _state.value = _state.value.copy(volume = volume)
        }

        override fun onPlayerError(error: PlaybackException) {
            _state.value = _state.value.copy(
                playing = false,
                error = error.message ?: "播放失败",
            )
        }
    }

    private val ticker = object : Runnable {
        override fun run() {
            val c = controller
            if (c != null) {
                val duration = c.duration.takeIf { it > 0 && it != C.TIME_UNSET } ?: 0L
                _state.value = _state.value.copy(
                    positionMs = c.currentPosition.coerceAtLeast(0),
                    durationMs = duration,
                )
            }
            main.postDelayed(this, 250)
        }
    }

    /** 连接成功后整体同步一次，之后由 Listener + ticker 增量更新。 */
    private fun syncFromController() {
        val c = controller ?: return
        val duration = c.duration.takeIf { it > 0 && it != C.TIME_UNSET } ?: 0L
        _state.value = _state.value.copy(
            playing = c.isPlaying,
            positionMs = c.currentPosition.coerceAtLeast(0),
            durationMs = duration,
            volume = c.volume,
            repeat = c.repeatMode.toRepeatMode(),
            shuffle = c.shuffleModeEnabled,
        )
        syncCurrent()
    }

    /** 曲目切换：用 playlist 下标映射回本地队列，保证标题 / 封面与系统通知同源。 */
    private fun syncCurrent() {
        val c = controller ?: return
        val state = _state.value
        val index = c.currentMediaItemIndex
        if (index < 0) return
        val rj = c.currentMediaItem?.mediaId?.substringBeforeLast('/', "").orEmpty()
        val file = state.queue.getOrNull(index)
        val current = if (file != null && rj.isNotBlank()) {
            PlayItem(rj, file, currentWork)
        } else {
            state.current
        }
        _state.value = state.copy(
            current = current,
            queueIndex = index,
            positionMs = c.currentPosition.coerceAtLeast(0),
            durationMs = c.duration.takeIf { it > 0 && it != C.TIME_UNSET } ?: 0L,
        )
    }

    private fun buildItems(rj: String, work: Work?, files: List<MediaFile>): List<MediaItem> {
        val artist = work?.actors?.takeIf { it.isNotEmpty() }?.joinToString("、")
            ?: work?.circle.orEmpty()
        val album = work?.title ?: rj
        return files.map { file ->
            MediaItem.Builder()
                .setMediaId(QueueMath.mediaId(rj, file.id))
                .setUri(mediaUrl(rj, file.id))
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(file.filename.ifBlank { album })
                        .setArtist(artist)
                        .setAlbumTitle(album)
                        .setIsPlayable(true)
                        .build(),
                )
                .build()
        }
    }

    private fun mediaUrl(rj: String, fileId: Long): String {
        val offline = AppContainer.offline
        return if (offline.hasAudio(rj, fileId)) {
            Uri.fromFile(offline.audioFile(rj, fileId)).toString()
        } else {
            AppContainer.api.mediaUrl(rj, fileId)
        }
    }

    private fun RepeatMode.toPlayerRepeatMode(): Int = when (this) {
        RepeatMode.OFF -> Player.REPEAT_MODE_OFF
        RepeatMode.ONE -> Player.REPEAT_MODE_ONE
        RepeatMode.ALL -> Player.REPEAT_MODE_ALL
    }

    private fun Int.toRepeatMode(): RepeatMode = when (this) {
        Player.REPEAT_MODE_ONE -> RepeatMode.ONE
        Player.REPEAT_MODE_ALL -> RepeatMode.ALL
        else -> RepeatMode.OFF
    }
}
