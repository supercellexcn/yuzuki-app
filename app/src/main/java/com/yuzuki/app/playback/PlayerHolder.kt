package com.yuzuki.app.playback

import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.yuzuki.app.data.AppContainer
import com.yuzuki.app.data.MediaFile
import com.yuzuki.app.data.Work
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class PlayItem(val rj: String, val file: MediaFile, val work: Work?)

data class PlayState(
    val current: PlayItem? = null,
    val queue: List<MediaFile> = emptyList(),
    val playing: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val volume: Float = 1f,
    val loop: Boolean = false,
    val fullscreen: Boolean = false,
    val error: String? = null,
)

/**
 * 播放状态唯一真相源（方案 §7）：Service 持有 ExoPlayer 并 attach 到这里，
 * UI 通过 StateFlow 单向观察；队列、元数据、时长上报全在原生，不再有 JS 事件桥。
 */
@UnstableApi
object PlayerHolder {
    private val main = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _state = MutableStateFlow(PlayState())
    val state: StateFlow<PlayState> = _state

    @Volatile private var player: ExoPlayer? = null
    @Volatile private var pendingUrl: String? = null
    @Volatile private var pendingPlay = false
    @Volatile private var artworkBytes: ByteArray? = null
    @Volatile private var artworkLoader: ArtworkLoader? = null
    @Volatile private var lastReportAt = 0L

    private fun update(block: (PlayState) -> PlayState) {
        _state.value = block(_state.value)
    }

    private val ticker = object : Runnable {
        override fun run() {
            val p = player
            if (p != null) {
                val pos = p.currentPosition.coerceAtLeast(0)
                val dur = p.duration.takeIf { it > 0 } ?: 0L
                update { it.copy(positionMs = pos, durationMs = dur) }
                if (p.isPlaying && System.currentTimeMillis() - lastReportAt > 30_000) {
                    lastReportAt = System.currentTimeMillis()
                    AppContainer.reporter.tick()
                }
            }
            main.postDelayed(this, 250)
        }
    }

