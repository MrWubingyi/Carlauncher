package com.example.carlauncher.data.local

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.util.Log

class LabDatabaseHelper(context: Context?) :
    SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    override fun onCreate(database: SQLiteDatabase?) {
        Log.i(
            TAG,
            ("Database onCreate, version=" + DATABASE_VERSION),
        )

        database!!.execSQL(SQL_CREATE_TABLE)

        Log.i(
            TAG,
            ("Table created: " + LabDatabaseContract.LabRecordEntry.TABLE_NAME),
        )
    }

    override fun onUpgrade(
        database: SQLiteDatabase?,
        oldVersion: Int,
        newVersion: Int,
    ) {
        Log.i(
            TAG,
            ("Database onUpgrade, oldVersion=" + oldVersion + ", newVersion=" + newVersion),
        )

        if (oldVersion < 2) {
            database!!.execSQL(
                ("ALTER TABLE " +
                    LabDatabaseContract.LabRecordEntry.TABLE_NAME +
                    " ADD COLUMN " +
                    LabDatabaseContract.LabRecordEntry.COLUMN_NOTE +
                    " TEXT NOT NULL DEFAULT ''")
            )

            Log.i(TAG, "Upgrade v1 to v2: note column added")
        }
    }

    override fun onOpen(database: SQLiteDatabase?) {
        super.onOpen(database)

        Log.i(
            TAG,
            ("Database opened, version=" + database!!.getVersion()),
        )
    }

    companion object {

        private const val TAG: String = "LAB_DATABASE"

        const val DATABASE_NAME: String = "car_launcher_lab_test.db"

        // 第一阶段必须保持为 1。
        const val DATABASE_VERSION: Int = 2

        private val SQL_CREATE_TABLE: String? =
            ("CREATE TABLE " +
                LabDatabaseContract.LabRecordEntry.TABLE_NAME +
                " (" +
                LabDatabaseContract.LabRecordEntry._ID +
                " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                LabDatabaseContract.LabRecordEntry.COLUMN_NAME +
                " TEXT NOT NULL UNIQUE, " +
                LabDatabaseContract.LabRecordEntry.COLUMN_VALUE +
                " TEXT NOT NULL, " +
                LabDatabaseContract.LabRecordEntry.COLUMN_CREATED_AT +
                " INTEGER NOT NULL, " +
                LabDatabaseContract.LabRecordEntry.COLUMN_NOTE +
                " TEXT NOT NULL DEFAULT ''" +
                ")")
    }
}
