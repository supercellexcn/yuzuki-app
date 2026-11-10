package com.yuzuki.app

import android.app.Application

class YuzukiApp : Application() {
    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    companion object {
        lateinit var instance: YuzukiApp
            private set
    }
}
