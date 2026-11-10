package com.yuzuki.app.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.yuzuki.app.data.AppContainer
import com.yuzuki.app.data.HostPolicy
import com.yuzuki.app.ui.theme.GlassPill
import com.yuzuki.app.ui.theme.GlassSurface
import com.yuzuki.app.ui.theme.LocalAppearance
import com.yuzuki.app.ui.theme.ShapeCard
import com.yuzuki.app.ui.theme.ShapeItem
import com.yuzuki.app.ui.theme.ShapePill
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/** 轻量图片加载：OkHttp + 内存 LRU + 磁盘缓存，避免引入额外依赖。 */
object ImageCache {
    private val memory = object : LruCache<String, Bitmap>(12 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }
    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .followRedirects(false)
        .build()

    suspend fun load(context: Context, url: String): Bitmap? = withContext(Dispatchers.IO) {
        if (url.isBlank()) return@withContext null
        memory.get(url)?.let { return@withContext it }
        val base = AppContainer.api.cachedBaseUrl
        if (base.isNotBlank() && !HostPolicy.isAllowedUrl(url, base)) return@withContext null
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
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return@withContext null
        memory.put(url, bitmap)
        bitmap
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
) {
    val context = LocalContext.current
    val bitmap by produceState<Bitmap?>(initialValue = null, key1 = url) {
        value = ImageCache.load(context, url)
    }
    Box(modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant)) {
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
