package com.yuzuki.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.yuzuki.app.data.AppContainer
import com.yuzuki.app.ui.AppRoot
import com.yuzuki.app.ui.theme.Appearance
import com.yuzuki.app.ui.theme.YuzukiTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val settings by AppContainer.settings.state.collectAsState()
            YuzukiTheme(appearance = Appearance.from(settings)) {
                AppRoot()
            }
        }
    }
}
