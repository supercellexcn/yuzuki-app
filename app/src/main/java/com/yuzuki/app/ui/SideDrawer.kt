package com.yuzuki.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yuzuki.app.data.AppContainer
import com.yuzuki.app.data.CountItem
import com.yuzuki.app.data.FilterCond
import com.yuzuki.app.ui.theme.LocalAppearance
import com.yuzuki.app.ui.theme.ShapePill
import com.yuzuki.app.ui.theme.WebColors
import kotlin.math.roundToInt

/**
 * 侧栏圆盘几何（纯数值，JVM 单测直接断言，不依赖 Compose 运行时）。
 *
 * 面板不再是 web 那种「品牌 + 文字列表」的抽屉，而是一块圆盘面板：
 * 上方 4 个筛选圆 + 1 个收藏圆（圆组高度跟随拉出侧栏时手指按下的高度），底部账号 / 设置两个圆。
 */
internal object DrawerGeometry {
    /** 面板宽度占屏宽的比例：太窄 4 个圆挤成一堆，太宽会把信息流挡光。 */
    const val WIDTH_RATIO = 0.82f
    /** 面板宽度上限：大屏 / 平板不再变宽。 */
    const val MAX_WIDTH_DP = 320f
    /** 极窄屏的兜底下限。 */
    const val MIN_WIDTH_DP = 240f
    /** 圆的最小直径（≈ 48dp 触控区的下限）。 */
    const val MIN_CIRCLE_DP = 44f

    fun widthDp(screenWidthDp: Float): Float =
        (screenWidthDp * WIDTH_RATIO).coerceIn(MIN_WIDTH_DP, MAX_WIDTH_DP)

    /** 一列圆的直径：可用宽度扣掉左右内边距与列间距后按列数等分。 */
    fun circleDp(widthDp: Float, padDp: Float, gapDp: Float, columns: Int = 4): Float {
        val usable = widthDp - padDp * 2 - gapDp * (columns - 1)
        return (usable / columns).coerceAtLeast(MIN_CIRCLE_DP)
    }

    /**
     * 圆组顶部位置：以「拉出侧栏时手指按下的高度」为中心算起，上下各留 [marginPx]。
     * 手指按在很靠上 / 很靠下的位置时圆组会被钳制回可用区间，不会跑出屏幕。
     */
    fun groupTopPx(startFraction: Float, availPx: Float, groupPx: Float, marginPx: Float): Float {
        val usable = availPx - groupPx - marginPx * 2
        if (usable <= 0f) return marginPx
        val centered = startFraction.coerceIn(0f, 1f) * availPx - groupPx / 2f
        return centered.coerceIn(marginPx, marginPx + usable)
    }
}

private data class SideEntry(val type: String, val label: String)

/** 圆下小字去掉「全部」二字：社团 / 系列 / 声优 / 标签。 */
private val SIDE_FILTERS = listOf(
    SideEntry("circle", "社团"),
    SideEntry("series", "系列"),
    SideEntry("actor", "声优"),
    SideEntry("tag", "标签"),
)

private val OrbPad = 18.dp
private val OrbGap = 10.dp
private val OrbLabelGap = 5.dp
private val OrbLabelHeight = 16.dp
private val OrbRowGap = 18.dp
private val OrbGroupMargin = 12.dp

