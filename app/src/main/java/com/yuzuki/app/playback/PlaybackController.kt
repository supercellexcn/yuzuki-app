package com.yuzuki.app.playback

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.core.content.ContextCompat
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.yuzuki.app.data.AppContainer
import com.yuzuki.app.data.MediaFile
import com.yuzuki.app.data.PlaybackSnapshot
import com.yuzuki.app.data.PlaybackStore
import com.yuzuki.app.data.Work
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * UI 侧播放控制器（方案 §7 架构图里的 PlaybackController）：
 * 通过 [MediaController] 连接 [PlaybackService]，所有播放指令都下发给服务，
 * Activity 里不持有任何播放器实例，因此页面重建 / 息屏 / 切 App 都不会中断播放。
 *
 * 播放队列以 ExoPlayer playlist 的形式下发，随机 / 循环 / 自动切歌由 ExoPlayer 推进，
 * 这里只是把 controller 的状态投影成 UI 需要的 [PlayState]。
 */
@UnstableApi
object PlaybackController {
    private val main = Handler(Looper.getMainLooper())
    private val _state = MutableStateFlow(PlayState())
    val state: StateFlow<PlayState> = _state

    private var appContext: Context? = null
    private var future: ListenableFuture<MediaController>? = null
    private var controller: MediaController? = null
    private var pendingAction: ((MediaController) -> Unit)? = null
    private var currentWork: Work? = null

    // ---- 本机播放位置 ----
    private var store: PlaybackStore? = null
    /** 启动时读到的快照，等第一次进播放页再消费。 */
    private var pendingSnapshot: PlaybackSnapshot? = null
    private var restoreConsumed = false
    private var restoreRequested = false
    /** 已还原 UI 但还没把队列下发给 Media3；此时任何播放器回调都不得覆盖进度。 */
    private var pendingStart = false
    /**
     * 恢复态已把队列下发、但播放器还没 READY。
     * 这段时间 c.currentPosition 仍是 0，若照抄进 state，进度条会先闪成 0 再跳回快照位置。
     */
    private var awaitingStartPosition = false
    /** 恢复时要跳到的目标毫秒，用于判断播放器是否已经把起始位置吃进去。 */
    private var startTargetMs = 0L
    private var startRequestedAt = 0L
    private var lastSaveAt = 0L

    private const val SAVE_INTERVAL_MS = 30_000L

    /** 应用级初始化，只保存 Context + 读一次本机快照，不建连接（首次播放或 UI 进场时才 bind 服务）。 */
    fun init(context: Context) {
        if (appContext == null) {
            val ctx = context.applicationContext
            appContext = ctx
            store = PlaybackStore(ctx)
            pendingSnapshot = store?.load()
            main.postDelayed(ticker, 250)
        }
    }

    /** UI 进场调用：提前连上服务，重建 Activity 后仍能直接读到正在播放的曲目。 */
    fun warmUp() = connect()

    // ---- UI 指令 ----

    fun play(rj: String, file: MediaFile, work: Work?, queue: List<MediaFile> = emptyList()) {
        // 用户主动点播后，本次会话不再恢复旧快照，也不允许旧快照覆盖刚选的曲目
        restoreConsumed = true
        pendingSnapshot = null
        pendingStart = false
        // 这是用户自己新选的曲目，进度就是 0，不需要再保护快照位置
        awaitingStartPosition = false
        startTargetMs = 0L
        val state = _state.value
        if (state.current?.rj == rj && state.current.file.id == file.id) {
            toggle()
            return
        }
        // queue 是「候选池」（详情页传的整棵文件树的可播放项），实际队列以被点文件
        // 所在目录为界：同一作品里 Track / 特典 / SE 常分目录，跨目录连播会把内容接错
        val pool = QueueMath.playable(queue)
        val scoped = QueueMath.sameDir(pool, file).ifEmpty {
            // 调用方只给了单文件：同作品当前队列也按目录收敛一次，收敛不到就只播这一首
            if (state.current?.rj == rj) QueueMath.sameDir(state.queue, file) else emptyList()
        }
        val list = scoped.ifEmpty { listOf(file) }
        val index = list.indexOfFirst { it.id == file.id }.coerceAtLeast(0)
        currentWork = work
        _state.value = state.copy(
            current = PlayItem(rj, file, work),
            queue = list,
            queueIndex = index,
            playing = true,
            positionMs = 0L,
            durationMs = 0L,
            error = null,
        )
        val items = buildItems(rj, work, list)
        withController { c ->
            c.setMediaItems(items, index, 0L)
            c.prepare()
            c.play()
        }
    }

