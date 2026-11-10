package com.yuzuki.app.playback

import com.yuzuki.app.data.MediaFile
import com.yuzuki.app.data.Work

/** 循环模式：关闭 / 单曲循环 / 列表循环，与 ExoPlayer 的 repeatMode 一一对应。 */
enum class RepeatMode { OFF, ONE, ALL }

/** 当前曲目：rj 作品号 + 文件 + 作品信息（封面 / 声优 / 收藏都要用）。 */
data class PlayItem(val rj: String, val file: MediaFile, val work: Work?)

/**
 * UI 侧播放状态（由 PlaybackController 从 MediaController 投影而来）。
 * 播放器本身只在 PlaybackService 里存在一份，这里只是快照。
 */
data class PlayState(
    val current: PlayItem? = null,
    val queue: List<MediaFile> = emptyList(),
    /** 当前曲目在队列中的下标（ExoPlayer 的 playlist index，随机播放时也不变）。 */
    val queueIndex: Int = 0,
    val playing: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val volume: Float = 1f,
    val repeat: RepeatMode = RepeatMode.OFF,
    val shuffle: Boolean = false,
    /** 纯 UI 状态：全屏播放器是否展开，不进 MediaSession。 */
    val fullscreen: Boolean = false,
    val error: String? = null,
)
