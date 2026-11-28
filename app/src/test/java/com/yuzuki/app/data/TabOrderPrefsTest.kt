package com.yuzuki.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 板块顺序偏好：默认顺序、脏数据兜底、移动、编解码往返（不依赖 Android）。 */
class TabOrderPrefsTest {

    @Test
    fun `默认顺序就是库 收藏 播放器 历史`() {
        assertEquals(
            listOf(MainTab.Library, MainTab.Favorites, MainTab.Player, MainTab.History),
            TabOrderPrefs.Default.order,
        )
        // 枚举声明顺序 = 默认顺序，补全逻辑依赖这一点
        assertEquals(MainTab.Library, MainTab.entries.first())
        assertEquals(listOf("库", "收藏", "播放器", "历史"), MainTab.entries.map { it.label })
    }

    @Test
    fun `合法顺序原样保留且长度恒为 4`() {
        val prefs = TabOrderPrefs(
            listOf(MainTab.Player, MainTab.Library, MainTab.History, MainTab.Favorites),
        ).sanitized()
        assertEquals(
            listOf(MainTab.Player, MainTab.Library, MainTab.History, MainTab.Favorites),
            prefs.order,
        )
        assertEquals(4, prefs.order.size)
    }

    @Test
    fun `缺项按默认顺序补到末尾`() {
        val prefs = TabOrderPrefs(listOf(MainTab.Player)).sanitized()
        assertEquals(
            listOf(MainTab.Player, MainTab.Library, MainTab.Favorites, MainTab.History),
            prefs.order,
        )
    }

    @Test
    fun `重复项只保留第一次出现的位置`() {
        val prefs = TabOrderPrefs(
            listOf(MainTab.Player, MainTab.Player, MainTab.Library, MainTab.Library),
        ).sanitized()
        assertEquals(
            listOf(MainTab.Player, MainTab.Library, MainTab.Favorites, MainTab.History),
            prefs.order,
        )
    }

    @Test
    fun `页码与板块双向换算`() {
        val prefs = TabOrderPrefs(
            listOf(MainTab.History, MainTab.Library, MainTab.Player, MainTab.Favorites),
        ).sanitized()
        assertEquals(0, prefs.indexOf(MainTab.History))
        assertEquals(1, prefs.indexOf(MainTab.Library))
        assertEquals(MainTab.History, prefs.tabAt(0))
        assertEquals(MainTab.Favorites, prefs.tabAt(3))
        // 越界兜底：页码落到库页，不抛异常
        assertEquals(MainTab.Library, prefs.tabAt(9))
    }

    @Test
    fun `上移下移是搬移而不是交换`() {
        // 把「库」从 0 挪到 3：其余三项整体前移一位
        assertEquals(
            listOf(MainTab.Favorites, MainTab.Player, MainTab.History, MainTab.Library),
            TabOrderPrefs.Default.moved(0, 3).order,
        )
        // 把「历史」从 3 挪到 0
        assertEquals(
            listOf(MainTab.History, MainTab.Library, MainTab.Favorites, MainTab.Player),
            TabOrderPrefs.Default.moved(3, 0).order,
        )
        // 相邻挪动 = 交换
        assertEquals(
            listOf(MainTab.Favorites, MainTab.Library, MainTab.Player, MainTab.History),
            TabOrderPrefs.Default.moved(0, 1).order,
        )
    }

    @Test
    fun `越界移动原样返回`() {
        assertEquals(TabOrderPrefs.Default.order, TabOrderPrefs.Default.moved(-1, 2).order)
        assertEquals(TabOrderPrefs.Default.order, TabOrderPrefs.Default.moved(0, 9).order)
        assertEquals(TabOrderPrefs.Default.order, TabOrderPrefs.Default.moved(2, 2).order)
    }

    @Test
    fun `编解码往返与脏数据兜底`() {
        val custom = TabOrderPrefs(
            listOf(MainTab.History, MainTab.Player, MainTab.Favorites, MainTab.Library),
        )
        assertEquals(custom.order, TabOrderPrefs.decode(custom.encode()).order)

        // null / 空串 → 默认
        assertEquals(TabOrderPrefs.Default.order, TabOrderPrefs.decode(null).order)
        assertEquals(TabOrderPrefs.Default.order, TabOrderPrefs.decode("  ").order)
        // 未知名字丢掉、缺的补齐、重复去重
        assertEquals(
            listOf(MainTab.History, MainTab.Library, MainTab.Favorites, MainTab.Player),
            TabOrderPrefs.decode(" Nope , History , History , Library ").order,
        )
        // 全脏 → 默认
        assertEquals(TabOrderPrefs.Default.order, TabOrderPrefs.decode("a,b,c").order)
    }

    @Test
    fun `编码结果稳定且在解码后仍是四项`() {
        assertEquals("Library,Favorites,Player,History", TabOrderPrefs.Default.encode())
        val decoded = TabOrderPrefs.decode("Player,Library")
        assertEquals(4, decoded.order.size)
        assertTrue(decoded.order.first() == MainTab.Player)
    }
}
