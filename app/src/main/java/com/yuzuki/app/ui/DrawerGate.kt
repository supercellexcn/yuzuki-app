package com.yuzuki.app.ui

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput

/**
 * 侧栏「旁路观察」手势与页面内横向滚动容器的仲裁。
 *
 * 背景：RootScreen 在 Pager 外层用 Initial pass 旁路观察右拖位移来拉侧栏。它刻意不消费事件
 * （避免和 Pager 抢手势），因此也**看不到子级有没有消费**。页面里「相关推荐」这类横向滚动列表
 * 滚到最左端后再右滑时，没有组件消费这段位移，外层就会误以为用户要开侧栏。
 *
 * 这里让横向滚动容器在**手指按下的那一帧**（Initial pass，此时外层累计位移还是 0，
 * 一定还没做出起手判定）先认领本轮手势，外层据此放弃。按下即认领、抬手即释放，
 * 不依赖 touchSlop 的时序调参，也不影响容器自己的滚动。
 */
object DrawerGate {
    /** true = 本轮手势已被横向滚动容器认领，外层不得再拉起侧栏。 */
    @Volatile
    var claimedByInner: Boolean = false
        private set

    fun claim() {
        claimedByInner = true
    }

    fun release() {
        claimedByInner = false
    }
}

/**
 * 侧栏起手的纯判定：右移过门槛、方向明显偏水平、抽屉未开、不在全屏播放器、
 * 内层未认领、当前不在覆盖层（设置 / 详情 / 收藏 / 改密）。
 *
 * 覆盖层必须排除：设置页的滑块、收藏页的横向标签都是「按住横滑」，外层在 Initial pass
 * 旁路观察时看不到子级消费，拖滑块就会被当成右滑拉起侧栏。纯数值逻辑，JVM 单测可直接断言。
 */
object DrawerDrag {
    fun canStart(
        sumX: Float,
        absYAcc: Float,
        startSlop: Float,
        innerClaimed: Boolean,
        fullscreen: Boolean,
        overlayOpen: Boolean = false,
    ): Boolean = !innerClaimed &&
        !fullscreen &&
        // 覆盖层里全是可拖动控件，一律不认侧栏手势
        !overlayOpen &&
        sumX > startSlop &&
        // 纵向累计必须明显小于水平位移：列表纵向滑动时手指难免带点横向抖动
        sumX > absYAcc * 1.5f
}

/**
 * 加在横向滚动容器（相关推荐等）上：手指按下即认领本轮手势，抬起或手势取消时释放。
 *
 * 用 Initial pass 的 awaitFirstDown 保证「按下」最早被看到；这里只做标记，不消费事件，
 * 容器的 horizontalScroll 照常工作。
 */
fun Modifier.claimHorizontalGesture(): Modifier = pointerInput(Unit) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        DrawerGate.claim()
        try {
            // 等到全部手指抬起；手势被取消时协程会被中断，finally 仍然会释放
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                if (event.changes.none { it.pressed }) break
            }
        } finally {
            DrawerGate.release()
        }
    }
}
