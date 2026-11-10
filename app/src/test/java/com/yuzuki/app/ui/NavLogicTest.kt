package com.yuzuki.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NavLogicTest {
    private sealed interface R {
        data object Library : R
        data class Work(val id: String) : R
        data object Favorites : R
    }

    @Test
    fun `go 后返回到上一页`() {
        val nav = NavStack<R>(R.Library)
        nav.go(R.Work("RJ1"))
        assertEquals(R.Work("RJ1"), nav.current)
        assertTrue(nav.canBack)
        assertTrue(nav.back())
        assertEquals(R.Library, nav.current)
        assertFalse(nav.canBack)
    }

    @Test
    fun `replace 清空栈后 backOr 仍能回主页`() {
        val nav = NavStack<R>(R.Library)
        nav.go(R.Favorites)
        nav.replace(R.Work("RJ2"))
        assertFalse(nav.canBack)
        assertFalse(nav.back())
        // 修复前：停在详情页，左上角返回点了没有任何反应
        assertEquals(R.Work("RJ2"), nav.current)
        assertTrue(nav.backOr(R.Library))
        assertEquals(R.Library, nav.current)
    }

    @Test
    fun `多层作品详情逐层返回`() {
        val nav = NavStack<R>(R.Library)
        nav.go(R.Work("RJ1"))
        nav.go(R.Work("RJ2"))
        assertTrue(nav.back())
        assertEquals(R.Work("RJ1"), nav.current)
        assertTrue(nav.back())
        assertEquals(R.Library, nav.current)
    }

    @Test
    fun `主页栈空时 backOr 交还双击退出`() {
        val nav = NavStack<R>(R.Library)
        assertFalse(nav.backOr(R.Library))
        assertEquals(R.Library, nav.current)
    }

    @Test
    fun `Tab 切换清空跨 Tab 的返回链`() {
        val nav = NavStack<R>(R.Library)
        nav.go(R.Work("RJ1"))
        nav.replace(R.Favorites)
        assertFalse(nav.canBack)
        assertEquals(R.Favorites, nav.current)
    }

    @Test
    fun `pageSize 与后端 clamp 一致`() {
        assertEquals(50, PagingLogic.pageSize(50))
        assertEquals(200, PagingLogic.pageSize(500))
        assertEquals(1, PagingLogic.pageSize(0))
        assertEquals(1, PagingLogic.pageSize(-10))
    }

    @Test
    fun `预取只在接近末尾且非加载态触发`() {
        assertTrue(PagingLogic.shouldLoadMore(45, 50, 300, false, false, false))
        assertFalse(PagingLogic.shouldLoadMore(45, 50, 300, true, false, false))
        assertFalse(PagingLogic.shouldLoadMore(45, 50, 300, false, true, false))
        assertFalse(PagingLogic.shouldLoadMore(45, 50, 300, false, false, true))
        assertFalse(PagingLogic.shouldLoadMore(10, 50, 300, false, false, false))
        assertFalse(PagingLogic.shouldLoadMore(45, 50, 50, false, false, false))
        assertFalse(PagingLogic.shouldLoadMore(45, 0, 300, false, false, false))
    }

    @Test
    fun `末页判定覆盖总数与不足一页`() {
        assertTrue(PagingLogic.endReached(100, 100, 50, 50))
        assertTrue(PagingLogic.endReached(75, 300, 25, 50))
        assertTrue(PagingLogic.endReached(50, 300, 0, 50))
        assertFalse(PagingLogic.endReached(50, 300, 50, 50))
    }
}
