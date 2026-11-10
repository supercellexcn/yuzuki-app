package com.yuzuki.app.web

import android.content.Context
import android.content.Intent
import android.util.Log
import android.webkit.JavascriptInterface
import com.yuzuki.app.SessionEvents
import com.yuzuki.app.data.HostPolicy
import com.yuzuki.app.playback.PlaybackService
import com.yuzuki.app.playback.PlayerHolder
import org.json.JSONObject

/** JS → Native 唯一入口（方案 §6.4）：方法名必须与 yuzuki-bridge.js 完全一致。 */
class JsBridge(
    context: Context,
    private val baseUrl: String,
) {
    private val appContext = context.applicationContext

    @JavascriptInterface
    fun setSource(url: String) {
        // 媒体 URL 带 ?token=，必须确保不会指向站外
        if (url.isBlank() || !HostPolicy.isAllowedUrl(url, baseUrl)) {
            PlayerHolder.emitError("媒体地址不在白名单内")
            return
        }
        ensureService()
        PlayerHolder.setSource(url)
    }

    @JavascriptInterface
    fun play() {
        ensureService()
        PlayerHolder.play()
    }

    @JavascriptInterface
    fun pause() = PlayerHolder.pause()

    @JavascriptInterface
    fun stop() = PlayerHolder.stop()

    @JavascriptInterface
    fun seekTo(positionMs: Long) = PlayerHolder.seekTo(positionMs)

    @JavascriptInterface
    fun seekBy(deltaMs: Long) = PlayerHolder.seekBy(deltaMs)

    @JavascriptInterface
    fun setVolume(v: Float) = PlayerHolder.setVolume(v)

    @JavascriptInterface
    fun setLoop(enabled: Boolean) = PlayerHolder.setLoop(enabled)

    @JavascriptInterface
    fun setMetadata(json: String) {
        runCatching {
            val src = JSONObject(json)
            PlayerHolder.setMetadata(
                JSONObject()
                    .put("title", src.optString("title"))
                    .put("artist", src.optString("artist"))
                    .put("album", src.optString("album"))
                    .put("artworkUrl", src.optString("artworkUrl"))
                    .toString(),
            )
        }.onFailure { PlayerHolder.emitError("元数据解析失败") }
    }

    /** WebView 重建时拉取最新状态快照。 */
    @JavascriptInterface
    fun getSnapshot(): String = PlayerHolder.getSnapshot()

    /** 调试用；脱敏，禁止 token 进入日志。 */
    @JavascriptInterface
    fun log(msg: String) {
        Log.d(TAG, msg.take(200).replace(TOKEN_PATTERN, "token=***"))
    }

    /** 前端 clearToken() 经 bridge.js 的 removeItem hook 调到这里（方案 §4、A5）。 */
    @JavascriptInterface
    fun logout() {
        PlayerHolder.stop()
        runCatching { appContext.stopService(Intent(appContext, PlaybackService::class.java)) }
        SessionEvents.logout.tryEmit(Unit)
    }

    private fun ensureService() {
        runCatching {
            appContext.startService(Intent(appContext, PlaybackService::class.java))
        }.onFailure { PlayerHolder.emitError("播放服务启动失败") }
    }

    private companion object {
        const val TAG = "YuzukiBridge"
        val TOKEN_PATTERN = Regex("(?i)token=[^&\\s]+")
    }
}
