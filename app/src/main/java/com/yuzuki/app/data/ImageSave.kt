package com.yuzuki.app.data

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.File

/**
 * 图片下载落盘（图片查看器 ⋮ →「下载图片」）。
 *
 * 落点固定为系统相册 `Pictures/Yuzuki/`：
 * - Android 10+ 走 MediaStore + `RELATIVE_PATH`，不需要任何存储权限；
 * - Android 8/9 只能写公共目录，调用方需先申请 `WRITE_EXTERNAL_STORAGE`。
 *
 * MIME 推断与文件名清洗不依赖 Android，抽成纯函数便于 JVM 单测。
 */
object ImageSave {
    /** 相册内的子目录（Q+ 的 RELATIVE_PATH，同时也是 8/9 的 Pictures 子目录名）。 */
    const val ALBUM_DIR = "Pictures/Yuzuki"

    /** 后缀 → MIME。未知后缀按 jpeg 处理（服务端图片基本都是 jpg/png/webp）。 */
    fun mimeType(filename: String): String =
        when (filename.substringAfterLast('.', "").lowercase()) {
            "png" -> "image/png"
            "webp" -> "image/webp"
            "gif" -> "image/gif"
            "bmp" -> "image/bmp"
            "heic", "heif" -> "image/heic"
            "avif" -> "image/avif"
            "jpeg", "jpg" -> "image/jpeg"
            else -> "image/jpeg"
        }

    /**
     * MediaStore 的 DISPLAY_NAME 不允许带路径分隔符，
     * 顺便把 Windows 侧的非法字符换成下划线；清洗后为空则回退 `image.jpg`。
     */
    fun safeFileName(filename: String): String {
        val base = filename.substringAfterLast('/').substringAfterLast('\\').trim()
        val cleaned = buildString {
            base.forEach { c ->
                append(if (c.isISOControl() || c in ILLEGAL) '_' else c)
            }
        }.trim()
        if (cleaned.isEmpty() || cleaned == "." || cleaned == "..") return "image.jpg"
        // 没有扩展名时补 .jpg，否则 MediaStore 无法按图片归类；形如 ".jpg" 的点文件补个主体名
        val stem = cleaned.substringBeforeLast('.')
        if (cleaned.contains('.') && stem.isNotEmpty()) return cleaned
        return if (cleaned.startsWith(".")) "image$cleaned" else "$cleaned.jpg"
    }

    private val ILLEGAL = charArrayOf(':', '*', '?', '"', '<', '>', '|')

    /** 下载并写入相册。失败时把原因原样带回给 UI。 */
    suspend fun downloadToGallery(
        context: Context,
        url: String,
        filename: String,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val bytes = fetch(url)
            if (bytes.isEmpty()) error("空响应")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                insertViaMediaStore(context, bytes, filename)
            } else {
                insertLegacy(context, bytes, filename)
            }
        }
    }

    private fun fetch(url: String): ByteArray {
        val request = Request.Builder().url(url).get().build()
        return AppContainer.http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("HTTP ${response.code}")
            response.body?.bytes() ?: error("空响应")
        }
    }

    /** Android 10+：MediaStore 直接落 Pictures/Yuzuki，无需权限。 */
    private fun insertViaMediaStore(context: Context, bytes: ByteArray, filename: String) {
        val resolver = context.contentResolver
        val name = safeFileName(filename)
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, name)
            put(MediaStore.Images.Media.MIME_TYPE, mimeType(name))
            put(MediaStore.Images.Media.RELATIVE_PATH, ALBUM_DIR)
            // 先标记 pending，写完再放行，避免相册里出现半张图
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: error("无法创建相册条目")
        runCatching {
            resolver.openOutputStream(uri)?.use { it.write(bytes) } ?: error("无法写入图片")
        }.onFailure {
            resolver.delete(uri, null, null)
            throw it
        }
        resolver.update(
            uri,
            ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) },
            null,
            null,
        )
    }

    /** Android 8/9：写公共 Pictures 目录 + 主动通知相册扫描（调用方已拿到存储权限）。 */
    @Suppress("DEPRECATION")
    private fun insertLegacy(context: Context, bytes: ByteArray, filename: String) {
        val pictures = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
        val dir = File(pictures, "Yuzuki")
        if (!dir.exists() && !dir.mkdirs()) error("无法创建目录")
        val target = uniqueFile(dir, safeFileName(filename))
        target.writeBytes(bytes)
        MediaScannerConnection.scanFile(
            context,
            arrayOf(target.absolutePath),
            arrayOf(mimeType(target.name)),
            null,
        )
    }

    /** 同名文件已存在时追加 ` (1)` / ` (2)`…，不覆盖用户已有的图。 */
    private fun uniqueFile(dir: File, name: String): File {
        val stem = name.substringBeforeLast('.', name)
        val ext = name.substringAfterLast('.', "")
        var candidate = File(dir, name)
        var i = 1
        while (candidate.exists()) {
            val renamed = if (ext.isEmpty()) "$stem (${i})" else "$stem (${i}).$ext"
            candidate = File(dir, renamed)
            i++
        }
        return candidate
    }
}
