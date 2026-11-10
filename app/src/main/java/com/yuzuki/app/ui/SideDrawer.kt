package com.yuzuki.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yuzuki.app.data.AppContainer
import com.yuzuki.app.data.CountItem
import com.yuzuki.app.data.FilterCond
import com.yuzuki.app.ui.theme.LocalAppearance
import com.yuzuki.app.ui.theme.ShapePill
import com.yuzuki.app.ui.theme.WebColors

/**
 * web 移动端的侧边抽屉（AppSidebar.vue @media max-width 760）：
 * 宽 260px、固定左侧滑出、不透明面板色底、左侧 1px 边框、半透明黑遮罩。
 *
 * 结构照抄：品牌 → 四个分类筛选项 → 分隔线 → 我的收藏 / 最近播放 → 用户 / 设置。
 */
private val DrawerWidth = 260.dp

private data class SideEntry(val type: String, val label: String)

private val SIDE_FILTERS = listOf(
    SideEntry("circle", "全部社团"),
    SideEntry("series", "全部系列"),
    SideEntry("actor", "全部声优"),
    SideEntry("tag", "全部标签"),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SideDrawer(
    visible: Boolean,
    userName: String,
    onDismiss: () -> Unit,
    onOpenFavorites: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenChangePassword: () -> Unit,
    onChangeServer: () -> Unit,
    onLogout: () -> Unit,
    onApplyCond: (FilterCond) -> Unit,
    onOpenViewSheet: () -> Unit,
    onAfterNavigate: () -> Unit,
) {
    val appearance = LocalAppearance.current
    // 抽屉盖在页面上，用不透明面板色（web: rgb(var(--panel-rgb))）
    val panelSolid = WebColors.panel(appearance.navColor, 1f)
    val controlBg = WebColors.control(appearance.controlAlpha)
    val accent = MaterialTheme.colorScheme.primary
    val accentSoft = accent.copy(alpha = 0.2f)

    var openType by remember { mutableStateOf<String?>(null) }
    var menuVisible by remember { mutableStateOf(false) }
    var counts by remember { mutableStateOf<Map<String, List<CountItem>>>(emptyMap()) }

    LaunchedEffect(visible) {
        if (!visible) return@LaunchedEffect
        if (counts.isNotEmpty()) return@LaunchedEffect
        counts = mapOf(
            "circle" to AppContainer.api.circles().getOrDefault(emptyList()),
            "series" to AppContainer.api.series().getOrDefault(emptyList()),
            "actor" to AppContainer.api.actors().getOrDefault(emptyList()),
            "tag" to AppContainer.api.tags().getOrDefault(emptyList()),
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(tween(180)),
            exit = fadeOut(tween(180)),
        ) {
            // 遮罩：rgba(0,0,0,0.5)
            Box(modifier = Modifier.fillMaxSize().background(Color(0x80000000)).clickable(onClick = onDismiss))
        }

        AnimatedVisibility(
            visible = visible,
            enter = slideInHorizontally(tween(220)) { -it },
            exit = slideOutHorizontally(tween(220)) { -it },
        ) {
            Column(
                modifier = Modifier
                    .width(DrawerWidth)
                    .fillMaxHeight()
                    .background(panelSolid)
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
                // 品牌区：padding 12 15 6
                Row(
                    modifier = Modifier.padding(start = 15.dp, end = 15.dp, top = 12.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    BrandMark()
                    Text(
                        "Yuzuki",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = accent,
                    )
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 10.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    SIDE_FILTERS.forEach { entry ->
                        val active = counts[entry.type].orEmpty().count {
                            LibraryState.conds.any { c -> c.type == entry.type && c.value == it.name }
                        }
                        DrawerItem(
                            label = entry.label,
                            icon = entry.type,
                            badge = active,
                            caret = true,
                            onClick = { openType = entry.type },
                        )
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 6.dp, vertical = 8.dp)
                            .height(1.dp)
                            .background(WebColors.Border),
                    )

                    DrawerItem(
                        label = "我的收藏",
                        icon = "star",
                        onClick = { onAfterNavigate(); onOpenFavorites() },
                    )
                    DrawerItem(
                        label = "最近播放",
                        icon = "history",
                        onClick = { onAfterNavigate(); onOpenHistory() },
                    )
                    DrawerItem(
                        label = "视图与排序",
                        icon = "series",
                        onClick = { onAfterNavigate(); onOpenViewSheet() },
                    )
                }

                Column(
                    modifier = Modifier.padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Box {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 40.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { menuVisible = true }
                                .padding(horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Box(
                                modifier = Modifier.size(24.dp).background(accent, ShapePill),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    (userName.ifBlank { "U" }).take(1).uppercase(),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimary,
                                )
                            }
                            Text(
                                userName.ifBlank { "未登录" },
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                color = WebColors.Text,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            Text("▸", fontSize = 14.sp, color = WebColors.Muted, modifier = Modifier.rotate(90f))
                        }
                        DropdownMenu(expanded = menuVisible, onDismissRequest = { menuVisible = false }) {
                            DropdownMenuItem(
                                text = { Text("修改密码") },
                                onClick = {
                                    menuVisible = false
                                    onAfterNavigate()
                                    onOpenChangePassword()
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("服务地址") },
                                onClick = {
                                    menuVisible = false
                                    onAfterNavigate()
                                    onChangeServer()
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("退出登录") },
                                onClick = {
                                    menuVisible = false
                                    onAfterNavigate()
                                    onLogout()
                                },
                            )
                        }
                    }
                    DrawerItem(
                        label = "设置",
                        icon = "settings",
                        onClick = { onAfterNavigate(); onOpenSettings() },
                    )
                }
            }
        }
    }

    val type = openType
    if (type != null) {
        ModalBottomSheet(
            onDismissRequest = { openType = null },
            sheetState = rememberModalBottomSheetState(),
        ) {
            ChipFilterSheet(
                title = SIDE_FILTERS.first { it.type == type }.label,
                items = counts[type].orEmpty(),
                activeValues = LibraryState.conds.filter { it.type == type }.map { it.value },
                controlBg = controlBg,
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

@Composable
private fun BrandMark() {
    Box(
        modifier = Modifier
            .size(34.dp)
            .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(9.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            "Y",
            fontSize = 19.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onPrimary,
        )
    }
}

/** .sidebar-item：min-height 40 / padding 0 12 / gap 10 / radius 8 / 14px 500。 */
@Composable
private fun DrawerItem(
    label: String,
    icon: String,
    badge: Int = 0,
    caret: Boolean = false,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 40.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (icon == "star") {
            SolidStarIcon(WebColors.Text, Modifier.size(18.dp))
        } else {
            SideIcon(icon, WebColors.Text, Modifier.size(18.dp))
        }
        Text(
            label,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = WebColors.Text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (badge > 0) {
            Box(
                modifier = Modifier
                    .height(18.dp)
                    .background(MaterialTheme.colorScheme.primary, ShapePill)
                    .padding(horizontal = 6.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    badge.toString(),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            }
        }
        if (caret) {
            Text("▸", fontSize = 14.sp, color = WebColors.Muted)
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
                                .background(if (active) accentSoft else controlBg, ShapePill)
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
