package com.yuzuki.app.playback

import android.app.PendingIntent
import android.content.Intent
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.yuzuki.app.MainActivity

/**
 * 原生播放服务（方案 §6.3）。播放在 Service 中，WebView 被冻结/销毁都不影响后台续播。
 * 媒体 URL 自带 ?token= 且支持 Range，无需自定义 Header；单一 MediaSession 换 MediaItem
 * 不重建会话，规避 web 端「换曲后通知栏控件消失」的 Chromium 缺陷。
 *
 * S4：用 ForwardingPlayer 把系统媒体按键的上一曲/下一曲转发到 WebView 的前端队列；
 * onConnect 显式开放这两个 player command，锁屏与蓝牙按键才会出现换曲按钮。
 */
@UnstableApi
class PlaybackService : MediaSessionService() {
    private var player: ExoPlayer? = null
    private var session: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        val httpFactory = DefaultHttpDataSource.Factory()
            .setUserAgent("YuzukiAndroid/0.1")
            // 禁止跨协议重定向，避免带 token 的媒体请求被带到站外
            .setAllowCrossProtocolRedirects(false)
            .setConnectTimeoutMs(10_000)
            .setReadTimeoutMs(30_000)

        val exo = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(httpFactory))
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .setUsage(C.USAGE_MEDIA)
                    .build(),
                true,
            )
            .setHandleAudioBecomingNoisy(true)
            .build()
        player = exo
        PlayerHolder.attach(exo)

        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        session = MediaSession.Builder(this, YuzukiPlayer(exo))
            .setCallback(SessionCallback())
            .setSessionActivity(openApp)
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onDestroy() {
        session?.release()
        session = null
        PlayerHolder.detach()
        player?.release()
        player = null
        super.onDestroy()
    }

    /** 队列由前端 player.ts 维护，原生只把换曲意图转过去。 */
    private class YuzukiPlayer(delegate: Player) : ForwardingPlayer(delegate) {
        override fun seekToNext() = PlayerHolder.requestCommand("next")

        override fun seekToPrevious() = PlayerHolder.requestCommand("prev")

        override fun getAvailableCommands(): Player.Commands =
            super.getAvailableCommands().buildUpon()
                .add(Player.COMMAND_SEEK_TO_NEXT)
                .add(Player.COMMAND_SEEK_TO_PREVIOUS)
                .build()
    }

    private class SessionCallback : MediaSession.Callback {
        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
        ): MediaSession.ConnectionResult {
            val playerCommands = MediaSession.ConnectionResult.DEFAULT_PLAYER_COMMANDS
                .buildUpon()
                .add(Player.COMMAND_SEEK_TO_NEXT)
                .add(Player.COMMAND_SEEK_TO_PREVIOUS)
                .build()
            return MediaSession.ConnectionResult.accept(
                MediaSession.ConnectionResult.DEFAULT_SESSION_AND_LIBRARY_COMMANDS,
                playerCommands,
            )
        }
    }
}
