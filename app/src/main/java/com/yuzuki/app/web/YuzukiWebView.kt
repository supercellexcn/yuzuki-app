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
import com.yuzuki.app.data.HostPolicy
import com.yuzuki.app.data.ServerConfig
import com.yuzuki.app.playback.PlayerHolder
import org.json.JSONObject
import java.io.ByteArrayInputStream

/** 专用 WebView：加载现有 web 页，App 不重画任何界面（方案 C4）。 */
@SuppressLint("SetJavaScriptEnabled", "AddJavascriptInterface")
class YuzukiWebView(
    context: Context,
    private val baseUrl: String,
    private val token: String,
    private val serverConfig: ServerConfig,
    private val onUnreachable: () -> Unit,
    private val onBridgeUnavailable: () -> Unit,
    private val onPathChanged: (String) -> Unit = {},
) : WebView(context) {

    private val jsBridge = JsBridge(context, baseUrl)
    private val documentStart: Boolean
    private var bridgeEnabled = false

    private val eventSink: (String) -> Unit = { json ->
        post {
            val quoted = JSONObject.quote(json)
            evaluateJavascript(
                "window.__yuzukiOnNativeEvent&&window.__yuzukiOnNativeEvent($quoted)",
                null,
            )
        }
    }

    // 注入顺序：token → nav → bridge，全部必须在页面自身 JS 执行前生效
    private val scripts: List<String> = listOfNotNull(
        BridgeInjector.tokenScript(token),
        NavScript.SCRIPT,
        BridgeInjector.bridgeScript(context),
    )

    init {
        configureSettings()
        syncJavascriptInterface("$baseUrl/")
        documentStart = installInjection()
        PlayerHolder.eventSink = eventSink
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
    }

    /** addJavascriptInterface 仅在 host 校验通过时启用（方案 §6.4）。 */
    private fun syncJavascriptInterface(url: String) {
        val allowed = HostPolicy.isAllowedUrl(url, baseUrl)
        if (allowed && !bridgeEnabled) {
            addJavascriptInterface(jsBridge, "YuzukiNative")
            bridgeEnabled = true
        } else if (!allowed && bridgeEnabled) {
            removeJavascriptInterface("YuzukiNative")
            bridgeEnabled = false
        }
    }

    private fun installInjection(): Boolean = BridgeInjector.install(
        webView = this,
        baseUrl = baseUrl,
        scripts = scripts,
    )

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
            syncJavascriptInterface(url)
            if (!documentStart) {
                // 低版本 WebView 回退，就绪性由 onPageFinished 断言
                scripts.forEach { view.evaluateJavascript(it, null) }
            }
        }

        override fun onPageFinished(view: WebView, url: String) {
            view.evaluateJavascript("(${BridgeInjector.READY_EXPR})") { value ->
                if (value != "true") post { onBridgeUnavailable() }
            }
            // 同步当前路由到原生 Tab 选中态（覆盖 SPA 回退 loadUrl 与首次加载）
            view.evaluateJavascript("location.pathname") { value ->
                val path = value?.trim('"').orEmpty()
                if (path.isNotEmpty()) post { onPathChanged(path) }
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
        if (PlayerHolder.eventSink === eventSink) PlayerHolder.eventSink = null
        stopLoading()
        super.destroy()
    }
}
