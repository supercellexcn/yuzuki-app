package com.yuzuki.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yuzuki.app.data.AppContainer
import com.yuzuki.app.data.CountItem
import com.yuzuki.app.data.FilterCond
import com.yuzuki.app.ui.theme.LocalAppearance
import com.yuzuki.app.ui.theme.ShapePill
import com.yuzuki.app.ui.theme.SheetShape
import com.yuzuki.app.ui.theme.WebColors
import com.yuzuki.app.ui.theme.pillTextStyle
import kotlin.math.roundToInt

/**
 * 浮动圆控件的几何（纯数值，JVM 单测直接断言，不依赖 Compose 运行时）。
 *
 * 这里**没有侧栏面板**：滑动只是把一组圆控件浮到内容之上，整体随手指水平位移进出。
 * 圆组**固定贴底**：不论从屏幕哪个高度右滑拉起，都落在底部导航栏上方同一位置，
 * 不再跟着起手高度上下浮动（2026-11-25 按用户要求改）。
 *
 * 尺寸不走「按屏宽等分」——那样 360 屏上圆直径会到 71dp，铺满整行，看起来又大又散。
 * 按市场主流做法（Material 3 FAB 56dp / 24dp 图标 / 16dp 间距，AssistiveTouch、
 * Telegram、Google Photos 的浮动圆菜单都是这一套）固定成 56dp，整组居中。
 */
internal object DrawerGeometry {
    /** 滑入 / 收起时手指位移换算成「全开」的基准宽度（占屏宽比例）。 */
    const val SWIPE_RATIO = 0.8f
    /** 圆直径：M3 FAB 标准尺寸，全版本统一，不随屏宽变化。 */
    const val CIRCLE_DP = 56f
    /** 圆内图标占圆径的比例：56 × 0.43 ≈ 24dp，正是 Material 标准图标尺寸。 */
    const val ICON_RATIO = 0.43f
    /** 圆与圆之间的水平 / 垂直间距（Material 的 4dp 栅格：16dp）。 */
    const val GAP_DP = 16f

    fun swipeDp(screenWidthDp: Float): Float = screenWidthDp.coerceAtLeast(240f) * SWIPE_RATIO

    /** 圆组的整体宽度（4 列 + 3 个间距），用于让各行左对齐并整体居中。 */
    fun groupWidthDp(
        circleDp: Float = CIRCLE_DP,
        gapDp: Float = GAP_DP,
        columns: Int = 4,
    ): Float = circleDp * columns + gapDp * (columns - 1)

    /** 圆组底边到屏幕底的距离：底部导航栏 + 固定 18dp 间隙，与起手高度无关。 */
    fun bottomPadDp(tabBarDp: Float, gapDp: Float): Float = tabBarDp + gapDp
}

private data class SideEntry(val type: String, val label: String)

/** 圆内小字去掉「全部」二字：社团 / 系列 / 声优 / 标签。 */
private val SIDE_FILTERS = listOf(
    SideEntry("circle", "社团"),
    SideEntry("series", "系列"),
    SideEntry("actor", "声优"),
    SideEntry("tag", "标签"),
)

/** 圆组与底部导航栏之间留出的距离。 */
private val BottomOrbGap = 18.dp
private val OrbRowGap = DrawerGeometry.GAP_DP.dp

