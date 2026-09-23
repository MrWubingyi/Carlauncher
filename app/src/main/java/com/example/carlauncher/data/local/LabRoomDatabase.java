package com.example.carlauncher.data.local;

import android.content.Context;
import android.database.Cursor;

import androidx.annotation.NonNull;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

@Database(
        entities = {LabRecord.class},
        version = 3,
        exportSchema = true
)
public abstract class LabRoomDatabase extends RoomDatabase {
    private static final String DATABASE_NAME = "car_launcher_lab_test.db";

    private static volatile LabRoomDatabase instance;

    public abstract LabRecordDao recordDao();

    public static final Migration MIGRATION_2_3 =
            new Migration(2, 3) {
                @Override
                public void migrate(
                        @NonNull SupportSQLiteDatabase database
                ) {
                    // 保留历史自增值：
                    // 即使最高 ID 的记录已删除，也不重新使用该 ID。
                    long previousSequence = 0;
                    try (Cursor cursor = database.query(
                            "SELECT seq FROM sqlite_sequence "
                                    + "WHERE name = 'lab_record'"
                    )) {
                        if (cursor.moveToFirst()) {
                            previousSequence = cursor.getLong(0);
                        }
                    }

                    // 1. 创建符合 Room v3 schema 的新表。
                    database.execSQL(
                            "CREATE TABLE `lab_record_new` ("
                                    + "`_id` INTEGER PRIMARY KEY "
                                    + "AUTOINCREMENT NOT NULL, "
                                    + "`name` TEXT NOT NULL, "
                                    + "`value` TEXT NOT NULL, "
                                    + "`created_at` INTEGER NOT NULL, "
                                    + "`note` TEXT NOT NULL DEFAULT ''"
                                    + ")"
                    );

                    // 2. 按列复制，保留原 ID 和所有字段。
                    database.execSQL(
                            "INSERT INTO `lab_record_new` "
                                    + "(`_id`, `name`, `value`, "
                                    + "`created_at`, `note`) "
                                    + "SELECT `_id`, `name`, `value`, "
                                    + "`created_at`, `note` "
                                    + "FROM `lab_record`"
                    );

                    // 3. 替换旧表。
                    database.execSQL("DROP TABLE `lab_record`");
                    database.execSQL(
                            "ALTER TABLE `lab_record_new` "
                                    + "RENAME TO `lab_record`"
                    );

                    // 4. 创建与 @Index 对应的唯一索引。
                    database.execSQL(
                            "CREATE UNIQUE INDEX `index_lab_record_name` "
                                    + "ON `lab_record` (`name`)"
                    );

                    // 5. 保留旧表的自增序号上限。
                    database.execSQL(
                            "UPDATE sqlite_sequence "
                                    + "SET seq = MAX(seq, ?) "
                                    + "WHERE name = 'lab_record'",
                            new Object[]{previousSequence}
                    );
                    database.execSQL(
                            "INSERT INTO sqlite_sequence(name, seq) "
                                    + "SELECT 'lab_record', ? "
                                    + "WHERE NOT EXISTS ("
                                    + "SELECT 1 FROM sqlite_sequence "
                                    + "WHERE name = 'lab_record')",
                            new Object[]{previousSequence}
                    );
                }
            };

    public static LabRoomDatabase getInstance(Context context) {
        if (instance == null) {
            synchronized (LabRoomDatabase.class) {
                if (instance == null) {
                    instance = Room.databaseBuilder(
                                    context.getApplicationContext(),
                                    LabRoomDatabase.class,
                                    DATABASE_NAME
                            )
                            .addMigrations(MIGRATION_2_3)
                            .build();
                }
            }
        }
        return instance;
    }
}