package com.yuzuki.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.yuzuki.app.data.AppContainer
import com.yuzuki.app.ui.AppRoot
import com.yuzuki.app.ui.theme.Appearance
import com.yuzuki.app.ui.theme.YuzukiTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        hideNavigationBars()
        setContent {
            val settings by AppContainer.settings.state.collectAsState()
            YuzukiTheme(appearance = Appearance.from(settings)) {
                AppRoot()
            }
        }
    }

    /** 从后台 / 弹窗 / 权限对话框回到前台时重新进入沉浸，避免金刚键又冒出来。 */
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideNavigationBars()
    }

    /**
     * 像游戏一样隐藏三大金刚键（以及手势导航条）：内容可以铺满整块屏幕，
     * 用户从底部边缘上滑时以半透明形式临时唤出，不改变布局。
     */
    private fun hideNavigationBars() {
        WindowInsetsControllerCompat(window, window.decorView).apply {
            systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.navigationBars())
        }
    }
}
