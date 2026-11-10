package com.yuzuki.app.web

import android.webkit.WebView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

enum class WebTab { LIBRARY, PLAYER, HISTORY }

/** Tab → 注入 JS；导航后做 pathname 断言，1.5s 未生效则回退 loadUrl（方案 §5.3）。 */
class NavController(
    private val webViewProvider: () -> WebView?,
    private val baseUrl: String,
) {
    fun go(tab: WebTab, scope: CoroutineScope, onNoPlayback: () -> Unit) {
        val wv = webViewProvider() ?: return
        if (tab == WebTab.PLAYER) {
            openPlayer(wv, scope, onNoPlayback)
            return
        }
        val name = if (tab == WebTab.LIBRARY) "library" else "history"
        val expected = NavScript.pathFor(name)
        wv.evaluateJavascript("window.__yuzukiNav&&window.__yuzukiNav('$name')", null)
        scope.launch {
            delay(1500)
            val current = evalJs(wv, "location.pathname")?.trim('"')
            if (current != expected) {
                withContext(Dispatchers.Main) { wv.loadUrl(baseUrl + expected) }
            }
        }
    }

    private fun openPlayer(wv: WebView, scope: CoroutineScope, onNoPlayback: () -> Unit) {
        scope.launch {
            val opened = evalJs(wv, "!!(window.__yuzukiOpenPlayer&&window.__yuzukiOpenPlayer())")
            if (opened != "true") {
                // 当前无播放：回媒体库 + 原生 Snackbar
                wv.evaluateJavascript("window.__yuzukiNav&&window.__yuzukiNav('library')", null)
                withContext(Dispatchers.Main) { onNoPlayback() }
            }
        }
    }

    /** evaluateJavascript 必须在主线程调用，并包装成可挂起的返回值。 */
    private suspend fun evalJs(wv: WebView, script: String): String? =
        withContext(Dispatchers.Main) {
            suspendCancellableCoroutine { cont ->
                wv.evaluateJavascript(script) { value ->
                    if (cont.isActive) cont.resume(value)
                }
            }
        }
}
