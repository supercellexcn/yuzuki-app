package com.yuzuki.app.ui

import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.yuzuki.app.data.AppContainer
import com.yuzuki.app.data.AppSettings
import com.yuzuki.app.data.JobStatus
import com.yuzuki.app.ui.theme.GlassPill
import com.yuzuki.app.ui.theme.GlassSurface
import com.yuzuki.app.ui.theme.ShapeCard
import com.yuzuki.app.ui.theme.ShapePill
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(modifier: Modifier = Modifier) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val saved by AppContainer.settings.state.collectAsState()
    var draft by remember { mutableStateOf(saved) }
    var dirty by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var advanced by remember { mutableStateOf(false) }
    var confirmReset by remember { mutableStateOf(false) }
    var scan by remember { mutableStateOf<JobStatus?>(null) }
    var coverJob by remember { mutableStateOf<JobStatus?>(null) }

    LaunchedEffect(saved) {
        if (!dirty) draft = saved
    }
    // 自动保存：离开控件后 5 秒倒计时（web 端语义的简化实现）
    LaunchedEffect(draft, dirty) {
        if (!dirty) return@LaunchedEffect
        delay(5000)
        AppContainer.settings.save(draft).fold(
            onSuccess = { dirty = false; message = "设置已保存" },
            onFailure = { message = "保存失败：${it.message}" },
        )
    }
    LaunchedEffect(Unit) {
        while (true) {
            scan = AppContainer.api.scanStatus().getOrNull()
            coverJob = AppContainer.api.coverStatus().getOrNull()
            delay(2000)
        }
    }

    fun edit(block: (AppSettings) -> AppSettings) {
        draft = block(draft)
        dirty = true
        message = "将在 5 秒后自动保存"
    }

    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            val bytes = runCatching {
                context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            }.getOrNull()
            if (bytes == null || bytes.size > 50 * 1024 * 1024) {
                message = "图片无效或超过 50MB"
                return@launch
            }
            val ext = uri.lastPathSegment?.substringAfterLast('.', "jpg") ?: "jpg"
            val base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
            message = "图片已选择，请用下方按钮指定用途"
            draft = draft // 触发重组
            pendingImage = base64 to ext
        }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize().padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("设置", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                if (dirty) {
                    GlassPill(onClick = { draft = saved; dirty = false; message = "已还原" }) {
                        Text("× 取消修改", style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp))
                    }
                }
                GlassPill(onClick = {
                    scope.launch {
                        AppContainer.settings.save(draft).fold(
                            onSuccess = { dirty = false; message = "设置已保存" },
                            onFailure = { message = "保存失败：${it.message}" },
                        )
                    }
                }) {
                    Text("立即保存", style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp))
                }
            }
            message?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary) }
        }

        item {
            SectionCard("系统设置") {
                NumberField("每页加载数量", draft.pageSize) { edit { s -> s.copy(pageSize = it) } }
                NumberField("快进 / 快退秒数", draft.seekSeconds) { edit { s -> s.copy(seekSeconds = it) } }
                NumberField("扫描层级深度", draft.scanDepth) { edit { s -> s.copy(scanDepth = it) } }
                NumberField("扫描并发数", draft.scanConcurrency) { edit { s -> s.copy(scanConcurrency = it) } }
                TextFieldRow("DLsite 代理", draft.proxy) { edit { s -> s.copy(proxy = it) } }
                TextFieldRow("源路径（逗号分隔）", draft.sourcePaths.joinToString(",")) {
                    edit { s -> s.copy(sourcePaths = it.split(",").map(String::trim).filter(String::isNotBlank)) }
                }
                TextFieldRow("封面缓存目录", draft.coverDir) { edit { s -> s.copy(coverDir = it) } }
            }
        }

        item {
            SectionCard("外观（与网页端共用，修改会影响所有设备）") {
                TextFieldRow("导航主题色 #RRGGBB", draft.themeColorNav) { edit { s -> s.copy(themeColorNav = it) } }
                TextFieldRow("声优标签色", draft.themeColorActor) { edit { s -> s.copy(themeColorActor = it) } }
                TextFieldRow("类型标签色", draft.themeColorTag) { edit { s -> s.copy(themeColorTag = it) } }
                PercentSlider("壁纸模糊", draft.wallpaperBlur) { edit { s -> s.copy(wallpaperBlur = it) } }
                PercentSlider("面板模糊", draft.panelBlur) { edit { s -> s.copy(panelBlur = it) } }
                PercentSlider("标签模糊", draft.tagBlur) { edit { s -> s.copy(tagBlur = it) } }
                PercentSlider("面板不透明度", draft.panelOpacity) { edit { s -> s.copy(panelOpacity = it) } }
                PercentSlider("文件列表不透明度", draft.filelistOpacity) { edit { s -> s.copy(filelistOpacity = it) } }
                PercentSlider("控件不透明度", draft.controlOpacity) { edit { s -> s.copy(controlOpacity = it) } }
                PercentSlider("声优标签不透明度", draft.actorTagOpacity) { edit { s -> s.copy(actorTagOpacity = it) } }
                PercentSlider("类型标签不透明度", draft.genreTagOpacity) { edit { s -> s.copy(genreTagOpacity = it) } }
                TextFieldRow("卡片尺寸 small/medium/large/custom", draft.cardSize) { edit { s -> s.copy(cardSize = it) } }
                NumberField("自定义卡片宽度 dp", draft.cardWidth) { edit { s -> s.copy(cardWidth = it) } }
                SwitchRow("启用移动壁纸", draft.wallpaperMobileEnabled) { edit { s -> s.copy(wallpaperMobileEnabled = it) } }
                SwitchRow("启用桌面壁纸", draft.wallpaperDesktopEnabled) { edit { s -> s.copy(wallpaperDesktopEnabled = it) } }
                Slider(
                    value = draft.animationDuration.toFloat(),
                    onValueChange = { edit { s -> s.copy(animationDuration = it.toDouble()) } },
                    valueRange = 0f..1f,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text("动画时长 ${draft.animationDuration}s", style = MaterialTheme.typography.labelSmall)
            }
        }

        item {
            SectionCard("图片上传") {
                GlassPill(onClick = { pickImage.launch("image/*") }) {
                    Text("选择图片（≤50MB）", modifier = Modifier.padding(12.dp))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GlassPill(onClick = {
                        scope.launch {
                            val img = pendingImage ?: return@launch
                            AppContainer.api.uploadImage("/api/settings/default-cover", img.first, img.second)
                                .onSuccess { message = "默认封面已上传" }
                                .onFailure { message = "上传失败：${it.message}" }
                        }
                    }) { Text("设为默认封面", modifier = Modifier.padding(12.dp)) }
                    GlassPill(onClick = {
                        scope.launch {
                            val img = pendingImage ?: return@launch
                            AppContainer.api.uploadImage("/api/settings/wallpaper/mobile", img.first, img.second)
                                .onSuccess { message = "移动壁纸已上传"; WallpaperState.bump() }
                                .onFailure { message = "上传失败：${it.message}" }
                        }
                    }) { Text("设为移动壁纸", modifier = Modifier.padding(12.dp)) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GlassPill(onClick = {
                        scope.launch {
                            AppContainer.api.deleteImage("/api/settings/default-cover")
                                .onSuccess { message = "默认封面已删除" }
                        }
                    }) { Text("删除默认封面", modifier = Modifier.padding(12.dp)) }
                    GlassPill(onClick = {
                        scope.launch {
                            AppContainer.api.deleteImage("/api/settings/wallpaper/mobile")
                                .onSuccess { message = "移动壁纸已删除"; WallpaperState.bump() }
                        }
                    }) { Text("删除移动壁纸", modifier = Modifier.padding(12.dp)) }
                }
            }
        }

        item {
            SectionCard("扫描") {
                val s = scan
                Text("状态：${s?.status ?: "—"}  ${s?.done ?: 0}/${s?.total ?: 0}  ${s?.current ?: ""}",
                    style = MaterialTheme.typography.labelSmall)
                if (!s?.errors.isNullOrEmpty()) {
                    Text("错误：${s!!.errors.take(3).joinToString("；")}",
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GlassPill(onClick = { scope.launch { AppContainer.api.scanStart(); message = "已开始扫描" } }) {
                        Text("立即扫描", modifier = Modifier.padding(12.dp))
                    }
                    GlassPill(onClick = { scope.launch { AppContainer.api.scanPause() } }) {
                        Text("暂停", modifier = Modifier.padding(12.dp))
                    }
                    GlassPill(onClick = { scope.launch { AppContainer.api.scanResume() } }) {
                        Text("继续", modifier = Modifier.padding(12.dp))
                    }
                }
                GlassPill(onClick = { scope.launch { AppContainer.api.redownloadCovers(); message = "开始重下封面" } }) {
                    Text("重下封面（${coverJob?.downloaded ?: 0} 已下载）", modifier = Modifier.padding(12.dp))
                }
            }
        }

        item {
            SectionCard("相关推荐") {
                SwitchRow("启用相关推荐", draft.recommendationEnabled) { edit { s -> s.copy(recommendationEnabled = it) } }
                SwitchRow("扫描后自动计算", draft.recommendationAutoCalc) { edit { s -> s.copy(recommendationAutoCalc = it) } }
                NumberField("每作品推荐数", draft.recommendationTopN) { edit { s -> s.copy(recommendationTopN = it) } }
                GlassPill(onClick = {
                    scope.launch { AppContainer.api.calcRecommendations(); message = "已开始计算推荐" }
                }) { Text("立即计算", modifier = Modifier.padding(12.dp)) }
            }
        }

        item {
            SectionCard("高级") {
                GlassPill(onClick = { advanced = !advanced }) {
                    Text(if (advanced) "收起高级项" else "展开高级项", modifier = Modifier.padding(12.dp))
                }
                if (advanced) {
                    TextFieldRow("DLsite Base", draft.dlsiteBase) { edit { s -> s.copy(dlsiteBase = it) } }
                    TextFieldRow("DLsite Path", draft.dlsitePath) { edit { s -> s.copy(dlsitePath = it) } }
                    TextFieldRow("DLsite 语言", draft.dlsiteLocale) { edit { s -> s.copy(dlsiteLocale = it) } }
                    NumberField("推荐接口返回上限", draft.recommendationApiLimit) {
                        edit { s -> s.copy(recommendationApiLimit = it) }
                    }
                    NumberField("候选池大小", draft.recommendationCandidateLimit) {
                        edit { s -> s.copy(recommendationCandidateLimit = it) }
                    }
                    NumberField("同系列上限", draft.recommendationSeriesMaxCount) {
                        edit { s -> s.copy(recommendationSeriesMaxCount = it) }
                    }
                    SwitchRow("同系列优先", draft.recommendationSeriesPriorityEnabled) {
                        edit { s -> s.copy(recommendationSeriesPriorityEnabled = it) }
                    }
                }
            }
        }

        item {
            SectionCard("危险操作") {
                Text("清空媒体库会删除作品元数据与封面缓存，播放历史与播放次数保留。",
                    style = MaterialTheme.typography.labelSmall)
                GlassPill(onClick = { confirmReset = true }) {
                    Text("清空媒体库…", modifier = Modifier.padding(12.dp),
                        color = MaterialTheme.colorScheme.error)
                }
                GlassPill(onClick = {
                    scope.launch {
                        AppContainer.offline.clearAll()
                        ImageCache.clear()
                        message = "本地离线缓存已清除"
                    }
                }) { Text("清除本机离线缓存", modifier = Modifier.padding(12.dp)) }
            }
        }
    }

    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text("确认清空媒体库？") },
            text = { Text("此操作会删除服务端所有作品元数据与封面缓存，且影响网页端与其他设备。") },
            confirmButton = {
                TextButton(onClick = {
                    confirmReset = false
                    scope.launch {
                        AppContainer.api.scanReset().fold(
                            onSuccess = { message = "媒体库已清空，请重新扫描" },
                            onFailure = { message = "清空失败：${it.message}" },
                        )
                    }
                }) { Text("确认清空") }
            },
            dismissButton = { TextButton(onClick = { confirmReset = false }) { Text("取消") } },
            shape = ShapeCard,
        )
    }
}

private var pendingImage: Pair<String, String>? = null

@Composable
private fun SectionCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    GlassSurface(modifier = Modifier.fillMaxWidth(), shape = ShapeCard) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            content()
        }
    }
}

@Composable
private fun NumberField(label: String, value: Int, onChange: (Int) -> Unit) {
    OutlinedTextField(
        value = value.toString(),
        onValueChange = { it.toIntOrNull()?.let(onChange) },
        label = { Text(label) },
        singleLine = true,
        shape = ShapePill,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun TextFieldRow(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        shape = ShapePill,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun PercentSlider(label: String, value: Int, onChange: (Int) -> Unit) {
    Column {
        Text("$label $value", style = MaterialTheme.typography.labelSmall)
        Slider(
            value = value.toFloat(),
            onValueChange = { onChange(it.toInt()) },
            valueRange = 0f..100f,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun SwitchRow(label: String, value: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Switch(checked = value, onCheckedChange = onChange)
    }
}
