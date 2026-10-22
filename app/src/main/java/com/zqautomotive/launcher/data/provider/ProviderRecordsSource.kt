package com.zqautomotive.launcher.data.provider

/** Immutable read result; editing remains the responsibility of the existing Room page. */
data class ProviderRecord(
    val id: Long,
    val name: String,
    val value: String,
    val createdAt: Long,
    val note: String,
)

fun interface ProviderRecordsSource {
    /** A null ID reads the collection; a nonnegative ID reads at most one record. */
    suspend fun queryRecords(id: Long?): List<ProviderRecord>
}
