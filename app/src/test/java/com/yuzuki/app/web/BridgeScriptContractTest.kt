package com.yuzuki.app.web

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** 方案 §6.1 / §12 的 bridge.js 静态契约断言（无需设备与服务）。 */
class BridgeScriptContractTest {

    private val script: String by lazy {
        listOf(
            File("src/main/assets/bridge/yuzuki-bridge.js"),
            File("app/src/main/assets/bridge/yuzuki-bridge.js"),
        ).first { it.exists() }.readText()
    }

    @Test
    fun `双代理齐备且 mediaSession 是代理不是删除`() {
        assertTrue(script.contains("window.Audio = NativeAudio"))
        assertTrue(script.contains("Object.defineProperty(navigator, 'mediaSession'"))
        assertTrue(script.contains("window.MediaMetadata = function"))
        assertFalse(script.contains("delete navigator.mediaSession"))
        assertFalse(script.contains("mediaSession = null"))
    }

    @Test
    fun `NativeAudio 实现最小接口集`() {
        listOf("'src'", "'paused'", "'currentTime'", "'volume'", "'loop'").forEach {
            assertTrue("缺少属性 $it", script.contains(it))
        }
        assertTrue(script.contains("NativeAudio.prototype.play"))
        assertTrue(script.contains("Promise.resolve()"))
        assertTrue(script.contains("NativeAudio.prototype.pause"))
        assertTrue(script.contains("NativeAudio.prototype.load"))
        assertTrue(script.contains("NativeAudio.prototype.removeAttribute"))
        assertTrue(script.contains("NativeAudio.prototype.dispatchEvent"))
    }

    @Test
    fun `事件总线与快照回放`() {
        assertTrue(script.contains("window.__yuzukiOnNativeEvent"))
        assertTrue(script.contains("__yuzukiPendingSnapshot"))
        assertTrue(script.contains("N.getSnapshot()"))
        listOf("timeupdate", "durationchange", "play", "pause", "ended", "error").forEach {
            assertTrue("缺少事件 $it", script.contains(it))
        }
    }

    @Test
    fun `不覆盖 S2 的导航实现`() {
        assertTrue(script.contains("if (!window.__yuzukiNav)"))
        assertTrue(script.contains("if (!window.__yuzukiOpenPlayer)"))
        assertTrue(script.contains("window.__YUZUKI_BRIDGE_READY__ = true"))
    }

    @Test
    fun `退出登录同步与 JS 接口调用契约`() {
        assertTrue(script.contains("Storage.prototype.removeItem"))
        assertTrue(script.contains("N.logout()"))
        listOf(
            "N.setSource", "N.play()", "N.pause()", "N.stop()",
            "N.seekTo", "N.setVolume", "N.setLoop", "N.setMetadata",
        ).forEach {
            assertTrue("缺少调用 $it", script.contains(it))
        }
    }
}
