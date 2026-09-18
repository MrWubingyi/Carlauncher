package com.example.carlauncher.data.local;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteConstraintException;
import android.database.sqlite.SQLiteDatabase;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.Before;
import org.junit.After;
import org.junit.runner.RunWith;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * LabDatabaseHelper 的 Instrumentation 真实 SQLite 行为测试：
 * 建表、增删改查、UNIQUE 约束与 v1→v2 升级迁移。
 */
@RunWith(AndroidJUnit4.class)
public class LabDatabaseHelperTest {

    private static final String TABLE = LabDatabaseContract.LabRecordEntry.TABLE_NAME;

    private Context context() {
        return ApplicationProvider.getApplicationContext();
    }

    @Before
    public void deleteDatabaseBeforeTest() {
        context().deleteDatabase(LabDatabaseHelper.DATABASE_NAME);
    }

    @After
    public void deleteDatabaseAfterTest() {
        context().deleteDatabase(LabDatabaseHelper.DATABASE_NAME);
    }

    @Test
    public void getWritableDatabase_createsSchemaWithVersionTwo() {
        LabDatabaseHelper helper = new LabDatabaseHelper(context());
        SQLiteDatabase database = helper.getWritableDatabase();

        assertEquals(LabDatabaseHelper.DATABASE_VERSION, database.getVersion());

        try (Cursor cursor = database.rawQuery(
                "SELECT count(*) FROM sqlite_master WHERE type='table' AND name=?",
                new String[]{TABLE})) {
            assertTrue(cursor.moveToFirst());
            assertEquals(1, cursor.getInt(0));
        }
        assertTrue("v2 应包含 note 列", hasColumn(database, "note"));
        helper.close();
    }

    @Test
    public void insert_thenQuery_roundTripsRecord() {
        LabDatabaseHelper helper = new LabDatabaseHelper(context());
        SQLiteDatabase database = helper.getWritableDatabase();

        long rowId = insertRecord(database, "speed", "80", "lab-note");
        assertTrue(rowId > 0);

        try (Cursor cursor = database.query(
                TABLE,
                null,
                LabDatabaseContract.LabRecordEntry.COLUMN_NAME + "=?",
                new String[]{"speed"},
                null,
                null,
                null)) {
            assertTrue(cursor.moveToFirst());
            assertEquals("speed", cursor.getString(
                    cursor.getColumnIndexOrThrow(
                            LabDatabaseContract.LabRecordEntry.COLUMN_NAME)));
            assertEquals("80", cursor.getString(
                    cursor.getColumnIndexOrThrow(
                            LabDatabaseContract.LabRecordEntry.COLUMN_VALUE)));
            assertEquals("lab-note", cursor.getString(
                    cursor.getColumnIndexOrThrow(
                            LabDatabaseContract.LabRecordEntry.COLUMN_NOTE)));
            assertEquals(1, cursor.getCount());
        }
        helper.close();
    }

    @Test
    public void insert_duplicateUniqueName_throwsConstraintException() {
        LabDatabaseHelper helper = new LabDatabaseHelper(context());
        SQLiteDatabase database = helper.getWritableDatabase();

        insertRecord(database, "dup", "1", "");
        try {
            insertRecord(database, "dup", "2", "");
            fail("name 列 UNIQUE 约束应拒绝重复插入");
        } catch (SQLiteConstraintException expected) {
            // 期望的约束冲突
        }
        helper.close();
    }

    @Test
    public void updateAndDelete_affectMatchingRows() {
        LabDatabaseHelper helper = new LabDatabaseHelper(context());
        SQLiteDatabase database = helper.getWritableDatabase();

        insertRecord(database, "gear", "P", "");
        insertRecord(database, "other", "x", "");

        ContentValues update = new ContentValues();
        update.put(LabDatabaseContract.LabRecordEntry.COLUMN_VALUE, "D");
        int updated = database.update(
                TABLE,
                update,
                LabDatabaseContract.LabRecordEntry.COLUMN_NAME + "=?",
                new String[]{"gear"});
        assertEquals(1, updated);

        int deleted = database.delete(
                TABLE,
                LabDatabaseContract.LabRecordEntry.COLUMN_NAME + "=?",
                new String[]{"other"});
        assertEquals(1, deleted);

        try (Cursor cursor = database.query(
                TABLE, null, null, null, null, null, null)) {
            assertEquals(1, cursor.getCount());
        }
        helper.close();
    }

