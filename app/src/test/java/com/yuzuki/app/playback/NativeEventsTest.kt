package com.yuzuki.app.playback

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 方案 §6.4 的 Native → JS 事件契约断言。 */
class NativeEventsTest {

    @Test
    fun `state 快照包含全部字段`() {
        val json = NativeAudioState(
            playing = true,
            paused = false,
            currentTime = 12.5,
            duration = 180.0,
            volume = 0.5f,
            loop = true,
        ).toEventJson()
        assertTrue(json.contains("\"type\":\"state\""))
        assertTrue(json.contains("\"playing\":true"))
        assertTrue(json.contains("\"paused\":false"))
        assertTrue(json.contains("\"currentTime\":12.5"))
        assertTrue(json.contains("\"duration\":180.0"))
        assertTrue(json.contains("\"volume\":0.5"))
        assertTrue(json.contains("\"loop\":true"))
    }

    @Test
    fun `非法数值不会产出 NaN 或 Infinity`() {
        val json = NativeEvents.timeupdate(Double.NaN, Double.POSITIVE_INFINITY)
        assertFalse(json.contains("NaN"))
        assertFalse(json.contains("Infinity"))
        assertTrue(json.contains("\"currentTime\":0.0"))
    }

    @Test
    fun `error 消息转义引号与换行`() {
        val json = NativeEvents.error("bad \"url\"\nnext")
        assertTrue(json.contains("\\\"url\\\""))
        assertTrue(json.contains("\\n"))
        assertFalse(json.contains("\n"))
    }

    @Test
    fun `六类事件类型齐备`() {
        listOf("play", "pause", "ended", "durationchange", "timeupdate", "error").forEach {
            assertTrue(NativeEvents.simple(it).contains("\"type\":\"$it\""))
        }
        assertTrue(NativeEvents.durationchange(30.0).contains("\"duration\":30.0"))
        assertTrue(NativeEvents.error("x").contains("\"message\":\"x\""))
    }
}
