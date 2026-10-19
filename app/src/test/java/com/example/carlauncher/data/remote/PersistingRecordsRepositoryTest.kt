package com.example.carlauncher.data.remote

import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class PersistingRecordsRepositoryTest {
    private val records = listOf(NetworkRecord(7, "speed", "42", "sample", 1234))

    @Test fun resultWaitsForPersistence() = runTest {
        val saving = CompletableDeferred<Unit>()
        val committed = CompletableDeferred<Unit>()
        val repository = PersistingRecordsRepository(RecordsRepository { records }, NetworkRecordsStore {
            assertEquals(records, it)
            saving.complete(Unit)
            committed.await()
        })
        val result = async { repository.fetchRecords(false) }
        saving.await()
        assertFalse(result.isCompleted)
        committed.complete(Unit)
        assertEquals(records, result.await())
    }

    @Test fun networkFailureDoesNotWrite() = runTest {
        var saved = false
        val repository = PersistingRecordsRepository(
            RecordsRepository { throw IOException("offline") }, NetworkRecordsStore { saved = true },
        )
        try {
            repository.fetchRecords(false)
            fail("Expected network failure")
        } catch (_: IOException) {
            assertFalse(saved)
        }
    }

    @Test fun cancelledLateResponseDoesNotWrite() = runTest {
        var saved = false
        val started = CompletableDeferred<Unit>()
        val repository = PersistingRecordsRepository(RecordsRepository {
            started.complete(Unit)
            try {
                awaitCancellation()
            } catch (_: CancellationException) {
                records
            }
        }, NetworkRecordsStore { saved = true })
        val request = launch { repository.fetchRecords(false) }
        started.await()
        request.cancelAndJoin()
        assertFalse(saved)
    }
}
