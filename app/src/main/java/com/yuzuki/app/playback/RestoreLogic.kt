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

    /**
     * 恢复后进度该以谁为准。
     *
     * 恢复态刚把队列交给 Media3 时，播放器还没 READY、`currentPosition` 仍是 0；
     * 若照抄进 UI，进度条左边的已播放数字会先闪成 0 再跳回历史位置。
     * 因此只有「已 READY 且起始位置确实生效」才交还控制权，
     * 另外用 [elapsedMs] 兜底，避免异常场景下进度永久冻结。
     */
    fun shouldUsePlayerPosition(
        playerReady: Boolean,
        playerPositionMs: Long,
        startTargetMs: Long,
        elapsedMs: Long,
        timeoutMs: Long = 3_000L,
    ): Boolean {
        if (!playerReady) return elapsedMs > timeoutMs
        // 从 0 开始播（快照进度为 0）时，READY 即可信；否则要等跳到目标位置之后再接管
        if (startTargetMs <= 0L || playerPositionMs > 0L) return true
        return elapsedMs > timeoutMs
    }
}
