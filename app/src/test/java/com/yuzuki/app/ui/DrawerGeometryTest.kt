package com.yuzuki.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 浮动圆控件的纯几何：滑入基准宽度、固定的 FAB 尺寸（56dp 圆 / 24dp 图标 / 16dp 间距）、
 * 以及圆组**固定贴底**（底边压在底部导航栏上方 18dp，与起手高度无关）。
 *
 * 这里刻意**不**按屏宽等分圆直径：早期版本那样做，360 屏上圆会到 71dp，整行铺满，显得又大又散；
 * 现在对齐 Material 3 FAB 与主流浮动圆菜单的固定尺寸，规则由本测试钉住。
 */
class DrawerGeometryTest {

    @Test
    fun `滑入基准宽度按屏宽比例`() {
        // 360dp 屏 → 288dp（360 × 0.8）
        assertEquals(288f, DrawerGeometry.swipeDp(360f), 0.01f)
        // 极窄屏按 240dp 兜底，避免滑一点点就全开
        assertEquals(192f, DrawerGeometry.swipeDp(240f), 0.01f)
        assertEquals(192f, DrawerGeometry.swipeDp(200f), 0.01f)
    }

    @Test
    fun `圆直径固定为 M3 FAB 尺寸且图标为标准 24dp`() {
        assertEquals(56f, DrawerGeometry.CIRCLE_DP, 0.01f)
        // 56 × 0.43 ≈ 24.1dp，正好是 Material 标准图标尺寸
        val icon = DrawerGeometry.CIRCLE_DP * DrawerGeometry.ICON_RATIO
        assertEquals(24.08f, icon, 0.01f)
        // 间距走 4dp 栅格
        assertEquals(0f, DrawerGeometry.GAP_DP % 4f, 0.01f)
    }

    @Test
    fun `整组宽度居中且窄屏也放得下`() {
        val group = DrawerGeometry.groupWidthDp()
        // 4 × 56 + 3 × 16 = 272
        assertEquals(272f, group, 0.01f)
        // 最窄的常见屏（320dp）两侧仍各有 24dp 留白，不会贴边
        assertTrue(group <= 320f - 2 * 24f)
        // 竖排两行（上排 4 筛选 + 下排账号 / 设置）的总高
        val groupHeight = DrawerGeometry.CIRCLE_DP * 2 + DrawerGeometry.GAP_DP
        assertEquals(128f, groupHeight, 0.01f)
    }

    @Test
    fun `圆组固定贴在导航栏上方`() {
        // 底边 = 导航栏 65 + 固定间隙 18 = 83dp：从屏幕哪个高度划出都是这个位置
        assertEquals(83f, DrawerGeometry.bottomPadDp(65f, 18f), 0.01f)
        // 底边始终高于导航栏本身，圆不会被栏遮住
        assertTrue(DrawerGeometry.bottomPadDp(65f, 18f) > 65f)
        // 栏高变化时圆组跟着抬高，间隙保持 18dp
        assertEquals(96f, DrawerGeometry.bottomPadDp(78f, 18f), 0.01f)
    }
}
