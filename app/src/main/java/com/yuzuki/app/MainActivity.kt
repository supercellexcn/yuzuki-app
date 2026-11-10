package com.yuzuki.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.yuzuki.app.ui.AppRoot
import com.yuzuki.app.ui.theme.YuzukiTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            YuzukiTheme {
                AppRoot()
            }
        }
    }
}
