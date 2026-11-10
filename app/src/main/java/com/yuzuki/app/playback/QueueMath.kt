package com.yuzuki.app.playback

import com.yuzuki.app.data.MediaFile

/** 队列与循环模式的纯 Kotlin 部分，便于 JVM 断言（不依赖 ExoPlayer / Android）。 */
object QueueMath {
    /** MediaItem.mediaId 约定："RJxxxxxx/文件id"，服务侧据此还原播放记录。 */
    fun mediaId(rj: String, fileId: Long): String = "$rj/$fileId"

    /** 单按钮三态循环：单曲循环 → 列表循环 → 关闭 → 单曲循环。 */
    fun cycleRepeat(mode: RepeatMode): RepeatMode = when (mode) {
        RepeatMode.OFF -> RepeatMode.ONE
        RepeatMode.ONE -> RepeatMode.ALL
        RepeatMode.ALL -> RepeatMode.OFF
    }

    fun nextIndex(ids: List<Long>, currentId: Long): Int {
        val index = ids.indexOf(currentId)
        return if (index >= 0 && index < ids.lastIndex) index + 1 else -1
    }

    fun prevIndex(ids: List<Long>, currentId: Long): Int {
        val index = ids.indexOf(currentId)
        return if (index > 0) index - 1 else -1
    }

    fun playable(files: List<MediaFile>): List<MediaFile> = files.filter { it.playable }
}
