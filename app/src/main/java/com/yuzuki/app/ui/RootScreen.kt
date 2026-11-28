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
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.yuzuki.app.data.AppContainer
import com.yuzuki.app.data.FilterCond
import com.yuzuki.app.data.MainTab
import com.yuzuki.app.playback.PlaybackController
import com.yuzuki.app.ui.theme.WebColors
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * 手机端根布局：横向 Pager（4 个板块）+ 底部 4 Tab + 浮动圆控件。
 *
 * **板块顺序可配**（设置页「板块顺序」，本机 `app_tab_order`）：顺序既决定底部 Tab 的左右次序，
 * 也决定 Pager 每一页放哪块内容 —— 全篇只用 [pageOf] / [tabOf] 在两套索引之间换算，
 * 所以换序后 Tab、翻页、面板内容永远指向同一块，不会出现「点收藏翻到历史」。
 *
 * 滑动语义与手指位移严格跟手：
 *   从第 1 页向左拖 → 第 2 页 → 第 3 页 → 第 4 页；反向同理；
 *   第 1 页（已在最左）继续向右拖的溢出量用来把浮动圆控件拉出来，控件层本身也可向左拖收回。
 *   顺序可变后「右滑拉出圆组」跟的是**排第一的板块**（只有最左页才有右拖溢出），
 *   库页顶栏的菜单按钮仍然是随时可用的备用入口。
 * 详情 / 设置 / 改密不是板块，作为覆盖层盖在 Pager 之上，此时不响应翻页。
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
    // 浮动圆控件的滑入基准宽度（没有面板了，只用来把手势位移换算成 0–1 的进度）
    val drawerSwipe = DrawerGeometry.swipeDp(
        LocalConfiguration.current.screenWidthDp.toFloat(),
    ).dp
    val drawerSwipePx = with(LocalDensity.current) { drawerSwipe.toPx() }

    // 库页数据 / 滚动状态放在 Pager 之外：翻到收藏 / 播放器 / 历史再回来时位置与已翻页的数据都还在。
    val libraryVm: LibraryListViewModel = viewModel()
    val libraryGridState = rememberLazyStaggeredGridState()
    val libraryListState = rememberLazyListState()

    // 板块顺序：设置页改完写本机 DataStore，这里订阅后 Tab 与 Pager 一起重排
    val tabOrder by AppContainer.tabOrder.state.collectAsState()
    val tabs = remember(tabOrder) { tabOrder.sanitized().order }

    /** 板块 → Pager 页码。 */
    fun pageOf(tab: MainTab): Int = tabs.indexOf(tab).coerceAtLeast(0)

    /** Pager 页码 → 板块。 */
    fun tabOf(page: Int): MainTab = tabs.getOrElse(page) { MainTab.Library }

    // 非板块页（详情 / 设置 / 改密）以覆盖层呈现，盖住 Pager
    val overlayRoute = nav.current.takeIf {
        it !is Route.Library && it !is Route.Favorites && it !is Route.History
    }

    val pagerState = rememberPagerState(
        initialPage = when {
            playState.fullscreen -> pageOf(MainTab.Player)
            nav.current is Route.History -> pageOf(MainTab.History)
            nav.current is Route.Favorites -> pageOf(MainTab.Favorites)
            else -> pageOf(MainTab.Library)
        },
    ) { tabs.size }

    // 换序时保持「当前这块」不跳：settledTab 记住停在哪块，顺序一变就把 Pager 摆到它的新页码。
    // 只依赖 settledPage 更新（不含 tabs），否则换序瞬间会用新顺序去解释旧页码，记错板块。
    var settledTab by remember { mutableStateOf(tabOf(pagerState.settledPage)) }
    LaunchedEffect(tabs) {
        val target = tabs.indexOf(settledTab)
        if (target >= 0 && pagerState.settledPage != target) pagerState.scrollToPage(target)
    }

    LaunchedEffect(Unit) {
        FavoritesState.refresh()
    }

    fun openLibraryWith(cond: FilterCond) {
        LibraryState.addCond(cond)
        nav.replace(Route.Library)
        scope.launch { pagerState.animateScrollToPage(pageOf(MainTab.Library)) }
    }

    /**
     * 从覆盖层（详情）点文件播放：详情盖在 Pager 之上，只置全屏标志的话播放器页看不见，
     * 所以先把覆盖层收起（与底部「播放器」Tab 的做法一致），再把 Pager 落到播放器页。
     */
    fun openPlayerFromOverlay() {
        PlaybackController.setFullscreen(true)
        if (PlayerNav.shouldCloseOverlay(nav.current is Route.Library)) nav.replace(Route.Library)
        scope.launch { pagerState.animateScrollToPage(pageOf(MainTab.Player)) }
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

    // Pager 落位 → 同步播放器全屏态与导航态（页码先换算回板块，顺序改了也不会认错页）
    LaunchedEffect(pagerState.settledPage) {
        val tab = tabOf(pagerState.settledPage)
        settledTab = tab
        when (tab) {
            MainTab.Player -> PlaybackController.setFullscreen(true)
            MainTab.Library -> {
                PlaybackController.setFullscreen(false)
                if (nav.current is Route.History || nav.current is Route.Favorites) {
                    nav.replace(Route.Library)
                }
            }
            MainTab.Favorites -> {
                PlaybackController.setFullscreen(false)
                if (nav.current !is Route.Favorites) nav.replace(Route.Favorites)
            }
            MainTab.History -> {
                PlaybackController.setFullscreen(false)
                if (nav.current is Route.Library || nav.current is Route.Favorites) {
                    nav.replace(Route.History)
                }
            }
        }
    }

    // 外部把播放器置为全屏（点 Tab / 冷启动恢复）时，Pager 跟着过去
    LaunchedEffect(playState.fullscreen) {
        val playerPage = pageOf(MainTab.Player)
        if (playState.fullscreen && pagerState.settledPage != playerPage) {
            pagerState.animateScrollToPage(playerPage)
        }
    }

    // 代码改导航（返回键 / 侧栏跳转）时把 Pager 摆回对应板块。
    // 全屏播放器展开期间跳过：否则「详情里点文件播放」时这里会把刚翻到播放器页的 Pager 拽回库页
    // （播放页点 (i) 进详情再点文件就会撞上，此时全屏标志本来就是 true，不会重新触发翻页）。
    LaunchedEffect(nav.current, playState.fullscreen) {
        if (!PlayerNav.shouldSyncPager(playState.fullscreen)) return@LaunchedEffect
        val target = when (nav.current) {
            is Route.Library -> pageOf(MainTab.Library)
            is Route.Favorites -> pageOf(MainTab.Favorites)
            is Route.History -> pageOf(MainTab.History)
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
            playState.fullscreen -> scope.launch { pagerState.animateScrollToPage(pageOf(MainTab.Library)) }
            nav.backOrHome() -> { }
            now - lastBackAt < 2000 -> activity?.finish()
            else -> {
                lastBackAt = now
                Toast.makeText(context, "再按一次退出", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // 最左页（Page 0）向右拖时 Pager 没有上一页可翻；这里在 Initial 阶段"旁路观察"
    // （不消费事件，不与 Pager 抢手势），把右拖位移映射成侧栏位移。
    // 顺序可配后锚点是「排第一的板块」而不是库页：只有 Page 0 才有右拖溢出，
    // 库页排到第 2 位时在库页右滑是正常翻回上一页，圆组改用库页顶栏的菜单按钮打开。
    //
    // 防误触：列表纵向滑动时手指难免带一点横向抖动，所以必须"向右位移明显大于纵向位移"
    // 才拉起侧栏；已经上手拖侧栏后若纵向分量转为占主导，立刻回弹并放弃这次手势。
    val drawerObserve = Modifier.pointerInput(pagerState, playState.fullscreen, overlayRoute) {
        val startSlop = 16.dp.toPx()      // 起手门槛（≈48px），轻微抖动不会触发
        val verticalSlop = 24.dp.toPx()
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            // 覆盖层（设置 / 详情 / 收藏 / 改密）整层不参与侧栏手势：
            // 设置页的滑块、收藏页的横向标签都是按住横滑，外层旁路观察看不到子级消费，
            // 不排除的话拖滑块就会把侧栏拉出来。覆盖层里也没有需要拉侧栏的入口。
            if (overlayRoute != null) return@awaitEachGesture
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
                    if (drawerFraction <= 0f && pagerState.currentPage == 0 &&
                        DrawerDrag.canStart(
                            sumX = sumX,
                            absYAcc = absYAcc,
                            startSlop = startSlop,
                            innerClaimed = DrawerGate.claimedByInner,
                            fullscreen = playState.fullscreen,
                            // 上面已经对覆盖层整层早退，走到这里的这一帧必然没有覆盖层
                            overlayOpen = false,
                        )
                    ) {
                        dragging = true
                        // 圆组固定贴底，不再记录起手高度：从屏幕哪里右滑，圆都在最低端出现
                    }
                } else {
                    // 拖到一半用户其实是往下滑列表 → 回弹收手
                    if (absYAcc > verticalSlop && absYAcc > sumX + verticalSlop) {
                        dragging = false
                        drawerFraction = 0f
                        drawerOpen = false
                        break
                    }
                    drawerFraction = (drawerFraction + dx / drawerSwipePx).coerceIn(0f, 1f)
                    drawerOpen = drawerFraction > 0f
                }
            }
            if (dragging) settleDrawer()
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                // 浮动圆控件浮出时，下层内容整体做一次高斯模糊（半径跟手渐入）：
                // 半导体感来自模糊本身，api 31+ 走 GPU RenderEffect；31 以下 Compose 自动跳过，
                // 只剩 SideDrawer 里的压暗层，不会崩也不会花屏。
                .blur(radius = 14.dp * drawerFraction),
        ) {
            Box(modifier = Modifier.weight(1f).then(drawerObserve)) {
                HorizontalPager(
                    state = pagerState,
                    // 邻页预组合，手指一拖内容就已经在那儿了
                    beyondViewportPageCount = 1,
                    userScrollEnabled = overlayRoute == null && !drawerOpen && !PlaylistDrag.active,
                    modifier = Modifier.fillMaxSize(),
                ) { page ->
                    // 每页必须裁到自己的边界：播放器背景是 blur(Unbounded)，不裁会糊出页外，
                    // 在库页 / 历史页右侧形成一条"播放器的遮罩"。
                    // 页码先换算成板块：顺序改了以后，同一页可能换成了另一块面板。
                    when (tabOf(page)) {
                        MainTab.Library -> Box(Modifier.fillMaxSize().clipToBounds().statusBarsPadding()) {
                            LibraryScreen(
                                vm = libraryVm,
                                gridState = libraryGridState,
                                listState = libraryListState,
                                onOpenWork = { nav.go(Route.Work(it)) },
                                onOpenDrawer = { animateDrawer(1f) },
                            )
                        }
                        MainTab.Favorites -> Box(Modifier.fillMaxSize().clipToBounds().statusBarsPadding()) {
                            FavoritesScreen(
                                onOpenWork = { nav.go(Route.Work(it)) },
                                onApplyCond = ::openLibraryWith,
                            )
                        }
                        MainTab.Player -> Box(Modifier.fillMaxSize().clipToBounds()) {
                            FullscreenPlayer(
                                onClose = { scope.launch { pagerState.animateScrollToPage(pageOf(MainTab.Library)) } },
                                onOpenWork = { rj -> nav.go(Route.Work(rj)) },
                                onApplyCond = ::openLibraryWith,
                            )
                        }
                        MainTab.History -> Box(Modifier.fillMaxSize().clipToBounds().statusBarsPadding()) {
                            HistoryScreen(
                                onOpenWork = { nav.go(Route.Work(it)) },
                            )
                        }
                    }
                }

                if (overlayRoute != null) {
                    if (overlayRoute is Route.Work) {
                        // 详情是弹出框：这一层不铺不透明底色，框顶那段留白要透出被压暗的底层内容
                        WorkDetailOverlay(
                            rj = overlayRoute.rj,
                            onDismiss = { nav.backOrHome() },
                            onOpenWork = { nav.go(Route.Work(it)) },
                            onOpenLibrary = { nav.replace(Route.Library) },
                            onOpenPlayer = ::openPlayerFromOverlay,
                        )
                    } else {
                        // 设置 / 改密仍是整页覆盖层，必须自带不透明页面底色：
                        // 否则会直接透出下层的库页卡片和播放器模糊封面，半透明面板压在上面很难辨认。
                        Box(
                            Modifier
                                .fillMaxSize()
                                .background(WebColors.PageBg)
                                .statusBarsPadding(),
                        ) {
                            when (overlayRoute) {
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
            }

            // 底部导航常驻，顺序跟随设置；选中态跟随 Pager 当前页（滑动过程中就跟着高亮）
            MainNavigationBar(
                order = tabs,
                selected = tabOf(pagerState.currentPage),
                onSelect = { tab ->
                    when (tab) {
                        MainTab.Library -> {
                            val fromPlayer = tabOf(pagerState.currentPage) == MainTab.Player
                            if (fromPlayer) PlaybackController.setFullscreen(false)
                            if (ScrollRestore.shouldScrollTopOnLibraryTab(
                                    fromPlayer, nav.current == Route.Library,
                                )
                            ) {
                                LibraryState.scrollToTop()
                            }
                            if (nav.current != Route.Library) nav.replace(Route.Library)
                            scope.launch { pagerState.animateScrollToPage(pageOf(MainTab.Library)) }
                        }
                        MainTab.Favorites -> {
                            PlaybackController.setFullscreen(false)
                            if (nav.current != Route.Favorites) nav.replace(Route.Favorites)
                            scope.launch { pagerState.animateScrollToPage(pageOf(MainTab.Favorites)) }
                        }
                        MainTab.Player -> {
                            if (overlayRoute != null) nav.replace(Route.Library)
                            PlaybackController.setFullscreen(true)
                            scope.launch { pagerState.animateScrollToPage(pageOf(MainTab.Player)) }
                        }
                        MainTab.History -> {
                            if (overlayRoute != null) nav.replace(Route.Library)
                            PlaybackController.setFullscreen(false)
                            if (nav.current != Route.History) nav.replace(Route.History)
                            scope.launch { pagerState.animateScrollToPage(pageOf(MainTab.History)) }
                        }
                    }
                },
            )
        }

        // 悬浮快捷控件（MobileDock）已按用户要求移除：回顶 = 重复点「库」Tab，
        // 切视图 / 随机 = 信息流顶部下拉（见 ui/FeedPull.kt）

        SideDrawer(
            fraction = drawerFraction,
            swipeWidth = drawerSwipe,
            onDragBy = { delta ->
                drawerFraction = (drawerFraction + delta).coerceIn(0f, 1f)
                drawerOpen = drawerFraction > 0f
            },
            onDragEnd = { total -> settleDrawer(closing = total < 0f) },
            userName = userName ?: "",
            onDismiss = { animateDrawer(0f) },
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
