package com.example.carlauncher.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface LabRecordDao {
    @Insert(onConflict = OnConflictStrategy.ABORT) fun insert(record: LabRecord): Long

    @Query("SELECT * FROM lab_record ORDER BY _id ASC") fun queryAll(): List<LabRecord>

    @Query(("UPDATE lab_record SET value = :value, note = :note" + " WHERE name = :name"))
    fun updateByName(name: String?, value: String?, note: String?): Int

    @Query("DELETE FROM lab_record WHERE name = :name") fun deleteByName(name: String?): Int
}
