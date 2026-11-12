package com.yuzuki.app.data

import android.content.Context
import java.io.File

/**
 * 播放位置快照的本机存储入口。
 *
 * 目录选 filesDir/playback/，刻意不放 offline/ 也不放 cacheDir：
 * 设置页「清除缓存」（CacheUsage.measure / clear）只统计和删除这两处，
 * 播放位置属于用户状态、不该被一键清掉，也就不必改动缓存口径与它的单测。
 */
class PlaybackStore(context: Context) {
    private val dir = File(context.applicationContext.filesDir, "playback")

    fun load(): PlaybackSnapshot? = PlaybackSnapshotFile.read(dir)

    fun save(snapshot: PlaybackSnapshot) = PlaybackSnapshotFile.write(dir, snapshot)

    fun clear() = PlaybackSnapshotFile.clear(dir)
}
