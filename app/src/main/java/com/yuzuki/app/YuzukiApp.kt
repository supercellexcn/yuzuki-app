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
        // 外观取本机 DataStore（不请求 /api/settings）。这里同步读一次，让首帧就是用户选的颜色，
        // 避免冷启动先闪一下默认紫再变；文件只有几十字节，耗时在毫秒级。
        runBlocking { runCatching { AppContainer.appearance.load() } }
    }
}
