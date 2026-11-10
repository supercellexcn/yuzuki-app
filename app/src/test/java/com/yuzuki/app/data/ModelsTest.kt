package com.yuzuki.app.data

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelsTest {
    @Test
    fun work字段与后端snake_case一一对应() {
        val json = JSONObject(
            """{"id":7,"rj_code":"RJ123456","title":"测试作品","circle":"社团A",
               "release_date":"2026-01-01","series":"系列B","sales_count":1234,
               "play_count":3,"file_count":12,"cover_url":"/api/cover/RJ123456",
               "actors":["声优1","声优2"],
               "tags":[{"name":"标签1","category":"genre"}]}""".trimIndent(),
        )
        val work = Models.work(json)
        assertEquals(7L, work.id)
        assertEquals("RJ123456", work.rjCode)
        assertEquals(1234, work.salesCount)
        assertEquals(listOf("声优1", "声优2"), work.actors)
        assertEquals("标签1", work.tags.first().name)
        assertEquals("/api/cover/RJ123456", work.coverUrl)
    }

    @Test
    fun sales_count为null时映射为null() {
        val work = Models.work(JSONObject("""{"rj_code":"RJ1","sales_count":null}"""))
        assertNull(work.salesCount)
    }

    @Test
    fun prefs条件可往返() {
        val json = LibraryPrefs(
            conditions = listOf(FilterCond("actor", "声优X"), FilterCond("tag", "标签Y")),
            q = "搜索词", sort = "sales", order = "asc",
        ).toJson()
        val parsed = Models.prefs(json)
        assertEquals(2, parsed.conditions.size)
        assertEquals("actor:声优X", parsed.conditions.first().encoded())
        assertEquals("搜索词", parsed.q)
        assertEquals("sales", parsed.sort)
        assertEquals("asc", parsed.order)
    }

    @Test
    fun settings全量往返且不回写只读派生字段() {
        val original = AppSettings(
            pageSize = 30, seekSeconds = 20, themeColorNav = "#112233",
            wallpaperMobileEnabled = false, panelBlur = 42, animationDuration = 0.4,
        )
        val json = original.toJson()
        assertFalse(json.has("has_default_cover"))
        val parsed = Models.settings(json)
        assertEquals(30, parsed.pageSize)
        assertEquals(20, parsed.seekSeconds)
        assertEquals("#112233", parsed.themeColorNav)
        assertFalse(parsed.wallpaperMobileEnabled)
        assertEquals(42, parsed.panelBlur)
        assertEquals(0.4, parsed.animationDuration, 0.0001)
        assertTrue(json.has("recommendation_enabled"))
        assertTrue(json.has("source_paths"))
    }

    @Test
    fun cond编码为后端要求的type_colon_value格式() {
        assertEquals("circle:社团A", FilterCond("circle", "社团A").encoded())
        assertEquals("tag:标签 Y", FilterCond("tag", "标签 Y").encoded())
    }
}
