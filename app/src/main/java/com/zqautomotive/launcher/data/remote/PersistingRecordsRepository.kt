package com.zqautomotive.launcher.data.remote

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

fun interface NetworkRecordsStore {
    suspend fun save(records: List<NetworkRecord>)
}

class RecordsSaveException(cause: Exception) : Exception("Cannot save network records", cause)

/** Returns only after the local transaction completes. */
class PersistingRecordsRepository(
    private val remote: RecordsRepository,
    private val store: NetworkRecordsStore,
) : RecordsRepository {
    override suspend fun fetchRecords(delayed: Boolean): List<NetworkRecord> {
        val records = remote.fetchRecords(delayed)
        currentCoroutineContext().ensureActive()
        store.save(records)
        return records
    }
}
