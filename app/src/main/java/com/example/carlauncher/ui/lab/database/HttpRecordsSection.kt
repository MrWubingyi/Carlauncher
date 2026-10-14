package com.example.carlauncher.ui.lab.database

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp

/** Stateless request UI: receives state and events, never accesses networking or Room. */
@Composable
internal fun HttpRecordsSection(
    state: HttpRecordsUiState,
    onLoad: (Boolean) -> Unit,
    onCancel: () -> Unit,
) {
    var delayed by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        HorizontalDivider()
        Text("网络 JSON", style = MaterialTheme.typography.titleMedium)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Switch(
                checked = delayed,
                onCheckedChange = { delayed = it },
                enabled = !state.loading,
                modifier = Modifier.testTag("httpDelayed"),
            )
            Text("延迟响应（用于超时／取消练习）")
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = { onLoad(delayed) },
                enabled = !state.loading,
                modifier = Modifier.testTag("httpLoad"),
            ) { Text("读取网络数据") }
            OutlinedButton(
                onClick = onCancel,
                enabled = state.loading,
                modifier = Modifier.testTag("httpCancel"),
            ) { Text("取消请求") }
        }
        Text(state.message, Modifier.testTag("httpStatus"))
        if (state.records.isNotEmpty()) {
            SelectionContainer {
                Text(
                    state.records.joinToString("\n") {
                        "#${it.id}  ${it.name} = ${it.value}\n备注：${it.note}\n创建时间：${it.createdAt}"
                    },
                    modifier = Modifier.testTag("httpRecords"),
                    fontFamily = FontFamily.Monospace,
                )
            }
        }
    }
}
