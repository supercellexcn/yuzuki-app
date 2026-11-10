package com.yuzuki.app.ui

import android.Manifest
import android.app.Activity
import android.os.Build
import android.os.SystemClock
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.yuzuki.app.BatteryGuidance
import com.yuzuki.app.R
import com.yuzuki.app.ui.theme.ShapeCard
import com.yuzuki.app.ui.theme.ShapePill

@Composable
fun AppRoot(vm: AppViewModel = viewModel()) {
    val context = LocalContext.current

    val requestNotifications = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { /* 拒绝时播放仍继续，只是通知栏控件不显示 */ }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    var showBatteryDialog by remember { mutableStateOf(BatteryGuidance.needsGuidance(context)) }
    if (showBatteryDialog) {
        AlertDialog(
            onDismissRequest = { showBatteryDialog = false },
            title = { Text(stringResource(R.string.battery_title)) },
            text = { Text(stringResource(R.string.battery_desc)) },
            confirmButton = {
                Button(
                    shape = ShapePill,
                    onClick = {
                        BatteryGuidance.openSettings(context)
                        showBatteryDialog = false
                    },
                ) { Text(stringResource(R.string.battery_go)) }
            },
            dismissButton = {
                TextButton(onClick = { showBatteryDialog = false }) {
                    Text(stringResource(R.string.battery_later))
                }
            },
            shape = ShapeCard,
        )
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

        is AppViewModel.State.ChangePassword -> ChangePasswordScreen(
            onChange = vm::changePassword,
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

        is AppViewModel.State.Main -> RootScreen(
            onLogout = vm::logout,
            onChangeServer = vm::onChangeServer,
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
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = MaterialTheme.colorScheme.onSurface,
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
