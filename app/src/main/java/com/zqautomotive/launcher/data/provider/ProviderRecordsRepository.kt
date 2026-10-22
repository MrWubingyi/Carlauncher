package com.zqautomotive.launcher.data.provider

import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.net.Uri
import java.io.IOException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/** Queries and consumes the Cursor off the main thread, closing it on every exit path. */
class ProviderRecordsRepository internal constructor(
    private val resolver: ContentResolver,
    private val collectionUri: Uri,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ProviderRecordsSource {
    constructor(context: Context) : this(
        context.applicationContext.contentResolver,
        LabRecordsContract.collectionUri(context.applicationContext),
    )

    override suspend fun queryRecords(id: Long?): List<ProviderRecord> = withContext(ioDispatcher) {
        require(id == null || id >= 0) { "Record ID must be nonnegative" }
        val uri = if (id == null) collectionUri else ContentUris.withAppendedId(collectionUri, id)
        currentCoroutineContext().ensureActive()
        val cursor = resolver.query(uri, LabRecordsContract.COLUMNS, null, null, null)
            ?: throw IOException("Provider returned no Cursor for $uri")

        cursor.use {
            val idColumn = it.getColumnIndexOrThrow(LabRecordsContract.COLUMN_ID)
            val nameColumn = it.getColumnIndexOrThrow(LabRecordsContract.COLUMN_NAME)
            val valueColumn = it.getColumnIndexOrThrow(LabRecordsContract.COLUMN_VALUE)
            val createdAtColumn = it.getColumnIndexOrThrow(LabRecordsContract.COLUMN_CREATED_AT)
            val noteColumn = it.getColumnIndexOrThrow(LabRecordsContract.COLUMN_NOTE)
            buildList {
                while (it.moveToNext()) {
                    currentCoroutineContext().ensureActive()
                    add(
                        ProviderRecord(
                            id = it.getLong(idColumn),
                            name = it.getString(nameColumn),
                            value = it.getString(valueColumn),
                            createdAt = it.getLong(createdAtColumn),
                            note = it.getString(noteColumn),
                        )
                    )
                }
            }
        }
    }
}
