package com.yuzuki.app.ui

import com.yuzuki.app.data.FilterCond
import com.yuzuki.app.data.LibraryFilterPrefs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 锁定「条件记在本机」的约定：
 * 1) 未挂落盘钩子（单测 / 冷启动前）时改条件不触达任何存储，也不会碰到未初始化的 `AppContainer`；
 * 2) 挂上钩子后每次改条件都把快照交出去一次，供 `FilterStore` 写 DataStore；
 * 3) `restore` 原样应用上次的条件，并把随机模式收回常规列表。
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
        LibraryState.persistHook = null

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

    @Test
    fun everyConditionChangeHandsSnapshotToHook() {
        resetToDefaults()
        val saved = mutableListOf<LibraryFilterPrefs>()
        LibraryState.persistHook = { saved += it }
        try {
            LibraryState.updateQuery("耳语")
            LibraryState.addCond(FilterCond("tag", "全年龄"))
            LibraryState.removeCond(FilterCond("tag", "全年龄"))
            LibraryState.addCond(FilterCond("actor", "五十嵐"))
            LibraryState.updateSort("sales")
            LibraryState.toggleOrder()

            assertEquals(6, saved.size)
            assertEquals(
                LibraryFilterPrefs(
                    query = "耳语",
                    conds = listOf(FilterCond("actor", "五十嵐")),
                    sort = "sales",
                    order = "asc",
                ),
                saved.last(),
            )
            // 快照与内存态一致
            assertEquals(LibraryState.snapshot(), saved.last())
        } finally {
            LibraryState.persistHook = null
            resetToDefaults()
        }
    }

    @Test
    fun restoreAppliesSavedConditionsAndLeavesRandomMode() {
        resetToDefaults()
        LibraryState.pullRandom()
        assertTrue(LibraryState.randomMode)

        LibraryState.restore(
            LibraryFilterPrefs(
                query = "测试",
                conds = listOf(FilterCond("actor", "五十嵐")),
                sort = "release",
                order = "asc",
            ),
        )

        assertEquals("测试", LibraryState.query)
        assertEquals(listOf(FilterCond("actor", "五十嵐")), LibraryState.conds)
        assertEquals("release", LibraryState.sort)
        assertEquals("asc", LibraryState.order)
        assertFalse(LibraryState.randomMode)

        resetToDefaults()
    }
}
