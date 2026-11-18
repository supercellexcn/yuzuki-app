package com.yuzuki.app.ui

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yuzuki.app.data.AppContainer
import com.yuzuki.app.data.AppearancePalette
import com.yuzuki.app.data.AppearancePrefs
import com.yuzuki.app.data.CacheUsage
import com.yuzuki.app.data.InteractionPrefs
import com.yuzuki.app.data.isValidHex
import com.yuzuki.app.data.normalizeHex
import com.yuzuki.app.ui.theme.GlassPill
import com.yuzuki.app.ui.theme.GlassSurface
import com.yuzuki.app.ui.theme.ShapeCard
import com.yuzuki.app.ui.theme.composeColor
import com.yuzuki.app.ui.theme.WebColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/**
 * 设置页（手机端本地设置）：App 外观 + 手势时长 + 本机缓存。
 *
 * 外观（主题色 / 标签色 / 不透明度）与手势（退出随机的按住时长）都只存本机 DataStore，
 * **不读写 /api/settings**：网页端改配置不影响 App，App 改设置也不影响 PC 浏览器。
 * 缓存卡片只统计 / 清理本机缓存，不触发扫描 / 上传 / 清空媒体库。
 */
@Composable
fun SettingsScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var snapshot by remember { mutableStateOf(CacheUsage.Snapshot(emptyList())) }
    var loading by remember { mutableStateOf(true) }
    var clearing by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var confirmClear by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        snapshot = measureCache(context)
        loading = false
    }

    Column(modifier = modifier.fillMaxSize()) {
        // 固定顶栏：48dp 触控区的返回箭头，返回统一走 AppNav.backOrHome()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onBack),
                contentAlignment = Alignment.Center,
            ) {
                BackArrowIcon(WebColors.Text, Modifier.size(20.dp))
            }
            Text(
                text = "设置",
                style = MaterialTheme.typography.titleMedium,
                color = WebColors.Text,
                modifier = Modifier.padding(start = 4.dp),
            )
        }
        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(WebColors.Border))

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            AppearanceCard()
            GestureCard()

            GlassSurface(modifier = Modifier.fillMaxWidth(), shape = ShapeCard) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = "已使用缓存",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = if (loading) "统计中…" else CacheUsage.format(snapshot.total),
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = "包含从服务端缓存到本机的全部内容",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    Spacer(modifier = Modifier.height(8.dp))
                    Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(WebColors.Border))
                    Spacer(modifier = Modifier.height(4.dp))

                    val visible = snapshot.items.filter { it.bytes > 0 }
                    if (visible.isEmpty() && !loading) {
                        Text(
                            text = "当前没有缓存",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 8.dp),
                        )
                    } else {
                        visible.forEach { item ->
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = item.label,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.weight(1f),
                                )
                                Text(
                                    text = CacheUsage.format(item.bytes),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    val canClear = !loading && !clearing && snapshot.total > 0
                    val clearAction: (() -> Unit)? = if (canClear) {
                        { confirmClear = true }
                    } else {
                        null
                    }
                    GlassPill(
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                        onClick = clearAction,
                    ) {
                        Box(
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = if (clearing) "清除中…" else "清除缓存",
                                style = MaterialTheme.typography.labelLarge,
                                color = if (canClear) {
                                    MaterialTheme.colorScheme.error
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                            )
                        }
                    }
                }
            }

            Text(
                text = "清除范围：作品 / 收藏 / 历史的数据快照、封面图片、锁屏通知封面等本机缓存，下次打开会重新从服务端获取。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "不会清除：账号登录状态、服务地址、个人偏好与尚未上报的播放记录；服务端数据不受影响。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            message?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("清除本地缓存？") },
            text = {
                Text(
                    "将删除共 ${CacheUsage.format(snapshot.total)} 的本机缓存，下次打开时重新从服务端获取。" +
                        "\n\n账号登录状态、服务地址、偏好设置与待上报的播放记录会保留，服务端数据不受影响。",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmClear = false
                    clearing = true
                    scope.launch {
                        val freed = clearCache(context)
                        ImageCache.clear()
                        snapshot = measureCache(context)
                        clearing = false
                        message = "已释放 ${CacheUsage.format(freed)}"
                    }
                }) { Text("确认清除") }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("取消") } },
            shape = ShapeCard,
        )
    }
}

/**
 * 外观卡片：颜色 + 不透明度，改完立即生效并落本机 DataStore。
 * 不提供模糊 / 壁纸项 —— App 端本来就不渲染这些（见 README「已知限制」）。
 */
