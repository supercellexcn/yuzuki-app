package com.yuzuki.app.data

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class PlaybackSnapshotTest {
    private fun file(id: Long, name: String, kind: String = "audio", dir: Boolean = false) = MediaFile(
        id = id,
        filename = name,
        relPath = name,
        ext = name.substringAfterLast('.', ""),
        kind = kind,
        isDir = dir,
        size = 1024L,
        sortOrder = id.toInt(),
    )

    private fun sample() = PlaybackSnapshot(
        rj = "RJ123456",
        fileId = 22L,
        positionMs = 75_000L,
        durationMs = 300_000L,
        queue = listOf(file(11L, "01.flac"), file(22L, "02.flac"), file(33L, "03.flac")),
        title = "测试作品",
        circle = "社团A",
        actors = listOf("声优1", "声优2"),
        coverUrl = "/api/cover/RJ123456",
    )

    @Test
    fun 往返编解码保留队列与作品信息() {
        val restored = PlaybackSnapshot.from(JSONObject(sample().toJson().toString()))
        assertNotNull(restored)
        assertEquals("RJ123456", restored!!.rj)
        assertEquals(22L, restored.fileId)
        assertEquals(75_000L, restored.positionMs)
        assertEquals(300_000L, restored.durationMs)
        assertEquals(listOf(11L, 22L, 33L), restored.queue.map { it.id })
        assertEquals("02.flac", restored.queue[1].filename)
        assertEquals("测试作品", restored.title)
        assertEquals(listOf("声优1", "声优2"), restored.actors)
        assertEquals(3, restored.toWork().fileCount)
    }

    @Test
    fun 最小Work带齐播放页标题社团声优封面() {
        val work = sample().toWork()
        assertEquals("RJ123456", work.rjCode)
        assertEquals("测试作品", work.title)
        assertEquals("社团A", work.circle)
        assertEquals("/api/cover/RJ123456", work.coverUrl)
        assertTrue(work.tags.isEmpty())
    }

    @Test
    fun 空标题时退化为作品号() {
        assertEquals("RJ123456", sample().copy(title = "").toWork().title)
    }

    @Test
    fun 非法作品号与文件id不恢复() {
        assertNull(sample().copy(rj = "").sanitized())
        assertNull(sample().copy(rj = "  ").sanitized())
        assertNull(sample().copy(fileId = 0L).sanitized())
        assertNull(sample().copy(fileId = -1L).sanitized())
    }

    @Test
    fun 空队列或找不到当前文件不恢复() {
        assertNull(sample().copy(queue = emptyList()).sanitized())
        assertNull(sample().copy(queue = listOf(file(11L, "01.flac"))).sanitized())
    }

    @Test
    fun 负进度与负时长都夹紧到零() {
        val s = sample().copy(positionMs = -500L, durationMs = -1L).sanitized()!!
        assertEquals(0L, s.positionMs)
        assertEquals(0L, s.durationMs)
    }

    @Test
    fun 进度到结尾按从头开始() {
        // 播完 / 越界都归零，避免恢复出来停在结尾、点播放直接跳下一首
        assertEquals(0L, sample().copy(positionMs = 999_999L).sanitized()!!.positionMs)
        assertEquals(0L, sample().copy(positionMs = 300_000L).sanitized()!!.positionMs)
        // 结尾之前仍按原位置恢复
        assertEquals(299_000L, sample().copy(positionMs = 299_000L).sanitized()!!.positionMs)
    }

    @Test
    fun 时长为零时进度原样保留() {
        assertEquals(75_000L, sample().copy(durationMs = 0L).sanitized()!!.positionMs)
    }

    @Test
    fun 目录项与不可播放项从队列过滤() {
        val json = JSONObject().apply {
            put("rj", "RJ1")
            put("file_id", 3L)
            put("position_ms", 10L)
            put("queue", JSONArray().apply {
                put(sample().queue[0].let { JSONObject().put("id", it.id).put("kind", "audio") })
                put(JSONObject().put("id", 2L).put("filename", "子目录").put("kind", "dir").put("is_dir", 1))
                put(JSONObject().put("id", 3L).put("filename", "03.txt").put("kind", "other"))
            })
        }
        // 3 号文件被判为不可播放，只剩 1 号，找不到当前文件 → 整体不恢复
        assertNull(PlaybackSnapshot.from(json))
    }

    @Test
    fun 损坏JSON与缺字段返回null() {
        assertNull(PlaybackSnapshot.from(JSONObject("{}")))
        assertNull(PlaybackSnapshot.from(JSONObject().put("rj", "RJ1")))
        assertNull(PlaybackSnapshot.from(JSONObject().put("rj", "RJ1").put("file_id", 1L)))
    }

    @Test
    fun 文件读写往返覆盖与清除() {
        val dir = Files.createTempDirectory("playback-snap").toFile()
        try {
            assertNull(PlaybackSnapshotFile.read(dir))
            PlaybackSnapshotFile.write(dir, sample())
            assertEquals(75_000L, PlaybackSnapshotFile.read(dir)!!.positionMs)
            PlaybackSnapshotFile.write(dir, sample().copy(positionMs = 120_000L))
            assertEquals(120_000L, PlaybackSnapshotFile.read(dir)!!.positionMs)
            assertFalse(File(dir, "state.json.tmp").exists())
            PlaybackSnapshotFile.clear(dir)
            assertNull(PlaybackSnapshotFile.read(dir))
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun 快照文件内容损坏时按无快照处理() {
        val dir = Files.createTempDirectory("playback-snap").toFile()
        try {
            dir.mkdirs()
            File(dir, "state.json").writeText("{ broken")
            assertNull(PlaybackSnapshotFile.read(dir))
        } finally {
            dir.deleteRecursively()
        }
    }
}
