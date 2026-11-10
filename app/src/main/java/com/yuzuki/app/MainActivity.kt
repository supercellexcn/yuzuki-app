package com.yuzuki.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.yuzuki.app.ui.theme.YuzukiTheme

/** S0 占位入口；S1 替换为「服务配置 → 登录 → WebView」状态机。 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            YuzukiTheme {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = "Yuzuki 初始化中…",
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                }
            }
        }
    }
}
