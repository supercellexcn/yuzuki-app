package com.yuzuki.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 浮动圆控件的纯几何：滑入基准宽度、固定的 FAB 尺寸（56dp 圆 / 24dp 图标 / 16dp 间距）、
 * 以及圆组跟随起手高度并在上下边界被钳制。
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
        // 竖排两行（4 筛选 + 1 收藏）的总高
        val groupHeight = DrawerGeometry.CIRCLE_DP * 2 + DrawerGeometry.GAP_DP
        assertEquals(128f, groupHeight, 0.01f)
    }

    @Test
    fun `圆组以起手高度为中心`() {
        // 可用高度 800、组高 200、留白 12 → 起手在正中时，顶边 = 800/2 - 200/2
        assertEquals(300f, DrawerGeometry.groupTopPx(0.5f, 800f, 200f, 12f), 0.01f)
        // 从上往下 1/4 处起手 → 200 - 100
        assertEquals(100f, DrawerGeometry.groupTopPx(0.25f, 800f, 200f, 12f), 0.01f)
    }

    @Test
    fun `起手太靠上或太靠下时圆组被钳制`() {
        // 起手在屏幕最上沿：顶边不能为负，压回留白处
        assertEquals(12f, DrawerGeometry.groupTopPx(0f, 800f, 200f, 12f), 0.01f)
        // 起手在屏幕最下沿：底边不能超出，顶边顶到 800 - 200 - 12
        assertEquals(588f, DrawerGeometry.groupTopPx(1f, 800f, 200f, 12f), 0.01f)
        // 越界输入（比例 <0 / >1）按边界处理
        assertEquals(12f, DrawerGeometry.groupTopPx(-0.5f, 800f, 200f, 12f), 0.01f)
        assertEquals(588f, DrawerGeometry.groupTopPx(1.5f, 800f, 200f, 12f), 0.01f)
    }

    @Test
    fun `可用高度装不下圆组时退回顶部留白`() {
        // 横屏 / 小窗：可用高度比圆组还矮，不能算出负数顶边
        assertEquals(12f, DrawerGeometry.groupTopPx(0.5f, 150f, 200f, 12f), 0.01f)
    }
}
