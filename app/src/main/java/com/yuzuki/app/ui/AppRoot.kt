package com.yuzuki.app.ui

import android.app.Activity
import android.os.SystemClock
import android.webkit.WebView
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.yuzuki.app.R
import com.yuzuki.app.ui.theme.ShapeCard
import com.yuzuki.app.ui.theme.ShapePill

@Composable
fun AppRoot(vm: AppViewModel = viewModel()) {
    val context = LocalContext.current
    val activity = context as? Activity
    val webViewRef = remember { mutableStateOf<WebView?>(null) }
    var lastBackAt by remember { mutableLongStateOf(0L) }

    // 返回键：WebView 内优先回退，最后一层双击退出（方案 §5.1）
    BackHandler(enabled = true) {
        val wv = webViewRef.value
        val now = SystemClock.elapsedRealtime()
        when {
            wv?.canGoBack() == true -> wv.goBack()
            now - lastBackAt < 2000 -> activity?.finish()
            else -> {
                lastBackAt = now
                Toast.makeText(
                    context,
                    context.getString(R.string.exit_confirm),
                    Toast.LENGTH_SHORT,
                ).show()
            }
        }
    }

    when (val s = vm.state) {
        is AppViewModel.State.Loading -> CenteredScreen { CircularProgressIndicator() }

        is AppViewModel.State.Setup -> ServerSetupScreen(
            onTest = vm::testConnection,
            onSave = vm::saveServer,
            onConfigured = vm::onServerConfigured,
        )

        is AppViewModel.State.Login -> LoginScreen(
            onLogin = vm::login,
            onSuccess = vm::onLoginSuccess,
        )

        is AppViewModel.State.Unreachable -> UnreachableScreen(
            baseUrl = s.baseUrl,
            onRetry = vm::bootstrap,
            onChangeAddress = vm::onChangeServer,
        )

        is AppViewModel.State.Fatal -> FatalScreen(
            message = s.message,
            onRetry = vm::bootstrap,
        )

        is AppViewModel.State.Web -> RootScreen(
            baseUrl = s.baseUrl,
            token = s.token,
            serverConfig = vm.serverConfig,
            webViewRef = webViewRef,
            onUnreachable = { vm.onUnreachable(s.baseUrl) },
            onBridgeUnavailable = vm::onBridgeUnavailable,
        )
    }
}

@Composable
private fun CenteredScreen(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { content() }
}

@Composable
private fun FatalScreen(message: String, onRetry: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize().systemBarsPadding().padding(20.dp),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            shape = ShapeCard,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 2.dp,
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(text = message, style = MaterialTheme.typography.bodyLarge)
                Button(shape = ShapePill, onClick = onRetry) {
                    Text(stringResource(R.string.unreachable_retry))
                }
            }
        }
    }
}
