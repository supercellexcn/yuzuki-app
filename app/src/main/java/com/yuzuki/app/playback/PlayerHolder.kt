package com.yuzuki.app.playback

import android.os.Handler
import android.os.Looper
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * ExoPlayer 单例状态机（方案 §6.3 / §6.4）：原生是唯一真相源。
 * Service 持有 ExoPlayer 并 attach 到这里；WebView 经 JsBridge 下发指令，
 * 这里回推 6 类事件，播放中每 250ms 一次 timeupdate。
 * S4：维护锁屏 MediaMetadata（含原生下载的封面）并转发换曲命令到 WebView。
 */
@UnstableApi
object PlayerHolder {
    private val main = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile private var player: ExoPlayer? = null
    @Volatile private var state = NativeAudioState()
    @Volatile private var pendingSource: String? = null
    @Volatile private var pendingPlay = false

    /** 由当前 WebView 设置，重建时覆盖；销毁时置空。 */
    @Volatile var eventSink: ((String) -> Unit)? = null

    /** 锁屏换曲命令 → WebView 的 __yuzukiPlayerCommand。 */
    @Volatile var commandSink: ((String) -> Unit)? = null

    /** 当前锁屏元数据；前端 updateMediaMetadata() 早于 a.src 赋值，故换源时一并带上。 */
    @Volatile private var title: String = ""
    @Volatile private var artist: String = ""
    @Volatile private var album: String = ""
    @Volatile private var artworkBytes: ByteArray? = null
    @Volatile private var artworkLoader: ArtworkLoader? = null
    @Volatile private var artworkBaseUrl: String = ""

    private val ticker = object : Runnable {
        override fun run() {
            val p = player
            if (p != null && p.isPlaying) {
                val t = p.currentPosition / 1000.0
                val d = p.duration.takeIf { it > 0 }?.div(1000.0) ?: state.duration
                state = state.copy(currentTime = t, duration = d, playing = true, paused = false)
                emit(NativeEvents.timeupdate(t, d))
            }
            main.postDelayed(this, 250)
        }
    }