/**
 * 圆盘侧边抽屉。
 *
 * - 跟手位移：位置由 [fraction] 直接驱动，手指拖多少走多少（不是固定时长动画）；
 * - 圆组位置不固定：以 [startFraction]（拉出时手指按下的高度占可用高度的比例）为垂直中心；
 * - 4 个筛选圆点开仍是同一套 chips 底部弹层，收藏圆进收藏页，底部左圆是账号菜单、右圆是设置。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SideDrawer(
    fraction: Float,
    width: Dp,
    /** 起手高度占可用高度的比例（0–1）；点右上角菜单按钮打开时沿用上一次的值。 */
    startFraction: Float,
    onDragBy: (Float) -> Unit,
    /** 松手回调，参数为整段拖动的水平位移（px，左负右正），用于区分"收起"与"回弹展开" */
    onDragEnd: (Float) -> Unit,
    userName: String,
    onDismiss: () -> Unit,
    onOpenFavorites: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenChangePassword: () -> Unit,
    onChangeServer: () -> Unit,
    onLogout: () -> Unit,
    onApplyCond: (FilterCond) -> Unit,
    onAfterNavigate: () -> Unit,
) {
    val appearance = LocalAppearance.current
    // 抽屉盖在页面上，用不透明面板色（web: rgb(var(--panel-rgb))）
    val panelSolid = WebColors.panel(appearance.navColor, 1f)
    val accent = MaterialTheme.colorScheme.primary
    val onAccent = MaterialTheme.colorScheme.onPrimary
    val accentSoft = accent.copy(alpha = 0.2f)

    var openType by remember { mutableStateOf<String?>(null) }
    var counts by remember { mutableStateOf<Map<String, List<CountItem>>>(emptyMap()) }

    val widthPx = with(LocalDensity.current) { width.toPx() }
    val circle = DrawerGeometry.circleDp(
        widthDp = width.value,
        padDp = OrbPad.value,
        gapDp = OrbGap.value,
    ).dp
    val groupHeight = (circle + OrbLabelGap + OrbLabelHeight) * 2 + OrbRowGap

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

    Box(modifier = Modifier.fillMaxSize()) {
        // 遮罩跟随拖拽渐变；只有完全打开时才接管点击（拖拽中点击要穿透给下层）
        if (fraction > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.5f * fraction))
                    .then(if (fraction > 0.99f) Modifier.clickable(onClick = onDismiss) else Modifier),
            )
        }

        // 面板：位置由 fraction 直接驱动，手指拖多少走多少（跟手，不是固定时长动画）
        Column(
            modifier = Modifier
                .width(width)
                .fillMaxHeight()
                .offset { IntOffset((-widthPx * (1f - fraction)).roundToInt(), 0) }
                .background(panelSolid)
                // 向左拖即收起，位移实时回传给 RootScreen
                .pointerInput(widthPx) {
                    var total = 0f
                    detectHorizontalDragGestures(
                        onDragStart = { total = 0f },
                        onHorizontalDrag = { _, dragAmount ->
                            total += dragAmount
                            onDragBy(dragAmount / widthPx)
                        },
                        onDragEnd = { onDragEnd(total) },
                        onDragCancel = { onDragEnd(total) },
                    )
                }
                // web 只有 border-right，这里只画右边一条线
                .drawBehind {
                    val w = 1.dp.toPx()
                    drawLine(
                        color = WebColors.Border,
                        start = Offset(size.width - w / 2f, 0f),
                        end = Offset(size.width - w / 2f, size.height),
                        strokeWidth = w,
                    )
                },
        ) {
            // 上方圆组：垂直中心跟随起手高度，整体在可用区间内钳制
            BoxWithConstraints(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) {
                val density = LocalDensity.current
                val availPx = with(density) { maxHeight.toPx() }
                val groupPx = with(density) { groupHeight.toPx() }
                val marginPx = with(density) { OrbGroupMargin.toPx() }
                val topPx = DrawerGeometry.groupTopPx(
                    startFraction = startFraction,
                    availPx = availPx,
                    groupPx = groupPx,
                    marginPx = marginPx,
                )

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .offset { IntOffset(0, topPx.roundToInt()) }
                        .padding(horizontal = OrbPad),
                    verticalArrangement = Arrangement.spacedBy(OrbRowGap),
                ) {
                    // 第 1 行：4 个筛选圆（社团 / 系列 / 声优 / 标签）
                    Row(horizontalArrangement = Arrangement.spacedBy(OrbGap)) {
                        SIDE_FILTERS.forEach { entry ->
                            val active = counts[entry.type].orEmpty().count {
                                LibraryState.conds.any { c -> c.type == entry.type && c.value == it.name }
                            }
                            Orb(
                                label = entry.label,
                                size = circle,
                                active = active > 0,
                                badge = active,
                                accent = accent,
                                onAccent = onAccent,
                                onClick = { openType = entry.type },
                            ) { tint, modifier -> SideIcon(entry.type, tint, modifier) }
                        }
                    }

                    // 第 2 行：收藏圆（与第 1 列左对齐）
                    Row(horizontalArrangement = Arrangement.spacedBy(OrbGap)) {
                        Orb(
                            label = "收藏",
                            size = circle,
                            accent = accent,
                            onAccent = onAccent,
                            onClick = { onAfterNavigate(); onOpenFavorites() },
                        ) { tint, modifier -> SolidStarIcon(tint, modifier) }
                    }
                }
            }

            // 底部两个圆：左账号（改密 / 服务地址 / 退出登录），右设置
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = OrbPad, end = OrbPad, bottom = 26.dp),
                horizontalArrangement = Arrangement.spacedBy(OrbGap),
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
                    label = "设置",
                    size = circle,
                    accent = accent,
                    onAccent = onAccent,
                    onClick = { onAfterNavigate(); onOpenSettings() },
                ) { tint, modifier -> SideIcon("settings", tint, modifier) }
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
 * 圆盘按钮：圆形底 + 圆内图标 + 圆下 11sp 小字。
 * 筛选生效时圆描边换成强调色、右上角显示已选数量（数量角标画在裁剪层之外，不会被圆裁掉）。
 */
@Composable
private fun Orb(
    label: String,
    size: Dp,
    accent: Color,
    onAccent: Color,
    onClick: () -> Unit,
    active: Boolean = false,
    badge: Int = 0,
    icon: @Composable (Color, Modifier) -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(modifier = Modifier.size(size)) {
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
                icon(if (active) accent else WebColors.Text, Modifier.size(size * 0.4f))
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
        Spacer(Modifier.height(OrbLabelGap))
        Text(
            label,
            fontSize = 11.sp,
            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
            color = if (active) accent else WebColors.Muted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** 左下角账号圆：圆内是用户名首字母，点开仍是改密 / 服务地址 / 退出登录。 */
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
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box {
            Box(
                modifier = Modifier
                    .size(size)
                    .clip(CircleShape)
                    .background(accent)
                    .clickable { menuVisible = true },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    initial,
                    fontSize = 18.sp,
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
        Spacer(Modifier.height(OrbLabelGap))
        Text(
            "账号",
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            color = WebColors.Muted,
            maxLines = 1,
        )
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
                                fontSize = 14.sp,
                                color = if (active) accent else Color(0xFFD3D9E3),
                                maxLines = 1,
                            )
                            Text(item.count.toString(), fontSize = 13.sp, color = WebColors.Muted)
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
