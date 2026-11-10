package com.yuzuki.app.playback

import com.yuzuki.app.data.HostPolicy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * 方案 §6.3：HTTP artwork URI 不能直接交给系统 MediaSession（SystemUI 无法保证明文/鉴权加载），
 * 原生下载为 bytes 后用 MediaMetadata.Builder.setArtworkData(bytes) 喂给会话。
 */
class ArtworkLoader(val baseUrl: String) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .followRedirects(false)
        .followSslRedirects(false)
        .build()

    // 少量 LRU，避免同一封面反复下载
    private val cache = object : LinkedHashMap<String, ByteArray>(8, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ByteArray>?): Boolean =
            size > 8
    }

    suspend fun load(url: String): ByteArray? = withContext(Dispatchers.IO) {
        if (url.isBlank() || !HostPolicy.isAllowedUrl(url, baseUrl)) return@withContext null
        synchronized(cache) { cache[url] }?.let { return@withContext it }
        runCatching {
            client.newCall(Request.Builder().url(url).get().build()).execute().use { response ->
                if (!response.isSuccessful) return@runCatching null
                val bytes = response.body?.bytes() ?: return@runCatching null
                if (bytes.isEmpty() || bytes.size > MAX_BYTES) return@runCatching null
                synchronized(cache) { cache[url] = bytes }
                bytes
            }
        }.getOrNull()
    }

    private companion object {
        const val MAX_BYTES = 8 * 1024 * 1024
    }
}