    fun toggle() {
        if (_state.value.current == null) return
        if (_state.value.playing) pause() else resume()
    }

    /** 点暂停立刻记一次，配合播放中的 30s 节拍兜底「多任务划掉 / 系统回收」这类无回调结束。 */
    fun pause() {
        maybeSave(force = true)
        withController { it.pause() }
    }

    fun resume() {
        val state = _state.value
        if (pendingStart && state.current != null) {
            // 恢复态：此刻才把队列和起始进度下发，播放记录也从这次真正播放开始
            startRestored(state.queueIndex, state.positionMs)
        } else {
            withController { it.play() }
        }
    }

    fun next() {
        val state = _state.value
        if (pendingStart) startRestored(state.queueIndex + 1, 0L)
        else withController { it.seekToNext() }
    }

    fun prev() {
        val state = _state.value
        if (pendingStart) startRestored(state.queueIndex - 1, 0L)
        else withController { it.seekToPrevious() }
    }

    fun seekTo(positionMs: Long) {
        val target = positionMs.coerceAtLeast(0)
        _state.value = _state.value.copy(positionMs = target)
        // 恢复态下播放器里还没有 MediaItem，seek 只改 UI，等点播放时作为起始进度下发
        if (pendingStart) return
        withController { it.seekTo(target) }
    }

    /**
     * 进度条按比例 seek：时长的唯一来源是 player 自己。
     * 不能用 UI 状态里的 durationMs —— 它在 seek / 重新 prepare 时会瞬时无效，
     * 一旦被兜成 0，进度条算出来就是 seek 到开头。
     * 恢复态是唯一例外：此时 player 尚未 prepare，只能用快照里的时长换算。
     */
    fun seekToRatio(ratio: Float) {
        val r = ratio.coerceIn(0f, 1f)
        if (pendingStart) {
            val dur = _state.value.durationMs
            if (dur > 0L) _state.value = _state.value.copy(positionMs = (dur * r).toLong())
            return
        }
        withController { c ->
            val dur = c.duration.takeIf { it > 0 && it != C.TIME_UNSET } ?: return@withController
            val target = (dur * r).toLong()
            _state.value = _state.value.copy(positionMs = target)
            c.seekTo(target)
        }
    }

    fun seekBy(seconds: Int) = withController {
        it.seekTo((it.currentPosition + seconds * 1000L).coerceAtLeast(0))
    }

    fun setVolume(value: Float) {
        val volume = value.coerceIn(0f, 1f)
        _state.value = _state.value.copy(volume = volume)
        withController { it.volume = volume }
    }

    /** 单按钮三态：单曲循环 → 列表循环 → 关闭。 */
    fun cycleRepeat() {
        val next = QueueMath.cycleRepeat(_state.value.repeat)
        _state.value = _state.value.copy(repeat = next)
        withController { it.repeatMode = next.toPlayerRepeatMode() }
    }

    fun toggleShuffle() {
        val enabled = !_state.value.shuffle
        _state.value = _state.value.copy(shuffle = enabled)
        withController { it.shuffleModeEnabled = enabled }
    }

    /**
     * 播放列表手动排序（播放页底部列表长按拖动后松手提交）。
     *
     * 本机队列与 ExoPlayer 的 playlist 必须同序，否则界面顺序与通知栏 / 上一曲下一曲不一致，
     * 所以两边一起改：本机换位后按 [QueueMath.movePlan] 逐项 `moveMediaItem`，
     * 不用 `setMediaItems` 重建队列（会打断播放、丢当前进度、多报一次播放记录）。
     * 当前播放的曲目跟着下标走，不换曲、不重播。
     */
    fun reorderQueue(newOrder: List<MediaFile>) {
        val state = _state.value
        val currentId = state.current?.file?.id ?: return
        val oldIds = state.queue.map { it.id }
        val newIds = newOrder.map { it.id }
        // 只接受「同一批文件换了个顺序」：队列刚被换过（如切了作品）时直接忽略
        if (newIds.size != oldIds.size || newIds.toSet() != oldIds.toSet() || newIds == oldIds) return
        val index = newIds.indexOf(currentId).coerceAtLeast(0)
        _state.value = state.copy(queue = newOrder, queueIndex = index)
        // 恢复态：队列还没下发给播放器，点播放时会按新顺序下发，这里只改 UI
        if (pendingStart) return
        val plan = QueueMath.movePlan(oldIds, newIds)
        withController { c ->
            if (c.mediaItemCount != newOrder.size) return@withController
            plan.forEach { (from, to) -> c.moveMediaItem(from, to) }
        }
        // 顺序也进本机快照：冷启动恢复时看到的仍是用户排好的顺序
        maybeSave(force = true)
    }

