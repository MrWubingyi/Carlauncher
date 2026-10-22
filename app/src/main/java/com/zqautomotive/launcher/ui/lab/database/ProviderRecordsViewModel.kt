package com.zqautomotive.launcher.ui.lab.database

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.zqautomotive.launcher.data.provider.ProviderRecord
import com.zqautomotive.launcher.data.provider.ProviderRecordsSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ProviderRecordsUiState(
    val loading: Boolean = false,
    val message: String = "尚未查询当前 Room 记录",
    val records: List<ProviderRecord> = emptyList(),
)

/** Presents read-only Provider results independently of the page's existing Room edits. */
class ProviderRecordsViewModel(
    private val source: ProviderRecordsSource,
) : ViewModel() {
    private val mutableUiState = MutableStateFlow(ProviderRecordsUiState())
    val uiState = mutableUiState.asStateFlow()

    fun queryAll() = queryRecords(null)

    fun queryById(input: String) {
        if (mutableUiState.value.loading) return
        val id = input.trim().toLongOrNull()
        if (id == null || id < 0) {
            mutableUiState.value = ProviderRecordsUiState(message = "请输入有效的非负整数 ID")
            return
        }
        queryRecords(id)
    }

    private fun queryRecords(id: Long?) {
        if (mutableUiState.value.loading) return
        mutableUiState.value = ProviderRecordsUiState(loading = true, message = "正在查询……")
        viewModelScope.launch {
            try {
                val records = source.queryRecords(id)
                mutableUiState.value = ProviderRecordsUiState(
                    message = if (records.isEmpty()) "没有匹配的记录" else "查询成功，共 ${records.size} 条记录",
                    records = records,
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                mutableUiState.value = ProviderRecordsUiState(message = "查询失败，请重试")
            }
        }
    }

    companion object {
        fun factory(source: ProviderRecordsSource): ViewModelProvider.Factory =
            viewModelFactory {
                initializer { ProviderRecordsViewModel(source) }
            }
    }
}
