package com.yuzuki.app

import kotlinx.coroutines.flow.MutableSharedFlow

/** 前端退出登录 / token 失效时，从 WebView 侧通知 App 状态机（方案 §4、A5）。 */
object SessionEvents {
    val logout = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
}
