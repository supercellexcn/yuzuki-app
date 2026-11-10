package com.yuzuki.app.web

import android.webkit.WebView
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import org.json.JSONObject

/** 文档级注入（方案 §6.1 / §12）：脚本必须在页面自身 JS 执行前生效。 */
object BridgeInjector {
    const val TOKEN_READY_EXPR =
        "window.__YUZUKI_TOKEN_READY__ === true && !!localStorage.getItem('yuzuki_token')"
    const val BRIDGE_READY_EXPR = "window.__YUZUKI_BRIDGE_READY__ === true"

    /** token 写进同源 localStorage，前端 authStore.init() 会自动恢复登录态。 */
    fun tokenScript(token: String): String {
        val quoted = JSONObject.quote(token) // 转义引号/反斜杠，避免注入拼接破坏脚本
        return """
            (function () {
              try {
                localStorage.setItem('yuzuki_token', $quoted);
                window.__YUZUKI_TOKEN_READY__ = true;
              } catch (e) {
                window.__YUZUKI_TOKEN_READY__ = false;
              }
            })();
        """.trimIndent()
    }

    fun supportsDocumentStart(): Boolean =
        WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)

    /** 返回是否用上 document-start；false 时由 WebViewClient 走 onPageStarted 回退。 */
    fun install(webView: WebView, baseUrl: String, scripts: List<String>): Boolean {
        if (!supportsDocumentStart()) return false
        val origins = setOf(baseUrl) // 只在已校验的服务 origin 上注入
        scripts.forEach { script ->
            WebViewCompat.addDocumentStartJavaScript(webView, script, origins)
        }
        return true
    }
}
