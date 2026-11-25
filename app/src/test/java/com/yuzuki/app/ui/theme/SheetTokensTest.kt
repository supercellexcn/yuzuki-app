package com.yuzuki.app.ui.theme

import androidx.compose.foundation.shape.CornerSize
import androidx.compose.ui.unit.dp
import com.yuzuki.app.ui.SheetDrag
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 钉住「弹出层令牌只有一处」：
 * 作品详情、播放列表、抽屉筛选弹层都读 [SheetTokens]，任何一处在页面里另写圆角 / 白条 / 时长都会在这里露馅。
 */
class SheetTokensTest {
    @Test
    fun 顶部圆角三处共用同一个值() {
        assertEquals(12f, SheetTokens.CornerRadius.value, 0.001f)
        // SheetShape 必须由令牌推导出来，不能在别处另起一个 RoundedCornerShape(24.dp)
        assertEquals(CornerSize(SheetTokens.CornerRadius), SheetShape.topStart)
        assertEquals(CornerSize(SheetTokens.CornerRadius), SheetShape.topEnd)
        // 只有上面两角圆：底部贴着内容区底边
        assertEquals(CornerSize(0.dp), SheetShape.bottomStart)
        assertEquals(CornerSize(0.dp), SheetShape.bottomEnd)
    }

    @Test
    fun 白条尺寸固定且高度小于占位高度() {
        assertEquals(64f, SheetTokens.GrabberWidth.value, 0.001f)
        assertEquals(5f, SheetTokens.GrabberHeight.value, 0.001f)
        // 白条居中放在占位带子里，热区必须比白条本身高，否则按不准
        assertTrue(SheetTokens.GrabberArea > SheetTokens.GrabberHeight)
        assertTrue(SheetTokens.GrabberArea.value >= 18f)
    }

    @Test
    fun 动画时长齐备且回弹快于收回() {
        assertTrue(SheetTokens.OPEN_MS in 120..400)
        assertTrue(SheetTokens.REBOUND_MS in 80..SheetTokens.CLOSE_MS)
        assertTrue(SheetTokens.CLOSE_MS in 120..400)
    }

    @Test
    fun 松手门槛与手势逻辑同一份() {
        assertEquals(0.25f, SheetTokens.DISMISS_FRACTION, 0.0001f)
        assertEquals(SheetTokens.DISMISS_FRACTION, SheetDrag.DISMISS_FRACTION, 0.0001f)
        // 起手门槛必须大于触屏抖动，又不至于让人以为不跟手
        assertTrue(SheetTokens.DragSlop.value in 4f..12f)
    }
}
