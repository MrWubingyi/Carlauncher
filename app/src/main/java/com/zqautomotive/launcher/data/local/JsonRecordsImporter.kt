package com.zqautomotive.launcher.data.local

import com.zqautomotive.launcher.data.remote.NetworkRecord
import com.zqautomotive.launcher.data.remote.NetworkRecordsStore
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import org.json.JSONArray

/** Parses the complete snapshot before starting an atomic merge into Room. */
class JsonRecordsImporter(private val store: NetworkRecordsStore) {
    suspend fun importJson(json: String): Int {
        val array = JSONArray(json)
        val records = List(array.length()) { index ->
            val item = array.getJSONObject(index)
            NetworkRecord(
                id = item.getLong("id"),
                name = item.getString("name"),
                value = item.getString("value"),
                note = item.optString("note", ""),
                createdAt = item.getLong("createdAt"),
            )
        }
        currentCoroutineContext().ensureActive()
        store.save(records)
        return records.size
    }
}
