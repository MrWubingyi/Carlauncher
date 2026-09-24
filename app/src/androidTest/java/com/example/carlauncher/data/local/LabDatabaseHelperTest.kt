package com.example.carlauncher.data.local

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteConstraintException
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** LabDatabaseHelper 的 Instrumentation 真实 SQLite 行为测试： 建表、增删改查、UNIQUE 约束与 v1→v2 升级迁移。 */
@RunWith(AndroidJUnit4::class)
open class LabDatabaseHelperTest {

    private fun context(): Context? = ApplicationProvider.getApplicationContext<Context?>()

    @Before
    open fun deleteDatabaseBeforeTest() {
        context()!!.deleteDatabase(LabDatabaseHelper.DATABASE_NAME)
    }

    @After
    open fun deleteDatabaseAfterTest() {
        context()!!.deleteDatabase(LabDatabaseHelper.DATABASE_NAME)
    }

    @Test
    open fun getWritableDatabase_createsSchemaWithVersionTwo() {
        val helper = LabDatabaseHelper(context())
        val database = helper.writableDatabase

        assertEquals(LabDatabaseHelper.DATABASE_VERSION.toLong(), database!!.getVersion().toLong())

        database!!
            .rawQuery(
                "SELECT count(*) FROM sqlite_master WHERE type='table' AND name=?",
                arrayOf<String>(TABLE),
            )!!
            .use { cursor ->
                assertTrue(cursor!!.moveToFirst())
                assertEquals(1, cursor!!.getInt(0).toLong())
            }
        assertTrue("v2 应包含 note 列", hasColumn(database, "note"))
        helper.close()
    }

    @Test
    open fun insert_thenQuery_roundTripsRecord() {
        val helper = LabDatabaseHelper(context())
        val database = helper.writableDatabase

        val rowId = insertRecord(database, "speed", "80", "lab-note")
        assertTrue(rowId > 0)

        database!!
            .query(
                TABLE,
                null,
                LabDatabaseContract.LabRecordEntry.COLUMN_NAME + "=?",
                arrayOf<String>("speed"),
                null,
                null,
                null,
            )!!
            .use { cursor ->
                assertTrue(cursor!!.moveToFirst())
                assertEquals(
                    "speed",
                    cursor!!.getString(
                        cursor!!.getColumnIndexOrThrow(
                            LabDatabaseContract.LabRecordEntry.COLUMN_NAME
                        )
                    ),
                )
                assertEquals(
                    "80",
                    cursor!!.getString(
                        cursor!!.getColumnIndexOrThrow(
                            LabDatabaseContract.LabRecordEntry.COLUMN_VALUE
                        )
                    ),
                )
                assertEquals(
                    "lab-note",
                    cursor!!.getString(
                        cursor!!.getColumnIndexOrThrow(
                            LabDatabaseContract.LabRecordEntry.COLUMN_NOTE
                        )
                    ),
                )
                assertEquals(1, cursor!!.getCount().toLong())
            }
        helper.close()
    }

    @Test
    open fun insert_duplicateUniqueName_throwsConstraintException() {
        val helper = LabDatabaseHelper(context())
        val database = helper.writableDatabase

        insertRecord(database, "dup", "1", "")
        try {
            insertRecord(database, "dup", "2", "")
            fail("name 列 UNIQUE 约束应拒绝重复插入")
        } catch (expected: SQLiteConstraintException) {
            // 期望的约束冲突
        }

        helper.close()
    }

    @Test
    open fun updateAndDelete_affectMatchingRows() {
        val helper = LabDatabaseHelper(context())
        val database = helper.writableDatabase

        insertRecord(database, "gear", "P", "")
        insertRecord(database, "other", "x", "")

        val update = ContentValues()
        update.put(LabDatabaseContract.LabRecordEntry.COLUMN_VALUE, "D")
        val updated =
            database!!.update(
                TABLE,
                update,
                LabDatabaseContract.LabRecordEntry.COLUMN_NAME + "=?",
                arrayOf<String>("gear"),
            )
        assertEquals(1, updated.toLong())

        val deleted =
            database!!.delete(
                TABLE,
                LabDatabaseContract.LabRecordEntry.COLUMN_NAME + "=?",
                arrayOf<String>("other"),
            )
        assertEquals(1, deleted.toLong())

        database!!
            .query(
                TABLE,
                null,
                null,
                null,
                null,
                null,
                null,
            )!!
            .use { cursor -> assertEquals(1, cursor!!.getCount().toLong()) }
        helper.close()
    }

