package com.zqautomotive.launcher.ui.lab.database

import com.zqautomotive.launcher.data.remote.NetworkRecord
import com.zqautomotive.launcher.data.remote.RecordsRepository
import java.net.SocketTimeoutException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HttpRecordsViewModelTest {
    @Test fun saveFailureIsDistinctAndAllowsRetry() = runTest(dispatcher) {
        var calls = 0
        val model = HttpRecordsViewModel(RecordsRepository {
            if (++calls == 1) throw com.zqautomotive.launcher.data.remote.RecordsSaveException(
                IllegalStateException("disk failure"),
            )
            listOf(record("retry"))
        })
        model.loadRecords()
        advanceUntilIdle()
        assertEquals("网络读取成功，但保存到本地数据库失败，请重试", model.uiState.value.message)
        assertFalse(model.uiState.value.loading)
        assertTrue(model.uiState.value.records.isEmpty())
        model.loadRecords()
        advanceUntilIdle()
        assertEquals("读取并保存成功，共 1 条记录", model.uiState.value.message)
    }

    private val dispatcher = StandardTestDispatcher()

    @Before fun setup() { Dispatchers.setMain(dispatcher) }
    @After fun cleanup() { Dispatchers.resetMain() }

    @Test fun successfulRequestPublishesRecords() = runTest(dispatcher) {
        val model = HttpRecordsViewModel(RecordsRepository { listOf(record("new")) })
        model.loadRecords()
        assertTrue(model.uiState.value.loading)
        advanceUntilIdle()
        assertFalse(model.uiState.value.loading)
        assertEquals("new", model.uiState.value.records.single().name)
    }

    @Test fun timeoutAllowsRetry() = runTest(dispatcher) {
        var calls = 0
        val model = HttpRecordsViewModel(RecordsRepository {
            if (++calls == 1) throw SocketTimeoutException()
            listOf(record("retry"))
        })
        model.loadRecords()
        advanceUntilIdle()
        assertEquals("请求超时，请重试", model.uiState.value.message)
        assertFalse(model.uiState.value.loading)
        model.loadRecords()
        advanceUntilIdle()
        assertEquals("retry", model.uiState.value.records.single().name)
    }

    @Test fun cancelSignalsRepositoryAndShowsCancelled() = runTest(dispatcher) {
        var cancelled = false
        val model = HttpRecordsViewModel(RecordsRepository {
            try {
                awaitCancellation()
            } finally {
                cancelled = true
            }
        })
        model.loadRecords()
        runCurrent()
        model.cancel()
        advanceUntilIdle()
        assertTrue(cancelled)
        assertFalse(model.uiState.value.loading)
        assertEquals("已取消请求", model.uiState.value.message)
    }

    @Test fun cancelledLateResultCannotReplaceNewResult() = runTest(dispatcher) {
        var calls = 0
        val model = HttpRecordsViewModel(RecordsRepository {
            if (++calls == 1) {
                try {
                    awaitCancellation()
                } catch (_: CancellationException) {
                    // Deliberately uncooperative dependency: stale results must still be rejected.
                    withContext(NonCancellable) { delay(100) }
                    listOf(record("old"))
                }
            } else {
                listOf(record("new"))
            }
        })
        model.loadRecords()
        runCurrent()
        model.cancel()
        model.loadRecords()
        runCurrent()
        assertEquals("new", model.uiState.value.records.single().name)
        advanceUntilIdle()
        assertEquals("new", model.uiState.value.records.single().name)
    }

    private fun record(name: String) = NetworkRecord(1, name, "42", "", 1234)
}
