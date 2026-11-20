package com.yuzuki.app.playback

import com.yuzuki.app.data.MediaFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QueueMathTest {
    @Test
    fun next与prev在边界不越界() {
        val ids = listOf(1L, 2L, 3L)
        assertEquals(1, QueueMath.nextIndex(ids, 1L))
        assertEquals(2, QueueMath.nextIndex(ids, 2L))
        assertEquals(-1, QueueMath.nextIndex(ids, 3L))
        assertEquals(-1, QueueMath.nextIndex(ids, 99L))
        assertEquals(-1, QueueMath.prevIndex(ids, 1L))
        assertEquals(0, QueueMath.prevIndex(ids, 2L))
        assertEquals(-1, QueueMath.prevIndex(emptyList(), 1L))
    }

    @Test
    fun 可播放队列只含非目录的音频与视频() {
        val files = listOf(
            file(1, "audio", false),
            file(2, "video", false),
            file(3, "image", false),
            file(4, "audio", true),
            file(5, "other", false),
        )
        assertEquals(listOf(1L, 2L), QueueMath.playable(files).map { it.id })
    }

    @Test
    fun 循环模式两态在顺序与单曲之间往返() {
        assertEquals(RepeatMode.ONE, QueueMath.cycleRepeat(RepeatMode.OFF))
        assertEquals(RepeatMode.OFF, QueueMath.cycleRepeat(RepeatMode.ONE))
        // ALL 是历史残留值（已取消列表循环），点一次直接回到顺序播放
        assertEquals(RepeatMode.OFF, QueueMath.cycleRepeat(RepeatMode.ALL))
    }

    @Test
    fun mediaId可还原作品号与文件id() {
        assertEquals("RJ01234567/42", QueueMath.mediaId("RJ01234567", 42))
    }

    @Test
    fun 队列按所选文件所在目录收敛() {
        val files = listOf(
            path(1, "01.flac", "Track/01.flac"),
            path(2, "02.flac", "Track/02.flac"),
            path(3, "cover.jpg", "Track/cover.jpg", kind = "image"),
            path(4, "04.flac", "Track/Extra/04.flac"),
            path(5, "05.flac", "Bonus/05.flac"),
        )
        // Track 层只取自己那层，图片被排除
        assertEquals(listOf(1L, 2L), QueueMath.sameDir(files, files[0]).map { it.id })
        // 子目录自成一组，不会并回父目录
        assertEquals(listOf(4L), QueueMath.sameDir(files, files[3]).map { it.id })
        assertEquals(listOf(5L), QueueMath.sameDir(files, files[4]).map { it.id })
    }

    @Test
    fun 根目录文件只与根目录文件同组() {
        val files = listOf(
            path(1, "a.mp3", "a.mp3"),
            path(2, "b.mp3", "b.mp3"),
            path(3, "c.mp3", "sub/c.mp3"),
        )
        assertEquals(listOf(1L, 2L), QueueMath.sameDir(files, files[1]).map { it.id })
    }

    @Test
    fun 同目录内保持入参顺序且跳过目录项() {
        val files = listOf(
            path(9, "z.flac", "D/z.flac"),
            path(8, "子目录", "D/子目录", kind = "dir", isDir = true),
            path(7, "a.flac", "D/a.flac"),
        )
        // 不重排：服务端 sort_order 已是播放顺序，这里只筛选
        assertEquals(listOf(9L, 7L), QueueMath.sameDir(files, files[2]).map { it.id })
    }

    @Test
    fun 手动排序把元素搬到目标位置并保持其它顺序() {
        val ids = listOf(1L, 2L, 3L, 4L)
        assertEquals(listOf(2L, 3L, 1L, 4L), QueueMath.reorder(ids, 0, 2))
        assertEquals(listOf(1L, 4L, 2L, 3L), QueueMath.reorder(ids, 3, 1))
        // 原地不动 / 越界都原样返回，调用方可以直接赋回状态
        assertEquals(ids, QueueMath.reorder(ids, 1, 1))
        assertEquals(ids, QueueMath.reorder(ids, -1, 2))
        assertEquals(ids, QueueMath.reorder(ids, 0, 9))
        assertEquals(emptyList<Long>(), QueueMath.reorder(emptyList<Long>(), 0, 1))
    }

    @Test
    fun 排序序列逐项搬位后与目标顺序一致() {
        val cases = listOf(
            listOf(1L, 2L, 3L, 4L) to listOf(4L, 1L, 3L, 2L),
            listOf(1L, 2L, 3L, 4L) to listOf(2L, 1L, 4L, 3L),
            listOf(1L, 2L, 3L, 4L) to listOf(3L, 2L, 1L, 4L),
            listOf(5L) to listOf(5L),
        )
        cases.forEach { (current, target) ->
            val plan = QueueMath.movePlan(current, target)
            val work = current.toMutableList()
            // 按 ExoPlayer moveMediaItem(from, to) 的语义重放：每步都基于上一步的结果
            plan.forEach { (from, to) -> work.add(to, work.removeAt(from)) }
            assertEquals(target, work)
            // 最少步数：每一步都至少让一个位置就位，不会无限搬
            assertTrue(plan.size <= current.size)
        }
    }

    @Test
    fun 顺序没变或元素集合不同时不下发搬位() {
        assertEquals(emptyList<Pair<Int, Int>>(), QueueMath.movePlan(listOf(1L, 2L), listOf(1L, 2L)))
        // 队列被换过（不同作品 / 少了一首）：放弃排序，别把播放器改成别的队列
        assertEquals(emptyList<Pair<Int, Int>>(), QueueMath.movePlan(listOf(1L, 2L), listOf(1L, 3L)))
        assertEquals(emptyList<Pair<Int, Int>>(), QueueMath.movePlan(listOf(1L, 2L), listOf(1L, 2L, 3L)))
        assertEquals(emptyList<Pair<Int, Int>>(), QueueMath.movePlan(emptyList(), emptyList()))
    }

    private fun file(id: Long, kind: String, isDir: Boolean) = MediaFile(
        id = id, filename = "$id", relPath = "dir/$id", ext = "", kind = kind,
        isDir = isDir, size = 0, sortOrder = id.toInt(),
    )
    private fun path(
        id: Long,
        name: String,
        relPath: String,
        kind: String = "audio",
        isDir: Boolean = false,
    ) = MediaFile(
        id = id, filename = name, relPath = relPath, ext = name.substringAfterLast('.', ""),
        kind = kind, isDir = isDir, size = 0, sortOrder = id.toInt(),
    )
}
