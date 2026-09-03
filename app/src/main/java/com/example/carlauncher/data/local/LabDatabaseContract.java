package com.example.carlauncher.data.local;

import android.provider.BaseColumns;

public final class LabDatabaseContract {

    private LabDatabaseContract() {
    }

    public static final class LabRecordEntry
            implements BaseColumns {

        public static final String TABLE_NAME = "lab_record";
        public static final String COLUMN_NAME = "name";
        public static final String COLUMN_VALUE = "value";
        public static final String COLUMN_CREATED_AT = "created_at";
        public static final String COLUMN_NOTE = "note";
        private LabRecordEntry() {
        }
    }
}