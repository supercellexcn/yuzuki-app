package com.yuzuki.app.web

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 方案 §6.3 换曲转发契约的静态断言。 */
class PlayerCommandsTest {

    @Test
    fun `脚本包含上一曲下一曲与播放按钮选择器`() {
        assertTrue(PlayerCommands.SCRIPT.contains("title=\"下一曲\""))
        assertTrue(PlayerCommands.SCRIPT.contains("title=\"上一曲\""))
        assertTrue(PlayerCommands.SCRIPT.contains(".bottom-player .btn.main"))
        assertTrue(PlayerCommands.SCRIPT.contains("window.__yuzukiPlayerCommand"))
    }

    @Test
    fun `脚本幂等且防异常不携带 token`() {
        assertTrue(PlayerCommands.SCRIPT.contains("if (window.__YUZUKI_COMMANDS_READY__) return;"))
        assertTrue(PlayerCommands.SCRIPT.contains("catch (e)"))
        assertTrue(PlayerCommands.SCRIPT.contains("window.__YUZUKI_COMMANDS_READY__ = true"))
        assertFalse(PlayerCommands.SCRIPT.contains("yuzuki_token"))
    }
}
