package com.example.carlauncher.data.local

import com.example.carlauncher.data.remote.NetworkRecord
import com.example.carlauncher.data.remote.NetworkRecordsStore
import com.example.carlauncher.data.remote.RecordsSaveException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

class RoomNetworkRecordsStore(private val database: LabRoomDatabase) : NetworkRecordsStore {
    override suspend fun save(records: List<NetworkRecord>) = withContext(Dispatchers.IO) {
        val context = coroutineContext
        try {
            database.runInTransaction {
                val dao = requireNotNull(database.recordDao())
                records.forEach { record ->
                    context.ensureActive()
                    val updated = dao.updateFromNetwork(
                        record.name, record.value, record.note, record.createdAt,
                    )
                    if (updated == 0) {
                        dao.insert(LabRecord().apply {
                            name = record.name
                            value = record.value
                            note = record.note
                            createdAt = record.createdAt
                        })
                    }
                }
                context.ensureActive()
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (exception: Exception) {
            throw RecordsSaveException(exception)
        }
    }
}
