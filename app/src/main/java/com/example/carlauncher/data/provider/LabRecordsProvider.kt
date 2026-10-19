package com.example.carlauncher.data.provider

import android.content.ContentProvider
import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.content.UriMatcher
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.CancellationSignal
import androidx.annotation.RequiresApi
import com.example.carlauncher.data.local.LabRecordDao
import com.example.carlauncher.data.local.LabRoomDatabase

/**
 * Read-only view of the existing Room database. Queries must run off the main thread.
 *
 * Every query returns [LabRecordsContract.COLUMNS] in their fixed order, sorted by `_id ASC`.
 * Projection may be null or exactly those columns. Selection and selection arguments must be
 * null or empty; sort order may be null, empty or [LabRecordsContract.DEFAULT_SORT_ORDER].
 * The Bundle overload accepts only the equivalent SQL selection, arguments and sort keys.
 * Unsupported query options,
 * query parameters, fragments and unknown URIs fail explicitly with [IllegalArgumentException].
 * Callers own and must close the returned Cursor. Room edits remain available outside this provider.
 */
class LabRecordsProvider internal constructor(
    private val daoFactory: (Context) -> LabRecordDao,
) : ContentProvider() {
    constructor() : this({ context ->
        checkNotNull(checkNotNull(LabRoomDatabase.getInstance(context)).recordDao()) {
            "Room records DAO is unavailable"
        }
    })

    private lateinit var providerAuthority: String
    private lateinit var uriMatcher: UriMatcher
    private val recordDao: LabRecordDao by lazy {
        daoFactory(checkNotNull(context) { "Records provider is not attached" })
    }

    override fun onCreate(): Boolean {
        val providerContext = context ?: return false
        providerAuthority = LabRecordsContract.authority(providerContext)
        uriMatcher = UriMatcher(UriMatcher.NO_MATCH).apply {
            addURI(providerAuthority, LabRecordsContract.PATH_RECORDS, RECORDS)
            addURI(providerAuthority, "${LabRecordsContract.PATH_RECORDS}/#", RECORD)
        }
        return true
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor {
        val match = matchUri(uri)
        require(projection == null || projection.contentEquals(LabRecordsContract.COLUMNS)) {
            "Projection must contain exactly the records contract columns"
        }
        require(selection.isNullOrEmpty() && selectionArgs.isNullOrEmpty()) {
            "Selection is not supported; use a record URI to query by ID"
        }
        require(sortOrder.isNullOrEmpty() || sortOrder == LabRecordsContract.DEFAULT_SORT_ORDER) {
            "Only ${LabRecordsContract.DEFAULT_SORT_ORDER} sorting is supported"
        }
        return when (match) {
            RECORDS -> recordDao.queryAllCursor()
            else -> recordDao.queryByIdCursor(recordId(uri))
        }
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
        cancellationSignal: CancellationSignal?,
    ): Cursor {
        cancellationSignal?.throwIfCanceled()
        return query(uri, projection, selection, selectionArgs, sortOrder)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        queryArgs: Bundle?,
        cancellationSignal: CancellationSignal?,
    ): Cursor {
        validateQueryArgs(queryArgs)
        cancellationSignal?.throwIfCanceled()
        return query(
            uri,
            projection,
            queryArgs?.getString(ContentResolver.QUERY_ARG_SQL_SELECTION),
            queryArgs?.getStringArray(ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS),
            queryArgs?.getString(ContentResolver.QUERY_ARG_SQL_SORT_ORDER),
        )
    }

    override fun getType(uri: Uri): String =
        when (matchUri(uri)) {
            RECORDS -> LabRecordsContract.MIME_COLLECTION
            else -> LabRecordsContract.MIME_ITEM
        }

    override fun insert(uri: Uri, values: ContentValues?): Uri =
        throw UnsupportedOperationException("Records provider is read-only: insert is not supported")

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?,
    ): Int = throw UnsupportedOperationException("Records provider is read-only: update is not supported")

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int =
        throw UnsupportedOperationException("Records provider is read-only: delete is not supported")

    private fun matchUri(uri: Uri): Int {
        require(uri.scheme == ContentResolver.SCHEME_CONTENT && uri.authority == providerAuthority) {
            "Unsupported records URI: $uri"
        }
        require(uri.encodedQuery == null && uri.encodedFragment == null) {
            "Records URI query parameters and fragments are not supported: $uri"
        }
        return when (val match = uriMatcher.match(uri)) {
            RECORDS -> {
                require(uri.encodedPath == "/${LabRecordsContract.PATH_RECORDS}") {
                    "Unsupported records path: $uri"
                }
                match
            }
            RECORD -> {
                recordId(uri)
                require(uri.encodedPath == "/${LabRecordsContract.PATH_RECORDS}/${uri.lastPathSegment}") {
                    "Unsupported record path: $uri"
                }
                match
            }
            else -> throw IllegalArgumentException("Unsupported records URI: $uri")
        }
    }

    private fun recordId(uri: Uri): Long =
        requireNotNull(uri.lastPathSegment?.toLongOrNull()) { "Invalid record ID: $uri" }

    private fun validateQueryArgs(queryArgs: Bundle?) {
        queryArgs?.keySet()?.forEach { key ->
            val value = queryArgs[key]
            val supported = when (key) {
                ContentResolver.QUERY_ARG_SQL_SELECTION,
                ContentResolver.QUERY_ARG_SQL_SORT_ORDER -> value == null || value is String
                ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS ->
                    value == null || value is Array<*> && value.isArrayOf<String>()
                else -> false
            }
            require(supported) { "Unsupported records query argument or value: $key" }
        }
    }

    private companion object {
        const val RECORDS = 1
        const val RECORD = 2
    }
}
