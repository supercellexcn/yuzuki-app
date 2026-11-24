package com.yuzuki.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 库页筛选条件的本机记忆：编解码往返、坏数据兜底、白名单与条数上限（不依赖 Android）。 */
class FilterPrefsTest {

    @Test
    fun `默认值就是空关键词加默认排序`() {
        val prefs = LibraryFilterPrefs.Default
        assertEquals("", prefs.query)
        assertTrue(prefs.conds.isEmpty())
        assertEquals("added", prefs.sort)
        assertEquals("desc", prefs.order)
    }

    @Test
    fun `编解码往返保持一致`() {
        val prefs = LibraryFilterPrefs(
            query = "耳语",
            conds = listOf(FilterCond("tag", "全年龄"), FilterCond("actor", "五十嵐")),
            sort = "sales",
            order = "asc",
        )
        val decoded = LibraryFilterPrefs.decode(prefs.encode())
        assertEquals(prefs.sanitized(), decoded)
        assertEquals(listOf(FilterCond("tag", "全年龄"), FilterCond("actor", "五十嵐")), decoded.conds)
    }

    @Test
    fun `空串与坏 JSON 都退回默认值`() {
        assertEquals(LibraryFilterPrefs.Default, LibraryFilterPrefs.decode(null))
        assertEquals(LibraryFilterPrefs.Default, LibraryFilterPrefs.decode(""))
        assertEquals(LibraryFilterPrefs.Default, LibraryFilterPrefs.decode("   "))
        assertEquals(LibraryFilterPrefs.Default, LibraryFilterPrefs.decode("not a json"))
        assertEquals(LibraryFilterPrefs.Default, LibraryFilterPrefs.decode("[]"))
    }

    @Test
    fun `字段缺失按默认值补齐`() {
        val decoded = LibraryFilterPrefs.decode("{}")
        assertEquals(LibraryFilterPrefs.Default, decoded)
    }

    @Test
    fun `排序与升降序走白名单`() {
        val decoded = LibraryFilterPrefs.decode("""{"q":"x","sort":"bogus","order":"up"}""")
        assertEquals("added", decoded.sort)
        assertEquals("desc", decoded.order)

        val asc = LibraryFilterPrefs(sort = "release", order = "asc").sanitized()
        assertEquals("release", asc.sort)
        assertEquals("asc", asc.order)
        assertEquals("sales", LibraryFilterPrefs(sort = "sales").sanitized().sort)
    }

    @Test
    fun `条件去重并夹到 5 条`() {
        val many = (1..8).map { FilterCond("tag", "标签$it") } + FilterCond("tag", "标签1")
        val clean = LibraryFilterPrefs(conds = many).sanitized()
        assertEquals(LibraryFilterPrefs.MAX_CONDS, clean.conds.size)
        assertEquals(clean.conds.distinct(), clean.conds)
    }

    @Test
    fun `未知类型与空值条件被丢掉`() {
        val clean = LibraryFilterPrefs(
            conds = listOf(
                FilterCond("tag", "保留"),
                FilterCond("bogus", "丢掉"),
                FilterCond("actor", "  "),
            ),
        ).sanitized()
        assertEquals(listOf(FilterCond("tag", "保留")), clean.conds)
    }

    @Test
    fun `关键词首尾空白被裁掉`() {
        assertEquals("测试", LibraryFilterPrefs(query = " 测试 ").sanitized().query)
    }
}
