package com.example.carlauncher.data.provider

import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.content.pm.ProviderInfo
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import com.example.carlauncher.data.local.LabRecord
import com.example.carlauncher.data.local.LabRecordDao
import com.example.carlauncher.data.local.LabRoomDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Real Android SQLite and provider dispatch, with a private in-memory database per test. */
@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 29)
class LabRecordsProviderInstrumentedTest {
    private lateinit var context: Context
    private lateinit var database: LabRoomDatabase
    private lateinit var dao: LabRecordDao
    private lateinit var resolver: ContentResolver
    private lateinit var collectionUri: Uri

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        database = Room.inMemoryDatabaseBuilder(context, LabRoomDatabase::class.java).build()
        dao = checkNotNull(database.recordDao())
        val provider = LabRecordsProvider { dao }
        provider.attachInfo(context, ProviderInfo().apply {
            authority = LabRecordsContract.authority(context)
            exported = false
            grantUriPermissions = false
        })
        // The wrapper exercises ContentResolver calls in-process. Manifest registration is
        // checked separately below; these isolated tests do not assert cross-app IPC behavior.
        resolver = ContentResolver.wrap(provider)
        collectionUri = LabRecordsContract.collectionUri(context)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun collectionQuery_returnsFixedColumnsAndEveryRoomValueInIdOrder() {
        insertRecord(27, "temperature", "21", "right", 1_700_000_027L)
        insertRecord(12, "车速", "42", "驾驶席", 1_700_000_012L)

        query(collectionUri).use { cursor ->
            assertArrayEquals(arrayOf("_id", "name", "value", "created_at", "note"), cursor.columnNames)
            assertEquals(2, cursor.count)
            assertTrue(cursor.moveToFirst())
            assertEquals(ProviderRecord(12, "车速", "42", 1_700_000_012L, "驾驶席"), cursor.readRecord())
            assertTrue(cursor.moveToNext())
            assertEquals(ProviderRecord(27, "temperature", "21", 1_700_000_027L, "right"), cursor.readRecord())
            assertFalse(cursor.moveToNext())
        }
    }

    @Test
    fun itemQuery_returnsOnlyTheRequestedRecord() {
        insertRecord(12, "first", "12", "selected", 12)
        insertRecord(27, "second", "27", "other", 27)

        query(LabRecordsContract.recordUri(context, 12)).use { cursor ->
            assertEquals(1, cursor.count)
            assertTrue(cursor.moveToFirst())
            assertEquals(ProviderRecord(12, "first", "12", 12, "selected"), cursor.readRecord())
            assertFalse(cursor.moveToNext())
        }
    }

    @Test
    fun emptyCollectionAndMissingItem_returnEmptyCursorsWithTheSameSchema() {
        query(collectionUri).use { cursor ->
            assertArrayEquals(LabRecordsContract.COLUMNS, cursor.columnNames)
            assertEquals(0, cursor.count)
            assertFalse(cursor.moveToFirst())
        }
        insertRecord(12, "present", "value", "note", 12)

        query(LabRecordsContract.recordUri(context, 999)).use { cursor ->
            assertArrayEquals(LabRecordsContract.COLUMNS, cursor.columnNames)
            assertEquals(0, cursor.count)
            assertFalse(cursor.moveToFirst())
        }
    }

    @Test
    fun mimeTypes_distinguishCollectionFromAnItemEvenWhenMissing() {
        assertEquals("vnd.android.cursor.dir/vnd.com.example.carlauncher.record", resolver.getType(collectionUri))
        assertEquals(
            "vnd.android.cursor.item/vnd.com.example.carlauncher.record",
            resolver.getType(LabRecordsContract.recordUri(context, 12)),
        )
    }

    @Test
    fun invalidUris_areRejectedByQueryAndGetType() {
        val authority = LabRecordsContract.authority(context)
        val invalidUris = listOf(
            "https://$authority/records",
            "content://wrong.authority/records",
            "content://$authority",
            "content://$authority/unknown",
            "$collectionUri/text",
            "$collectionUri/-1",
            "$collectionUri/9223372036854775808",
            "$collectionUri/12/extra",
            "$collectionUri/",
            "$collectionUri//12",
            "$collectionUri/%31%32",
            "$collectionUri?limit=1",
            "$collectionUri#record",
        )

        invalidUris.forEach { text ->
            val uri = Uri.parse(text)
            assertThrows(text, IllegalArgumentException::class.java) { query(uri).close() }
            assertThrows(text, IllegalArgumentException::class.java) { resolver.getType(uri) }
        }
    }

