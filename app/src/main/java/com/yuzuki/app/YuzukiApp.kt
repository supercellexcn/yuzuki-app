package com.yuzuki.app

import android.app.Application
import com.yuzuki.app.data.AppContainer

class YuzukiApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AppContainer.init(this)
    }
}
