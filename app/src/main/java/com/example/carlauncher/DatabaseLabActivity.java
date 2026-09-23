package com.example.carlauncher;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteConstraintException;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.util.Log;

import androidx.appcompat.app.AppCompatActivity;

import com.example.carlauncher.data.local.LabDatabaseContract;
import com.example.carlauncher.data.local.LabRecord;
import com.example.carlauncher.data.local.LabRecordDao;
import com.example.carlauncher.data.local.LabRoomDatabase;
import com.example.carlauncher.databinding.ActivityDatabaseLabBinding;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class DatabaseLabActivity
        extends AppCompatActivity {

    private static final String TAG = "DATABASE_LAB";

    private ActivityDatabaseLabBinding binding;


    private final ExecutorService databaseExecutor =
            Executors.newSingleThreadExecutor();
    private LabRoomDatabase database;
    private LabRecordDao recordDao;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = ActivityDatabaseLabBinding.inflate(
                getLayoutInflater()
        );
        setContentView(binding.getRoot());


        database = LabRoomDatabase.getInstance(this);
        recordDao = database.recordDao();
        binding.insertButton.setOnClickListener(view -> {
            String name =
                    binding.nameInput.getText().toString().trim();
            String value =
                    binding.valueInput.getText().toString().trim();
            String note =
                    binding.noteInput.getText().toString().trim();

            if (name.isEmpty() || value.isEmpty()) {
                binding.databaseStatusText.setText(
                        R.string.db_fields_required
                );
                return;
            }

            insertRecord(name, value, note);
        });

        binding.updateButton.setOnClickListener(view -> {
            String name =
                    binding.nameInput.getText().toString().trim();
            String value =
                    binding.valueInput.getText().toString().trim();
            String note =
                    binding.noteInput.getText().toString().trim();

            if (name.isEmpty() || value.isEmpty()) {
                binding.databaseStatusText.setText(
                        R.string.db_update_fields_required
                );
                return;
            }

            updateRecord(name, value, note);
        });

        binding.deleteButton.setOnClickListener(view -> {
            String name =
                    binding.nameInput.getText().toString().trim();

            if (name.isEmpty()) {
                binding.databaseStatusText.setText(
                        R.string.db_delete_name_required
                );
                return;
            }

            deleteRecord(name);
        });

        binding.queryButton.setOnClickListener(
                view -> queryAllRecords()
        );
        openDatabase();
    }

    private void openDatabase() {
        binding.databaseStatusText.setText(
                R.string.db_opening
        );

        databaseExecutor.execute(() -> {
            try {

                int version = database.getOpenHelper().getWritableDatabase().getVersion();

                Log.i(
                        TAG,
                        "Database ready, version=" + version
                );

                runOnUiThread(() -> {
                    if (binding == null || isFinishing() || isDestroyed()) {
                        return;
                    }

                    binding.databaseStatusText.setText(
                            getString(R.string.db_opened, version)
                    );
                });
            } catch (RuntimeException exception) {
                Log.e(
                        TAG,
                        "Database open failed",
                        exception
                );

                runOnUiThread(() -> {
                    if (binding == null || isFinishing() || isDestroyed()) {
                        return;
                    }
                    if (isFinishing() || isDestroyed()) {
                        return;
                    }

                    binding.databaseStatusText.setText(
                            R.string.db_open_failed
                    );
                });
            }
        });
    }

    private void insertRecord(
            String name,
            String value,
            String note
    ) {
        databaseExecutor.execute(() -> {
            try {


                LabRecord record = new LabRecord();
                record.name = name;
                record.value = value;
                record.note = note;
                record.createdAt = System.currentTimeMillis();

                long rowId = recordDao.insert(record);

                Log.i(
                        TAG,
                        "Insert success, rowId="
                                + rowId
                                + ", name="
                                + name
                );

                runOnUiThread(() -> {
                    if (binding == null || isFinishing() || isDestroyed()) {
                        return;
                    }
                    binding.databaseStatusText.setText(
                            getString(R.string.db_inserted, rowId)
                    );
                    queryAllRecords();
                });
            } catch (SQLiteConstraintException exception) {
                Log.w(
                        TAG,
                        "Insert constraint failed, name=" + name,
                        exception
                );

                runOnUiThread(() -> {
                    if (binding == null || isFinishing() || isDestroyed()) {
                        return;
                    }
                    binding.databaseStatusText.setText(
                            R.string.db_duplicate
                    );
                });
            } catch (RuntimeException exception) {
                Log.e(TAG, "Insert failed", exception);
            }
        });
    }

    private void updateRecord(
            String name,
            String value,
            String note
    ) {
        databaseExecutor.execute(() -> {
            try {


                int updatedRows = recordDao.updateByName(name, value, note);

                Log.i(
                        TAG,
                        "Update finish, name="
                                + name
                                + ", updatedRows="
                                + updatedRows
                );

                runOnUiThread(() -> {
                    if (binding == null || isFinishing() || isDestroyed()) {
                        return;
                    }
                    if (updatedRows > 0) {
                        binding.databaseStatusText.setText(
                                getString(R.string.db_updated, updatedRows)
                        );
                        queryAllRecords();
                    } else {
                        binding.databaseStatusText.setText(
                                R.string.db_update_missing
                        );
                    }
                });
            } catch (RuntimeException exception) {
                Log.e(TAG, "Update failed", exception);

                runOnUiThread(() -> {
                            if (binding == null || isFinishing() || isDestroyed()) {
                                return;
                            }
                            binding.databaseStatusText.setText(
                                    R.string.db_update_failed
                            );
                        }
                );
            }
        });
    }

    private void deleteRecord(String name) {
        databaseExecutor.execute(() -> {
            try {


                int deletedRows = recordDao.deleteByName(name);

                Log.i(
                        TAG,
                        "Delete finish, name="
                                + name
                                + ", deletedRows="
                                + deletedRows
                );

                runOnUiThread(() -> {
                    if (binding == null || isFinishing() || isDestroyed()) {
                        return;
                    }
                    if (deletedRows > 0) {
                        binding.databaseStatusText.setText(
                                getString(R.string.db_deleted, deletedRows)
                        );
                        queryAllRecords();
                    } else {
                        binding.databaseStatusText.setText(
                                R.string.db_delete_missing
                        );
                    }
                });
            } catch (RuntimeException exception) {
                Log.e(TAG, "Delete failed", exception);

                runOnUiThread(() -> {

                            if (binding == null || isFinishing() || isDestroyed()) {
                                return;
                            }
                            binding.databaseStatusText.setText(
                                    R.string.db_delete_failed
                            );
                        }
                );
            }
        });
    }

    private void queryAllRecords() {
        databaseExecutor.execute(() -> {
            try {
                List<LabRecord> records = recordDao.queryAll();
                StringBuilder result = new StringBuilder();

                for (LabRecord record : records) {
                    result.append(getString(
                            R.string.record_row,
                            record.id,
                            record.name,
                            record.value,
                            record.note
                    )).append('\n');
                }

                String displayText = records.isEmpty()
                        ? getString(R.string.no_records)
                        : result.toString();

                Log.i(TAG, "Query success, count=" + records.size());

                runOnUiThread(() -> {
                    if (binding == null || isFinishing() || isDestroyed()) {
                        return;
                    }
                    binding.databaseResultText.setText(displayText);
                });
            } catch (RuntimeException exception) {
                Log.e(TAG, "Query failed", exception);

                runOnUiThread(() -> {
                    if (binding == null || isFinishing() || isDestroyed()) {
                        return;
                    }
                    binding.databaseStatusText.setText(R.string.db_query_failed);
                });
            }
        });
    }

    @Override
    protected void onDestroy() {
        databaseExecutor.shutdown();


        binding = null;
        super.onDestroy();
    }
}