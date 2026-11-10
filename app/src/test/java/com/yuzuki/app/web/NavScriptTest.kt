package com.yuzuki.app.web

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 方案 §5.3 导航契约的确定性断言：不依赖设备与服务。 */
class NavScriptTest {

    @Test
    fun `路由映射与方案一致`() {
        assertEquals("/", NavScript.pathFor("library"))
        assertEquals("/history", NavScript.pathFor("history"))
        assertEquals("/favorites", NavScript.pathFor("favorites"))
        assertEquals("/", NavScript.pathFor("unknown"))
    }

    @Test
    fun `脚本内嵌同一份路由表`() {
        NavScript.ROUTES.forEach { (name, path) ->
            assertTrue("缺少路由 $name", NavScript.SCRIPT.contains("'$name':'$path'"))
        }
    }

    @Test
    fun `脚本使用 pushState 并派发 SPA popstate`() {
        assertTrue(NavScript.SCRIPT.contains("history.pushState"))
        assertTrue(NavScript.SCRIPT.contains("PopStateEvent"))
        assertTrue(NavScript.SCRIPT.contains("location.assign(p)"))
    }

    @Test
    fun `脚本能展开全屏播放器并设置就绪标记`() {
        assertTrue(NavScript.SCRIPT.contains(".bottom-player .btn.expand"))
        assertTrue(NavScript.SCRIPT.contains("window.__yuzukiOpenPlayer"))
        assertTrue(NavScript.SCRIPT.contains("window.__YUZUKI_NAV_READY__=true"))
    }

    @Test
    fun `脚本不携带 token 且防重复注入`() {
        assertFalse(NavScript.SCRIPT.contains("yuzuki_token"))
        assertTrue(NavScript.SCRIPT.contains("if(window.__YUZUKI_NAV_READY__)return;"))
    }
}
