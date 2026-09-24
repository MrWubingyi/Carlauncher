package com.example.carlauncher.data.local

import android.content.Context
import android.database.Cursor
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [LabRecord::class], version = 3, exportSchema = true)
abstract class LabRoomDatabase : RoomDatabase() {

    abstract fun recordDao(): LabRecordDao?

    companion object {
        private const val DATABASE_NAME: String = "car_launcher_lab_test.db"

        @Volatile private var instance: LabRoomDatabase? = null

        @JvmField
        val MIGRATION_2_3: Migration =
            object : Migration(2, 3) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    // 保留历史自增值：
                    // 即使最高 ID 的记录已删除，也不重新使用该 ID。
                    var previousSequence: Long = 0
                    db.query(("SELECT seq FROM sqlite_sequence " + "WHERE name = 'lab_record'"))
                        .use { cursor ->
                            if (cursor.moveToFirst()) {
                                previousSequence = cursor.getLong(0)
                            }
                        }

                    // 1. 创建符合 Room v3 schema 的新表。
                    db.execSQL(
                        ("CREATE TABLE `lab_record_new` (" +
                            "`_id` INTEGER PRIMARY KEY " +
                            "AUTOINCREMENT NOT NULL, " +
                            "`name` TEXT NOT NULL, " +
                            "`value` TEXT NOT NULL, " +
                            "`created_at` INTEGER NOT NULL, " +
                            "`note` TEXT NOT NULL DEFAULT ''" +
                            ")")
                    )

                    // 2. 按列复制，保留原 ID 和所有字段。
                    db.execSQL(
                        ("INSERT INTO `lab_record_new` " +
                            "(`_id`, `name`, `value`, " +
                            "`created_at`, `note`) " +
                            "SELECT `_id`, `name`, `value`, " +
                            "`created_at`, `note` " +
                            "FROM `lab_record`")
                    )

                    // 3. 替换旧表。
                    db.execSQL("DROP TABLE `lab_record`")
                    db.execSQL(("ALTER TABLE `lab_record_new` " + "RENAME TO `lab_record`"))

                    // 4. 创建与 @Index 对应的唯一索引。
                    db.execSQL(
                        ("CREATE UNIQUE INDEX `index_lab_record_name` " +
                            "ON `lab_record` (`name`)")
                    )

                    // 5. 保留旧表的自增序号上限。
                    db.execSQL(
                        ("UPDATE sqlite_sequence " +
                            "SET seq = MAX(seq, ?) " +
                            "WHERE name = 'lab_record'"),
                        arrayOf<Any?>(previousSequence),
                    )
                    db.execSQL(
                        ("INSERT INTO sqlite_sequence(name, seq) " +
                            "SELECT 'lab_record', ? " +
                            "WHERE NOT EXISTS (" +
                            "SELECT 1 FROM sqlite_sequence " +
                            "WHERE name = 'lab_record')"),
                        arrayOf<Any?>(previousSequence),
                    )
                }
            }

        @JvmStatic
        fun getInstance(context: Context?): LabRoomDatabase? {
            if (instance == null) {
                synchronized(LabRoomDatabase::class.java) {
                    if (instance == null) {
                        instance =
                            Room.databaseBuilder<LabRoomDatabase>(
                                    context!!.getApplicationContext()!!,
                                    LabRoomDatabase::class.java,
                                    DATABASE_NAME,
                                )
                                .addMigrations(MIGRATION_2_3)
                                .build()
                    }
                }
            }
            return instance
        }
    }
}
