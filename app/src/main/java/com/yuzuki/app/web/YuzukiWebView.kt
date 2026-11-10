package com.yuzuki.app.web

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.util.Log
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

private const val TAG = "YuzukiViewport"

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

    /** 锁屏换曲 → 点击前端 BottomPlayer 的上一曲/下一曲按钮。 */
    private val commandSink: (String) -> Unit = { command ->
        post {
            val quoted = JSONObject.quote(command)
            evaluateJavascript(
                "window.__yuzukiPlayerCommand&&window.__yuzukiPlayerCommand($quoted)",
                null,
            )
        }
    }

    // 注入顺序：token → nav → bridge → commands，全部必须在页面自身 JS 执行前生效
    private val scripts: List<String> = listOfNotNull(
        BridgeInjector.tokenScript(token),
        NavScript.SCRIPT,
        BridgeInjector.bridgeScript(context),
        PlayerCommands.SCRIPT,
    )

    init {
        configureSettings()
        syncJavascriptInterface("$baseUrl/")
        documentStart = installInjection()
        PlayerHolder.eventSink = eventSink
        PlayerHolder.commandSink = commandSink
        webViewClient = YuzukiClient()
        webChromeClient = WebChromeClient()
        loadUrl("$baseUrl/")
    }

    private fun configureSettings() {
        settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            // 遵循 HTTP 缓存头：nginx 对静态资源给了 expires 30d，断网仍可渲染壳（方案 §9）；
            // 而 /api/ 没有缓存头，PC 端改的壁纸/偏好等设置能实时反映到 App。
            // 不能用 LOAD_CACHE_ELSE_NETWORK——它「即使过期也用缓存」，会把旧设置一直带在 App 里。
            cacheMode = WebSettings.LOAD_DEFAULT
            mediaPlaybackRequiresUserGesture = false
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            allowFileAccess = false
            allowContentAccess = false
            javaScriptCanOpenWindowsAutomatically = false
            setSupportMultipleWindows(false)
            setSupportZoom(false)
            builtInZoomControls = false
            displayZoomControls = false
            // 必须为 true：让 index.html 的 width=device-width 生效，否则 WebView 按约 980px
            // 宽视口渲染，前端 matchMedia('(max-width:760px)') 为 false 会走桌面布局。
            useWideViewPort = true
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
            // 视口断言：真机 Logcat 中应为 "true|宽x高"，宽度需 <=760 才算命中移动断点
            view.evaluateJavascript(ViewportPolicy.probeScript()) { value ->
                Log.i(TAG, "viewport(mobile|WxH)=${value?.trim('"').orEmpty()}")
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
        if (PlayerHolder.commandSink === commandSink) PlayerHolder.commandSink = null
        stopLoading()
        super.destroy()
    }
}
