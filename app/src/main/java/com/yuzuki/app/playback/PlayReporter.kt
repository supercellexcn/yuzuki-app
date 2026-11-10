package com.yuzuki.app.playback

import com.yuzuki.app.data.OfflineCache
import com.yuzuki.app.data.YuzukiApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.Instant

/**
 * 复刻 frontend/src/stores/player.ts 的播放记录与时长语义（方案 §8）：
 * 每次新文件开始播放 POST /api/plays；暂停/切歌/结束结算；播放中约 30s 增量上报；
 * play_id 晚到时不得写入新会话；离线时入本地补报队列，联网后重放。
 */
class PlayReporter(
    private val api: YuzukiApi,
    private val cache: OfflineCache,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lock = Any()

    private class Session(
        val rj: String,
        val fileId: Long?,
        val startedAt: String,
    ) {
        var playId: Long? = null
        var totalMs: Long = 0L
        var activeSince: Long? = System.currentTimeMillis()
        var lastSent: Int = 0
        var offline: Boolean = false
    }

    private var session: Session? = null

    fun start(rj: String, fileId: Long?) {
        synchronized(lock) { settleLocked() }
        val s = Session(rj, fileId, Instant.now().toString())
        synchronized(lock) { session = s }
        scope.launch {
            val id = api.recordPlay(rj, fileId).getOrNull()
            val report = synchronized(lock) {
                if (session !== s) null
                else if (id != null && id > 0) {
                    s.playId = id
                    val seconds = secondsLocked(s)
                    s.lastSent = seconds
                    id to seconds
                } else {
                    s.offline = true
                    null
                }
            }
            if (report != null) api.updateDuration(report.first, report.second)
        }
    }

    /** 播放中由 PlaybackAgent 每 30s 调用一次。 */
    fun tick() = sendIfNeeded()

    fun pause() {
        val report = synchronized(lock) {
            val s = session ?: return@synchronized null
            if (s.activeSince != null) {
                s.totalMs += System.currentTimeMillis() - s.activeSince!!
                s.activeSince = null
            }
            val seconds = secondsLocked(s)
            if (seconds > s.lastSent) {
                s.lastSent = seconds
                s.playId?.let { it to seconds }
            } else null
        }
        if (report != null) scope.launch { api.updateDuration(report.first, report.second) }
    }

    fun resume() {
        synchronized(lock) { session?.let { if (it.activeSince == null) it.activeSince = System.currentTimeMillis() } }
    }

    /** 暂停 / 切歌 / ended / error / stop 的统一结算。 */
    fun settle() {
        synchronized(lock) { settleLocked() }
    }

    suspend fun flushPendingSuspend(): Result<Int> = cache.flush(api)

    private fun settleLocked() {
        val s = session ?: return
        if (s.activeSince != null) {
            s.totalMs += System.currentTimeMillis() - s.activeSince!!
            s.activeSince = null
        }
        val seconds = secondsLocked(s)
        session = null
        if (s.playId != null && seconds > s.lastSent) {
            val id = s.playId!!
            scope.launch { api.updateDuration(id, seconds) }
        }
        if (s.offline) {
            cache.enqueue(OfflineCache.PendingPlay(s.rj, s.fileId, s.startedAt, seconds))
        }
    }

    private fun sendIfNeeded() {
        val report = synchronized(lock) {
            val s = session ?: return@synchronized null
            val seconds = secondsLocked(s)
            if (seconds <= s.lastSent) null
            else {
                s.lastSent = seconds
                s.playId?.let { it to seconds }
            }
        }
        if (report != null) scope.launch { api.updateDuration(report.first, report.second) }
    }

    private fun secondsLocked(s: Session): Int {
        val active = s.activeSince?.let { System.currentTimeMillis() - it } ?: 0L
        return ((s.totalMs + active) / 1000L).toInt().coerceAtLeast(0)
    }
}
