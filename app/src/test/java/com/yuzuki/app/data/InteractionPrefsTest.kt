package com.yuzuki.app.data

import com.yuzuki.app.ui.FeedPull
import org.junit.Assert.assertEquals
import org.junit.Test

/** 本机交互偏好：默认按住时长、范围夹紧、秒转毫秒（不依赖 Android）。 */
class InteractionPrefsTest {

    @Test
    fun `默认按住 3 秒且与手势默认值一致`() {
        assertEquals(3, InteractionPrefs.Default.exitRandomHoldSeconds)
        assertEquals(3, InteractionPrefs.DefaultHoldSeconds)
        // 设置默认值与 FeedPull.HOLD_MS 必须对齐，否则没读设置时提示文案与实际触发不符
        assertEquals(FeedPull.HOLD_MS, InteractionPrefs.holdMillis(InteractionPrefs.DefaultHoldSeconds))
    }

    @Test
    fun `时长越界被夹到 1 到 10 秒`() {
        assertEquals(1, InteractionPrefs(exitRandomHoldSeconds = 0).sanitized().exitRandomHoldSeconds)
        assertEquals(1, InteractionPrefs(exitRandomHoldSeconds = -5).sanitized().exitRandomHoldSeconds)
        assertEquals(10, InteractionPrefs(exitRandomHoldSeconds = 11).sanitized().exitRandomHoldSeconds)
        assertEquals(7, InteractionPrefs(exitRandomHoldSeconds = 7).sanitized().exitRandomHoldSeconds)
    }

    @Test
    fun `秒换算成毫秒也夹范围`() {
        assertEquals(3_000L, InteractionPrefs.holdMillis(3))
        assertEquals(1_000L, InteractionPrefs.holdMillis(0))
        assertEquals(1_000L, InteractionPrefs.holdMillis(-1))
        assertEquals(10_000L, InteractionPrefs.holdMillis(99))
    }
}
