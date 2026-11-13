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

    @Test
    fun `详情点文件播放必须先收起覆盖层`() {
        // 覆盖层开着（详情 / 收藏 / 设置 / 改密）：不收起就会盖住 Pager 的播放器页
        assertTrue(PlayerNav.shouldCloseOverlay(currentIsLibrary = false))
        // 已经在库页：没有覆盖层可收，只翻页
        assertFalse(PlayerNav.shouldCloseOverlay(currentIsLibrary = true))
    }

    @Test
    fun `全屏播放器展开时 nav 不同步 Pager`() {
        // 播放页点 (i) 进详情再点文件：nav 回到库页，但 Pager 必须留在播放器页
        assertFalse(PlayerNav.shouldSyncPager(fullscreen = true))
        assertTrue(PlayerNav.shouldSyncPager(fullscreen = false))
    }

    @Test
    fun `图片查看器横滑未过阈值回弹`() {
        // 屏宽 1000，阈值 250：滑 200 且手速很慢 → 不翻页
        assertEquals(ViewerSwipe.NONE, ViewerSwipe.decide(-200f, -100f, 1000f))
        assertEquals(ViewerSwipe.NONE, ViewerSwipe.decide(200f, 100f, 1000f))
        assertEquals(ViewerSwipe.NONE, ViewerSwipe.decide(0f, 0f, 1000f))
    }

    @Test
    fun `图片查看器横向位移过阈值即翻页`() {
        // 左滑超过 250 → 下一张；右滑超过 250 → 上一张（首尾循环在 neighbor 里处理）
        assertEquals(ViewerSwipe.NEXT, ViewerSwipe.decide(-300f, 0f, 1000f))
        assertEquals(ViewerSwipe.PREV, ViewerSwipe.decide(300f, 0f, 1000f))
        // 正好等于阈值也算翻页，避免「刚好卡在边界」手感发钝
        assertEquals(ViewerSwipe.NEXT, ViewerSwipe.decide(-250f, 0f, 1000f))
    }

    @Test
    fun `图片查看器快速甩动即使位移小也翻页`() {
        assertEquals(ViewerSwipe.NEXT, ViewerSwipe.decide(-80f, -1500f, 1000f))
        assertEquals(ViewerSwipe.PREV, ViewerSwipe.decide(80f, 1500f, 1000f))
        // 反向甩（速度与位移不同向）不能翻页，否则回弹时会被误判
        assertEquals(ViewerSwipe.NONE, ViewerSwipe.decide(60f, -1500f, 1000f))
        assertEquals(ViewerSwipe.NONE, ViewerSwipe.decide(-60f, 1500f, 1000f))
    }

    @Test
    fun `图片查看器相邻下标首尾循环`() {
        assertEquals(1, ViewerSwipe.neighbor(0, ViewerSwipe.NEXT, 5))
        assertEquals(0, ViewerSwipe.neighbor(4, ViewerSwipe.NEXT, 5))
        assertEquals(4, ViewerSwipe.neighbor(0, ViewerSwipe.PREV, 5))
        assertEquals(3, ViewerSwipe.neighbor(4, ViewerSwipe.PREV, 5))
        // 单张图没有邻居，也不该翻页；未知方向返回 null
        assertEquals(null, ViewerSwipe.neighbor(0, ViewerSwipe.NEXT, 1))
        assertEquals(null, ViewerSwipe.neighbor(0, ViewerSwipe.NONE, 5))
        // 宽高为 0（首帧未测量）不做判定，避免除零式误翻
        assertEquals(ViewerSwipe.NONE, ViewerSwipe.decide(-999f, -9999f, 0f))
    }

    @Test
    fun `横向滚动容器认领后侧栏不拉起`() {
        // 详情页「相关推荐」按下就认领：无论右移多远，都不能把侧栏拽出来
        assertFalse(
            DrawerDrag.canStart(
                sumX = 400f,
                absYAcc = 0f,
                startSlop = 48f,
                innerClaimed = true,
                fullscreen = false,
            ),
        )
    }

    @Test
    fun `侧栏起手判定保持原条件`() {
        fun can(sumX: Float, absY: Float, slop: Float = 48f, fullscreen: Boolean = false) =
            DrawerDrag.canStart(sumX, absY, slop, innerClaimed = false, fullscreen = fullscreen)

        // 页面空白处右滑（未过门槛 / 已过门槛）
        assertFalse(can(30f, 0f))
        assertTrue(can(200f, 10f))
        // 纵向为主：不拉起，避免滑列表时误触
        assertFalse(can(100f, 200f))
        // 全屏播放器展开时不允许拉侧栏
        assertFalse(can(200f, 10f, fullscreen = true))
    }
}
