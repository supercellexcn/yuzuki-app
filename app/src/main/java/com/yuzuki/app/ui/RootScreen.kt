package com.yuzuki.app.ui

import android.app.Activity
import android.os.SystemClock
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.yuzuki.app.data.AppContainer
import com.yuzuki.app.data.FilterCond
import com.yuzuki.app.playback.PlayerHolder

/**
 * 手机端根布局：顶部状态栏避让 + 内容区 + 底部 3 Tab。
 * 迷你播放条已移除；中间「播放器」Tab 直接打开全屏播放器，筛选 / 排序收进主页底部面板。
 */
@Composable
fun RootScreen(
    onLogout: () -> Unit,
    onChangeServer: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val nav = rememberAppNav()
    val context = LocalContext.current
    val activity = context as? Activity
    val playState by PlayerHolder.state.collectAsState()
    var lastBackAt by remember { mutableLongStateOf(0L) }

    LaunchedEffect(Unit) {
        FavoritesState.refresh()
        LibraryState.restore(AppContainer.prefs.current)
    }

    fun openLibraryWith(cond: FilterCond) {
        LibraryState.addCond(cond)
        nav.replace(Route.Library)
    }

    // 系统返回键与 UI 返回共用一套语义：全屏播放器 → 返回栈 / 回主页 → 双击退出
    BackHandler(enabled = true) {
        val now = SystemClock.elapsedRealtime()
        when {
            playState.fullscreen -> PlayerHolder.setFullscreen(false)
            nav.backOrHome() -> { }
            now - lastBackAt < 2000 -> activity?.finish()
            else -> {
                lastBackAt = now
                Toast.makeText(context, "再按一次退出", Toast.LENGTH_SHORT).show()
            }
        }
    }

    Box(modifier = modifier.fillMaxSize().statusBarsPadding()) {
        Column(modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.weight(1f)) {
                when (val route = nav.current) {
                    is Route.Library -> LibraryScreen(
                        onOpenWork = { nav.go(Route.Work(it)) },
                        onOpenSettings = { nav.go(Route.Settings) },
                        onOpenFavorites = { nav.go(Route.Favorites) },
                        onOpenChangePassword = { nav.go(Route.ChangePassword) },
                        onChangeServer = onChangeServer,
                        onLogout = onLogout,
                    )
                    is Route.Work -> WorkDetailScreen(
                        rj = route.rj,
                        onBack = { nav.backOrHome() },
                        onOpenWork = { nav.go(Route.Work(it)) },
                    )
                    is Route.Favorites -> FavoritesScreen(
                        onOpenWork = { nav.go(Route.Work(it)) },
                        onApplyCond = ::openLibraryWith,
                    )
                    is Route.History -> HistoryScreen(
                        onOpenWork = { nav.go(Route.Work(it)) },
                    )
                    is Route.Settings -> SettingsScreen()
                    is Route.ChangePassword -> ChangePasswordScreen(
                        firstLogin = false,
                        onChange = { old, new -> AppContainer.api.changePassword(old, new) },
                    )
                }
            }

            // 详情页沉浸式阅读，隐藏底部 Tab；返回箭头 + 系统返回键负责退出
            if (nav.current !is Route.Work && !playState.fullscreen) {
                MainNavigationBar(
                    selected = when (nav.current) {
                        is Route.History -> MainTab.History
                        else -> MainTab.Library
                    },
                    onSelect = { tab ->
                        when (tab) {
                            MainTab.Library -> {
                                if (nav.current == Route.Library) LibraryState.scrollToTop()
                                else nav.replace(Route.Library)
                            }
                            MainTab.Player -> PlayerHolder.setFullscreen(true)
                            MainTab.History -> {
                                if (nav.current != Route.History) nav.replace(Route.History)
                            }
                        }
                    },
                )
            }
        }

        if (playState.fullscreen) {
            FullscreenPlayer(
                onClose = { PlayerHolder.setFullscreen(false) },
                onOpenWork = { rj ->
                    PlayerHolder.setFullscreen(false)
                    nav.go(Route.Work(rj))
                },
            )
        }
    }
}
