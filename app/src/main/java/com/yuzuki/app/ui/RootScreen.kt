package com.yuzuki.app.ui

import android.app.Activity
import android.os.SystemClock
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.yuzuki.app.data.AppContainer
import com.yuzuki.app.data.CountItem
import com.yuzuki.app.data.FilterCond
import com.yuzuki.app.data.Work
import com.yuzuki.app.playback.PlayerHolder
import com.yuzuki.app.ui.theme.GlassPill
import com.yuzuki.app.ui.theme.GlassSurface
import com.yuzuki.app.ui.theme.ShapeCard
import com.yuzuki.app.ui.theme.ShapeItem
import kotlinx.coroutines.launch

@Composable
fun RootScreen(
    onLogout: () -> Unit,
    onChangeServer: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val nav = rememberAppNav()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val activity = context as? Activity
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val playState by PlayerHolder.state.collectAsState()
    var lastBackAt by remember { mutableLongStateOf(0L) }
    var counts by remember { mutableStateOf<Map<String, List<CountItem>>>(emptyMap()) }
    var recent by remember { mutableStateOf<List<Work>>(emptyList()) }
    var toast by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        FavoritesState.refresh()
        LibraryState.restore(AppContainer.prefs.current)
        counts = mapOf(
            "circle" to AppContainer.api.circles().getOrDefault(emptyList()),
            "series" to AppContainer.api.series().getOrDefault(emptyList()),
            "actor" to AppContainer.api.actors().getOrDefault(emptyList()),
            "tag" to AppContainer.api.tags().getOrDefault(emptyList()),
        )
        recent = AppContainer.api.recentPlays(10).getOrDefault(emptyList())
    }

    BackHandler(enabled = true) {
        val now = SystemClock.elapsedRealtime()
        when {
            playState.fullscreen -> PlayerHolder.setFullscreen(false)
            nav.canBack -> nav.back()
            now - lastBackAt < 2000 -> activity?.finish()
            else -> {
                lastBackAt = now
                Toast.makeText(context, "再按一次退出", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun openLibraryWith(cond: FilterCond) {
        LibraryState.addCond(cond)
        nav.replace(Route.Library)
        scope.launch { drawerState.close() }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(drawerContainerColor = MaterialTheme.colorScheme.surface) {
                DrawerContent(
                    counts = counts,
                    recent = recent,
                    onCond = ::openLibraryWith,
                    onNavigate = { route ->
                        nav.replace(route)
                        scope.launch { drawerState.close() }
                    },
                    onChangeServer = {
                        onChangeServer()
                        scope.launch { drawerState.close() }
                    },
                    onLogout = {
                        onLogout()
                        scope.launch { drawerState.close() }
                    },
                )
            }
        },
    ) {
        Box(modifier = modifier.fillMaxSize().statusBarsPadding()) {
            WallpaperBackground()
            Column(modifier = Modifier.fillMaxSize()) {
                Box(modifier = Modifier.weight(1f)) {
                    when (val route = nav.current) {
                        is Route.Library -> LibraryScreen(
                            onOpenDrawer = { scope.launch { drawerState.open() } },
                            onOpenWork = { nav.go(Route.Work(it)) },
                        )
                        is Route.Work -> WorkDetailScreen(
                            rj = route.rj,
                            onBack = { nav.back() },
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
                if (playState.current != null && !playState.fullscreen) {
                    MiniPlayer(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                        onExpand = { PlayerHolder.setFullscreen(true) },
                    )
                }
                BottomTabs(
                    selected = if (nav.current is Route.History) BottomTab.History else BottomTab.Library,
                    onTabClick = { tab ->
                        when (tab) {
                            BottomTab.Library -> nav.replace(Route.Library)
                            BottomTab.History -> nav.replace(Route.History)
                            BottomTab.Player -> {
                                if (playState.current != null) PlayerHolder.setFullscreen(true)
                                else toast = "暂无播放"
                            }
                        }
                    },
                )
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

            toast?.let { message ->
                GlassPill(
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 72.dp),
                ) {
                    Text(message, style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                }
                LaunchedEffect(message) {
                    kotlinx.coroutines.delay(1500)
                    toast = null
                }
            }
        }
    }
}

@Composable
private fun DrawerContent(
    counts: Map<String, List<CountItem>>,
    recent: List<Work>,
    onCond: (FilterCond) -> Unit,
    onNavigate: (Route) -> Unit,
    onChangeServer: () -> Unit,
    onLogout: () -> Unit,
) {
    var expanded by remember { mutableStateOf<String?>(null) }
    GlassSurface(
        modifier = Modifier.fillMaxWidth().padding(12.dp),
        shape = ShapeCard,
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            item { Text("Yuzuki", style = MaterialTheme.typography.titleLarge) }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    GlassPill(onClick = { onNavigate(Route.Favorites) }) {
                        Text("我的收藏", modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp))
                    }
                    GlassPill(onClick = { onNavigate(Route.History) }) {
                        Text("最近播放", modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp))
                    }
                }
            }
            listOf(
                "circle" to "全部社团",
                "series" to "全部系列",
                "actor" to "全部声优",
                "tag" to "全部标签",
            ).forEach { (type, label) ->
                item {
                    GlassPill(onClick = { expanded = if (expanded == type) null else type }) {
                        Text("$label (${counts[type]?.size ?: 0})",
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp))
                    }
                }
                if (expanded == type) {
                    items(counts[type].orEmpty().take(50)) { item ->
                        Text(
                            "${item.name}  ${item.count}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onCond(FilterCond(type, item.name)) }
                                .padding(horizontal = 14.dp, vertical = 6.dp),
                        )
                    }
                }
            }
            item { Text("最近播放", style = MaterialTheme.typography.titleSmall) }
            items(recent, key = { it.rjCode }) { work ->
                GlassSurface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = ShapeItem,
                    onClick = { onNavigate(Route.Work(work.rjCode)) },
                ) {
                    Text(work.title, style = MaterialTheme.typography.bodySmall, maxLines = 1,
                        modifier = Modifier.padding(10.dp))
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    GlassPill(onClick = { onNavigate(Route.Settings) }) {
                        Text("设置", modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp))
                    }
                    GlassPill(onClick = { onNavigate(Route.ChangePassword) }) {
                        Text("修改密码", modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp))
                    }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    GlassPill(onClick = onChangeServer) {
                        Text("服务地址", modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp))
                    }
                    GlassPill(onClick = onLogout) {
                        Text("退出登录", modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp))
                    }
                }
            }
        }
    }
}
