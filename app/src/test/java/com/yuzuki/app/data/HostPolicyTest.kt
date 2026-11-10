package com.yuzuki.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 方案 §10 S1 / §7 的确定性断言：不依赖设备、不依赖服务是否运行。 */
class HostPolicyTest {

    @Test
    fun `局域网地址规范化`() {
        assertEquals("http://192.168.1.10:8080", HostPolicy.normalize("192.168.1.10").getOrThrow())
        assertEquals("http://192.168.1.10:8080", HostPolicy.normalize("http://192.168.1.10").getOrThrow())
        assertEquals("http://192.168.1.10:8080", HostPolicy.normalize("http://192.168.1.10:8080/").getOrThrow())
        assertEquals("http://10.0.0.5:3000", HostPolicy.normalize("10.0.0.5:3000").getOrThrow())
        assertEquals("http://172.20.1.2:8080", HostPolicy.normalize("172.20.1.2").getOrThrow())
        assertEquals("http://127.0.0.1:8080", HostPolicy.normalize("127.0.0.1").getOrThrow())
    }

    @Test
    fun `公网地址与域名一律拒绝`() {
        listOf(
            "https://example.com",
            "example.com",
            "8.8.8.8",
            "172.32.0.1",
            "192.169.1.1",
            "ftp://192.168.1.10",
            "192.168.1.10/evil",
            "192.168.1.10@evil.com",
            "",
            "192.168.1.10:8080?token=x",
            "192.168.1.10.evil.com",
            "1.2.3.4.5",
            "999.1.1.1",
        ).forEach { raw ->
            assertTrue("应被拒绝: $raw", HostPolicy.normalize(raw).isFailure)
        }
    }

    @Test
    fun `同源请求放行`() {
        val base = "http://192.168.1.10:8080"
        assertTrue(HostPolicy.isAllowedUrl("$base/api/works?page=1", base))
        assertTrue(HostPolicy.isAllowedUrl("$base/api/media/RJ123/1.flac?token=abc", base))
        assertTrue(HostPolicy.isAllowedUrl("$base/history", base))
        assertTrue(HostPolicy.isAllowedUrl("/api/works", base))
        assertTrue(HostPolicy.isAllowedUrl("api/works", base))
    }

    @Test
    fun `外链全部拦截`() {
        val base = "http://192.168.1.10:8080"
        listOf(
            "https://example.com",
            "http://evil.com",
            "http://192.168.1.11:8080/api/works",
            "http://192.168.1.10:3000/api/works",
            "http://192.168.1.10.evil.com:8080/",
            "http://evil.com@192.168.1.10:8080/",
            "http://192.168.1.10:8080@evil.com/",
            "http://[::1]:8080/",
            "//evil.com/x",
            "javascript:alert(1)",
            "data:text/html,x",
            "file:///etc/passwd",
            "about:blank",
        ).forEach { url ->
            assertFalse("应被拦截: $url", HostPolicy.isAllowedUrl(url, base))
        }
    }

    @Test
    fun `更换服务地址后旧地址失效`() {
        val oldBase = "http://192.168.1.10:8080"
        val newBase = "http://192.168.1.20:8080"
        assertFalse(HostPolicy.isAllowedUrl("$oldBase/api/works", newBase))
        assertTrue(HostPolicy.isAllowedUrl("$newBase/api/works", newBase))
    }

    @Test
    fun `isLanHost 严格按四段字面 IPv4 判定`() {
        assertFalse(HostPolicy.isLanHost("192.168.1.10.evil"))
        assertFalse(HostPolicy.isLanHost("192.168.1.abc"))
        assertFalse(HostPolicy.isLanHost("localhost"))
        assertTrue(HostPolicy.isLanHost("172.31.255.255"))
        assertFalse(HostPolicy.isLanHost("172.32.0.1"))
    }
}
