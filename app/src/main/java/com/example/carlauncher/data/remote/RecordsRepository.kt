package com.example.carlauncher.data.remote

/** Network data only; never inserted into the Room database by this feature. */
data class NetworkRecord(
    val id: Long,
    val name: String,
    val value: String,
    val note: String,
    val createdAt: Long,
)

fun interface RecordsRepository {
    suspend fun fetchRecords(delayed: Boolean): List<NetworkRecord>
}