    @Test
    open fun upgrade_fromVersionOne_addsNoteColumn() {
        val context = context()
        // 先手工创建 v1 结构（无 note 列）的数据库文件
        val v1 =
            context!!.openOrCreateDatabase(
                LabDatabaseHelper.DATABASE_NAME,
                Context.MODE_PRIVATE,
                null,
            )
        v1!!.execSQL(
            ("CREATE TABLE " +
                TABLE +
                " (" +
                LabDatabaseContract.LabRecordEntry._ID +
                " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                LabDatabaseContract.LabRecordEntry.COLUMN_NAME +
                " TEXT NOT NULL UNIQUE, " +
                LabDatabaseContract.LabRecordEntry.COLUMN_VALUE +
                " TEXT NOT NULL, " +
                LabDatabaseContract.LabRecordEntry.COLUMN_CREATED_AT +
                " INTEGER NOT NULL" +
                ")")
        )
        v1!!.setVersion(1)
        v1!!.close()

        val helper = LabDatabaseHelper(context)
        val upgraded = helper.writableDatabase

        assertEquals(LabDatabaseHelper.DATABASE_VERSION.toLong(), upgraded!!.getVersion().toLong())
        assertTrue("v1→v2 升级应新增 note 列", hasColumn(upgraded, "note"))

        // 升级后的表应能写入 note（含 DEFAULT ''）
        val rowId = insertRecord(upgraded, "post-upgrade", "v", "")
        assertTrue(rowId > 0)
        helper.close()
    }

    @Test
    open fun upgrade_populatedVersionOnePreservesIdValueAndTimestampWithEmptyNote() {
        SQLiteDatabase.create(null).use { database ->
            LabDatabaseHelper(context()).use { helper ->
                database!!.execSQL(
                    ("CREATE TABLE " +
                        TABLE +
                        " (" +
                        "_id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL UNIQUE, " +
                        "value TEXT NOT NULL, created_at INTEGER NOT NULL)")
                )
                database!!.execSQL(
                    ("INSERT INTO " +
                        TABLE +
                        " (_id,name,value,created_at) VALUES (41,'saved','80',1234)")
                )
                helper.onUpgrade(database, 1, 2)
                database!!.rawQuery("SELECT * FROM " + TABLE, null).use { cursor ->
                    assertEquals(1, cursor!!.getCount().toLong())
                    assertTrue(cursor!!.moveToFirst())
                    assertEquals(
                        41,
                        cursor!!.getLong(cursor!!.getColumnIndexOrThrow("_id")),
                    )
                    assertEquals(
                        "saved",
                        cursor!!.getString(cursor!!.getColumnIndexOrThrow("name")),
                    )
                    assertEquals(
                        "80",
                        cursor!!.getString(cursor!!.getColumnIndexOrThrow("value")),
                    )
                    assertEquals(
                        1234,
                        cursor!!.getLong(cursor!!.getColumnIndexOrThrow("created_at")),
                    )
                    assertEquals(
                        "",
                        cursor!!.getString(cursor!!.getColumnIndexOrThrow("note")),
                    )
                }
                assertTrue(insertRecord(database, "next", "81", "note") > 41)
            }
        }
    }

    @Test
    open fun schema_omittedNoteDefaultsEmptyButExplicitNullIsRejected() {
        SQLiteDatabase.create(null).use { database ->
            LabDatabaseHelper(context()).use { helper ->
                helper.onCreate(database)
                database!!.execSQL(
                    ("INSERT INTO " + TABLE + " (name,value,created_at) VALUES ('default','v',1)")
                )
                database!!.rawQuery("SELECT note FROM " + TABLE, null).use { cursor ->
                    assertTrue(cursor!!.moveToFirst())
                    assertEquals("", cursor!!.getString(0))
                }
                try {
                    database!!.execSQL(
                        ("INSERT INTO " +
                            TABLE +
                            " (name,value,created_at,note) VALUES ('null','v',2,NULL)")
                    )
                    fail("Explicit NULL must not bypass the note NOT NULL contract")
                } catch (expected: SQLiteConstraintException) {
                    // The database, rather than UI validation, enforces the schema contract.
                }
            }
        }
    }

    companion object {

        private val TABLE: String = LabDatabaseContract.LabRecordEntry.TABLE_NAME

        @JvmStatic
        private fun insertRecord(
            database: SQLiteDatabase?,
            name: String?,
            value: String?,
            note: String?,
        ): Long {
            val values = ContentValues()
            values.put(LabDatabaseContract.LabRecordEntry.COLUMN_NAME, name)
            values.put(LabDatabaseContract.LabRecordEntry.COLUMN_VALUE, value)
            values.put(LabDatabaseContract.LabRecordEntry.COLUMN_NOTE, note)
            values.put(
                LabDatabaseContract.LabRecordEntry.COLUMN_CREATED_AT,
                System.currentTimeMillis(),
            )
            return database!!.insertOrThrow(TABLE, null, values)
        }

        @JvmStatic
        private fun hasColumn(database: SQLiteDatabase?, column: String?): Boolean {
            database!!
                .rawQuery(
                    "PRAGMA table_info(" + TABLE + ")",
                    null,
                )!!
                .use { cursor ->
                    val nameIndex = cursor!!.getColumnIndexOrThrow("name")
                    while (cursor!!.moveToNext()) {
                        if (column == cursor!!.getString(nameIndex)) {
                            return true
                        }
                    }
                }
            return false
        }
    }
}
