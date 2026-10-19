package com.example.carlauncher.ui.lab.database

import com.example.carlauncher.data.provider.ProviderRecord
import com.example.carlauncher.data.provider.ProviderRecordsSource
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ProviderRecordsViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before fun setup() { Dispatchers.setMain(dispatcher) }
    @After fun cleanup() { Dispatchers.resetMain() }

    @Test fun queryAllPublishesSourceRecordsAndClearsLoading() = runTest(dispatcher) {
        val requestedIds = mutableListOf<Long?>()
        val records = listOf(record(12), record(13))
        val model = ProviderRecordsViewModel(ProviderRecordsSource { id ->
            requestedIds += id
            records
        })

        model.queryAll()
        assertTrue(model.uiState.value.loading)
        advanceUntilIdle()

        assertEquals(listOf<Long?>(null), requestedIds)
        assertEquals(records, model.uiState.value.records)
        assertEquals("查询成功，共 2 条记录", model.uiState.value.message)
        assertFalse(model.uiState.value.loading)
    }

    @Test fun queryByIdPassesTrimmedIdAndInclusiveLongBounds() = runTest(dispatcher) {
        val requestedIds = mutableListOf<Long?>()
        val model = ProviderRecordsViewModel(ProviderRecordsSource { id ->
            requestedIds += id
            listOf(record(requireNotNull(id)))
        })

        listOf(" 12 " to 12L, "0" to 0L, Long.MAX_VALUE.toString() to Long.MAX_VALUE).forEach { (input, id) ->
            model.queryById(input)
            advanceUntilIdle()
            assertEquals(id, model.uiState.value.records.single().id)
            assertFalse(model.uiState.value.loading)
        }
        assertEquals(listOf(12L, 0L, Long.MAX_VALUE), requestedIds)
    }

    @Test fun emptyCollectionAndMissingIdPublishEmptyState() = runTest(dispatcher) {
        val model = ProviderRecordsViewModel(ProviderRecordsSource { emptyList() })

        model.queryAll()
        advanceUntilIdle()
        assertEquals("没有匹配的记录", model.uiState.value.message)
        assertTrue(model.uiState.value.records.isEmpty())
        assertFalse(model.uiState.value.loading)

        model.queryById("12")
        advanceUntilIdle()
        assertEquals("没有匹配的记录", model.uiState.value.message)
        assertTrue(model.uiState.value.records.isEmpty())
        assertFalse(model.uiState.value.loading)
    }

    @Test fun failedQueryClearsOldRowsAndAllowsRetry() = runTest(dispatcher) {
        var calls = 0
        val model = ProviderRecordsViewModel(ProviderRecordsSource {
            if (++calls == 2) throw IllegalStateException("Provider unavailable")
            listOf(record(calls.toLong()))
        })
        model.queryAll()
        advanceUntilIdle()
        assertEquals(1L, model.uiState.value.records.single().id)

        model.queryAll()
        assertTrue(model.uiState.value.records.isEmpty())
        advanceUntilIdle()
        assertEquals("查询失败，请重试", model.uiState.value.message)
        assertFalse(model.uiState.value.loading)
        assertTrue(model.uiState.value.records.isEmpty())

        model.queryAll()
        advanceUntilIdle()
        assertEquals(3L, model.uiState.value.records.single().id)
        assertFalse(model.uiState.value.loading)
    }

    @Test fun invalidIdsNeverCallSource() = runTest(dispatcher) {
        var calls = 0
        val model = ProviderRecordsViewModel(ProviderRecordsSource {
            calls++
            listOf(record(12))
        })

        listOf("", " ", "-1", "abc", "1.5", "9223372036854775808").forEach { input ->
            model.queryById(input)
            advanceUntilIdle()
            assertEquals("Invalid input: $input", "请输入有效的非负整数 ID", model.uiState.value.message)
            assertFalse(model.uiState.value.loading)
            assertTrue(model.uiState.value.records.isEmpty())
        }
        assertEquals(0, calls)
    }

    @Test fun repeatedClicksAndInvalidInputCannotReplacePendingQuery() = runTest(dispatcher) {
        val result = CompletableDeferred<List<ProviderRecord>>()
        val requestedIds = mutableListOf<Long?>()
        val model = ProviderRecordsViewModel(ProviderRecordsSource { id ->
            requestedIds += id
            result.await()
        })

        model.queryById("12")
        model.queryAll()
        runCurrent()
        model.queryById("13")
        model.queryById("invalid")
        runCurrent()

        assertEquals(listOf(12L), requestedIds)
        assertTrue(model.uiState.value.loading)
        assertEquals("正在查询……", model.uiState.value.message)

        result.complete(listOf(record(12)))
        advanceUntilIdle()
        assertEquals(12L, model.uiState.value.records.single().id)
        assertFalse(model.uiState.value.loading)
    }

    private fun record(id: Long) = ProviderRecord(
        id = id,
        name = "record-$id",
        value = "42",
        createdAt = 1234,
        note = "note",
    )
}
