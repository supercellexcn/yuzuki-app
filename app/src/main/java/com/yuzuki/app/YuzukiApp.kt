package com.yuzuki.app

import android.app.Application
import com.yuzuki.app.data.AppContainer
import com.yuzuki.app.playback.PlaybackController

class YuzukiApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AppContainer.init(this)
        PlaybackController.init(this)
    }
}
