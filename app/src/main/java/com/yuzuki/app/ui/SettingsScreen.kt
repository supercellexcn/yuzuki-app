package com.yuzuki.app.ui

import android.content.Context
import androidx.compose.foundation.background
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.yuzuki.app.data.CacheUsage
import com.yuzuki.app.ui.theme.GlassPill
import com.yuzuki.app.ui.theme.GlassSurface
import com.yuzuki.app.ui.theme.ShapeCard
import com.yuzuki.app.ui.theme.WebColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 设置页（手机端本地设置）：只负责「已使用缓存」与「清除缓存」。
 *
 * 不再复刻 web 端的服务端设置项：不读写 /api/settings、不触发扫描 / 上传 / 清空媒体库，
 * 因此不会影响 PC 浏览器与其他设备的全局配置。统计与清理统一走 [CacheUsage]，
 * 两者口径一致，清完必然显示 0 B。
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

private suspend fun measureCache(context: Context): CacheUsage.Snapshot = withContext(Dispatchers.IO) {
    CacheUsage.measure(context.filesDir, context.cacheDir)
}

private suspend fun clearCache(context: Context): Long = withContext(Dispatchers.IO) {
    CacheUsage.clear(context.filesDir, context.cacheDir)
}
