package com.yuzuki.app.playback

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import com.yuzuki.app.data.HostPolicy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * 系统媒体控制用的封面加载（方案 §6）：HTTP artwork URI 不能直接交给 SystemUI，
 * 这里把封面取成 bytes 交给 MediaMetadata.setArtworkData。
 *
 * 三级缓存：内存 → 磁盘（cacheDir/artwork）→ NAS。切歌不再每次重读共享目录，
 * 断网时也能出封面；作品没有封面时用内置默认封面兜底。
 */
class ArtworkLoader(private val context: Context) {
    private val dir = File(context.cacheDir, "artwork").apply { mkdirs() }
    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .followRedirects(false)
        .followSslRedirects(false)
        .build()

    private val memory = object : LinkedHashMap<String, ByteArray>(8, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ByteArray>?): Boolean =
            size > 16
    }

    /** 返回可直接塞进 MediaMetadata 的图片字节；任何失败都退到默认封面。 */
    suspend fun load(rj: String, url: String): ByteArray = withContext(Dispatchers.IO) {
        val key = rj.ifBlank { "default" }
        synchronized(memory) { memory[key] }?.let { return@withContext it }

        val disk = File(dir, "${key.replace(Regex("[^A-Za-z0-9_-]"), "_")}.img")
        if (disk.length() > 0) {
            runCatching { disk.readBytes() }.getOrNull()?.takeIf { it.isNotEmpty() }?.let {
                synchronized(memory) { memory[key] = it }
                return@withContext it
            }
        }

        val base = com.yuzuki.app.data.AppContainer.api.cachedBaseUrl
        val fetched = if (url.isBlank() || !HostPolicy.isAllowedUrl(url, base)) null else fetch(url)
        val bytes = fetched ?: defaultArtwork()
        synchronized(memory) { memory[key] = bytes }
        if (fetched != null) runCatching { disk.writeBytes(bytes) }
        bytes
    }

    private fun fetch(url: String): ByteArray? = runCatching {
        client.newCall(Request.Builder().url(url).get().build()).execute().use { response ->
            if (!response.isSuccessful) return@runCatching null
            val bytes = response.body?.bytes() ?: return@runCatching null
            if (bytes.isEmpty() || bytes.size > MAX_BYTES) return@runCatching null
            bytes
        }
    }.getOrNull()

    /** 无封面兜底：512×512 深紫底 + 白色 Y，落盘一次，之后直接读磁盘。 */
    private fun defaultArtwork(): ByteArray {
        val cached = File(dir, "default.img")
        if (cached.length() > 0) runCatching { cached.readBytes() }.getOrNull()?.let { return it }
        val size = 512
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        Canvas(bitmap).apply {
            drawColor(Color.parseColor("#201828"))
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#A888F8")
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                textSize = size * 0.52f
                textAlign = Paint.Align.CENTER
            }
            val y = size / 2f - (paint.descent() + paint.ascent()) / 2f
            drawText("Y", size / 2f, y, paint)
        }
        val out = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 88, out)
        bitmap.recycle()
        val bytes = out.toByteArray()
        runCatching { cached.writeBytes(bytes) }
        return bytes
    }

    private companion object {
        const val MAX_BYTES = 8 * 1024 * 1024
    }
}
