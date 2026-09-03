package com.example.carlauncher.data.local;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

import androidx.annotation.Nullable;

public final class LabDatabaseHelper
        extends SQLiteOpenHelper {

    private static final String TAG = "LAB_DATABASE";

    public static final String DATABASE_NAME =
            "car_launcher_lab.db";

    // 第一阶段必须保持为 1。
    public static final int DATABASE_VERSION = 2;

    private static final String SQL_CREATE_TABLE =
            "CREATE TABLE "
                    + LabDatabaseContract.LabRecordEntry.TABLE_NAME
                    + " ("
                    + LabDatabaseContract.LabRecordEntry._ID
                    + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                    + LabDatabaseContract.LabRecordEntry.COLUMN_NAME
                    + " TEXT NOT NULL UNIQUE, "
                    + LabDatabaseContract.LabRecordEntry.COLUMN_VALUE
                    + " TEXT NOT NULL, "
                    + LabDatabaseContract.LabRecordEntry.COLUMN_CREATED_AT
                    + " INTEGER NOT NULL, "
                    + LabDatabaseContract.LabRecordEntry.COLUMN_NOTE
                    + " TEXT NOT NULL DEFAULT ''"
                    + ")";

    public LabDatabaseHelper(@Nullable Context context) {
        super(
                context,
                DATABASE_NAME,
                null,
                DATABASE_VERSION
        );
    }

    @Override
    public void onCreate(SQLiteDatabase database) {
        Log.i(
                TAG,
                "Database onCreate, version="
                        + DATABASE_VERSION
        );

        database.execSQL(SQL_CREATE_TABLE);

        Log.i(
                TAG,
                "Table created: "
                        + LabDatabaseContract
                        .LabRecordEntry
                        .TABLE_NAME
        );
    }

    @Override
    public void onUpgrade(
            SQLiteDatabase database,
            int oldVersion,
            int newVersion
    ) {
        Log.i(
                TAG,
                "Database onUpgrade, oldVersion="
                        + oldVersion
                        + ", newVersion="
                        + newVersion
        );

        if (oldVersion < 2) {
            database.execSQL(
                    "ALTER TABLE "
                            + LabDatabaseContract.LabRecordEntry.TABLE_NAME
                            + " ADD COLUMN "
                            + LabDatabaseContract.LabRecordEntry.COLUMN_NOTE
                            + " TEXT NOT NULL DEFAULT ''"
            );

            Log.i(TAG, "Upgrade v1 to v2: note column added");
        }
    }

    @Override
    public void onOpen(SQLiteDatabase database) {
        super.onOpen(database);

        Log.i(
                TAG,
                "Database opened, version="
                        + database.getVersion()
        );
    }
}