    /** 退出登录 / 清空播放：停止并清队列，媒体通知随之消失。 */
    fun stop() {
        clearSavedPosition()
        withController {
            it.stop()
            it.clearMediaItems()
        }
        pendingAction = null
        currentWork = null
        _state.value = _state.value.copy(
            current = null,
            queue = emptyList(),
            queueIndex = 0,
            playing = false,
            positionMs = 0L,
            durationMs = 0L,
            fullscreen = false,
            error = null,
        )
    }

    fun setFullscreen(visible: Boolean) {
        // 打开播放页时才消费快照：不打开就不恢复，也不会打断正在播放的内容
        if (visible) {
            restoreRequested = true
            tryRestore()
        }
        _state.value = _state.value.copy(fullscreen = visible)
    }

    fun clearError() {
        _state.value = _state.value.copy(error = null)
    }

    /** 退出登录时彻底断开，避免服务被继续绑定。 */
    fun disconnect() {
        clearSavedPosition()
        pendingAction = null
        controller?.removeListener(listener)
        controller = null
        future?.let { MediaController.releaseFuture(it) }
        future = null
        currentWork = null
        _state.value = PlayState()
    }

    // ---- 本机播放位置 ----

    /** 首次打开播放页时还原上次曲目 / 队列 / 进度；只改 UI 状态，不下发播放器、不自动开播。 */
    private fun tryRestore() {
        if (restoreConsumed) return
        val snapshot = pendingSnapshot ?: return
        // 点通知 / 锁屏卡片进来时 MediaController 可能还没连上，等 connect 回调再判断，
        // 否则会把「服务里正在播的曲目」误判成冷启动，用旧快照覆盖掉。
        val c = controller
        // 服务里已有 playlist（后台续播 / 通知栏进来）：这是同一次播放，不恢复
        if (!RestoreLogic.shouldRestore(
                connected = c != null,
                mediaItemCount = c?.mediaItemCount ?: 0,
                isPlaying = c?.isPlaying ?: false,
                hasCurrent = _state.value.current != null,
            )
        ) {
            // 还没连上不能判定，保留快照等 connect 回调再试；其余情况直接丢弃
            if (c != null) {
                restoreConsumed = true
                pendingSnapshot = null
            }
            return
        }
        restoreConsumed = true
        pendingSnapshot = null
        val index = snapshot.queue.indexOfFirst { it.id == snapshot.fileId }
        if (index < 0) return
        val work = snapshot.toWork()
        currentWork = work
        pendingStart = true
        _state.value = PlayState(
            current = PlayItem(snapshot.rj, snapshot.queue[index], work),
            queue = snapshot.queue,
            queueIndex = index,
            playing = false,
            positionMs = snapshot.positionMs,
            durationMs = snapshot.durationMs,
            fullscreen = true,
        )
    }

    /** 播放中每 30s 记一次；force=true 用于点暂停，立即记一次。 */
    /**
     * 读取「可以写进 UI 的播放位置」。
     *
     * 恢复态刚把队列下发时，Media3 还没 prepare 完，`currentPosition` 仍是 0；
     * 直接照抄会让进度条左边的已播放数字先闪成 0 再跳回历史位置。
     * 所以 READY 之前一律返回 null（调用方保留快照里的进度），
     * READY 且位置已落到起始点后才交还控制权；3 秒兜底避免异常时永久冻结。
     */
    private fun trackablePosition(c: MediaController?): Long? {
        if (c == null) return null
        val position = c.currentPosition.coerceAtLeast(0)
        if (!awaitingStartPosition) return position
        val use = RestoreLogic.shouldUsePlayerPosition(
            playerReady = c.playbackState == Player.STATE_READY,
            playerPositionMs = position,
            startTargetMs = startTargetMs,
            elapsedMs = SystemClock.elapsedRealtime() - startRequestedAt,
        )
        if (!use) return null
        awaitingStartPosition = false
        return position
    }

