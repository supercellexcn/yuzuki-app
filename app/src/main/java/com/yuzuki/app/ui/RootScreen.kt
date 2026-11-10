package com.yuzuki.app.ui

import android.webkit.WebView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.yuzuki.app.R
import com.yuzuki.app.data.ServerConfig
import com.yuzuki.app.ui.theme.ShapeContainer
import com.yuzuki.app.web.NavController
import com.yuzuki.app.web.WebTab
import com.yuzuki.app.web.YuzukiWebView
import kotlinx.coroutines.launch

/**
 * 方案 §5.1：WebView 用 padding(bottom=56dp) 为原生 Tab 栏预留空间，Tab 栏不覆盖 WebView。
 * 因此前端 position:fixed 的 .bottom-player 自然贴在 Tab 栏上方，innerHeight 与
 * MobileDock 的 clampY 也自动适配，两层不重叠。
 */
@Composable
fun RootScreen(
    baseUrl: String,
    token: String,
    serverConfig: ServerConfig,
    webViewRef: MutableState<WebView?>,
    onUnreachable: () -> Unit,
    onBridgeUnavailable: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var selectedTab by remember { mutableStateOf(WebTab.LIBRARY) }
    val noPlaybackText = stringResource(R.string.toast_no_playback)

    val navController = remember(baseUrl) {
        NavController(webViewProvider = { webViewRef.value }, baseUrl = baseUrl)
    }

    fun handleTab(tab: WebTab) {
        if (tab != WebTab.PLAYER) selectedTab = tab
        navController.go(
            tab = tab,
            scope = scope,
            onNoPlayback = {
                scope.launch { snackbarHostState.showSnackbar(noPlaybackText) }
            },
        )
    }

    Box(modifier.fillMaxSize().statusBarsPadding()) {
        key(baseUrl, token) {
            AndroidView(
                modifier = Modifier.fillMaxSize().padding(bottom = TabBarHeight),
                factory = { ctx ->
                    YuzukiWebView(
                        context = ctx,
                        baseUrl = baseUrl,
                        token = token,
                        serverConfig = serverConfig,
                        onUnreachable = onUnreachable,
                        onBridgeUnavailable = onBridgeUnavailable,
                        onPathChanged = { path ->
                            selectedTab = if (path == "/history") WebTab.HISTORY else WebTab.LIBRARY
                        },
                    ).also { webViewRef.value = it }
                },
                onRelease = { view ->
                    if (webViewRef.value === view) webViewRef.value = null
                    view.destroy()
                },
            )
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = TabBarHeight + 8.dp),
        ) { data ->
            Snackbar(
                snackbarData = data,
                shape = ShapeContainer,
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        BottomTabs(
            selected = selectedTab,
            onTabClick = ::handleTab,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}