@Composable
private fun AppearanceCard(modifier: Modifier = Modifier) {
    val prefs by AppContainer.appearance.state.collectAsState()
    val scope = rememberCoroutineScope()

    fun apply(next: AppearancePrefs) {
        scope.launch { AppContainer.appearance.save(next) }
    }

    GlassSurface(modifier = modifier.fillMaxWidth(), shape = ShapeCard) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "外观",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "只作用于本机，不会同步到网页端，也不会被网页端的主题色覆盖",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            ColorSetting(
                title = "主题色",
                desc = "顶栏、卡片底色与强调色",
                value = prefs.navColor,
                palette = AppearancePalette.nav,
                onPick = { apply(prefs.copy(navColor = it)) },
            )
            SectionDivider()
            ColorSetting(
                title = "声优标签色",
                desc = "卡片与详情里的声优胶囊",
                value = prefs.actorColor,
                palette = AppearancePalette.actor,
                onPick = { apply(prefs.copy(actorColor = it)) },
            )
            SectionDivider()
            ColorSetting(
                title = "类型标签色",
                desc = "封面类型标签与筛选标签",
                value = prefs.tagColor,
                palette = AppearancePalette.tag,
                onPick = { apply(prefs.copy(tagColor = it)) },
            )
            SectionDivider()

            Text(
                text = "不透明度",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OpacitySetting("面板", prefs.panelOpacity) { apply(prefs.copy(panelOpacity = it)) }
            OpacitySetting("控件", prefs.controlOpacity) { apply(prefs.copy(controlOpacity = it)) }
            OpacitySetting("声优标签", prefs.actorTagOpacity) { apply(prefs.copy(actorTagOpacity = it)) }
            OpacitySetting("类型标签", prefs.genreTagOpacity) { apply(prefs.copy(genreTagOpacity = it)) }

            SectionDivider()
            GlassPill(
                modifier = Modifier.heightIn(min = 44.dp),
                onClick = {
                    scope.launch {
                        AppContainer.appearance.reset()
                        AppToast.show("已恢复默认外观")
                    }
                },
            ) {
                Box(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "恢复默认外观",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionDivider() {
    Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(WebColors.Border))
}

/**
 * 手势卡片：只放会改变操作的时长项，值存本机 `app_interaction`，不进服务端。
 */
@Composable
private fun GestureCard(modifier: Modifier = Modifier) {
    val prefs by AppContainer.interaction.state.collectAsState()
    val scope = rememberCoroutineScope()

    GlassSurface(modifier = modifier.fillMaxWidth(), shape = ShapeCard) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = "手势",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            HoldSecondsSetting(prefs.exitRandomHoldSeconds) { seconds ->
                scope.launch {
                    AppContainer.interaction.save(prefs.copy(exitRandomHoldSeconds = seconds))
                }
            }
            Text(
                text = "库页右半屏下拉越过门槛后，按住不动达到该时长即退出随机推荐；" +
                    "提前松手仍是换一批（随机模式下提示条会读秒）。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun HoldSecondsSetting(value: Int, onCommit: (Int) -> Unit) {
    val min = InteractionPrefs.MinHoldSeconds
    val max = InteractionPrefs.MaxHoldSeconds
    // 拖动期间只改本地值，松手才落盘
    var local by remember(value) { mutableStateOf(value.coerceIn(min, max).toFloat()) }
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "退出随机推荐：按住时长",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "${local.roundToInt()} 秒",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Slider(
            value = local,
            onValueChange = { local = it },
            onValueChangeFinished = { onCommit(local.roundToInt()) },
            valueRange = min.toFloat()..max.toFloat(),
            // 1 秒一档：10 个刻度之间 8 个台阶
            steps = max - min - 1,
            modifier = Modifier.fillMaxWidth().height(48.dp),
        )
    }
}

@Composable
private fun ColorSetting(
    title: String,
    desc: String,
    value: String,
    palette: List<String>,
    onPick: (String) -> Unit,
) {
    var custom by remember { mutableStateOf(false) }
    val current = normalizeHex(value)

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = desc,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = current,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            palette.forEach { hex ->
                SwatchDot(hex = hex, selected = normalizeHex(hex) == current) { onPick(hex) }
            }
        }
        GlassPill(
            modifier = Modifier.heightIn(min = 40.dp),
            onClick = { custom = true },
        ) {
            Box(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "自定义颜色…",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }

    if (custom) {
        HexColorDialog(
            initial = current,
            onDismiss = { custom = false },
            onConfirm = { hex ->
                custom = false
                onPick(hex)
            },
        )
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.SwatchDot(
    hex: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .weight(1f)
            .height(44.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(30.dp)
                .clip(CircleShape)
                .background(composeColor(hex))
                .border(
                    BorderStroke(
                        width = if (selected) 2.dp else 1.dp,
                        color = if (selected) WebColors.Text else WebColors.Border,
                    ),
                    CircleShape,
                ),
        )
    }
}

@Composable
private fun OpacitySetting(title: String, value: Int, onCommit: (Int) -> Unit) {
    // 拖动期间只改本地值，松手才落盘：避免每帧写一次 DataStore
    var local by remember(value) { mutableStateOf(value.coerceIn(0, 100).toFloat()) }
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "${local.roundToInt()}%",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Slider(
            value = local,
            onValueChange = { local = it },
            onValueChangeFinished = { onCommit(local.roundToInt()) },
            valueRange = 0f..100f,
            modifier = Modifier.fillMaxWidth().height(48.dp),
        )
    }
}

/** 自定义色值弹窗：非法输入禁用「确定」，并提示格式。 */
@Composable
private fun HexColorDialog(
    initial: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var text by remember { mutableStateOf(initial) }
    val valid = isValidHex(text)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("自定义颜色") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it.take(7) },
                    singleLine = true,
                    isError = !valid,
                    label = { Text("十六进制色值") },
                    placeholder = { Text("#A78BFA") },
                )
                if (valid) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(18.dp)
                                .clip(CircleShape)
                                .background(composeColor(normalizeHex(text))),
                        )
                        Text(
                            text = normalizeHex(text),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                } else {
                    Text(
                        text = "请输入 6 位色值，例如 #A78BFA",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(normalizeHex(text)) },
                enabled = valid,
            ) { Text("确定") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
        shape = ShapeCard,
    )
}

private suspend fun measureCache(context: Context): CacheUsage.Snapshot = withContext(Dispatchers.IO) {
    CacheUsage.measure(context.filesDir, context.cacheDir)
}

private suspend fun clearCache(context: Context): Long = withContext(Dispatchers.IO) {
    CacheUsage.clear(context.filesDir, context.cacheDir)
}
