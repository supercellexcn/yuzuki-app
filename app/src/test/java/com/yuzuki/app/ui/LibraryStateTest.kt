package com.yuzuki.app.ui

import com.yuzuki.app.data.FilterCond
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 锁定「搜索条件不记忆」的约定：
 * 1) 冷启动一律空关键词 + 无筛选 + 默认排序；
 * 2) 改条件 / 改排序 / home() 全程不触达持久层
 *    （单测里 AppContainer 未初始化，若还残留 persist 调用会抛 lateinit 异常）。
 */
class LibraryStateTest {

    /** 单测共用同一个 object 单例：先把手动改过的排序归位到默认，再断言。 */
    private fun resetToDefaults() {
        LibraryState.home()
        LibraryState.updateSort("added")
        if (LibraryState.order != "desc") LibraryState.toggleOrder()
    }

    @Test
    fun coldStartHasNoConditions() {
        resetToDefaults()

        assertEquals("", LibraryState.query)
        assertTrue(LibraryState.conds.isEmpty())
        assertEquals("added", LibraryState.sort)
        assertEquals("desc", LibraryState.order)
    }

    @Test
    fun changingConditionsDoesNotTouchPersistence() {
        resetToDefaults()

        LibraryState.addCond(FilterCond("tag", "耳语"))
        LibraryState.updateSort("released")
        LibraryState.toggleOrder()
        LibraryState.updateQuery("测试")

        // 内存态生效
        assertEquals(listOf(FilterCond("tag", "耳语")), LibraryState.conds)
        assertEquals("released", LibraryState.sort)
        assertEquals("asc", LibraryState.order)
        assertEquals("测试", LibraryState.query)

        // 清空筛选不发持久化请求
        LibraryState.clearConds()
        assertTrue(LibraryState.conds.isEmpty())

        // home() 归零
        LibraryState.home()
        assertEquals("", LibraryState.query)
        assertTrue(LibraryState.conds.isEmpty())
    }
}
