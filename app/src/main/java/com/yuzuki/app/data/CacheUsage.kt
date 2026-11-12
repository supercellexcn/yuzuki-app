package com.yuzuki.app.data

import java.io.File
import java.nio.file.Files
import java.util.Locale

/**
 * 本机缓存用量统计与清理（设置页唯一数据源）。
 *
 * 口径严格一致：`measure` 计入的文件就是 `clear` 删除的文件，清完必然显示 0 B。
 * 只依赖 java.io.File，便于 JVM 单测直接构造临时目录断言，不依赖 Android Context。
 *
 * 计入范围（都是「从服务端缓存到本地」的内容）：
 *  - filesDir/offline：作品 / 详情 / 收藏 / 历史 / 设置的 JSON 快照、images/ 封面、audio/ 离线音频
 *  - cacheDir：img_* 卡片封面原图、artwork/ 锁屏通知封面、其它临时文件
 *
 * 保留：pending_plays.json（未上报的播放记录，删掉会丢数据）、filesDir 下其它内容
 * （DataStore token / 偏好等）、服务端数据本身。清理全程不调用任何服务端接口。
 */
object CacheUsage {
    private const val OFFLINE_DIR = "offline"
    private const val PENDING_PLAYS = "pending_plays.json"

    private const val KB = 1024L
    private const val MB = KB * KB
    private const val GB = MB * KB

    data class Item(val label: String, val bytes: Long)

    data class Snapshot(val items: List<Item>) {
        val total: Long get() = items.sumOf { it.bytes }
    }

    /** 分类顺序固定，UI 直接遍历；0 值分类由 UI 隐藏。 */
    fun measure(filesDir: File, cacheDir: File): Snapshot {
        val offline = File(filesDir, OFFLINE_DIR)
        val pending = File(offline, PENDING_PLAYS).takeIf { it.isFile }?.length() ?: 0L

        val data = sizeOfRootFiles(offline) {
            it.extension.equals("json", ignoreCase = true) && it.name != PENDING_PLAYS
        }
        val images = recursiveSize(File(offline, "images"))
        val audio = recursiveSize(File(offline, "audio"))
        val diskImages = sizeOfRootFiles(cacheDir) { it.name.startsWith("img_") }
        val artwork = recursiveSize(File(cacheDir, "artwork"))

        // 未归类的文件（offline 根层非 JSON、其它子目录、cacheDir 其它文件）兜底计入「其它」，
        // 保证各类之和恒等于可清理总量，清完显示 0 B。
        val counted = recursiveSize(offline) - pending + recursiveSize(cacheDir)
        val other = (counted - (data + images + audio + diskImages + artwork)).coerceAtLeast(0L)

        return Snapshot(
            listOf(
                Item("作品与列表数据快照", data),
                Item("封面与图片", images + diskImages),
                Item("锁屏 / 通知栏封面", artwork),
                Item("离线音频", audio),
                Item("其它临时文件", other),
            ),
        )
    }

    /** 删除同口径的全部缓存文件，返回实际释放的字节数；目录骨架保留供缓存类继续写入。 */
    fun clear(filesDir: File, cacheDir: File): Long {
        val before = measure(filesDir, cacheDir).total
        deleteContents(File(filesDir, OFFLINE_DIR), preserve = setOf(PENDING_PLAYS))
        deleteContents(cacheDir)
        return (before - measure(filesDir, cacheDir).total).coerceAtLeast(0L)
    }

    fun format(bytes: Long): String {
        val v = bytes.coerceAtLeast(0L)
        return when {
            v < KB -> "$v B"
            v < MB -> "%.1f KB".format(Locale.US, v / KB.toDouble())
            v < GB -> "%.1f MB".format(Locale.US, v / MB.toDouble())
            else -> "%.2f GB".format(Locale.US, v / GB.toDouble())
        }
    }

    private fun sizeOfRootFiles(dir: File, predicate: (File) -> Boolean): Long {
        if (!dir.isDirectory) return 0L
        return dir.listFiles()?.asSequence()
            ?.filter { it.isFile && !isLink(it) && predicate(it) }
            ?.sumOf { it.length() } ?: 0L
    }

    private fun recursiveSize(dir: File): Long {
        if (isLink(dir)) return 0L
        if (dir.isFile) return dir.length()
        if (!dir.isDirectory) return 0L
        return dir.listFiles()?.asSequence()?.sumOf { recursiveSize(it) } ?: 0L
    }

    /** 只删文件、保留目录结构：OfflineCache / ArtworkLoader 持有目录 File 引用，删掉会导致后续写入失败。 */
    private fun deleteContents(dir: File, preserve: Set<String> = emptySet()) {
        if (isLink(dir)) {
            dir.delete()
            return
        }
        if (!dir.isDirectory) return
        dir.listFiles()?.forEach { child ->
            if (child.name in preserve) return@forEach
            if (isLink(child)) {
                child.delete()
                return@forEach
            }
            if (child.isDirectory) deleteContents(child) else child.delete()
        }
    }

    private fun isLink(file: File): Boolean = runCatching {
        Files.isSymbolicLink(file.toPath())
    }.getOrDefault(false)
}
