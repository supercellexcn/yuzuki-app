package com.yuzuki.app.ui

import android.app.Activity
import android.os.SystemClock
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.yuzuki.app.data.AppContainer
import com.yuzuki.app.data.FilterCond
import com.yuzuki.app.playback.PlaybackController

/**
 * 手机端根布局：顶部状态栏避让 + 内容区 + 底部 3 Tab + 侧栏抽屉 + 全屏播放器。
 * 抽屉挂在根层级，才能盖住底部 Tab（与 web 的抽屉覆盖全屏一致）。
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
    val playState by PlaybackController.state.collectAsState()
    val userName by AppContainer.tokenStore.username.collectAsState(initial = null)
    var lastBackAt by remember { mutableLongStateOf(0L) }
    var drawerOpen by remember { mutableStateOf(false) }

    // 库页数据 / 滚动状态放在 when 分支之外：主页被移出组合时不会被销毁，
    // 从详情 / 收藏 / 历史 / 设置返回时直接复用，位置与已翻页的数据都还在。
    val libraryVm: LibraryListViewModel = viewModel()
    val libraryGridState = rememberLazyStaggeredGridState()
    val libraryListState = rememberLazyListState()

    LaunchedEffect(Unit) {
        FavoritesState.refresh()
        LibraryState.restore(AppContainer.prefs.current)
    }

    fun openLibraryWith(cond: FilterCond) {
        LibraryState.addCond(cond)
        nav.replace(Route.Library)
    }

    // 系统返回键与 UI 返回共用一套语义：抽屉 → 全屏播放器 → 返回栈 / 回主页 → 双击退出
    BackHandler(enabled = true) {
        val now = SystemClock.elapsedRealtime()
        when {
            drawerOpen -> drawerOpen = false
            playState.fullscreen -> PlaybackController.setFullscreen(false)
            nav.backOrHome() -> { }
            now - lastBackAt < 2000 -> activity?.finish()
            else -> {
                lastBackAt = now
                Toast.makeText(context, "再按一次退出", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // 播放页不做状态栏避让：封面模糊背景需要铺进状态栏后面（沉浸），
    // 控件自身的避让交给 PlayerUI 内部的 statusBarsPadding，其它页面维持原行为。
    Box(
        modifier = modifier
            .fillMaxSize()
            .then(if (playState.fullscreen) Modifier else Modifier.statusBarsPadding()),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.weight(1f)) {
                when (val route = nav.current) {
                    is Route.Library -> LibraryScreen(
                        vm = libraryVm,
                        gridState = libraryGridState,
                        listState = libraryListState,
                        onOpenWork = { nav.go(Route.Work(it)) },
                        onOpenDrawer = { drawerOpen = true },
                    )
                    is Route.Work -> WorkDetailScreen(
                        rj = route.rj,
                        onBack = { nav.backOrHome() },
                        onOpenWork = { nav.go(Route.Work(it)) },
                        onOpenLibrary = { nav.replace(Route.Library) },
                        onOpenDrawer = { drawerOpen = true },
                    )
                    is Route.Favorites -> FavoritesScreen(
                        onOpenWork = { nav.go(Route.Work(it)) },
                        onApplyCond = ::openLibraryWith,
                    )
                    is Route.History -> HistoryScreen(
                        onOpenWork = { nav.go(Route.Work(it)) },
                        onBackToLibrary = { nav.replace(Route.Library) },
                        onOpenDrawer = { drawerOpen = true },
                    )
                    is Route.Settings -> SettingsScreen(onBack = { nav.backOrHome() })
                    is Route.ChangePassword -> ChangePasswordScreen(
                        firstLogin = false,
                        onChange = { old, new -> AppContainer.api.changePassword(old, new) },
                    )
                }

                // 全屏播放器画在内容区里（底部 Tab 之上），
                // 这样三大导航始终可见，播放器控件整体上移。
                if (playState.fullscreen) {
                    FullscreenPlayer(
                        onClose = { PlaybackController.setFullscreen(false) },
                        onOpenWork = { rj ->
                            PlaybackController.setFullscreen(false)
                            nav.go(Route.Work(rj))
                        },
                        onApplyCond = ::openLibraryWith,
                    )
                }
            }

            // 底部导航常驻（详情页、播放器页都不再隐藏）
            run {
                MainNavigationBar(
                    selected = when {
                        playState.fullscreen -> MainTab.Player
                        nav.current is Route.History -> MainTab.History
                        else -> MainTab.Library
                    },
                    onSelect = { tab ->
                        when (tab) {
                            MainTab.Library -> {
                                // 播放器是叠加层，库页一直在组合树里；从播放器返回时
                                // 不能触发 scrollToTop（否则会被仍挂载的库页立即消费）。
                                val fromPlayer = playState.fullscreen
                                if (fromPlayer) PlaybackController.setFullscreen(false)
                                if (ScrollRestore.shouldScrollTopOnLibraryTab(
                                        fromPlayer, nav.current == Route.Library,
                                    )
                                ) {
                                    LibraryState.scrollToTop()
                                } else if (nav.current != Route.Library) {
                                    nav.replace(Route.Library)
                                }
                            }
                            MainTab.Player -> PlaybackController.setFullscreen(true)
                            MainTab.History -> {
                                if (playState.fullscreen) PlaybackController.setFullscreen(false)
                                if (nav.current != Route.History) nav.replace(Route.History)
                            }
                        }
                    },
                )
            }
        }

        // 移动端悬浮快捷控件（web MobileDock）：内容之上、抽屉之下
        if (!playState.fullscreen) {
            MobileDock(onGoHome = { nav.replace(Route.Library) })
        }

        SideDrawer(
            visible = drawerOpen,
            userName = userName ?: "",
            onDismiss = { drawerOpen = false },
            onOpenFavorites = { nav.replace(Route.Favorites) },
            onOpenSettings = { nav.go(Route.Settings) },
            onOpenChangePassword = { nav.go(Route.ChangePassword) },
            onChangeServer = {
                libraryVm.clear()
                onChangeServer()
            },
            onLogout = {
                libraryVm.clear()
                onLogout()
            },
            onApplyCond = { cond ->
                LibraryState.addCond(cond)
                nav.replace(Route.Library)
            },
            onAfterNavigate = { drawerOpen = false },
        )
    }
}
