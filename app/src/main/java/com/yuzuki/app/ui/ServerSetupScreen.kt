package com.yuzuki.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.yuzuki.app.R
import com.yuzuki.app.data.ServerConfigException
import com.yuzuki.app.ui.theme.ShapeCard
import com.yuzuki.app.ui.theme.ShapePill
import kotlinx.coroutines.launch

@Composable
fun ServerSetupScreen(
    onTest: suspend (String) -> Result<String>,
    onSave: suspend (String) -> Result<String>,
    onConfigured: (String) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var input by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    fun describe(error: Throwable): String = when (error) {
        is ServerConfigException.NotLan -> context.getString(R.string.setup_err_public)
        is ServerConfigException.Format,
        is ServerConfigException.Empty -> context.getString(R.string.setup_err_format)
        else -> context.getString(R.string.setup_test_fail)
    }

    Box(
        modifier = Modifier.fillMaxSize().systemBarsPadding().padding(20.dp),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            shape = ShapeCard,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 2.dp,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    text = stringResource(R.string.setup_title),
                    style = MaterialTheme.typography.headlineSmall,
                )
                Text(
                    text = stringResource(R.string.setup_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it; message = null },
                    label = { Text(stringResource(R.string.setup_address_label)) },
                    placeholder = { Text(stringResource(R.string.setup_address_placeholder)) },
                    singleLine = true,
                    shape = ShapePill,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Uri,
                        imeAction = ImeAction.Done,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
                message?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(
                        shape = ShapePill,
                        enabled = !busy,
                        onClick = {
                            scope.launch {
                                busy = true
                                message = context.getString(R.string.setup_testing)
                                val result = onTest(input)
                                message = result.fold(
                                    onSuccess = { context.getString(R.string.setup_test_ok) },
                                    onFailure = { describe(it) },
                                )
                                busy = false
                            }
                        },
                    ) { Text(stringResource(R.string.setup_test)) }

                    Button(
                        shape = ShapePill,
                        enabled = !busy,
                        onClick = {
                            scope.launch {
                                busy = true
                                val result = onSave(input)
                                busy = false
                                result.fold(
                                    onSuccess = { onConfigured(it) },
                                    onFailure = { message = describe(it) },
                                )
                            }
                        },
                    ) { Text(stringResource(R.string.setup_save)) }
                }
            }
        }
    }
}
