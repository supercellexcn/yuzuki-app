package com.yuzuki.app.playback

import com.yuzuki.app.data.MediaFile

/** 队列与循环模式的纯 Kotlin 部分，便于 JVM 断言（不依赖 ExoPlayer / Android）。 */
object QueueMath {
    /** MediaItem.mediaId 约定："RJxxxxxx/文件id"，服务侧据此还原播放记录。 */
    fun mediaId(rj: String, fileId: Long): String = "$rj/$fileId"

    /**
     * 循环只有两态：顺序播放（关闭循环）↔ 单曲循环。
     * 队列本身由 ExoPlayer 自行推进，顺序播完整个列表即停，不存在「列表循环」。
     * ALL 只是 PlaybackController 侧的历史残留值，出现时直接归到顺序播放。
     */
    fun cycleRepeat(mode: RepeatMode): RepeatMode = when (mode) {
        RepeatMode.OFF -> RepeatMode.ONE
        RepeatMode.ONE -> RepeatMode.OFF
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

    /**
     * 播放队列的作用域：与 [anchor] 同一个目录的其它可播放文件（不含子目录、不含父目录）。
     *
     * 一个作品文件夹里通常按「Track / 特典 / SE」等子目录分组，跨目录连播会把不同内容
     * 接在一起，因此队列严格锁在被点文件所在的那一层目录内。
     * 顺序沿用入参（即服务端 files 的 sort_order），这里只做筛选不做排序；
     * anchor 自身也走同一条过滤规则，所以结果里一定包含它（除非它本身不可播放）。
     */
    fun sameDir(files: List<MediaFile>, anchor: MediaFile): List<MediaFile> {
        val dir = anchor.dirOf()
        return files.filter { it.playable && it.dirOf() == dir }
    }
}
