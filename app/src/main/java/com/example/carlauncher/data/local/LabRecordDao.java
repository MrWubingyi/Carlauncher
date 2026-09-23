package com.example.carlauncher.data.local;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

@Dao
public interface LabRecordDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    long insert(LabRecord record);

    @Query("SELECT * FROM lab_record ORDER BY _id ASC")
    List<LabRecord> queryAll();

    @Query("UPDATE lab_record SET value = :value, note = :note"
            + " WHERE name = :name")
    int updateByName(String name, String value, String note);

    @Query("DELETE FROM lab_record WHERE name = :name")
    int deleteByName(String name);
}