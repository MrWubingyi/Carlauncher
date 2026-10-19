package com.example.carlauncher.data.provider

import android.content.ContentProvider
import android.content.ContentResolver
import android.content.ContentValues
import android.database.Cursor
import android.database.CursorWrapper
import android.database.MatrixCursor
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Cursor ownership and column-name mapping on Android's real cursor implementation. */
@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 29)
class ProviderRecordsRepositoryInstrumentedTest {
    private val collectionUri = Uri.parse("content://repository.test/records")

    @Test
    fun queryRecords_mapsByColumnNameAndClosesTheCursor() = runBlocking {
        // Deliberately shuffle columns: the repository must use names rather than offsets.
        val cursor = MatrixCursor(arrayOf("note", "created_at", "value", "_id", "name")).apply {
            addRow(arrayOf<Any>("驾驶席", 1_700_000_012L, "42", 12L, "车速"))
        }
        val provider = QueryCursorProvider(cursor)
        val repository = repository(provider)

        assertEquals(
            listOf(ProviderRecord(12, "车速", "42", 1_700_000_012L, "驾驶席")),
            repository.queryRecords(null),
        )
        assertEquals(collectionUri, provider.queriedUri)
        assertArrayEquals(LabRecordsContract.COLUMNS, provider.queriedProjection)
        assertNull(provider.queriedSelection)
        assertNull(provider.queriedSelectionArgs)
        assertNull(provider.queriedSortOrder)
        assertTrue(cursor.isClosed)
    }

    @Test
    fun itemQuery_appendsTheIdAndClosesAnEmptyCursor() = runBlocking {
        val cursor = MatrixCursor(LabRecordsContract.COLUMNS)
        val provider = QueryCursorProvider(cursor)

        assertTrue(repository(provider).queryRecords(12).isEmpty())
        assertEquals(Uri.parse("$collectionUri/12"), provider.queriedUri)
        assertTrue(cursor.isClosed)
    }

    @Test
    fun missingColumn_reportsFailureAndStillClosesTheCursor() {
        val cursor = MatrixCursor(arrayOf("_id", "name", "value", "created_at")).apply {
            addRow(arrayOf<Any>(12L, "name", "value", 12L))
        }
        val repository = repository(QueryCursorProvider(cursor))

        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { repository.queryRecords(null) }
        }
        assertTrue(cursor.isClosed)
    }

    @Test
    fun cursorReadFailure_propagatesAndStillClosesTheCursor() {
        val underlying = MatrixCursor(LabRecordsContract.COLUMNS).apply {
            addRow(arrayOf<Any>(12L, "name", "value", 12L, "note"))
        }
        val cursor = object : CursorWrapper(underlying) {
            override fun getString(columnIndex: Int): String {
                throw IllegalStateException("Simulated cursor read failure")
            }
        }
        val repository = repository(QueryCursorProvider(cursor))

        val error = assertThrows(IllegalStateException::class.java) {
            runBlocking { repository.queryRecords(null) }
        }
        assertEquals("Simulated cursor read failure", error.message)
        assertTrue(underlying.isClosed)
    }

    @Test
    fun nullCursor_isReportedAsQueryFailureInsteadOfAnEmptyResult() {
        val repository = repository(QueryCursorProvider(null))

        assertThrows(IOException::class.java) {
            runBlocking { repository.queryRecords(null) }
        }
    }

    @Test
    fun negativeId_isRejectedBeforeQueryingTheResolver() {
        val provider = QueryCursorProvider(null)
        val repository = repository(provider)

        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { repository.queryRecords(-1) }
        }
        assertNull(provider.queriedUri)
    }

    private fun repository(provider: ContentProvider): ProviderRecordsRepository =
        ProviderRecordsRepository(ContentResolver.wrap(provider), collectionUri, Dispatchers.Unconfined)

    private class QueryCursorProvider(private val cursor: Cursor?) : ContentProvider() {
        var queriedUri: Uri? = null
            private set
        var queriedProjection: Array<out String>? = null
            private set
        var queriedSelection: String? = null
            private set
        var queriedSelectionArgs: Array<out String>? = null
            private set
        var queriedSortOrder: String? = null
            private set

        override fun onCreate(): Boolean = true

        override fun query(
            uri: Uri,
            projection: Array<out String>?,
            selection: String?,
            selectionArgs: Array<out String>?,
            sortOrder: String?,
        ): Cursor? {
            queriedUri = uri
            queriedProjection = projection
            queriedSelection = selection
            queriedSelectionArgs = selectionArgs
            queriedSortOrder = sortOrder
            return cursor
        }

        override fun getType(uri: Uri): String? = null

        override fun insert(uri: Uri, values: ContentValues?): Uri =
            throw UnsupportedOperationException("Test provider only supplies cursors")

        override fun update(
            uri: Uri,
            values: ContentValues?,
            selection: String?,
            selectionArgs: Array<out String>?,
        ): Int = throw UnsupportedOperationException("Test provider only supplies cursors")

        override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int =
            throw UnsupportedOperationException("Test provider only supplies cursors")
    }
}
