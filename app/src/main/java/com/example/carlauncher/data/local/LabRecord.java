package com.example.carlauncher.data.local;


import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(
        tableName = "lab_record",
        indices = {@Index(value = {"name"}, unique = true)}
)
public class LabRecord {
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "_id")
    public long id;

    @NonNull
    public String name = "";

    @NonNull
    public String value = "";

    @ColumnInfo(name = "created_at")
    public long createdAt;

    @NonNull
    @ColumnInfo(defaultValue = "''")
    public String note = "";
}