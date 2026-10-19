package com.example.carlauncher.data.local

import com.example.carlauncher.data.remote.NetworkRecord
import com.example.carlauncher.data.remote.NetworkRecordsStore
import com.example.carlauncher.data.remote.RecordsSaveException
import kotlinx.coroutines.runBlocking
import org.json.JSONException
import org.junit.Assert.*
import org.junit.Test

class JsonRecordsImporterTest {
    private val record = """{"id":12,"name":"speed","value":"42","createdAt":1234}"""

    @Test fun snapshotFieldsAreSavedBeforeSuccess() = runBlocking {
        var saved = emptyList<NetworkRecord>()
        val count = JsonRecordsImporter { saved = it }.importJson("[$record]")
        assertEquals(1, count)
        assertEquals(NetworkRecord(12, "speed", "42", "", 1234), saved.single())
    }

    @Test fun invalidLaterRecordPreventsAllWrites() = runBlocking {
        var writes = 0
        val importer = JsonRecordsImporter { writes++ }
        for (json in listOf("[$record,{}]", "{}", "not JSON")) {
            try {
                importer.importJson(json)
                fail("Expected invalid document")
            } catch (_: JSONException) {
                assertEquals(0, writes)
            }
        }
    }

    @Test fun persistenceFailureIsNotReportedAsSuccess() = runBlocking {
        val failure = RecordsSaveException(IllegalStateException("disk failure"))
        val importer = JsonRecordsImporter(NetworkRecordsStore { throw failure })
        try {
            importer.importJson("[$record]")
            fail("Expected save failure")
        } catch (exception: RecordsSaveException) {
            assertSame(failure, exception)
        }
    }
}
