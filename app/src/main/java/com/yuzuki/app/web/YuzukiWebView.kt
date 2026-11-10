package com.yuzuki.app.web

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature
import com.yuzuki.app.data.ServerConfig
import java.io.ByteArrayInputStream

/** 专用 WebView：加载现有 web 页，App 不重画任何界面（方案 C4）。 */
@SuppressLint("SetJavaScriptEnabled")
class YuzukiWebView(
    context: Context,
    private val baseUrl: String,
    private val token: String,
    private val serverConfig: ServerConfig,
    private val onUnreachable: () -> Unit,
    private val onBridgeUnavailable: () -> Unit,
) : WebView(context) {

    private val documentStart: Boolean

    init {
        configureSettings()
        documentStart = installInjection()
        webViewClient = YuzukiClient()
        webChromeClient = WebChromeClient()
        loadUrl("$baseUrl/")
    }

    private fun configureSettings() {
        settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            // P0 离线第 1 层：断网后仍可渲染已缓存的壳（方案 §9）
            cacheMode = WebSettings.LOAD_CACHE_ELSE_NETWORK
            mediaPlaybackRequiresUserGesture = false
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            allowFileAccess = false
            allowContentAccess = false
            javaScriptCanOpenWindowsAutomatically = false
            setSupportMultipleWindows(false)
            setSupportZoom(false)
            builtInZoomControls = false
            displayZoomControls = false
            // 前端 index.html 已声明 width=device-width，手机宽度天然命中 <760px 断点
            useWideViewPort = false
            loadWithOverviewMode = false
        }
        // 前端已声明 color-scheme: dark，禁止 WebView 自动反色覆盖其外观
        if (WebViewFeature.isFeatureSupported(WebViewFeature.FORCE_DARK)) {
            @Suppress("DEPRECATION")
            WebSettingsCompat.setForceDark(settings, WebSettingsCompat.FORCE_DARK_OFF)
        }
        // S3 才启用 addJavascriptInterface，且仅在 host 校验通过时（方案 §6.4）
    }

    private fun installInjection(): Boolean {
        // S1 只注入 token 脚本；S3 在此追加 bridge.js（Audio / MediaSession 双代理）
        return BridgeInjector.install(
            webView = this,
            baseUrl = baseUrl,
            scripts = listOf(BridgeInjector.tokenScript(token)),
        )
    }

    private inner class YuzukiClient : WebViewClient() {
        /** data:/blob: 是页面内联资源，不产生出站请求，不参与 host 白名单。 */
        private fun isInline(url: String): Boolean =
            url.startsWith("data:") || url.startsWith("blob:")

        /** 外链一律拦截：媒体 URL 带 ?token=，外跳有泄露风险（方案 §7 / §11）。 */
        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
            val url = request.url.toString()
            if (isInline(url)) return false
            return !serverConfig.isAllowedUrl(url, baseUrl)
        }

        override fun shouldInterceptRequest(
            view: WebView,
            request: WebResourceRequest,
        ): WebResourceResponse? {
            val url = request.url.toString()
            if (isInline(url)) return null
            if (!serverConfig.isAllowedUrl(url, baseUrl)) {
                return WebResourceResponse(
                    "text/plain",
                    "utf-8",
                    403,
                    "Forbidden",
                    emptyMap(),
                    ByteArrayInputStream(ByteArray(0)),
                )
            }
            // S6 在此接入 Room 离线缓存
            return null
        }

        override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
            if (!documentStart) {
                // 低版本 WebView 回退，就绪性由 onPageFinished 断言
                view.evaluateJavascript(BridgeInjector.tokenScript(token), null)
            }
        }

        override fun onPageFinished(view: WebView, url: String) {
            view.evaluateJavascript("(${BridgeInjector.TOKEN_READY_EXPR})") { value ->
                if (value != "true") post { onBridgeUnavailable() }
            }
        }

        override fun onReceivedError(
            view: WebView,
            request: WebResourceRequest,
            error: WebResourceError,
        ) {
            if (request.isForMainFrame) post { onUnreachable() }
        }
    }

    override fun destroy() {
        stopLoading()
        super.destroy()
    }
}
