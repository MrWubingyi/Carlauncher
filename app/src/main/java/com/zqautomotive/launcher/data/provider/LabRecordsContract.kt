package com.zqautomotive.launcher.data.provider

import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.BaseColumns

/** Stable URI, column and MIME contract for the app-private, read-only Room records provider. */
object LabRecordsContract {
    const val PATH_RECORDS = "records"
    const val COLUMN_ID = BaseColumns._ID
    const val COLUMN_NAME = "name"
    const val COLUMN_VALUE = "value"
    const val COLUMN_CREATED_AT = "created_at"
    const val COLUMN_NOTE = "note"
    const val DEFAULT_SORT_ORDER = "$COLUMN_ID ASC"
    const val MIME_COLLECTION = "vnd.android.cursor.dir/vnd.com.zqautomotive.launcher.record"
    const val MIME_ITEM = "vnd.android.cursor.item/vnd.com.zqautomotive.launcher.record"

    /** A new array prevents callers from changing the fixed provider schema. */
    val COLUMNS: Array<String>
        get() = arrayOf(COLUMN_ID, COLUMN_NAME, COLUMN_VALUE, COLUMN_CREATED_AT, COLUMN_NOTE)

    fun authority(context: Context): String = "${context.packageName}.records"

    fun collectionUri(context: Context): Uri =
        Uri.Builder()
            .scheme(ContentResolver.SCHEME_CONTENT)
            .authority(authority(context))
            .appendPath(PATH_RECORDS)
            .build()

    fun recordUri(context: Context, id: Long): Uri {
        require(id >= 0) { "Record ID must be non-negative: $id" }
        return ContentUris.withAppendedId(collectionUri(context), id)
    }
}
