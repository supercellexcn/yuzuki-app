package com.yuzuki.app

import android.app.Application
import com.yuzuki.app.data.AppContainer
import com.yuzuki.app.playback.PlaybackController
import kotlinx.coroutines.runBlocking

class YuzukiApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AppContainer.init(this)
        PlaybackController.init(this)
        // 本机偏好（外观 + 手势）不请求 /api/settings。这里同步读一次，让首帧就是用户选的配色，
        // 手势时长也立刻生效；两个文件都只有几十字节，耗时在毫秒级。
        runBlocking {
            runCatching { AppContainer.appearance.load() }
            runCatching { AppContainer.interaction.load() }
        }
    }
}
