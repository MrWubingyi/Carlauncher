package com.example.carlauncher.ui.lab.database

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

@Composable
internal fun ProviderRecordsSection(
    state: ProviderRecordsUiState,
    onQueryAll: () -> Unit,
    onQueryById: (String) -> Unit,
) {
    var recordId by rememberSaveable { mutableStateOf("") }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        HorizontalDivider()
        Text("当前 Room 记录 · 只读查询", style = MaterialTheme.typography.titleMedium)
        Text("通过 content:// 地址读取当前数据库；编辑记录后可重新查询。")
        OutlinedTextField(
            value = recordId,
            onValueChange = { recordId = it },
            label = { Text("记录 ID（非负整数）") },
            singleLine = true,
            enabled = !state.loading,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth().testTag("providerId"),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = onQueryAll,
                enabled = !state.loading,
                modifier = Modifier.testTag("providerQueryAll"),
            ) { Text("查询全部") }
            OutlinedButton(
                onClick = { onQueryById(recordId) },
                enabled = !state.loading,
                modifier = Modifier.testTag("providerQueryById"),
            ) { Text("按 ID 查询") }
        }
        Text(state.message, Modifier.testTag("providerStatus"))
        if (state.records.isNotEmpty()) {
            SelectionContainer {
                Text(
                    state.records.joinToString("\n") {
                        "#${it.id}  ${it.name} = ${it.value}\n备注：${it.note}\n创建时间：${it.createdAt}"
                    },
                    modifier = Modifier.testTag("providerRecords"),
                    fontFamily = FontFamily.Monospace,
                )
            }
        }
    }
}
