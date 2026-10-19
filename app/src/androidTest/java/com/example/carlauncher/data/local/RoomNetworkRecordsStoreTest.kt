package com.example.carlauncher.data.local

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.carlauncher.data.remote.NetworkRecord
import com.example.carlauncher.data.remote.RecordsSaveException
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RoomNetworkRecordsStoreTest {
    private lateinit var database: LabRoomDatabase
    private lateinit var store: RoomNetworkRecordsStore

    @Before fun setup() {
        database = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext, LabRoomDatabase::class.java,
        ).build()
        store = RoomNetworkRecordsStore(database)
    }

    @After fun cleanup() { database.close() }

    @Test fun documentImportPersistsAndRejectsMalformedBatch() = runBlocking {
        val importer = JsonRecordsImporter(store)
        val json = """[{"id":12,"name":"document","value":"42","note":"SAF","createdAt":1234}]"""
        assertEquals(1, importer.importJson(json))
        assertEquals(1, importer.importJson(json))
        assertEquals(0, importer.importJson("[]"))
        val dao = requireNotNull(database.recordDao())
        assertEquals("SAF", dao.queryAll().single().note)
        try {
            importer.importJson("""[{"id":12,"name":"document","value":"changed","createdAt":1},{}]""")
            fail("Expected invalid document")
        } catch (_: org.json.JSONException) {
            assertEquals("42", dao.queryAll().single().value)
        }
    }

    @Test fun mergesByNamePreservesLocalIdsAndUnrelatedRows() = runBlocking {
        val dao = requireNotNull(database.recordDao())
        val localId = dao.insert(LabRecord().apply { name = "speed"; value = "old" })
        dao.insert(LabRecord().apply { name = "local-only" })
        val records = listOf(
            NetworkRecord(900, "speed", "42", "updated", 1234),
            NetworkRecord(localId, "temperature", "21", "new", 5678),
        )
        store.save(records)
        store.save(records)
        store.save(emptyList())
        val rows = dao.queryAll()
        assertEquals(3, rows.size)
        val speed = rows.single { it.name == "speed" }
        assertEquals(localId, speed.id)
        assertEquals("42", speed.value)
        assertEquals("updated", speed.note)
        assertEquals(1234L, speed.createdAt)
        assertNotEquals(localId, rows.single { it.name == "temperature" }.id)
    }

    @Test fun failedBatchRollsBackEarlierUpdates() = runBlocking {
        val dao = requireNotNull(database.recordDao())
        dao.insert(LabRecord().apply { name = "speed"; value = "old" })
        database.openHelper.writableDatabase.execSQL(
            "CREATE TRIGGER reject_test_row BEFORE INSERT ON lab_record " +
                "WHEN NEW.name = 'reject' BEGIN SELECT RAISE(ABORT, 'test failure'); END",
        )
        try {
            store.save(listOf(
                NetworkRecord(1, "speed", "42", "", 1),
                NetworkRecord(2, "reject", "21", "", 2),
            ))
            fail("Expected save failure")
        } catch (_: RecordsSaveException) {
            assertEquals("old", dao.queryAll().single().value)
        }
    }
}
