package com.example.carlauncher

import android.content.ContentValues
import android.database.Cursor
import android.database.sqlite.SQLiteConstraintException
import android.database.sqlite.SQLiteDatabase
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import com.example.carlauncher.data.local.LabDatabaseContract
import com.example.carlauncher.data.local.LabRecord
import com.example.carlauncher.data.local.LabRecordDao
import com.example.carlauncher.data.local.LabRoomDatabase
import com.example.carlauncher.databinding.ActivityDatabaseLabBinding
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class DatabaseLabActivity : AppCompatActivity() {

    private var binding: ActivityDatabaseLabBinding? = null

    private val databaseExecutor: ExecutorService? = Executors.newSingleThreadExecutor()
    private var database: LabRoomDatabase? = null
    private var recordDao: LabRecordDao? = null

    protected override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityDatabaseLabBinding.inflate(getLayoutInflater()!!)
        setContentView(binding!!.getRoot())

        database = LabRoomDatabase.getInstance(this)
        recordDao = database!!.recordDao()
        binding!!.insertButton.setOnClickListener {
            val name = binding!!.nameInput.getText().toString().trim { it <= ' ' }
            val value = binding!!.valueInput.getText().toString().trim { it <= ' ' }
            val note = binding!!.noteInput.getText().toString().trim { it <= ' ' }

            if (name!!.isEmpty() || value!!.isEmpty()) {
                binding!!.databaseStatusText.setText(R.string.db_fields_required)
                return@setOnClickListener
            }

            insertRecord(name, value, note)
        }

        binding!!.updateButton.setOnClickListener {
            val name = binding!!.nameInput.getText().toString().trim { it <= ' ' }
            val value = binding!!.valueInput.getText().toString().trim { it <= ' ' }
            val note = binding!!.noteInput.getText().toString().trim { it <= ' ' }

            if (name!!.isEmpty() || value!!.isEmpty()) {
                binding!!.databaseStatusText.setText(R.string.db_update_fields_required)
                return@setOnClickListener
            }

            updateRecord(name, value, note)
        }

        binding!!.deleteButton.setOnClickListener {
            val name = binding!!.nameInput.getText().toString().trim { it <= ' ' }

            if (name!!.isEmpty()) {
                binding!!.databaseStatusText.setText(R.string.db_delete_name_required)
                return@setOnClickListener
            }

            deleteRecord(name)
        }

        binding!!.queryButton.setOnClickListener { queryAllRecords() }
        openDatabase()
    }

    private fun openDatabase() {
        binding!!.databaseStatusText.setText(R.string.db_opening)

        databaseExecutor!!.execute {
            try {

                val version = database!!.openHelper.writableDatabase.version

                Log.i(
                    TAG,
                    "Database ready, version=" + version,
                )

                runOnUiThread {
                    if (binding == null || isFinishing() || isDestroyed()) {
                        return@runOnUiThread
                    }

                    binding!!.databaseStatusText.setText(getString(R.string.db_opened, version))
                }
            } catch (exception: RuntimeException) {
                Log.e(
                    TAG,
                    "Database open failed",
                    exception,
                )

                runOnUiThread {
                    if (binding == null || isFinishing() || isDestroyed()) {
                        return@runOnUiThread
                    }
                    if (isFinishing() || isDestroyed()) {
                        return@runOnUiThread
                    }

                    binding!!.databaseStatusText.setText(R.string.db_open_failed)
                }
            }
        }
    }

    private fun insertRecord(
        name: String,
        value: String,
        note: String,
    ) {
        databaseExecutor!!.execute {
            try {

                val record = LabRecord()
                record.name = name
                record.value = value
                record.note = note
                record.createdAt = System.currentTimeMillis()

                val rowId = recordDao!!.insert(record)

                Log.i(
                    TAG,
                    ("Insert success, rowId=" + rowId + ", name=" + name),
                )

                runOnUiThread {
                    if (binding == null || isFinishing() || isDestroyed()) {
                        return@runOnUiThread
                    }
                    binding!!.databaseStatusText.setText(getString(R.string.db_inserted, rowId))
                    queryAllRecords()
                }
            } catch (exception: SQLiteConstraintException) {
                Log.w(
                    TAG,
                    "Insert constraint failed, name=" + name,
                    exception,
                )

                runOnUiThread {
                    if (binding == null || isFinishing() || isDestroyed()) {
                        return@runOnUiThread
                    }
                    binding!!.databaseStatusText.setText(R.string.db_duplicate)
                }
            } catch (exception: RuntimeException) {
                Log.e(TAG, "Insert failed", exception)
            }
        }
    }

    private fun updateRecord(
        name: String,
        value: String,
        note: String,
    ) {
        databaseExecutor!!.execute {
            try {

                val updatedRows = recordDao!!.updateByName(name, value, note)

                Log.i(
                    TAG,
                    ("Update finish, name=" + name + ", updatedRows=" + updatedRows),
                )

                runOnUiThread {
                    if (binding == null || isFinishing() || isDestroyed()) {
                        return@runOnUiThread
                    }
                    if (updatedRows > 0) {
                        binding!!
                            .databaseStatusText
                            .setText(getString(R.string.db_updated, updatedRows))
                        queryAllRecords()
                    } else {
                        binding!!.databaseStatusText.setText(R.string.db_update_missing)
                    }
                }
            } catch (exception: RuntimeException) {
                Log.e(TAG, "Update failed", exception)

                runOnUiThread {
                    if (binding == null || isFinishing() || isDestroyed()) {
                        return@runOnUiThread
                    }
                    binding!!.databaseStatusText.setText(R.string.db_update_failed)
                }
            }
        }
    }

    private fun deleteRecord(name: String) {
        databaseExecutor!!.execute {
            try {

                val deletedRows = recordDao!!.deleteByName(name)

                Log.i(
                    TAG,
                    ("Delete finish, name=" + name + ", deletedRows=" + deletedRows),
                )

                runOnUiThread {
                    if (binding == null || isFinishing() || isDestroyed()) {
                        return@runOnUiThread
                    }
                    if (deletedRows > 0) {
                        binding!!
                            .databaseStatusText
                            .setText(getString(R.string.db_deleted, deletedRows))
                        queryAllRecords()
                    } else {
                        binding!!.databaseStatusText.setText(R.string.db_delete_missing)
                    }
                }
            } catch (exception: RuntimeException) {
                Log.e(TAG, "Delete failed", exception)

                runOnUiThread {
                    if (binding == null || isFinishing() || isDestroyed()) {
                        return@runOnUiThread
                    }
                    binding!!.databaseStatusText.setText(R.string.db_delete_failed)
                }
            }
        }
    }

    private fun queryAllRecords() {
        databaseExecutor!!.execute {
            try {
                val records = recordDao!!.queryAll()
                val result = StringBuilder()

                for (record in records!!) {
                    result
                        .append(
                            getString(
                                R.string.record_row,
                                record.id,
                                record.name,
                                record.value,
                                record.note,
                            )
                        )!!
                        .append('\n')
                }

                val displayText =
                    if (records!!.isEmpty()) getString(R.string.no_records) else result.toString()

                Log.i(TAG, "Query success, count=" + records!!.size)

                runOnUiThread {
                    if (binding == null || isFinishing() || isDestroyed()) {
                        return@runOnUiThread
                    }
                    binding!!.databaseResultText.setText(displayText)
                }
            } catch (exception: RuntimeException) {
                Log.e(TAG, "Query failed", exception)

                runOnUiThread {
                    if (binding == null || isFinishing() || isDestroyed()) {
                        return@runOnUiThread
                    }
                    binding!!.databaseStatusText.setText(R.string.db_query_failed)
                }
            }
        }
    }

    protected override fun onDestroy() {
        databaseExecutor!!.shutdown()

        binding = null
        super.onDestroy()
    }

    companion object {

        private const val TAG: String = "DATABASE_LAB"
    }
}