/**
 * 浮动圆控件层（取代原来的侧栏抽屉）。
 *
 * 没有面板、没有遮罩：
 * - 在库页右拖超过门槛后，这组圆随水平位移从左浮入（跟手，不是固定时长动画）；
 * - 圆组**固定贴底**：上排 4 个筛选圆（社团 / 系列 / 声优 / 标签），下排账号 / 设置两个圆，
 *   底边压在底部导航栏上方 18dp，与手指从哪里按下去无关；
 * - **没有收藏圆**：收藏入口已改到底部导航栏的星标 Tab；
 * - 点圆外的空白关闭，在控件层上向左拖也能收起（位移实时回传给 RootScreen）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SideDrawer(
    fraction: Float,
    /** 手势位移换算基准宽度：拖过这个宽度算「全开」。 */
    swipeWidth: Dp,
    onDragBy: (Float) -> Unit,
    /** 松手回调，参数为整段拖动的水平位移（px，左负右正），用于区分「收起」与「回弹展开」 */
    onDragEnd: (Float) -> Unit,
    userName: String,
    onDismiss: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenChangePassword: () -> Unit,
    onChangeServer: () -> Unit,
    onLogout: () -> Unit,
    onApplyCond: (FilterCond) -> Unit,
    onAfterNavigate: () -> Unit,
) {
    val appearance = LocalAppearance.current
    // 底部弹层仍用不透明面板色：它是完整的 Sheet，不是浮动控件
    val panelSolid = WebColors.panel(appearance.navColor, 1f)
    val accent = MaterialTheme.colorScheme.primary
    val onAccent = MaterialTheme.colorScheme.onPrimary
    val accentSoft = accent.copy(alpha = 0.2f)

    var openType by remember { mutableStateOf<String?>(null) }
    var counts by remember { mutableStateOf<Map<String, List<CountItem>>>(emptyMap()) }

    val density = LocalDensity.current
    val swipePx = with(density) { swipeWidth.toPx() }
    // 成熟浮动圆菜单的固定尺寸：56dp 圆 / 24dp 图标 / 16dp 间距，整组居中
    val circle = DrawerGeometry.CIRCLE_DP.dp
    val groupWidth = DrawerGeometry.groupWidthDp().dp
    val gap = DrawerGeometry.GAP_DP.dp
    // 圆组固定贴底：底边永远压在底部导航栏上方 18dp
    val bottomPad = DrawerGeometry.bottomPadDp(TabBarHeight.value, BottomOrbGap.value).dp

    LaunchedEffect(fraction > 0f) {
        if (fraction <= 0f) return@LaunchedEffect
        if (counts.isNotEmpty()) return@LaunchedEffect
        counts = mapOf(
            "circle" to AppContainer.api.circles().getOrDefault(emptyList()),
            "series" to AppContainer.api.series().getOrDefault(emptyList()),
            "actor" to AppContainer.api.actors().getOrDefault(emptyList()),
            "tag" to AppContainer.api.tags().getOrDefault(emptyList()),
        )
    }

    if (fraction <= 0f) return

    Box(
        modifier = Modifier
            .fillMaxSize()
            // 透明压暗层：圆浮在内容之上时把底下的列表压暗一点，文字 / 图片不会和圆抢视线
            .background(Color.Black.copy(alpha = 0.45f * fraction))
            // 向左拖即收起，位移实时回传给 RootScreen
            .pointerInput(swipePx) {
                var total = 0f
                detectHorizontalDragGestures(
                    onDragStart = { total = 0f },
                    onHorizontalDrag = { _, dragAmount ->
                        total += dragAmount
                        onDragBy(dragAmount / swipePx)
                    },
                    onDragEnd = { onDragEnd(total) },
                    onDragCancel = { onDragEnd(total) },
                )
            }
            // 点圆外的空白关闭。圆自身的 clickable 会消费事件，tap 不会跟着触发；
            // 这一层同时挡掉了下层底部 Tab 的点击
            .pointerInput(Unit) {
                detectTapGestures { onDismiss() }
            },
    ) {
        // 内容层：跟手水平位移，圆组整体从屏幕左侧浮入
        Box(
            modifier = Modifier
                .fillMaxSize()
                .offset { IntOffset((-swipePx * (1f - fraction)).roundToInt(), 0) },
        ) {
            // ---- 圆组：固定贴在底部导航栏上方 ----
            // 上排 = 4 个筛选圆，下排 = 账号（左）/ 设置（右）。
            // 圆组位置只由底部导航栏与固定间距决定，不再读起手高度：从屏幕任何位置右滑，
            // 圆都从同一个「最低端」浮出，肌肉记忆不会丢。
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(bottom = bottomPad),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(OrbRowGap),
            ) {
                // 上排：4 个筛选圆
                Row(
                    modifier = Modifier.width(groupWidth),
                    horizontalArrangement = Arrangement.spacedBy(gap),
                ) {
                    SIDE_FILTERS.forEach { entry ->
                        val active = counts[entry.type].orEmpty().count {
                            LibraryState.conds.any { c -> c.type == entry.type && c.value == it.name }
                        }
                        Orb(
                            size = circle,
                            active = active > 0,
                            badge = active,
                            accent = accent,
                            onAccent = onAccent,
                            onClick = { openType = entry.type },
                        ) { tint, modifier -> SideIcon(entry.type, tint, modifier) }
                    }
                }

                // 下排：账号（左）+ 设置（右）
                Row(
                    modifier = Modifier.width(groupWidth),
                    horizontalArrangement = Arrangement.spacedBy(gap),
                ) {
                    AccountOrb(
                        size = circle,
                        initial = (userName.ifBlank { "U" }).take(1).uppercase(),
                        accent = accent,
                        onAccent = onAccent,
                        onOpenChangePassword = { onAfterNavigate(); onOpenChangePassword() },
                        onChangeServer = { onAfterNavigate(); onChangeServer() },
                        onLogout = { onAfterNavigate(); onLogout() },
                    )
                    Orb(
                        size = circle,
                        accent = accent,
                        onAccent = onAccent,
                        onClick = { onAfterNavigate(); onOpenSettings() },
                    ) { tint, modifier -> SideIcon("settings", tint, modifier) }
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }

    val type = openType
    if (type != null) {
        ModalBottomSheet(
            onDismissRequest = { openType = null },
            sheetState = rememberModalBottomSheetState(),
            // 面板必须不透明：默认容器色跟随主题 surface，半透明时文字很难辨认
            containerColor = panelSolid,
            scrimColor = Color.Black.copy(alpha = 0.6f),
            // 圆角与白条跟详情 / 播放列表统一：M3 默认是 28dp 圆角 + 32×4dp 抓手，
            // 这里换成 SheetTokens 的 12dp + 64×5dp，三处弹层看起来才是同一个东西。
            shape = SheetShape,
            dragHandle = { SheetGrabber() },
        ) {
            ChipFilterSheet(
                title = SIDE_FILTERS.first { it.type == type }.label,
                items = counts[type].orEmpty(),
                activeValues = LibraryState.conds.filter { it.type == type }.map { it.value },
                controlBg = WebColors.control(1f),
                chipBg = Color(0xFF2B2B36),
                accent = accent,
                accentSoft = accentSoft,
                onPick = { name ->
                    onApplyCond(FilterCond(type, name))
                    openType = null
                    onAfterNavigate()
                },
            )
        }
    }
}

/**
 * 圆控件：**只有图标、绝对居中**（圆内不再放文字）。
 * 筛选生效时圆描边换成强调色、右上角显示已选数量（角标画在裁剪层之外，不会被圆裁掉）。
 */
@Composable
private fun Orb(
    size: Dp,
    accent: Color,
    onAccent: Color,
    onClick: () -> Unit,
    active: Boolean = false,
    badge: Int = 0,
    icon: @Composable (Color, Modifier) -> Unit,
) {
    val tint = if (active) accent else WebColors.Text
    // 阴影让圆在压暗的内容上「浮」起来，和 M3 FAB 的层次一致（不涉及模糊 / 半透明容器）
    Box(modifier = Modifier.size(size).shadow(elevation = 3.dp, shape = CircleShape)) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(CircleShape)
                .background(WebColors.control(1f))
                .border(
                    width = if (active) 1.5.dp else 1.dp,
                    color = if (active) accent else WebColors.Border,
                    shape = CircleShape,
                )
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            // 没有文字了，图标直接落在圆心；0.46 让图形在圆里不显小、也不顶到边
            icon(tint, Modifier.size(size * DrawerGeometry.ICON_RATIO))
        }
        if (badge > 0) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 3.dp, y = (-3).dp)
                    .height(16.dp)
                    .background(accent, ShapePill)
                    .padding(horizontal = 5.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    badge.toString(),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = onAccent,
                )
            }
        }
    }
}

