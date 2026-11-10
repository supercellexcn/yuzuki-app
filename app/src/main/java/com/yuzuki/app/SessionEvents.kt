package com.yuzuki.app

import kotlinx.coroutines.flow.MutableSharedFlow

/** token 失效 / 主动退出登录时通知 App 状态机（方案 C2、A5）。 */
object SessionEvents {
    val logout = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
}
