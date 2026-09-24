package com.example.carlauncher.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "lab_record", indices = [Index(value = ["name"], unique = true)])
open class LabRecord {
    @field:PrimaryKey(autoGenerate = true)
    @field:ColumnInfo(name = "_id")
    @JvmField
    var id: Long = 0

    @JvmField var name: String = ""

    @JvmField var value: String = ""

    @field:ColumnInfo(name = "created_at") @JvmField var createdAt: Long = 0

    @field:ColumnInfo(defaultValue = "''") @JvmField var note: String = ""
}
