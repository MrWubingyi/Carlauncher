package com.example.carlauncher.ui.lab.database

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.carlauncher.data.remote.HttpStatusException
import com.example.carlauncher.data.remote.NetworkRecord
import com.example.carlauncher.data.remote.RecordsRepository
import com.example.carlauncher.data.remote.RecordsSaveException
import java.io.IOException
import java.io.InterruptedIOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONException

data class HttpRecordsUiState(
    val loading: Boolean = false,
    val message: String = "尚未读取网络数据",
    val records: List<NetworkRecord> = emptyList(),
)

/** Owns only this page's HTTP feature; existing database/file operations stay independent. */
class HttpRecordsViewModel(
    private val repository: RecordsRepository,
) : ViewModel() {
    private val mutableUiState = MutableStateFlow(HttpRecordsUiState())
    val uiState = mutableUiState.asStateFlow()

    private var requestJob: Job? = null
    private var requestGeneration = 0L

    // UI events and state updates run on the main thread.
    fun loadRecords(delayed: Boolean = false) {
        if (mutableUiState.value.loading) return

        val generation = ++requestGeneration
        mutableUiState.value = HttpRecordsUiState(
            loading = true,
            message = "正在读取……",
        )

        // Assign before starting, including when a repository completes immediately.
        requestJob = viewModelScope.launch(start = CoroutineStart.LAZY) {
            try {
                val records = repository.fetchRecords(delayed)
                if (generation == requestGeneration) {
                    mutableUiState.value = HttpRecordsUiState(
                        message = "读取并保存成功，共 ${records.size} 条记录",
                        records = records,
                    )
                }
            } catch (cancelled: CancellationException) {
                // Cancellation is not a network failure.
                throw cancelled
            } catch (exception: Exception) {
                if (generation == requestGeneration) {
                    val message = when (exception) {
                        is RecordsSaveException -> "网络读取成功，但保存到本地数据库失败，请重试"
                        is HttpStatusException -> "服务器返回 HTTP ${exception.statusCode}"
                        is InterruptedIOException -> "请求超时，请重试"
                        is JSONException -> "响应 JSON 格式或记录字段不正确"
                        is IOException -> "网络连接或读取失败，请检查服务和网络后重试"
                        else -> "读取失败，请重试"
                    }
                    mutableUiState.value = HttpRecordsUiState(message = message)
                }
            } finally {
                if (generation == requestGeneration) requestJob = null
            }
        }
        requestJob?.start()
    }

    fun cancel() {
        if (!mutableUiState.value.loading) return
        // Invalidate queued/late results before cancelling the transport.
        ++requestGeneration
        requestJob?.cancel()
        requestJob = null
        mutableUiState.value = HttpRecordsUiState(message = "已取消请求")
    }

    companion object {
        fun factory(repository: RecordsRepository): ViewModelProvider.Factory =
            viewModelFactory {
                initializer { HttpRecordsViewModel(repository) }
            }
    }
}
