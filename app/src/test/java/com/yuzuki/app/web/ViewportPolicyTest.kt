package com.yuzuki.app.web

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 断点契约与诊断脚本的确定性断言（对应本轮两个界面 bug 的根因）。 */
class ViewportPolicyTest {

    @Test
    fun `断点与前端 useIsMobile 一致`() {
        assertEquals(760, ViewportPolicy.MOBILE_BREAKPOINT_PX)
        assertEquals("(max-width: 760px)", ViewportPolicy.MEDIA_QUERY)
        assertTrue(ViewportPolicy.isMobileLayout(360))
        assertTrue(ViewportPolicy.isMobileLayout(760))
        assertFalse(ViewportPolicy.isMobileLayout(761))
        assertFalse(ViewportPolicy.isMobileLayout(980))
        assertFalse(ViewportPolicy.isMobileLayout(0))
    }

    @Test
    fun `诊断脚本包含同一条媒体查询`() {
        assertTrue(ViewportPolicy.probeScript().contains(ViewportPolicy.MEDIA_QUERY))
        assertTrue(ViewportPolicy.probeScript().contains("innerWidth"))
    }
}
