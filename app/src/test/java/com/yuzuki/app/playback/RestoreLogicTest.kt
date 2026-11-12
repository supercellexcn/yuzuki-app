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
}
