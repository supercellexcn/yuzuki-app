package com.yuzuki.app.playback

/**
 * 冷启动恢复判定，纯 Kotlin 便于 JVM 断言（不依赖 MediaController / Android）。
 * 与 QueueMath / NavLogic 一样，只放无副作用的分支逻辑。
 */
object RestoreLogic {
    /**
     * 只有「已连上服务 + 服务里没有队列 + 没在播 + UI 没有当前曲目」才恢复快照。
     * 后台续播、通知栏 / 锁屏卡片进来时服务里已有 playlist，绝不能被旧快照盖掉；
     * controller 还没连上时也不能判定，留给 connect 回调重试。
     */
    fun shouldRestore(
        connected: Boolean,
        mediaItemCount: Int,
        isPlaying: Boolean,
        hasCurrent: Boolean,
    ): Boolean = connected && !isPlaying && mediaItemCount <= 0 && !hasCurrent

    /** 快照下标可能因服务端重新扫描而越界；空队列返回 -1 表示不可恢复。 */
    fun clampIndex(index: Int, size: Int): Int =
        if (size <= 0) -1 else index.coerceIn(0, size - 1)
}
