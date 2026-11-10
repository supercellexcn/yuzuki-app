package com.yuzuki.app.playback

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.yuzuki.app.data.AppContainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 服务侧播放代理（方案 §6 / §7）：只关心「当前这首」的元数据、封面、播放记录与错误恢复，
 * 队列推进由 ExoPlayer 的 playlist 自己完成，因此通知栏 / 锁屏 / App 内的曲目天然一致。
 *
 * 所有 replaceMediaItem 只改 MediaMetadata，LocalConfiguration 不变，
 * ExoPlayer 会复用同一个 MediaSource，不会打断正在播放的音频。
 */
@UnstableApi
class PlaybackAgent(
    context: Context,
    private val player: ExoPlayer,
) : Player.Listener {

    private val main = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val artworkLoader = ArtworkLoader(context)

    private var artworkJob: Job? = null
    private var lastMediaId: String? = null
    private var retries = 0
    private var lastTickAt = 0L

    private val ticker = object : Runnable {
        override fun run() {
            if (player.isPlaying) {
                val now = System.currentTimeMillis()
                if (now - lastTickAt >= 30_000) {
                    lastTickAt = now
                    AppContainer.reporter.tick()
                }
            }
            main.postDelayed(this, 5_000)
        }
    }

    fun start() {
        player.addListener(this)
        main.postDelayed(ticker, 5_000)
    }

    fun stop() {
        player.removeListener(this)
        main.removeCallbacks(ticker)
        artworkJob?.cancel()
        scope.cancel()
        runCatching { AppContainer.reporter.settle() }
    }

    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
        val mediaId = mediaItem?.mediaId ?: return
        // 只换元数据（封面 / 时长）也会触发 transition，按 mediaId 去重避免重复上报
        if (mediaId == lastMediaId) return
        lastMediaId = mediaId
        retries = 0
        val parsed = parseMediaId(mediaId) ?: return
        AppContainer.reporter.start(parsed.first, parsed.second)
        loadArtwork(parsed.first, mediaId)
    }

    override fun onPlaybackStateChanged(playbackState: Int) {
        when (playbackState) {
            Player.STATE_READY -> {
                retries = 0
                applyDuration()
            }
            Player.STATE_ENDED -> AppContainer.reporter.settle()
        }
    }

    override fun onIsPlayingChanged(isPlaying: Boolean) {
        if (isPlaying) AppContainer.reporter.resume() else AppContainer.reporter.pause()
    }

    /** NAS 抖动 / 断网：先退避重试两次，仍失败才把错误交给 UI 与通知栏。 */
    override fun onPlayerError(error: PlaybackException) {
        AppContainer.reporter.settle()
        if (retries >= MAX_RETRIES || !isRetryable(error)) return
        val mediaId = player.currentMediaItem?.mediaId ?: return
        val delay = if (retries == 0) 2_000L else 5_000L
        retries++
        main.postDelayed({
            if (player.currentMediaItem?.mediaId == mediaId && player.playerError != null) {
                player.prepare()
            }
        }, delay)
    }

    // ---- 元数据 ----

    private fun applyDuration() {
        val duration = player.duration.takeIf { it > 0 && it != C.TIME_UNSET } ?: return
        val item = player.currentMediaItem ?: return
        if (item.mediaMetadata.durationMs == duration) return
        replaceMetadata(
            item,
            item.mediaMetadata.buildUpon().setDurationMs(duration).build(),
        )
    }

    private fun loadArtwork(rj: String, mediaId: String) {
        artworkJob?.cancel()
        val url = AppContainer.api.coverUrl(rj)
        artworkJob = scope.launch {
            val bytes = artworkLoader.load(rj, url)
            withContext(Dispatchers.Main) {
                val item = player.currentMediaItem ?: return@withContext
                // 代际校验：慢返回的旧封面绝不能盖到新曲上
                if (item.mediaId != mediaId) return@withContext
                replaceMetadata(
                    item,
                    item.mediaMetadata.buildUpon()
                        .setArtworkData(bytes, MediaMetadata.PICTURE_TYPE_FRONT_COVER)
                        .build(),
                )
            }
        }
    }

    private fun replaceMetadata(item: MediaItem, metadata: MediaMetadata) {
        val index = player.currentMediaItemIndex
        if (index < 0) return
        player.replaceMediaItem(index, item.buildUpon().setMediaMetadata(metadata).build())
    }

    private fun isRetryable(error: PlaybackException): Boolean = when (error.errorCode) {
        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT,
        PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS,
        PlaybackException.ERROR_CODE_IO_UNSPECIFIED,
        -> true
        else -> false
    }

    private companion object {
        const val MAX_RETRIES = 2

        /** mediaId 约定为 "RJxxxxxx/文件id"。 */
        fun parseMediaId(mediaId: String): Pair<String, Long?>? {
            val index = mediaId.lastIndexOf('/')
            if (index <= 0) return null
            val rj = mediaId.substring(0, index)
            val fileId = mediaId.substring(index + 1).toLongOrNull()
            return rj to fileId
        }
    }
}