    private fun maybeSave(force: Boolean = false) {
        val state = _state.value
        val current = state.current ?: return
        if (pendingStart) return
        if (!force && !state.playing) return
        val now = System.currentTimeMillis()
        if (!force && now - lastSaveAt < SAVE_INTERVAL_MS) return
        lastSaveAt = now
        val work = current.work
        store?.save(
            PlaybackSnapshot(
                rj = current.rj,
                fileId = current.file.id,
                positionMs = state.positionMs,
                durationMs = state.durationMs,
                queue = state.queue,
                title = work?.title.orEmpty(),
                circle = work?.circle.orEmpty(),
                actors = work?.actors.orEmpty(),
                coverUrl = work?.coverUrl.orEmpty(),
            )
        )
    }

    /** 恢复态点播放 / 上一曲 / 下一曲：此刻才把队列与起始进度交给 Media3。 */
    private fun startRestored(index: Int, positionMs: Long) {
        val state = _state.value
        val current = state.current ?: return
        val safe = RestoreLogic.clampIndex(index, state.queue.size)
        if (safe < 0) return
        val file = state.queue[safe]
        pendingStart = false
        // 起点靠 setMediaItems 的 startPositionMs 生效；READY 之前进度仍以快照为准
        awaitingStartPosition = true
        startTargetMs = positionMs
        startRequestedAt = SystemClock.elapsedRealtime()
        _state.value = state.copy(
            current = PlayItem(current.rj, file, currentWork),
            queueIndex = safe,
            positionMs = positionMs,
            playing = true,
        )
        val items = buildItems(current.rj, currentWork, state.queue)
        withController { c ->
            c.setMediaItems(items, safe, positionMs)
            c.prepare()
            c.play()
        }
    }

    private fun clearSavedPosition() {
        store?.clear()
        pendingSnapshot = null
        restoreConsumed = true
        restoreRequested = false
        pendingStart = false
        awaitingStartPosition = false
        startTargetMs = 0L
        lastSaveAt = 0L
    }

    // ---- 内部 ----

    private fun connect() {
        val context = appContext ?: return
        if (controller != null || future != null) return
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val pending = MediaController.Builder(context, token).buildAsync()
        future = pending
        pending.addListener({
            if (future !== pending) return@addListener
            val connected = runCatching { pending.get() }.getOrNull()
            if (connected == null) {
                future = null
                _state.value = _state.value.copy(playing = false, error = "无法连接播放服务")
                return@addListener
            }
            controller = connected
            connected.addListener(listener)
            syncFromController()
            pendingAction?.let { it(connected) }
            pendingAction = null
            // 连接期间若用户已打开播放页，此刻才能判断是冷启动还是后台续播
            if (restoreRequested) tryRestore()
        }, ContextCompat.getMainExecutor(context))
    }