/**
 * 左下角账号圆：首字母是主体、居中，小字「账号」压在圆内底部。
 * 点开仍是改密 / 服务地址 / 退出登录。
 */
@Composable
private fun AccountOrb(
    size: Dp,
    initial: String,
    accent: Color,
    onAccent: Color,
    onOpenChangePassword: () -> Unit,
    onChangeServer: () -> Unit,
    onLogout: () -> Unit,
) {
    var menuVisible by remember { mutableStateOf(false) }
    Box {
        Box(
            modifier = Modifier
                .size(size)
                .shadow(elevation = 3.dp, shape = CircleShape)
                .clip(CircleShape)
                .background(accent)
                .clickable { menuVisible = true },
            contentAlignment = Alignment.Center,
        ) {
            // 与其它圆一致：只有内容、绝对居中，首字母当图标
            Text(
                initial,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = onAccent,
            )
        }
        DropdownMenu(expanded = menuVisible, onDismissRequest = { menuVisible = false }) {
            DropdownMenuItem(
                text = { Text("修改密码") },
                onClick = {
                    menuVisible = false
                    onOpenChangePassword()
                },
            )
            DropdownMenuItem(
                text = { Text("服务地址") },
                onClick = {
                    menuVisible = false
                    onChangeServer()
                },
            )
            DropdownMenuItem(
                text = { Text("退出登录") },
                onClick = {
                    menuVisible = false
                    onLogout()
                },
            )
        }
    }
}

