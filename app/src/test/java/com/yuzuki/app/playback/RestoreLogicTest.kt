package com.yuzuki.app.playback

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Test

class RestoreLogicTest {
    @Test
    fun 冷启动且服务为空时恢复() {
        assertTrue(RestoreLogic.shouldRestore(connected = true, 0, false, false))
    }

    @Test
    fun 控制器未连接时不判定留给回调重试() {
        assertFalse(RestoreLogic.shouldRestore(connected = false, 0, false, false))
    }

    @Test
    fun 服务里已有队列时不覆盖后台续播() {
        assertFalse(RestoreLogic.shouldRestore(connected = true, 3, false, false))
    }

    @Test
    fun 正在播放时不恢复() {
        assertFalse(RestoreLogic.shouldRestore(connected = true, 0, true, false))
    }

    @Test
    fun UI已有当前曲目时不恢复() {
        assertFalse(RestoreLogic.shouldRestore(connected = true, 0, false, true))
    }

    @Test
    fun 通知栏进来同时有队列且在播放时不恢复() {
        assertFalse(RestoreLogic.shouldRestore(connected = true, 1, true, true))
    }

    @Test
    fun 下标夹紧到合法范围() {
        assertEquals(2, RestoreLogic.clampIndex(2, 5))
        assertEquals(0, RestoreLogic.clampIndex(-7, 5))
        assertEquals(4, RestoreLogic.clampIndex(99, 5))
    }

    @Test
    fun 空队列不可恢复() {
        assertEquals(-1, RestoreLogic.clampIndex(0, 0))
        assertEquals(-1, RestoreLogic.clampIndex(3, -1))
    }

    // ---- 恢复后进度归属：READY 之前不许用播放器的 0 覆盖快照进度 ----

    @Test
    fun 恢复未就绪时进度仍以快照为准() {
        assertFalse(
            RestoreLogic.shouldUsePlayerPosition(
                playerReady = false, playerPositionMs = 0L,
                startTargetMs = 67_240L, elapsedMs = 200L,
            ),
        )
    }

    @Test
    fun 就绪且已跳到目标位置后交还播放器() {
        assertTrue(
            RestoreLogic.shouldUsePlayerPosition(
                playerReady = true, playerPositionMs = 67_240L,
                startTargetMs = 67_240L, elapsedMs = 600L,
            ),
        )
    }

    @Test
    fun 就绪但位置还没生效时继续等() {
        // READY 但 currentPosition 仍是 0（起始位置还没应用），此时接管就会闪 0
        assertFalse(
            RestoreLogic.shouldUsePlayerPosition(
                playerReady = true, playerPositionMs = 0L,
                startTargetMs = 67_240L, elapsedMs = 800L,
            ),
        )
    }

    @Test
    fun 快照进度为零时就绪即可接管() {
        assertTrue(
            RestoreLogic.shouldUsePlayerPosition(
                playerReady = true, playerPositionMs = 0L,
                startTargetMs = 0L, elapsedMs = 500L,
            ),
        )
    }

    @Test
    fun 超时兜底避免进度永久冻结() {
        assertTrue(
            RestoreLogic.shouldUsePlayerPosition(
                playerReady = false, playerPositionMs = 0L,
                startTargetMs = 67_240L, elapsedMs = 3_500L,
            ),
        )
    }
}
