package com.yuzuki.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.yuzuki.app.R
import com.yuzuki.app.ui.theme.ShapeCard
import com.yuzuki.app.ui.theme.ShapePill

@Composable
fun UnreachableScreen(
    baseUrl: String,
    onRetry: () -> Unit,
    onChangeAddress: () -> Unit,
) {
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
                    text = stringResource(R.string.unreachable_title),
                    style = MaterialTheme.typography.headlineSmall,
                )
                Text(
                    text = stringResource(R.string.unreachable_desc, baseUrl),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(shape = ShapePill, onClick = onRetry) {
                        Text(stringResource(R.string.unreachable_retry))
                    }
                    OutlinedButton(shape = ShapePill, onClick = onChangeAddress) {
                        Text(stringResource(R.string.unreachable_change))
                    }
                }
            }
        }
    }
}