    private val listener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            when (playbackState) {
                Player.STATE_READY -> {
                    val d = player?.duration?.takeIf { it > 0 }?.div(1000.0) ?: 0.0
                    state = state.copy(duration = d)
                    emit(NativeEvents.durationchange(d))
                }
                Player.STATE_ENDED -> {
                    state = state.copy(playing = false, paused = true)
                    emit(NativeEvents.simple("ended"))
                }
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            state = state.copy(playing = false, paused = true)
            emit(NativeEvents.error(error.message ?: "播放失败"))
        }
    }

    fun attach(newPlayer: ExoPlayer) = runOnMain {
        detachPlayer()
        player = newPlayer
        newPlayer.volume = state.volume
        newPlayer.repeatMode = if (state.loop) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
        newPlayer.addListener(listener)
        // Service 启动有延迟：setSource / play 先于 attach 到达时在此补齐
        pendingSource?.let { applySource(newPlayer, it) }
        if (pendingPlay) {
            newPlayer.play()
            state = state.copy(playing = true, paused = false)
            emit(NativeEvents.simple("play"))
        }
        pendingSource = null
        pendingPlay = false
        main.removeCallbacks(ticker)
        main.postDelayed(ticker, 250)
    }

    fun detach() = runOnMain {
        detachPlayer()
        main.removeCallbacks(ticker)
    }

    fun setSource(url: String) = runOnMain {
        state = state.copy(sourceUrl = url, currentTime = 0.0, duration = 0.0)
        val p = player
        if (p == null) {
            pendingSource = url
            pendingPlay = false
        } else {
            applySource(p, url)
        }
    }

    fun play() = runOnMain {
        val p = player
        if (p == null) {
            pendingPlay = true
            return@runOnMain
        }
        p.play()
        state = state.copy(playing = true, paused = false)
        emit(NativeEvents.simple("play"))
    }

    fun pause() = runOnMain {
        player?.pause()
        state = state.copy(playing = false, paused = true)
        emit(NativeEvents.simple("pause"))
    }

    fun stop() = runOnMain {
        pendingSource = null
        pendingPlay = false
        player?.apply {
            stop()
            clearMediaItems()
        }
        state = state.copy(
            playing = false,
            paused = true,
            currentTime = 0.0,
            duration = 0.0,
            sourceUrl = "",
        )
        emit(NativeEvents.simple("pause"))
    }

    fun seekTo(positionMs: Long) = runOnMain {
        val ms = positionMs.coerceAtLeast(0)
        player?.seekTo(ms)
        state = state.copy(currentTime = ms / 1000.0)
        emit(NativeEvents.timeupdate(state.currentTime, state.duration))
    }

    fun seekBy(deltaMs: Long) = runOnMain {
        val p = player
        val base = p?.currentPosition ?: (state.currentTime * 1000).toLong()
        seekTo(base + deltaMs)
    }

    fun setVolume(v: Float) = runOnMain {
        val value = v.coerceIn(0f, 1f)
        state = state.copy(volume = value)
        player?.volume = value
    }

    fun setLoop(enabled: Boolean) = runOnMain {
        state = state.copy(loop = enabled)
        player?.repeatMode = if (enabled) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
    }

    /** 前端 new MediaMetadata({...}) 经 JsBridge 到达；封面异步下载后二次写回会话。 */
    fun setMetadata(json: String, baseUrl: String) {
        val obj = runCatching { JSONObject(json) }.getOrElse { return }
        title = obj.optString("title")
        artist = obj.optString("artist")
        album = obj.optString("album")
        val artworkUrl = obj.optString("artworkUrl")
        artworkBaseUrl = baseUrl
        applyMetadataToCurrentItem()

        if (artworkUrl.isBlank()) {
            artworkBytes = null
            return
        }
        val loader = artworkLoader?.takeIf { artworkBaseUrl == baseUrl } ?: ArtworkLoader(baseUrl)
        artworkLoader = loader
        scope.launch {
            val bytes = loader.load(artworkUrl) ?: return@launch
            artworkBytes = bytes
            runOnMain { applyMetadataToCurrentItem() }
        }
    }

    /** 锁屏 / 蓝牙的上一曲、下一曲：队列归前端 player.ts 管，原生只转命令。 */
    fun requestCommand(command: String) = runOnMain {
        commandSink?.invoke(command)
    }

    fun getSnapshot(): String = state.toEventJson()

    fun emitError(message: String) {
        state = state.copy(playing = false, paused = true)
        emit(NativeEvents.error(message))
    }

    private fun buildMediaMetadata(): MediaMetadata = MediaMetadata.Builder()
        .setTitle(title)
        .setArtist(artist)
        .setAlbumTitle(album)
        .apply {
            artworkBytes?.let {
                setArtworkData(it, MediaMetadata.PICTURE_TYPE_FRONT_COVER)
            }
        }
        .build()

    private fun applyMetadataToCurrentItem() = runOnMain {
        val p = player ?: return@runOnMain
        val item = p.currentMediaItem ?: return@runOnMain
        p.replaceMediaItem(
            p.currentMediaItemIndex,
            item.buildUpon().setMediaMetadata(buildMediaMetadata()).build(),
        )
    }

    private fun applySource(p: ExoPlayer, url: String) {
        p.setMediaItem(
            MediaItem.Builder()
                .setUri(url)
                .setMediaMetadata(buildMediaMetadata())
                .build(),
        )
        p.prepare()
    }

    private fun detachPlayer() {
        player?.removeListener(listener)
        player = null
    }

    private fun emit(json: String) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            eventSink?.invoke(json)
        } else {
            main.post { eventSink?.invoke(json) }
        }
    }

    private inline fun runOnMain(crossinline block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) block()
        else main.post { block() }
    }
}
