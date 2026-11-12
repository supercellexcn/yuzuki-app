package com.yuzuki.app.data

import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * 本机播放位置快照：只记「上次播到哪个作品的哪个文件、第几秒」以及当时的可播放队列，
 * 全部落盘在 App 私有目录，不写服务端任何接口、不新增 /api 调用。
 *
 * 纯 Kotlin + org.json，不依赖 Android Context，便于 JVM 单测覆盖编解码与脏数据校验。
 * 恢复时只还原 UI 状态与队列，不自动开播；用户点播放才把 startPositionMs 下发给 Media3。
 */
data class PlaybackSnapshot(
    val rj: String,
    val fileId: Long,
    val positionMs: Long,
    val durationMs: Long = 0L,
    val queue: List<MediaFile> = emptyList(),
    val title: String = "",
    val circle: String = "",
    val actors: List<String> = emptyList(),
    val coverUrl: String = "",
) {
    /** 恢复用的最小 Work：只带播放页标题 / 封面 / 声优需要的字段，其余留空。 */
    fun toWork(): Work = Work(
        id = -1L,
        rjCode = rj,
        title = title.ifBlank { rj },
        circle = circle,
        releaseDate = "",
        series = "",
        salesCount = null,
        playCount = 0,
        fileCount = queue.size,
        folderPath = "",
        addedAt = "",
        updatedAt = "",
        coverUrl = coverUrl,
        actors = actors,
        tags = emptyList(),
    )

    fun toJson(): JSONObject = JSONObject()
        .put("v", 1)
        .put("rj", rj)
        .put("file_id", fileId)
        .put("position_ms", positionMs)
        .put("duration_ms", durationMs)
        .put("title", title)
        .put("circle", circle)
        .put("cover_url", coverUrl)
        .put("actors", JSONArray().apply { actors.forEach { put(it) } })
        .put("queue", JSONArray().apply { queue.forEach { put(it.toJson()) } })

    /** 校验并夹紧：脏数据一律不恢复，避免把进度条拉到无效位置或恢复出空队列。 */
    fun sanitized(): PlaybackSnapshot? {
        if (rj.isBlank() || fileId <= 0L) return null
        if (queue.isEmpty() || queue.none { it.id == fileId }) return null
        val duration = durationMs.coerceAtLeast(0L)
        val raw = positionMs.coerceAtLeast(0L)
        // 播到结尾的记录按「从头开始」处理：否则恢复出来就是结尾，点播放直接跳下一首
        val position = when {
            duration <= 0L -> raw
            raw >= duration -> 0L
            else -> raw
        }
        return copy(positionMs = position, durationMs = duration)
    }

    companion object {
        /** 从磁盘 JSON 还原；任何字段缺失 / 损坏都返回 null，调用方当作没有快照处理。 */
        fun from(o: JSONObject): PlaybackSnapshot? = runCatching {
            val arr = o.optJSONArray("queue") ?: JSONArray()
            val files = (0 until arr.length()).mapNotNull { i ->
                arr.optJSONObject(i)?.let { Models.mediaFile(it) }
            }.filter { it.playable }
            val actors = o.optJSONArray("actors")?.let { a ->
                (0 until a.length()).mapNotNull { a.optString(it, "").takeIf(String::isNotBlank) }
            } ?: emptyList()
            PlaybackSnapshot(
                rj = o.optString("rj", ""),
                fileId = o.optLong("file_id", -1L),
                positionMs = o.optLong("position_ms", 0L),
                durationMs = o.optLong("duration_ms", 0L),
                queue = files,
                title = o.optString("title", ""),
                circle = o.optString("circle", ""),
                actors = actors,
                coverUrl = o.optString("cover_url", ""),
            ).sanitized()
        }.getOrNull()

        private fun MediaFile.toJson(): JSONObject = JSONObject()
            .put("id", id)
            .put("filename", filename)
            .put("rel_path", relPath)
            .put("ext", ext)
            .put("kind", kind)
            .put("is_dir", if (isDir) 1 else 0)
            .put("size", size)
            .put("sort_order", sortOrder)
    }
}

/**
 * 快照文件读写，只依赖 java.io.File 便于 JVM 断言。
 * 先写 .tmp 再 rename，避免写到一半被系统杀进程导致快照损坏。
 */
object PlaybackSnapshotFile {
    private const val NAME = "state.json"

    fun read(dir: File): PlaybackSnapshot? = runCatching {
        val file = File(dir, NAME)
        if (!file.isFile) return null
        PlaybackSnapshot.from(JSONObject(file.readText()))
    }.getOrNull()

    fun write(dir: File, snapshot: PlaybackSnapshot) {
        runCatching {
            dir.mkdirs()
            val target = File(dir, NAME)
            val tmp = File(dir, "$NAME.tmp")
            tmp.writeText(snapshot.toJson().toString())
            if (!tmp.renameTo(target)) {
                tmp.copyTo(target, overwrite = true)
                tmp.delete()
            }
        }
    }

    fun clear(dir: File) {
        runCatching {
            File(dir, NAME).delete()
            File(dir, "$NAME.tmp").delete()
        }
    }
}