    private val listener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            when (playbackState) {
                Player.STATE_READY -> {
                    val dur = player?.duration?.takeIf { it > 0 } ?: 0L
                    update { it.copy(durationMs = dur) }
                }
                Player.STATE_ENDED -> {
                    update { it.copy(playing = false) }
                    AppContainer.reporter.pause()
                    next()
                }
            }
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            update { it.copy(playing = isPlaying) }
            if (isPlaying) AppContainer.reporter.resume() else AppContainer.reporter.pause()
        }

        override fun onPlayerError(error: PlaybackException) {
            update { it.copy(playing = false, error = error.message ?: "播放失败") }
            AppContainer.reporter.settle()
        }
    }

    fun attach(newPlayer: ExoPlayer) = runOnMain {
        detachPlayer()
        player = newPlayer
        newPlayer.volume = _state.value.volume
        newPlayer.repeatMode = if (_state.value.loop) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
        newPlayer.addListener(listener)
        pendingUrl?.let { applySource(newPlayer, it) }
        if (pendingPlay) newPlayer.play()
        pendingUrl = null
        pendingPlay = false
        main.removeCallbacks(ticker)
        main.postDelayed(ticker, 250)
    }

    fun detach() = runOnMain {
        detachPlayer()
        main.removeCallbacks(ticker)
    }

    // ---- UI 指令 ----

    fun play(rj: String, file: MediaFile, work: Work?, queue: List<MediaFile> = emptyList()) {
        val state = _state.value
        if (state.current?.rj == rj && state.current.file.id == file.id) {
            toggle()
            return
        }
        ensureService()
        val item = PlayItem(rj, file, work)
        val newQueue = QueueMath.playable(queue).ifEmpty {
            state.queue.takeIf { state.current?.rj == rj }.orEmpty()
        }
        update {
            it.copy(
                current = item,
                queue = newQueue,
                playing = true,
                positionMs = 0,
                durationMs = 0,
                error = null,
            )
        }
        artworkBytes = null
        loadArtwork(rj)
        val url = mediaUrl(rj, file.id)
        AppContainer.reporter.start(rj, file.id)
        runOnMain {
            val p = player
            if (p == null) {
                pendingUrl = url
                pendingPlay = true
            } else {
                applySource(p, url)
                p.play()
            }
        }
    }

    fun toggle() {
        if (_state.value.current == null) return
        if (_state.value.playing) pause() else resume()
    }

    fun pause() = runOnMain { player?.pause() }
    fun resume() = runOnMain { player?.play() }

    fun next() {
        val s = _state.value
        val cur = s.current ?: return
        val idx = QueueMath.nextIndex(s.queue.map { it.id }, cur.file.id)
        if (idx < 0) return
        play(cur.rj, s.queue[idx], cur.work, s.queue)
    }

    fun prev() {
        val s = _state.value
        val cur = s.current ?: return
        val idx = QueueMath.prevIndex(s.queue.map { it.id }, cur.file.id)
        if (idx < 0) return
        play(cur.rj, s.queue[idx], cur.work, s.queue)
    }

    fun seekTo(positionMs: Long) = runOnMain {
        val ms = positionMs.coerceAtLeast(0)
        player?.seekTo(ms)
        update { it.copy(positionMs = ms) }
    }

    fun seekBy(seconds: Int) = runOnMain {
        val p = player
        val base = p?.currentPosition ?: _state.value.positionMs
        seekTo(base + seconds * 1000L)
    }

    fun setVolume(v: Float) = runOnMain {
        val value = v.coerceIn(0f, 1f)
        update { it.copy(volume = value) }
        player?.volume = value
    }

    fun toggleLoop() = runOnMain {
        val value = !_state.value.loop
        update { it.copy(loop = value) }
        player?.repeatMode = if (value) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
    }

    fun stop() = runOnMain {
        AppContainer.reporter.settle()
        player?.apply { stop(); clearMediaItems() }
        pendingUrl = null
        pendingPlay = false
        update {
            it.copy(
                current = null, queue = emptyList(), playing = false,
                positionMs = 0, durationMs = 0, fullscreen = false,
            )
        }
    }

    fun setFullscreen(visible: Boolean) = update { it.copy(fullscreen = visible) }

    fun clearError() = update { it.copy(error = null) }

    // ---- 内部 ----

    private fun mediaUrl(rj: String, fileId: Long): String {
        val offline = AppContainer.offline
        return if (offline.hasAudio(rj, fileId)) {
            Uri.fromFile(offline.audioFile(rj, fileId)).toString()
        } else AppContainer.api.mediaUrl(rj, fileId)
    }

    private fun buildMetadata(): MediaMetadata {
        val item = _state.value.current
        return MediaMetadata.Builder()
            .setTitle(item?.file?.filename?.takeIf { it.isNotBlank() } ?: item?.work?.title ?: "")
            .setArtist(
                item?.work?.actors?.takeIf { it.isNotEmpty() }?.joinToString("、")
                    ?: item?.work?.circle ?: "",
            )
            .setAlbumTitle(item?.work?.title ?: item?.rj ?: "")
            .apply {
                artworkBytes?.let { setArtworkData(it, MediaMetadata.PICTURE_TYPE_FRONT_COVER) }
            }
            .build()
    }

    private fun loadArtwork(rj: String) {
        val url = AppContainer.api.coverUrl(rj)
        val base = AppContainer.api.cachedBaseUrl
        val loader = artworkLoader?.takeIf { it.baseUrl == base } ?: ArtworkLoader(base)
        artworkLoader = loader
        scope.launch {
            val bytes = loader.load(url) ?: return@launch
            artworkBytes = bytes
            runOnMain { applyArtwork() }
        }
    }

    private fun applyArtwork() = runOnMain {
        val p = player ?: return@runOnMain
        val item = p.currentMediaItem ?: return@runOnMain
        p.replaceMediaItem(
            p.currentMediaItemIndex,
            item.buildUpon().setMediaMetadata(buildMetadata()).build(),
        )
    }

    private fun applySource(p: ExoPlayer, url: String) {
        p.setMediaItem(MediaItem.Builder().setUri(url).setMediaMetadata(buildMetadata()).build())
        p.prepare()
    }

    private fun detachPlayer() {
        player?.removeListener(listener)
        player = null
    }

    private fun ensureService() {
        runCatching {
            AppContainer.app.startService(Intent(AppContainer.app, PlaybackService::class.java))
        }
    }

    private inline fun runOnMain(crossinline block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) block()
        else main.post { block() }
    }
}
