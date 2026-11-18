package com.yuzuki.app.ui

import android.app.Activity
import android.os.SystemClock
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.yuzuki.app.data.AppContainer
import com.yuzuki.app.data.FilterCond
import com.yuzuki.app.playback.PlaybackController
import com.yuzuki.app.ui.theme.WebColors
import kotlinx.coroutines.launch
import kotlin.math.abs

// 三个板块在横向上是一条连续的纸带：库 ←→ 播放器 ←→ 历史，侧栏贴在库页左侧。
private const val LIBRARY_PAGE = 0
private const val PLAYER_PAGE = 1
private const val HISTORY_PAGE = 2

/**
 * 手机端根布局：横向 Pager（库 / 播放器 / 历史）+ 底部 3 Tab + 侧栏抽屉。
 *
 * 滑动语义与手指位移严格跟手：
 *   库页向左拖 → 播放器 → 历史；历史页向右拖 → 播放器 → 库；
 *   库页（已在最左）继续向右拖的溢出量用来把侧栏拉出来，侧栏本身也可向左拖收回。
 * 详情 / 收藏 / 设置 / 改密不是板块，作为覆盖层盖在 Pager 之上，此时不响应翻页。
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
    var drawerFraction by remember { mutableFloatStateOf(0f) }
    var drawerOpen by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val drawerWidthPx = with(LocalDensity.current) { DrawerWidth.toPx() }

    // 库页数据 / 滚动状态放在 Pager 之外：翻到播放器 / 历史再回来时位置与已翻页的数据都还在。
    val libraryVm: LibraryListViewModel = viewModel()
    val libraryGridState = rememberLazyStaggeredGridState()
    val libraryListState = rememberLazyListState()

    // 非板块页（详情 / 收藏 / 设置 / 改密）以覆盖层呈现，盖住 Pager
    val overlayRoute = nav.current.takeIf { it !is Route.Library && it !is Route.History }

    val pagerState = rememberPagerState(
        initialPage = when {
            playState.fullscreen -> PLAYER_PAGE
            nav.current is Route.History -> HISTORY_PAGE
            else -> LIBRARY_PAGE
        },
    ) { 3 }

    LaunchedEffect(Unit) {
        FavoritesState.refresh()
        LibraryState.restore(AppContainer.prefs.current)
    }

    fun openLibraryWith(cond: FilterCond) {
        LibraryState.addCond(cond)
        nav.replace(Route.Library)
        scope.launch { pagerState.animateScrollToPage(LIBRARY_PAGE) }
    }

    /**
     * 从覆盖层（详情）点文件播放：详情盖在 Pager 之上，只置全屏标志的话播放器页看不见，
     * 所以先把覆盖层收起（与底部「播放器」Tab 的做法一致），再把 Pager 落到播放器页。
     */
    fun openPlayerFromOverlay() {
        PlaybackController.setFullscreen(true)
        if (PlayerNav.shouldCloseOverlay(nav.current is Route.Library)) nav.replace(Route.Library)
        scope.launch { pagerState.animateScrollToPage(PLAYER_PAGE) }
    }

    fun animateDrawer(target: Float) {
        scope.launch {
            animate(drawerFraction, target, animationSpec = tween(200)) { v, _ -> drawerFraction = v }
            drawerOpen = target > 0.5f
        }
    }

    /**
     * 松手吸附。展开与收起用不同门槛：
     * - 展开（从库页右拖）：过 40% 才吸全开，避免轻微右拖就弹出来；
     * - 收起（在面板上左拖）：只要拖过 22%（≈57dp）就关，不用一路滑到底。
     */
    fun settleDrawer(closing: Boolean = false) {
        val target = if (closing) {
            if (drawerFraction < 0.78f) 0f else 1f
        } else {
            if (drawerFraction > 0.4f) 1f else 0f
        }
        animateDrawer(target)
    }

    // Pager 落位 → 同步播放器全屏态与导航态
    LaunchedEffect(pagerState.settledPage) {
        when (pagerState.settledPage) {
            PLAYER_PAGE -> PlaybackController.setFullscreen(true)
            LIBRARY_PAGE -> {
                PlaybackController.setFullscreen(false)
                if (nav.current is Route.History) nav.replace(Route.Library)
            }
            HISTORY_PAGE -> {
                PlaybackController.setFullscreen(false)
                if (nav.current is Route.Library) nav.replace(Route.History)
            }
        }
    }

    // 外部把播放器置为全屏（点 Tab / 冷启动恢复）时，Pager 跟着过去
    LaunchedEffect(playState.fullscreen) {
        if (playState.fullscreen && pagerState.settledPage != PLAYER_PAGE) {
            pagerState.animateScrollToPage(PLAYER_PAGE)
        }
    }

    // 代码改导航（返回键 / 侧栏跳转）时把 Pager 摆回对应板块。
    // 全屏播放器展开期间跳过：否则「详情里点文件播放」时这里会把刚翻到播放器页的 Pager 拽回库页
    // （播放页点 (i) 进详情再点文件就会撞上，此时全屏标志本来就是 true，不会重新触发翻页）。
    LaunchedEffect(nav.current, playState.fullscreen) {
        if (!PlayerNav.shouldSyncPager(playState.fullscreen)) return@LaunchedEffect
        val target = when (nav.current) {
            is Route.Library -> LIBRARY_PAGE
            is Route.History -> HISTORY_PAGE
            else -> null
        }
        if (target != null && pagerState.settledPage != target && !pagerState.isScrollInProgress) {
            pagerState.scrollToPage(target)
        }
    }

    // 系统返回键与 UI 返回共用一套语义：侧栏 → 播放器 → 返回栈 / 回主页 → 双击退出
    BackHandler(enabled = true) {
        val now = SystemClock.elapsedRealtime()
        when {
            drawerFraction > 0f -> animateDrawer(0f)
            playState.fullscreen -> scope.launch { pagerState.animateScrollToPage(LIBRARY_PAGE) }
            nav.backOrHome() -> { }
            now - lastBackAt < 2000 -> activity?.finish()
            else -> {
                lastBackAt = now
                Toast.makeText(context, "再按一次退出", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // 库页已在最左，向右拖时 Pager 没有上一页可翻；这里在 Initial 阶段"旁路观察"
    // （不消费事件，不与 Pager 抢手势），把右拖位移映射成侧栏位移。
    //
    // 防误触：列表纵向滑动时手指难免带一点横向抖动，所以必须"向右位移明显大于纵向位移"
    // 才拉起侧栏；已经上手拖侧栏后若纵向分量转为占主导，立刻回弹并放弃这次手势。
    val drawerObserve = Modifier.pointerInput(pagerState, playState.fullscreen) {
        val startSlop = 16.dp.toPx()      // 起手门槛（≈48px），轻微抖动不会触发
        val verticalSlop = 24.dp.toPx()
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            var lastX = down.position.x
            var lastY = down.position.y
            var sumX = 0f      // 水平位移（右为正）
            var absYAcc = 0f   // 纵向累计绝对值：上下抖动不会互相抵消
            var dragging = false
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                val dx = change.position.x - lastX
                val dy = change.position.y - lastY
                lastX = change.position.x
                lastY = change.position.y
                if (!change.pressed) break
                sumX += dx
                absYAcc += abs(dy)
                if (!dragging) {
                    // 内层横向滚动容器（详情页「相关推荐」）按下时就已认领本轮手势，
                    // 滚到最左端再右滑也不会把侧栏拉出来
                    if (drawerFraction <= 0f && pagerState.currentPage == LIBRARY_PAGE &&
                        DrawerDrag.canStart(
                            sumX = sumX,
                            absYAcc = absYAcc,
                            startSlop = startSlop,
                            innerClaimed = DrawerGate.claimedByInner,
                            fullscreen = playState.fullscreen,
                        )
                    ) {
                        dragging = true
                    }
                } else {
                    // 拖到一半用户其实是往下滑列表 → 回弹收手
                    if (absYAcc > verticalSlop && absYAcc > sumX + verticalSlop) {
                        dragging = false
                        drawerFraction = 0f
                        drawerOpen = false
                        break
                    }
                    drawerFraction = (drawerFraction + dx / drawerWidthPx).coerceIn(0f, 1f)
                    drawerOpen = drawerFraction > 0f
                }
            }
            if (dragging) settleDrawer()
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.weight(1f).then(drawerObserve)) {
                HorizontalPager(
                    state = pagerState,
                    // 邻页预组合，手指一拖内容就已经在那儿了
                    beyondViewportPageCount = 1,
                    userScrollEnabled = overlayRoute == null && !drawerOpen,
                    modifier = Modifier.fillMaxSize(),
                ) { page ->
                    // 每页必须裁到自己的边界：播放器背景是 blur(Unbounded)，不裁会糊出页外，
                    // 在库页 / 历史页右侧形成一条"播放器的遮罩"。
                    when (page) {
                        LIBRARY_PAGE -> Box(Modifier.fillMaxSize().clipToBounds().statusBarsPadding()) {
                            LibraryScreen(
                                vm = libraryVm,
                                gridState = libraryGridState,
                                listState = libraryListState,
                                onOpenWork = { nav.go(Route.Work(it)) },
                                onOpenDrawer = { animateDrawer(1f) },
                            )
                        }
                        PLAYER_PAGE -> Box(Modifier.fillMaxSize().clipToBounds()) {
                            FullscreenPlayer(
                                onClose = { scope.launch { pagerState.animateScrollToPage(LIBRARY_PAGE) } },
                                onOpenWork = { rj -> nav.go(Route.Work(rj)) },
                                onApplyCond = ::openLibraryWith,
                            )
                        }
                        else -> Box(Modifier.fillMaxSize().clipToBounds().statusBarsPadding()) {
                            HistoryScreen(
                                onOpenWork = { nav.go(Route.Work(it)) },
                                onBackToLibrary = { nav.replace(Route.Library) },
                                onOpenDrawer = { animateDrawer(1f) },
                            )
                        }
                    }
                }

                if (overlayRoute != null) {
                    // 覆盖层必须自带不透明页面底色：详情 / 收藏 / 设置原本直接透出下面的
                    // 库页卡片和播放器模糊封面，半透明面板压在上面很难辨认。
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(WebColors.PageBg)
                            .statusBarsPadding(),
                    ) {
                        when (overlayRoute) {
                            is Route.Work -> WorkDetailScreen(
                                rj = overlayRoute.rj,
                                onBack = { nav.backOrHome() },
                                onOpenWork = { nav.go(Route.Work(it)) },
                                onOpenLibrary = { nav.replace(Route.Library) },
                                onOpenDrawer = { animateDrawer(1f) },
                                onOpenPlayer = ::openPlayerFromOverlay,
                            )
                            is Route.Favorites -> FavoritesScreen(
                                onOpenWork = { nav.go(Route.Work(it)) },
                                onApplyCond = ::openLibraryWith,
                            )
                            is Route.Settings -> SettingsScreen(onBack = { nav.backOrHome() })
                            is Route.ChangePassword -> ChangePasswordScreen(
                                firstLogin = false,
                                onChange = { old, new -> AppContainer.api.changePassword(old, new) },
                            )
                            else -> Unit
                        }
                    }
                }
            }

            // 底部导航常驻，选中态跟随 Pager 当前页（滑动过程中就跟着高亮）
            MainNavigationBar(
                selected = when (pagerState.currentPage) {
                    PLAYER_PAGE -> MainTab.Player
                    HISTORY_PAGE -> MainTab.History
                    else -> MainTab.Library
                },
                onSelect = { tab ->
                    when (tab) {
                        MainTab.Library -> {
                            val fromPlayer = pagerState.currentPage == PLAYER_PAGE
                            if (fromPlayer) PlaybackController.setFullscreen(false)
                            if (ScrollRestore.shouldScrollTopOnLibraryTab(
                                    fromPlayer, nav.current == Route.Library,
                                )
                            ) {
                                LibraryState.scrollToTop()
                            }
                            if (nav.current != Route.Library) nav.replace(Route.Library)
                            scope.launch { pagerState.animateScrollToPage(LIBRARY_PAGE) }
                        }
                        MainTab.Player -> {
                            if (overlayRoute != null) nav.replace(Route.Library)
                            PlaybackController.setFullscreen(true)
                            scope.launch { pagerState.animateScrollToPage(PLAYER_PAGE) }
                        }
                        MainTab.History -> {
                            if (overlayRoute != null) nav.replace(Route.Library)
                            PlaybackController.setFullscreen(false)
                            if (nav.current != Route.History) nav.replace(Route.History)
                            scope.launch { pagerState.animateScrollToPage(HISTORY_PAGE) }
                        }
                    }
                },
            )
        }

        // 悬浮快捷控件（MobileDock）已按用户要求移除：回顶 = 重复点「库」Tab，
        // 切视图 / 随机 = 信息流顶部下拉（见 ui/FeedPull.kt）

        SideDrawer(
            fraction = drawerFraction,
            onDragBy = { delta ->
                drawerFraction = (drawerFraction + delta).coerceIn(0f, 1f)
                drawerOpen = drawerFraction > 0f
            },
            onDragEnd = { total -> settleDrawer(closing = total < 0f) },
            userName = userName ?: "",
            onDismiss = { animateDrawer(0f) },
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
            onAfterNavigate = { animateDrawer(0f) },
        )

        // App 内轻提示（收藏 / 随机 / 循环切换），挂在最顶层，抽屉之上也能看到
        AppToastHost()
    }
}