    private fun withController(action: (MediaController) -> Unit) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            main.post { withController(action) }
            return
        }
        val ready = controller
        if (ready != null) action(ready) else {
            pendingAction = action
            connect()
        }
    }

    private val listener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            val c = controller
            val before = _state.value
            _state.value = before.copy(
                playing = isPlaying,
                // 恢复态 / 尚未 READY 时服务里的位置还不可信，别把快照进度冲成 0
                positionMs = if (pendingStart) before.positionMs
                else trackablePosition(c) ?: before.positionMs,
            )
            // 暂停也必须立刻落盘：通知栏 / 锁屏 / 蓝牙 / 耳机键的暂停不经过 pause()，
            // 而暂停后 ticker 的非强制保存会因 playing=false 直接返回，不补这一步进度会滞后一整个节拍
            if (before.playing && !isPlaying) maybeSave(force = true)
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_READY) syncFromController()
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            syncCurrent()
            // 切歌后立刻落盘：否则「刚切到下一首就被划掉」会恢复到上一首的位置，
            // 而本功能的目的正是定位到上次那个文件
            maybeSave(force = true)
        }

        override fun onRepeatModeChanged(repeatMode: Int) {
            _state.value = _state.value.copy(repeat = repeatMode.toRepeatMode())
        }

        override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
            _state.value = _state.value.copy(shuffle = shuffleModeEnabled)
        }

        override fun onVolumeChanged(volume: Float) {
            _state.value = _state.value.copy(volume = volume)
        }

        override fun onPlayerError(error: PlaybackException) {
            _state.value = _state.value.copy(
                playing = false,
                error = error.message ?: "播放失败",
            )
        }
    }

    private val ticker = object : Runnable {
        override fun run() {
            val c = controller
            // pendingStart：只还原了 UI，播放器还没拿到队列，进度以快照为准
            if (c != null && !pendingStart) {
                // 注意：seek / 重新 prepare 期间 c.duration 会短暂回到 0，
                // 不能把 0 写回 state，否则进度条 ratio 会算成 0，点后面就 seek 到 0
                val duration = c.duration.takeIf { it > 0 && it != C.TIME_UNSET }
                    ?: _state.value.durationMs
                // trackablePosition 为 null 表示播放器还没 READY，进度继续用快照值，避免闪 0
                val position = trackablePosition(c)
                _state.value = _state.value.copy(
                    positionMs = position ?: _state.value.positionMs,
                    durationMs = duration,
                )
                maybeSave()
            }
            main.postDelayed(this, 250)
        }
    }

    /** 连接成功后整体同步一次，之后由 Listener + ticker 增量更新。 */
    private fun syncFromController() {
        val c = controller ?: return
        if (pendingStart) return
        val duration = c.duration.takeIf { it > 0 && it != C.TIME_UNSET }
            ?: _state.value.durationMs
        _state.value = _state.value.copy(
            playing = c.isPlaying,
            positionMs = trackablePosition(c) ?: _state.value.positionMs,
            durationMs = duration,
            volume = c.volume,
            repeat = c.repeatMode.toRepeatMode(),
            shuffle = c.shuffleModeEnabled,
        )
        syncCurrent()
    }

    /** 曲目切换：用 playlist 下标映射回本地队列，保证标题 / 封面与系统通知同源。 */
    private fun syncCurrent() {
        val c = controller ?: return
        if (pendingStart) return
        val state = _state.value
        val index = c.currentMediaItemIndex
        if (index < 0) return
        val rj = c.currentMediaItem?.mediaId?.substringBeforeLast('/', "").orEmpty()
        val file = state.queue.getOrNull(index)
        val current = if (file != null && rj.isNotBlank()) {
            PlayItem(rj, file, currentWork)
        } else {
            state.current
        }
        _state.value = state.copy(
            current = current,
            queueIndex = index,
            // 恢复起始位置还没生效前（READY 之前）仍用快照进度，别闪成 0
            positionMs = trackablePosition(c) ?: state.positionMs,
            // 时长同理：prepare 期间会短暂为 0，写回 0 会让进度条瞬间跳回开头
            durationMs = c.duration.takeIf { it > 0 && it != C.TIME_UNSET } ?: state.durationMs,
        )
    }

    private fun buildItems(rj: String, work: Work?, files: List<MediaFile>): List<MediaItem> {
        val artist = work?.actors?.takeIf { it.isNotEmpty() }?.joinToString("、")
            ?: work?.circle.orEmpty()
        val album = work?.title ?: rj
        return files.map { file ->
            MediaItem.Builder()
                .setMediaId(QueueMath.mediaId(rj, file.id))
                .setUri(mediaUrl(rj, file.id))
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(file.filename.ifBlank { album })
                        .setArtist(artist)
                        .setAlbumTitle(album)
                        .setIsPlayable(true)
                        .build(),
                )
                .build()
        }
    }

    private fun mediaUrl(rj: String, fileId: Long): String {
        val offline = AppContainer.offline
        return if (offline.hasAudio(rj, fileId)) {
            Uri.fromFile(offline.audioFile(rj, fileId)).toString()
        } else {
            AppContainer.api.mediaUrl(rj, fileId)
        }
    }

    private fun RepeatMode.toPlayerRepeatMode(): Int = when (this) {
        RepeatMode.OFF -> Player.REPEAT_MODE_OFF
        RepeatMode.ONE -> Player.REPEAT_MODE_ONE
        RepeatMode.ALL -> Player.REPEAT_MODE_ALL
    }

    private fun Int.toRepeatMode(): RepeatMode = when (this) {
        Player.REPEAT_MODE_ONE -> RepeatMode.ONE
        Player.REPEAT_MODE_ALL -> RepeatMode.ALL
        else -> RepeatMode.OFF
    }
}
