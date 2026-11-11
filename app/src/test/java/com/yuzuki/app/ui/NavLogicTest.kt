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

    private fun fp(
        query: String = "",
        conds: List<String> = emptyList(),
        sort: String = "added",
        order: String = "desc",
        random: Boolean = false,
    ) = ScrollRestore.fingerprint(listOf(query, sort, order, random.toString()) + conds)

    @Test
    fun `冷启动没有数据时必须重新拉第一页`() {
        // 不能为了「返回复用」跳过首次 reset，否则主界面首帧没有作品，N 个作品的数量不对
        assertTrue(ScrollRestore.shouldReset(null, fp()))
        assertTrue(ScrollRestore.shouldReset(null, fp(query = "RJ")))
    }

    @Test
    fun `返回主页且条件未变时复用数据`() {
        val current = fp(sort = "sales", conds = listOf("tag:治愈"))
        assertFalse(ScrollRestore.shouldReset(current, current))
    }

    @Test
    fun `搜索词筛选排序升降序随机任一变化都要重载`() {
        val base = fp()
        assertTrue(ScrollRestore.shouldReset(base, fp(query = "治愈")))
        assertTrue(ScrollRestore.shouldReset(base, fp(conds = listOf("actor:花泽"))))
        assertTrue(ScrollRestore.shouldReset(base, fp(sort = "sales")))
        assertTrue(ScrollRestore.shouldReset(base, fp(order = "asc")))
        assertTrue(ScrollRestore.shouldReset(base, fp(random = true)))
        assertFalse(ScrollRestore.shouldReset(base, fp()))
    }

    @Test
    fun `指纹用长度前缀避免查询词里的分隔符碰撞`() {
        assertFalse(fp(query = "a|b") == fp(query = "a", conds = listOf("b")))
        assertFalse(fp(query = "1:x") == fp(query = "1", conds = listOf("x")))
        assertEquals(fp(query = "a|b"), fp(query = "a|b"))
    }

    @Test
    fun `冷启动不额外置顶条件变化才置顶`() {
        assertFalse(ScrollRestore.shouldScrollTopOnReset(null))
        assertTrue(ScrollRestore.shouldScrollTopOnReset(fp()))
        assertTrue(ScrollRestore.shouldScrollTopOnReset(fp(sort = "sales")))
    }

    @Test
    fun `库 Tab 只在非播放器返回且已在库页时回顶`() {
        assertTrue(ScrollRestore.shouldScrollTopOnLibraryTab(false, true))
        // 从全屏播放器点「库」Tab 返回：保留滚动位置
        assertFalse(ScrollRestore.shouldScrollTopOnLibraryTab(true, true))
        // 从播放器 / 其它页面回到库页：走 replace，由 ViewModel 与滚动 state 保留位置
        assertFalse(ScrollRestore.shouldScrollTopOnLibraryTab(true, false))
        assertFalse(ScrollRestore.shouldScrollTopOnLibraryTab(false, false))
    }

    @Test
    fun `置顶票只在进入页面之后变化才消费`() {
        assertFalse(ScrollRestore.shouldConsumeTicket(0, 0))
        assertFalse(ScrollRestore.shouldConsumeTicket(5, 5))
        assertTrue(ScrollRestore.shouldConsumeTicket(1, 0))
        assertTrue(ScrollRestore.shouldConsumeTicket(6, 5))
    }

    @Test
    fun `恢复滚动位置只在数据属于当前条件且快照有效时`() {
        val current = fp(query = "治愈")
        assertTrue(ScrollRestore.shouldRestore(current, current, current, 120, 80))
        assertFalse(ScrollRestore.shouldRestore(null, current, current, 120, 80))
        assertFalse(ScrollRestore.shouldRestore(fp(), current, current, 120, 80))
        assertFalse(ScrollRestore.shouldRestore(current, fp(), current, 120, 80))
        assertFalse(ScrollRestore.shouldRestore(current, current, current, 0, 80))
        assertFalse(ScrollRestore.shouldRestore(current, current, current, 120, 0))
        assertFalse(ScrollRestore.shouldRestore(current, current, current, 120, 120))
    }

    @Test
    fun `恢复下标夹紧到有效范围`() {
        assertEquals(0, ScrollRestore.clampIndex(-3, 50))
        assertEquals(49, ScrollRestore.clampIndex(999, 50))
        assertEquals(0, ScrollRestore.clampIndex(80, 0))
        assertEquals(30, ScrollRestore.clampIndex(30, 50))
    }
}
