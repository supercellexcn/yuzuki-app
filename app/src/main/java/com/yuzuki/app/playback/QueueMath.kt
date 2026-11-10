package com.yuzuki.app.playback

import com.yuzuki.app.data.MediaFile

/** 队列计算的纯 Kotlin 部分，便于 JVM 断言（不依赖 ExoPlayer / Android）。 */
object QueueMath {
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
