package com.zqautomotive.launcher.data.remote

/** Remote IDs identify source records; Room assigns independent local IDs. */
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