/** 筛选 chips：与 web 的 FilterMenu popover 内容一致（搜索 + chips + 计数 + 提示）。 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun ChipFilterSheet(
    title: String,
    items: List<CountItem>,
    activeValues: List<String>,
    controlBg: Color,
    chipBg: Color,
    accent: Color,
    accentSoft: Color,
    onPick: (String) -> Unit,
) {
    var keyword by remember { mutableStateOf("") }
    val filtered = remember(keyword, items) {
        val q = keyword.trim().lowercase()
        if (q.isEmpty()) items.take(300) else items.filter { it.name.lowercase().contains(q) }.take(300)
    }

    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
        Text(title, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(10.dp))
        BasicTextField(
            value = keyword,
            onValueChange = { keyword = it },
            singleLine = true,
            textStyle = TextStyle(color = WebColors.Text, fontSize = 13.sp),
            cursorBrush = SolidColor(accent),
            modifier = Modifier.fillMaxWidth(),
            decorationBox = { inner ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(34.dp)
                        .background(controlBg, ShapePill)
                        .border(1.dp, WebColors.Border, ShapePill)
                        .padding(horizontal = 12.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    if (keyword.isEmpty()) {
                        Text("搜索…", fontSize = 13.sp, color = WebColors.Muted)
                    }
                    inner()
                }
            },
        )
        Spacer(Modifier.height(10.dp))
        Column(
            modifier = Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState()),
        ) {
            if (filtered.isEmpty()) {
                Text(
                    "无匹配项",
                    fontSize = 13.sp,
                    color = WebColors.Muted,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 20.dp),
                )
            } else {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    filtered.forEach { item ->
                        val active = activeValues.contains(item.name)
                        Row(
                            modifier = Modifier
                                .background(if (active) accentSoft else chipBg, ShapePill)
                                .border(1.dp, if (active) accent else Color.Transparent, ShapePill)
                                .clickable { onPick(item.name) }
                                .padding(horizontal = 11.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                        ) {
                            Text(
                                item.name,
                                style = pillTextStyle(14.sp),
                                color = if (active) accent else Color(0xFFD3D9E3),
                                maxLines = 1,
                            )
                            Text(item.count.toString(), style = pillTextStyle(13.sp), color = WebColors.Muted)
                        }
                    }
                }
            }
        }
        if (activeValues.isEmpty()) {
            Spacer(Modifier.height(10.dp))
            Text("点击标签加入筛选条件（最多 5 个，可叠加）", fontSize = 12.sp, color = WebColors.Muted)
        }
        Spacer(Modifier.height(24.dp))
    }
}
