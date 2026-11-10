package com.yuzuki.app.playback

import android.os.Handler
import android.os.Looper
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer

/**
 * ExoPlayer 单例状态机（方案 §6.3 / §6.4）：原生是唯一真相源。
 * Service 持有 ExoPlayer 并 attach 到这里；WebView 经 JsBridge 下发指令，
 * 这里回推 6 类事件，播放中每 250ms 一次 timeupdate。
 */
@UnstableApi
object PlayerHolder {
    private val main = Handler(Looper.getMainLooper())

    @Volatile private var player: ExoPlayer? = null
    @Volatile private var state = NativeAudioState()
    @Volatile private var pendingSource: String? = null
    @Volatile private var pendingPlay = false

    /** 由当前 WebView 设置，重建时覆盖；销毁时置空。 */
    @Volatile var eventSink: ((String) -> Unit)? = null

    /** S4 的 MediaSession / ArtworkLoader 消费这里的最新元数据。 */
    @Volatile var metadataJson: String = ""

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

    fun setMetadata(json: String) {
        metadataJson = json
    }

    fun getSnapshot(): String = state.toEventJson()

    fun emitError(message: String) {
        state = state.copy(playing = false, paused = true)
        emit(NativeEvents.error(message))
    }

    private fun applySource(p: ExoPlayer, url: String) {
        p.setMediaItem(MediaItem.fromUri(url))
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
