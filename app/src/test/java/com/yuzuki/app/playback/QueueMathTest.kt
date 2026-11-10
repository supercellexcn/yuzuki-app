package com.yuzuki.app.playback

import com.yuzuki.app.data.MediaFile
import org.junit.Assert.assertEquals
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

    private fun file(id: Long, kind: String, isDir: Boolean) = MediaFile(
        id = id, filename = "$id", relPath = "dir/$id", ext = "", kind = kind,
        isDir = isDir, size = 0, sortOrder = id.toInt(),
    )
}
