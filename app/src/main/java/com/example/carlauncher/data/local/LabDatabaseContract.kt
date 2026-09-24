package com.example.carlauncher.data.local

import android.provider.BaseColumns

class LabDatabaseContract private constructor() {

    class LabRecordEntry private constructor() : BaseColumns {
        companion object {

            const val _ID = BaseColumns._ID
            const val _COUNT = BaseColumns._COUNT
            const val TABLE_NAME: String = "lab_record"
            const val COLUMN_NAME: String = "name"
            const val COLUMN_VALUE: String = "value"
            const val COLUMN_CREATED_AT: String = "created_at"
            const val COLUMN_NOTE: String = "note"
        }
    }
}