    @Test
    fun nonDefaultQueryArguments_areRejectedInsteadOfSilentlyIgnored() {
        assertThrows(IllegalArgumentException::class.java) {
            resolver.query(collectionUri, arrayOf("_id"), null, null, null)?.close()
        }
        assertThrows(IllegalArgumentException::class.java) {
            resolver.query(collectionUri, null, "_id = 12", null, null)?.close()
        }
        assertThrows(IllegalArgumentException::class.java) {
            resolver.query(collectionUri, null, null, arrayOf("12"), null)?.close()
        }
        assertThrows(IllegalArgumentException::class.java) {
            resolver.query(collectionUri, null, null, null, "_id DESC")?.close()
        }
        assertThrows(IllegalArgumentException::class.java) {
            resolver.query(collectionUri, null, Bundle().apply {
                putInt(ContentResolver.QUERY_ARG_LIMIT, 1)
            }, null)?.close()
        }
    }

    @Test
    fun explicitDefaultArguments_surviveContentResolverBundleAdaptation() {
        insertRecord(12, "default", "value", "note", 12)

        resolver.query(
            collectionUri,
            LabRecordsContract.COLUMNS,
            "",
            emptyArray(),
            LabRecordsContract.DEFAULT_SORT_ORDER,
        ).use { cursor ->
            assertEquals(1, checkNotNull(cursor).count)
            assertArrayEquals(LabRecordsContract.COLUMNS, cursor.columnNames)
        }
    }

    @Test
    fun providerWrites_throwAndLeaveRoomDataUnchanged() {
        insertRecord(12, "original", "42", "keep", 12)
        val values = ContentValues().apply {
            put("name", "replacement")
            put("value", "99")
        }
        val itemUri = LabRecordsContract.recordUri(context, 12)

        assertThrows(UnsupportedOperationException::class.java) { resolver.insert(collectionUri, values) }
        assertThrows(UnsupportedOperationException::class.java) { resolver.update(itemUri, values, null, null) }
        assertThrows(UnsupportedOperationException::class.java) { resolver.delete(itemUri, null, null) }

        val remaining = dao.queryAll()
        assertEquals(1, remaining.size)
        assertEquals(12L, remaining.single().id)
        assertEquals("original", remaining.single().name)
        assertEquals("42", remaining.single().value)
        assertEquals(12L, remaining.single().createdAt)
        assertEquals("keep", remaining.single().note)
    }

    @Test
    fun existingRoomEdits_areVisibleThroughTheReadOnlyProvider() {
        insertRecord(12, "editable", "before", "old", 12)
        assertEquals(1, dao.updateByName("editable", "after", "new"))

        query(LabRecordsContract.recordUri(context, 12)).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(ProviderRecord(12, "editable", "after", 12, "new"), cursor.readRecord())
        }
        assertEquals(1, dao.deleteByName("editable"))
        query(collectionUri).use { assertEquals(0, it.count) }
    }

    @Test
    fun repository_resolvesCollectionItemAndEmptyResultsThroughProviderAndRoom() = runBlocking {
        insertRecord(12, "first", "12", "selected", 12)
        insertRecord(27, "second", "27", "other", 27)
        val repository = ProviderRecordsRepository(resolver, collectionUri, Dispatchers.Unconfined)

        assertEquals(
            listOf(
                ProviderRecord(12, "first", "12", 12, "selected"),
                ProviderRecord(27, "second", "27", 27, "other"),
            ),
            repository.queryRecords(null),
        )
        assertEquals(listOf(ProviderRecord(12, "first", "12", 12, "selected")), repository.queryRecords(12))
        assertTrue(repository.queryRecords(999).isEmpty())
    }

    @Test
    fun manifest_registersPrivateProviderAndRealResolverCanReadIt() {
        val info = context.packageManager.resolveContentProvider(LabRecordsContract.authority(context), 0)
        assertNotNull(info)
        assertEquals(LabRecordsProvider::class.java.name, checkNotNull(info).name)
        assertFalse(info.exported)
        assertFalse(info.grantUriPermissions)
        assertTrue(info.enabled)

        // Read only: never clear, seed or replace the installed application's existing database.
        context.contentResolver.query(collectionUri, null, null, null, null).use { cursor ->
            assertArrayEquals(LabRecordsContract.COLUMNS, checkNotNull(cursor).columnNames)
            assertEquals(LabRecordsContract.MIME_COLLECTION, context.contentResolver.getType(collectionUri))
        }
    }

    private fun insertRecord(id: Long, name: String, value: String, note: String, createdAt: Long) {
        dao.insert(LabRecord().apply {
            this.id = id
            this.name = name
            this.value = value
            this.note = note
            this.createdAt = createdAt
        })
    }

    private fun query(uri: Uri): Cursor =
        checkNotNull(resolver.query(uri, null, null, null, null))

    private fun Cursor.readRecord(): ProviderRecord = ProviderRecord(
        id = getLong(getColumnIndexOrThrow("_id")),
        name = getString(getColumnIndexOrThrow("name")),
        value = getString(getColumnIndexOrThrow("value")),
        createdAt = getLong(getColumnIndexOrThrow("created_at")),
        note = getString(getColumnIndexOrThrow("note")),
    )
}
