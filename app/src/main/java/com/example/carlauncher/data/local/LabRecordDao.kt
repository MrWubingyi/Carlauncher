package com.example.carlauncher.data.local

import android.database.Cursor
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface LabRecordDao {
    @Insert(onConflict = OnConflictStrategy.ABORT) fun insert(record: LabRecord): Long

    @Query("SELECT * FROM lab_record ORDER BY _id ASC") fun queryAll(): List<LabRecord>

    @Query("SELECT _id, name, value, created_at, note FROM lab_record ORDER BY _id ASC")
    fun queryAllCursor(): Cursor

    @Query("SELECT _id, name, value, created_at, note FROM lab_record WHERE _id = :id ORDER BY _id ASC")
    fun queryByIdCursor(id: Long): Cursor

    @Query(("UPDATE lab_record SET value = :value, note = :note" + " WHERE name = :name"))
    fun updateByName(name: String?, value: String?, note: String?): Int

    @Query("UPDATE lab_record SET value = :value, note = :note, created_at = :createdAt WHERE name = :name")
    fun updateFromNetwork(name: String, value: String, note: String, createdAt: Long): Int

    @Query("DELETE FROM lab_record WHERE name = :name") fun deleteByName(name: String?): Int
}
