package com.yuzuki.app.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yuzuki.app.data.AppContainer
import com.yuzuki.app.data.HostPolicy
import com.yuzuki.app.ui.theme.GlassPill
import com.yuzuki.app.ui.theme.GlassSurface
import com.yuzuki.app.ui.theme.LocalAppearance
import com.yuzuki.app.ui.theme.ShapeCard
import com.yuzuki.app.ui.theme.ShapeItem
import com.yuzuki.app.ui.theme.ShapePill
import com.yuzuki.app.ui.theme.WebColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/** 轻量图片加载：OkHttp + 内存 LRU + 磁盘缓存，避免引入额外依赖。 */
object ImageCache {
    /** 默认解码长边：卡片封面 ≈ 200dp，2x 屏约 550px，720 足够清晰且体积可控。 */
    const val DEFAULT_TARGET_PX = 720

    /** 内存缓存按可用堆的 1/8（8~64MB）：降采样后一张封面约 1MB，够存一屏多的卡片。 */
    private val cacheBytes = (Runtime.getRuntime().maxMemory() / 8)
        .toInt().coerceIn(8 shl 20, 64 shl 20)

    private val memory = object : LruCache<String, Bitmap>(cacheBytes) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }
    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .followRedirects(false)
        .build()

    /**
     * @param targetPx 解码长边上限（<=0 表示按原图）。按需降采样是流畅度的关键：
     *   卡片封面原图常见 1000px+，全尺寸解码一张约 4MB，滚动时几乎每张都要重新解码并触发 GC。
     */
    /** 同步命中内存缓存（滚动回到已看过的卡片时首帧直接出图，不闪白）。 */
    fun peek(url: String, targetPx: Int = DEFAULT_TARGET_PX): Bitmap? {
        if (url.isBlank()) return null
        val key = if (targetPx <= 0) url else "$url@$targetPx"
        return memory.get(key)
    }

    suspend fun load(
        context: Context,
        url: String,
        targetPx: Int = DEFAULT_TARGET_PX,
    ): Bitmap? = withContext(Dispatchers.IO) {
        if (url.isBlank()) return@withContext null
        val key = if (targetPx <= 0) url else "$url@$targetPx"
        memory.get(key)?.let { return@withContext it }
        val base = AppContainer.api.cachedBaseUrl
        if (base.isNotBlank() && !HostPolicy.isAllowedUrl(url, base)) return@withContext null
        // 磁盘缓存始终存原始字节，解码时才按需降采样
        val file = File(context.cacheDir, "img_" + sha(url))
        val bytes = if (file.exists() && file.length() > 0) {
            runCatching { file.readBytes() }.getOrNull()
        } else {
            runCatching {
                client.newCall(Request.Builder().url(url).get().build()).execute().use { response ->
                    if (!response.isSuccessful) return@runCatching null
                    response.body?.bytes()?.also { file.writeBytes(it) }
                }
            }.getOrNull()
        } ?: return@withContext null
        val bitmap = decodeSampled(bytes, targetPx) ?: return@withContext null
        memory.put(key, bitmap)
        bitmap
    }

    /** 先读图片头算 inSampleSize，再按最大 2 的幂降采样解码。 */
    private fun decodeSampled(bytes: ByteArray, targetPx: Int): Bitmap? {
        if (targetPx <= 0) return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        var longest = maxOf(bounds.outWidth, bounds.outHeight)
        while (longest / 2 >= targetPx) {
            sample *= 2
            longest /= 2
        }
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
    }

    fun clear() {
        memory.evictAll()
    }

    private fun sha(s: String): String = MessageDigest.getInstance("SHA-256")
        .digest(s.toByteArray()).joinToString("") { "%02x".format(it) }.take(24)
}

@Composable
fun RemoteImage(
    url: String,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    description: String? = null,
    /** 加载中 / 失败时的占位底色；默认 surfaceVariant，需要无色块时传 Color.Transparent。 */
    placeholder: Color = MaterialTheme.colorScheme.surfaceVariant,
    /** 解码长边上限，<=0 表示原图（大图查看器用）。 */
    targetPx: Int = ImageCache.DEFAULT_TARGET_PX,
) {
    val context = LocalContext.current
    val cached = remember(url, targetPx) { ImageCache.peek(url, targetPx) }
    val bitmap by produceState<Bitmap?>(initialValue = cached, url, targetPx) {
        if (value == null) value = ImageCache.load(context, url, targetPx)
    }
    Box(modifier = modifier.background(placeholder)) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap!!.asImageBitmap(),
                contentDescription = description,
                contentScale = contentScale,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/**
 * Material 3 版本为保证文字与封面清晰，不再铺壁纸、不再叠模糊与黑色蒙版。
 * 壁纸上传 / 启停设置仍保留并写回服务端，供网页端使用。
 */
@Composable
@Suppress("UNUSED_PARAMETER")
fun WallpaperBackground(modifier: Modifier = Modifier) {
    // 故意留空：干净的不透明背景由 MaterialTheme 的 surface 系列提供
}

/** M3 标签：secondaryContainer 不透明底 + onSecondaryContainer 高对比文字。 */
@Composable
@Suppress("UNUSED_PARAMETER")
fun Chip(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    alpha: Float = 0.8f,
    onClick: (() -> Unit)? = null,
) {
    Surface(
        modifier = modifier,
        shape = ShapePill,
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
        )
    }
}

/**
 * web 顶栏（App.vue `.topbar`）：min-height 56 / padding 8 10 / 底色 topbar-color / 底边 1px。
 * 非媒体库页面（作品详情、播放历史）只有左侧菜单按钮，没有搜索与排序控件。
 */
@Composable
fun WebTopBar(onOpenDrawer: () -> Unit, modifier: Modifier = Modifier) {
    val appearance = LocalAppearance.current
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(WebColors.topbar(appearance.navColor, appearance.panelAlpha)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .border(1.dp, WebColors.Border, CircleShape)
                    .clickable(onClick = onOpenDrawer),
                contentAlignment = Alignment.Center,
            ) {
                MenuIcon(WebColors.Text, Modifier.size(18.dp))
            }
        }
        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(WebColors.Border))
    }
}

/** web `BackButton.vue`：40×40 圆形、1px 边框、面板底、20px 左箭头。 */
@Composable
fun WebBackButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val appearance = LocalAppearance.current
    Box(
        modifier = modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(WebColors.panel(appearance.navColor, appearance.panelAlpha))
            .border(1.dp, WebColors.Border, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        BackArrowIcon(WebColors.Text, Modifier.size(20.dp))
    }
}

/** `.section-title`：16px / 600 / margin-top 8（区块间距由列表的 16 给出）。 */
@Composable
fun WebSectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        fontSize = 16.sp,
        fontWeight = FontWeight.SemiBold,
        color = WebColors.Text,
        modifier = modifier.padding(top = 8.dp),
    )
}

@Composable
fun StateBox(
    message: String,
    modifier: Modifier = Modifier,
    loading: Boolean = false,
    onRetry: (() -> Unit)? = null,
) {
    GlassSurface(modifier = modifier, shape = ShapeCard) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (loading) CircularProgressIndicator(modifier = Modifier.size(28.dp))
            Text(text = message, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
            if (onRetry != null) {
                GlassPill(onClick = onRetry) {
                    Text(
                        text = "重试",
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp),
                    )
                }
            }
        }
    }
}

fun formatDuration(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
}

fun formatSeconds(seconds: Int): String {
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    return if (h > 0) "${h}小时${m}分" else "${m}分${seconds % 60}秒"
}
