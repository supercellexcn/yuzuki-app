package com.yuzuki.app.playback


import android.app.PendingIntent
import android.content.Intent
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.yuzuki.app.MainActivity

/**
 * 系统媒体控制的唯一载体（方案 §7）：
 *
 * - ExoPlayer 只由本服务持有，MediaSession 直接绑定这个 ExoPlayer；
 * - 整个播放队列作为 ExoPlayer playlist 下发，上一曲 / 下一曲 / 自动切歌 / 循环全部由
 *   ExoPlayer 自己推进，通知栏、锁屏、蓝牙与 App 内看到的必然是同一首；
 * - MediaSessionService 负责前台服务与媒体通知，不自绘任何通知 / 锁屏 UI。
 */
@UnstableApi
class PlaybackService : MediaSessionService() {
    private var player: ExoPlayer? = null
    private var session: MediaSession? = null
    private var agent: PlaybackAgent? = null

    override fun onCreate() {
        super.onCreate()

        // 离线 file:// 与在线 Range 请求都要能播，所以用 DefaultDataSource 包一层 HTTP
        val httpFactory = DefaultHttpDataSource.Factory()
            .setUserAgent("YuzukiAndroid/0.2")
            .setAllowCrossProtocolRedirects(false)
            .setConnectTimeoutMs(10_000)
            .setReadTimeoutMs(30_000)
        val dataSourceFactory = DefaultDataSource.Factory(this, httpFactory)

        // 在线媒体是几百 MB 的无损单文件，远距离 seek 需要重新拉一段数据。
        // 默认 LoadControl（50s 缓冲）在这种文件上容易在 seek 后长时间停在 BUFFERING，
        // 被 ExoPlayer 判成「Playback stuck buffering and not loading」直接报错；
        // 这里把回缓冲阈值和总缓冲都放大。
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                60_000,   // minBufferMs：先攒 60s 再起播
                180_000,  // maxBufferMs：最多缓存 180s
                1_000,    // bufferForPlaybackMs
                5_000,    // bufferForPlaybackAfterRebufferMs
            )
            .build()

        // 关键：FLAC 等无损文件常常不带 seek table（索引），远距离 seek 时
        // FlacExtractor 找不到锚点，会认为「已加载完」而不继续拉数据，
        // 表现出来就是「进度条点到后面 → 卡在缓冲 + Playback stuck buffering」。
        // 打开按码率估算的 seek（CBR seeking）后，可以用时间 × 码率估算字节偏移。
        val extractorsFactory = DefaultExtractorsFactory()
            .setConstantBitrateSeekingEnabled(true)

        val exo = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory, extractorsFactory))
            .setLoadControl(loadControl)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .setUsage(C.USAGE_MEDIA)
                    .build(),
                /* handleAudioFocus = */ true,
            )
            .setHandleAudioBecomingNoisy(true)
            .build()
        // 不要用 CLOSEST_SYNC：FLAC 通常没有 seek table，取不到同步点索引时会退化到 0，
        // 表现为「往进度条后面点就跳回开头」。远距离 seek 用默认 EXACT，靠 Range 取目标段。
        // NAS 是网络流，息屏后需要保持 WiFi 与 CPU 可用，否则会被挂起
        exo.setWakeMode(C.WAKE_MODE_NETWORK)
        player = exo

        agent = PlaybackAgent(this, exo).also { it.start() }

        session = MediaSession.Builder(this, exo)
            .setCallback(SessionCallback())
            .setSessionActivity(openPlayerIntent())
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onDestroy() {
        agent?.stop()
        agent = null
        session?.release()
        session = null
        player?.release()
        player = null
        super.onDestroy()
    }

    /** 点通知 / 锁屏卡片直接进全屏播放器，而不是回到上次停留的列表页。 */
    private fun openPlayerIntent(): PendingIntent {
        val intent = Intent(this, MainActivity::class.java).apply {
            action = ACTION_OPEN_PLAYER
            putExtra(MainActivity.EXTRA_OPEN_PLAYER, true)
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            this, 1, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    /**
     * 默认命令集不含「换队列」，而 App 内点播 / 播放列表换曲都要用；
     * 其余命令（上一首 / 下一首 / 循环 / 随机 / 音量 / 进度）显式放开给 MediaController。
     */
    private class SessionCallback : MediaSession.Callback {
        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
        ): MediaSession.ConnectionResult {
            val playerCommands = MediaSession.ConnectionResult.DEFAULT_PLAYER_COMMANDS
                .buildUpon()
                .addAll(
                    Player.COMMAND_CHANGE_MEDIA_ITEMS,
                    Player.COMMAND_SET_MEDIA_ITEM,
                    Player.COMMAND_SEEK_TO_NEXT,
                    Player.COMMAND_SEEK_TO_PREVIOUS,
                    Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
                    Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM,
                    Player.COMMAND_SEEK_TO_MEDIA_ITEM,
                    Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM,
                    Player.COMMAND_SET_REPEAT_MODE,
                    Player.COMMAND_SET_SHUFFLE_MODE,
                    Player.COMMAND_SET_VOLUME,
                )
                .build()
            return MediaSession.ConnectionResult.accept(
                MediaSession.ConnectionResult.DEFAULT_SESSION_AND_LIBRARY_COMMANDS,
                playerCommands,
            )
        }
    }

    private companion object {
        const val ACTION_OPEN_PLAYER = "com.yuzuki.app.action.OPEN_PLAYER"
    }
}
