package com.yuzuki.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.yuzuki.app.data.AppContainer
import com.yuzuki.app.playback.PlaybackController
import com.yuzuki.app.ui.AppRoot
import com.yuzuki.app.ui.theme.Appearance
import com.yuzuki.app.ui.theme.YuzukiTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 连上后台播放服务：Activity 重建后状态仍在，播放不会被页面销毁带走
        PlaybackController.warmUp()
        handleIntent(intent)
        setContent {
            // 外观只吃本机 DataStore：网页端 /api/settings 的主题色不再影响 App
            val prefs by AppContainer.appearance.state.collectAsState()
            YuzukiTheme(appearance = Appearance.from(prefs)) {
                AppRoot()
            }
        }
        // 内容视图就绪后再进入沉浸，避免被 setContentView 重置
        hideNavigationBars()
    }

    /** 点通知 / 锁屏卡片进来时（含 App 已在后台的情况）直达全屏播放器。 */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.getBooleanExtra(EXTRA_OPEN_PLAYER, false) == true) {
            PlaybackController.setFullscreen(true)
            intent.removeExtra(EXTRA_OPEN_PLAYER)
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

    companion object {
        const val EXTRA_OPEN_PLAYER = "com.yuzuki.app.extra.OPEN_PLAYER"
    }
}