    @Test
    public void upgrade_fromVersionOne_addsNoteColumn() {
        Context context = context();
        // 先手工创建 v1 结构（无 note 列）的数据库文件
        SQLiteDatabase v1 = context.openOrCreateDatabase(
                LabDatabaseHelper.DATABASE_NAME, Context.MODE_PRIVATE, null);
        v1.execSQL("CREATE TABLE " + TABLE + " ("
                + LabDatabaseContract.LabRecordEntry._ID
                + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                + LabDatabaseContract.LabRecordEntry.COLUMN_NAME
                + " TEXT NOT NULL UNIQUE, "
                + LabDatabaseContract.LabRecordEntry.COLUMN_VALUE
                + " TEXT NOT NULL, "
                + LabDatabaseContract.LabRecordEntry.COLUMN_CREATED_AT
                + " INTEGER NOT NULL"
                + ")");
        v1.setVersion(1);
        v1.close();

        LabDatabaseHelper helper = new LabDatabaseHelper(context);
        SQLiteDatabase upgraded = helper.getWritableDatabase();

        assertEquals(LabDatabaseHelper.DATABASE_VERSION, upgraded.getVersion());
        assertTrue("v1→v2 升级应新增 note 列", hasColumn(upgraded, "note"));

        // 升级后的表应能写入 note（含 DEFAULT ''）
        long rowId = insertRecord(upgraded, "post-upgrade", "v", "");
        assertTrue(rowId > 0);
        helper.close();
    }

    private static long insertRecord(
            SQLiteDatabase database,
            String name,
            String value,
            String note
    ) {
        ContentValues values = new ContentValues();
        values.put(LabDatabaseContract.LabRecordEntry.COLUMN_NAME, name);
        values.put(LabDatabaseContract.LabRecordEntry.COLUMN_VALUE, value);
        values.put(LabDatabaseContract.LabRecordEntry.COLUMN_NOTE, note);
        values.put(LabDatabaseContract.LabRecordEntry.COLUMN_CREATED_AT,
                System.currentTimeMillis());
        return database.insertOrThrow(TABLE, null, values);
    }

    @Test
    public void upgrade_populatedVersionOnePreservesIdValueAndTimestampWithEmptyNote() {
        try (SQLiteDatabase database = SQLiteDatabase.create(null);
             LabDatabaseHelper helper = new LabDatabaseHelper(context())) {
            database.execSQL("CREATE TABLE " + TABLE + " ("
                    + "_id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL UNIQUE, "
                    + "value TEXT NOT NULL, created_at INTEGER NOT NULL)");
            database.execSQL("INSERT INTO " + TABLE
                    + " (_id,name,value,created_at) VALUES (41,'saved','80',1234)");
            helper.onUpgrade(database, 1, 2);
            try (Cursor cursor = database.rawQuery("SELECT * FROM " + TABLE, null)) {
                assertEquals(1, cursor.getCount());
                assertTrue(cursor.moveToFirst());
                assertEquals(41, cursor.getLong(cursor.getColumnIndexOrThrow("_id")));
                assertEquals("saved", cursor.getString(cursor.getColumnIndexOrThrow("name")));
                assertEquals("80", cursor.getString(cursor.getColumnIndexOrThrow("value")));
                assertEquals(1234, cursor.getLong(cursor.getColumnIndexOrThrow("created_at")));
                assertEquals("", cursor.getString(cursor.getColumnIndexOrThrow("note")));
            }
            assertTrue(insertRecord(database, "next", "81", "note") > 41);
        }
    }

    @Test
    public void schema_omittedNoteDefaultsEmptyButExplicitNullIsRejected() {
        try (SQLiteDatabase database = SQLiteDatabase.create(null);
             LabDatabaseHelper helper = new LabDatabaseHelper(context())) {
            helper.onCreate(database);
            database.execSQL("INSERT INTO " + TABLE
                    + " (name,value,created_at) VALUES ('default','v',1)");
            try (Cursor cursor = database.rawQuery("SELECT note FROM " + TABLE, null)) {
                assertTrue(cursor.moveToFirst());
                assertEquals("", cursor.getString(0));
            }
            try {
                database.execSQL("INSERT INTO " + TABLE
                        + " (name,value,created_at,note) VALUES ('null','v',2,NULL)");
                fail("Explicit NULL must not bypass the note NOT NULL contract");
            } catch (SQLiteConstraintException expected) {
                // The database, rather than UI validation, enforces the schema contract.
            }
        }
    }

    private static boolean hasColumn(SQLiteDatabase database, String column) {
        try (Cursor cursor = database.rawQuery(
                "PRAGMA table_info(" + TABLE + ")", null)) {
            int nameIndex = cursor.getColumnIndexOrThrow("name");
            while (cursor.moveToNext()) {
                if (column.equals(cursor.getString(nameIndex))) {
                    return true;
                }
            }
        }
        return false;
    }
}

