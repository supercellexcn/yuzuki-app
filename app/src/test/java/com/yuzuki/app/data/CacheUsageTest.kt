package com.yuzuki.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/** 设置页缓存口径的 JVM 断言：统计 / 分类 / 格式化 / 清理 / 保留项，不依赖 Android。 */
class CacheUsageTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private fun filesDir() = File(tmp.root, "files")
    private fun cacheDir() = File(tmp.root, "cache")

    private fun write(path: String, size: Int, content: Byte = 0x41): File {
        val file = File(tmp.root, path)
        file.parentFile?.mkdirs()
        file.writeBytes(ByteArray(size) { content })
        return file
    }

    private fun labels(snapshot: CacheUsage.Snapshot) =
        snapshot.items.associate { it.label to it.bytes }

    @Test
    fun emptyDirectoriesReportZero() {
        filesDir().mkdirs()
        cacheDir().mkdirs()
        val snapshot = CacheUsage.measure(filesDir(), cacheDir())
        assertEquals(0L, snapshot.total)
        assertEquals(5, snapshot.items.size)
        assertTrue(snapshot.items.all { it.bytes == 0L })
    }

    @Test
    fun missingDirectoriesReportZeroWithoutThrowing() {
        val snapshot = CacheUsage.measure(filesDir(), cacheDir())
        assertEquals(0L, snapshot.total)
        assertEquals(0L, CacheUsage.clear(filesDir(), cacheDir()))
    }

    @Test
    fun categoriesMatchFileLayoutAndSumToTotal() {
        write("files/offline/works_1.json", 100)
        write("files/offline/settings.json", 50)
        write("files/offline/pending_plays.json", 999)
        write("files/offline/images/cover_A.bin", 200)
        write("files/offline/audio/RJ_1.bin", 300)
        write("cache/img_abc", 400)
        write("cache/artwork/RJ.img", 500)
        write("cache/other.tmp", 600)
        write("files/offline/stray.bin", 700)

        val snapshot = CacheUsage.measure(filesDir(), cacheDir())
        val byLabel = labels(snapshot)
        assertEquals(150L, byLabel["作品与列表数据快照"])
        assertEquals(600L, byLabel["封面与图片"])
        assertEquals(500L, byLabel["锁屏 / 通知栏封面"])
        assertEquals(300L, byLabel["离线音频"])
        assertEquals(1300L, byLabel["其它临时文件"])
        assertEquals(2850L, snapshot.total)
        assertEquals(snapshot.total, snapshot.items.sumOf { it.bytes })
    }

    @Test
    fun formatUsesBinaryUnitsWithBounds() {
        assertEquals("0 B", CacheUsage.format(0))
        assertEquals("512 B", CacheUsage.format(512))
        assertEquals("1.0 KB", CacheUsage.format(1024))
        assertEquals("1.5 KB", CacheUsage.format(1536))
        assertEquals("1.0 MB", CacheUsage.format(1024 * 1024))
        assertEquals("2.00 GB", CacheUsage.format(2L * 1024 * 1024 * 1024))
        assertEquals("0 B", CacheUsage.format(-99))
    }

    @Test
    fun nestedFilesAreCountedAndCleared() {
        write("files/offline/audio/nested/RJ_1.bin.part", 128)
        write("files/offline/images/sub/cover.bin", 64)
        val before = CacheUsage.measure(filesDir(), cacheDir())
        assertEquals(128L + 64L, before.total)
        assertEquals(before.total, CacheUsage.clear(filesDir(), cacheDir()))
        assertEquals(0L, CacheUsage.measure(filesDir(), cacheDir()).total)
    }

    @Test
    fun clearFreesEverythingCountedAndKeepsPendingPlays() {
        write("files/offline/works_1.json", 100)
        val pending = write("files/offline/pending_plays.json", 999, 0x5A.toByte())
        write("files/offline/images/cover_A.bin", 200)
        write("cache/img_abc", 400)
        write("cache/artwork/RJ.img", 500)
        val token = write("files/datastore/token.bin", 32, 0x7F.toByte())

        val before = CacheUsage.measure(filesDir(), cacheDir())
        val freed = CacheUsage.clear(filesDir(), cacheDir())

        assertEquals(1200L, before.total)
        assertEquals(before.total, freed)
        assertEquals(0L, CacheUsage.measure(filesDir(), cacheDir()).total)
        assertTrue(pending.isFile)
        assertEquals(999, pending.readBytes().size)
        assertEquals(0x5A.toByte(), pending.readBytes()[0])
        assertTrue(token.isFile)
        assertEquals(0x7F.toByte(), token.readBytes()[0])
        assertFalse(File(filesDir(), "offline/works_1.json").exists())
        assertFalse(File(cacheDir(), "img_abc").exists())
        assertFalse(File(cacheDir(), "artwork/RJ.img").exists())
    }

    @Test
    fun clearKeepsDirectorySkeletonForCacheWriters() {
        write("files/offline/images/cover_A.bin", 200)
        write("files/offline/audio/RJ_1.bin", 300)
        write("cache/artwork/RJ.img", 500)
        CacheUsage.clear(filesDir(), cacheDir())

        assertTrue(File(filesDir(), "offline/images").isDirectory)
        assertTrue(File(filesDir(), "offline/audio").isDirectory)
        assertTrue(File(cacheDir(), "artwork").isDirectory)
    }

    @Test
    fun secondClearIsNoOp() {
        write("files/offline/works_1.json", 100)
        write("cache/img_abc", 400)
        assertEquals(500L, CacheUsage.clear(filesDir(), cacheDir()))
        assertEquals(0L, CacheUsage.clear(filesDir(), cacheDir()))
    }
}
