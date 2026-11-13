package com.yuzuki.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 图片下载落盘（查看器 ⋮ →「下载图片」）的纯逻辑断言。
 * 只覆盖不依赖 Android 的部分：MIME 推断、文件名清洗、相册目录约定。
 */
class ImageSaveTest {

    @Test
    fun `常见后缀映射到图片 MIME`() {
        assertEquals("image/jpeg", ImageSave.mimeType("cover.jpg"))
        assertEquals("image/jpeg", ImageSave.mimeType("cover.JPEG"))
        assertEquals("image/png", ImageSave.mimeType("a.png"))
        assertEquals("image/webp", ImageSave.mimeType("b.webp"))
        assertEquals("image/gif", ImageSave.mimeType("c.gif"))
        assertEquals("image/heic", ImageSave.mimeType("d.heic"))
    }

    @Test
    fun `未知后缀与无后缀按 jpeg 处理`() {
        assertEquals("image/jpeg", ImageSave.mimeType("noext"))
        assertEquals("image/jpeg", ImageSave.mimeType("weird.xyz"))
        assertEquals("image/jpeg", ImageSave.mimeType(""))
    }

    @Test
    fun `文件名去掉路径与前后的空白`() {
        assertEquals("a.jpg", ImageSave.safeFileName("dir/sub/a.jpg"))
        assertEquals("a.jpg", ImageSave.safeFileName("dir\\sub\\a.jpg"))
        assertEquals("a.jpg", ImageSave.safeFileName("  a.jpg  "))
    }

    @Test
    fun `非法字符与控制字符替换为下划线`() {
        assertEquals("a_b.jpg", ImageSave.safeFileName("a:b.jpg"))
        assertEquals("a_b.jpg", ImageSave.safeFileName("a?b.jpg"))
        assertEquals("a_b.jpg", ImageSave.safeFileName("a|b.jpg"))
        assertEquals("a_b.jpg", ImageSave.safeFileName("a\nb.jpg"))
        assertEquals("a_b.jpg", ImageSave.safeFileName("a*b.jpg"))
    }

    @Test
    fun `空名与纯点回退 imagejpg`() {
        assertEquals("image.jpg", ImageSave.safeFileName(""))
        assertEquals("image.jpg", ImageSave.safeFileName("   "))
        assertEquals("image.jpg", ImageSave.safeFileName("dir/"))
        assertEquals("image.jpg", ImageSave.safeFileName(".."))
        assertEquals("image.jpg", ImageSave.safeFileName("."))
    }

    @Test
    fun `没有扩展名时补 jpg 否则 MediaStore 不按图片归类`() {
        assertEquals("cover.jpg", ImageSave.safeFileName("cover"))
        // 形如 .jpg 的点文件没有主体，补成 image.jpg
        assertEquals("image.jpg", ImageSave.safeFileName(".jpg"))
        assertEquals("cover.webp", ImageSave.safeFileName("cover.webp"))
    }

    @Test
    fun `相册目录固定在 Pictures 下且可嵌套`() {
        assertTrue(ImageSave.ALBUM_DIR.startsWith("Pictures/"))
        assertEquals("Yuzuki", ImageSave.ALBUM_DIR.substringAfterLast('/'))
        assertFalse(ImageSave.ALBUM_DIR.endsWith("/"))
    }
}
