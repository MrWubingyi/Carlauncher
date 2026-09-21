package com.example.carlauncher;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteConstraintException;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.util.Log;

import androidx.appcompat.app.AppCompatActivity;

import com.example.carlauncher.data.local.LabDatabaseContract;
import com.example.carlauncher.data.local.LabDatabaseHelper;
import com.example.carlauncher.databinding.ActivityDatabaseLabBinding;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class DatabaseLabActivity
        extends AppCompatActivity {

    private static final String TAG = "DATABASE_LAB";

    private ActivityDatabaseLabBinding binding;
    private LabDatabaseHelper databaseHelper;

    private final ExecutorService databaseExecutor =
            Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = ActivityDatabaseLabBinding.inflate(
                getLayoutInflater()
        );
        setContentView(binding.getRoot());

        databaseHelper = new LabDatabaseHelper(
                getApplicationContext()
        );
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
                SQLiteDatabase database =
                        databaseHelper.getWritableDatabase();

                int version = database.getVersion();

                Log.i(
                        TAG,
                        "Database ready, version=" + version
                );

                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) {
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
                SQLiteDatabase database =
                        databaseHelper.getWritableDatabase();

                ContentValues values = new ContentValues();
                values.put(
                        LabDatabaseContract
                                .LabRecordEntry
                                .COLUMN_NAME,
                        name
                );
                values.put(
                        LabDatabaseContract
                                .LabRecordEntry
                                .COLUMN_VALUE,
                        value
                );
                values.put(
                        LabDatabaseContract
                                .LabRecordEntry
                                .COLUMN_NOTE,
                        note
                );
                values.put(
                        LabDatabaseContract
                                .LabRecordEntry
                                .COLUMN_CREATED_AT,
                        System.currentTimeMillis()
                );

                long rowId = database.insertOrThrow(
                        LabDatabaseContract
                                .LabRecordEntry
                                .TABLE_NAME,
                        null,
                        values
                );

                Log.i(
                        TAG,
                        "Insert success, rowId="
                                + rowId
                                + ", name="
                                + name
                );

                runOnUiThread(() -> {
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

                runOnUiThread(() ->
                        binding.databaseStatusText.setText(
                                R.string.db_duplicate
                        )
                );
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
                SQLiteDatabase database =
                        databaseHelper.getWritableDatabase();

                ContentValues values = new ContentValues();
                values.put(
                        LabDatabaseContract
                                .LabRecordEntry
                                .COLUMN_VALUE,
                        value
                );
                values.put(
                        LabDatabaseContract
                                .LabRecordEntry
                                .COLUMN_NOTE,
                        note
                );

                String selection =
                        LabDatabaseContract
                                .LabRecordEntry
                                .COLUMN_NAME
                                + " = ?";
                String[] selectionArgs = { name };

                int updatedRows = database.update(
                        LabDatabaseContract
                                .LabRecordEntry
                                .TABLE_NAME,
                        values,
                        selection,
                        selectionArgs
                );

                Log.i(
                        TAG,
                        "Update finish, name="
                                + name
                                + ", updatedRows="
                                + updatedRows
                );

                runOnUiThread(() -> {
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

                runOnUiThread(() ->
                        binding.databaseStatusText.setText(
                                R.string.db_update_failed
                        )
                );
            }
        });
    }
    private void deleteRecord(String name) {
        databaseExecutor.execute(() -> {
            try {
                SQLiteDatabase database =
                        databaseHelper.getWritableDatabase();

                String selection =
                        LabDatabaseContract
                                .LabRecordEntry
                                .COLUMN_NAME
                                + " = ?";
                String[] selectionArgs = { name };

                int deletedRows = database.delete(
                        LabDatabaseContract
                                .LabRecordEntry
                                .TABLE_NAME,
                        selection,
                        selectionArgs
                );

                Log.i(
                        TAG,
                        "Delete finish, name="
                                + name
                                + ", deletedRows="
                                + deletedRows
                );

                runOnUiThread(() -> {
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

                runOnUiThread(() ->
                        binding.databaseStatusText.setText(
                                R.string.db_delete_failed
                        )
                );
            }
        });
    }
    private void queryAllRecords() {
        databaseExecutor.execute(() -> {
            SQLiteDatabase database =
                    databaseHelper.getReadableDatabase();

            String[] columns = {
                    LabDatabaseContract.LabRecordEntry._ID,
                    LabDatabaseContract
                            .LabRecordEntry
                            .COLUMN_NAME,
                    LabDatabaseContract
                            .LabRecordEntry
                            .COLUMN_VALUE,
                    LabDatabaseContract
                            .LabRecordEntry
                            .COLUMN_NOTE,
                    LabDatabaseContract
                            .LabRecordEntry
                            .COLUMN_CREATED_AT
            };

            StringBuilder result = new StringBuilder();

            try (Cursor cursor = database.query(
                    LabDatabaseContract
                            .LabRecordEntry
                            .TABLE_NAME,
                    columns,
                    null,
                    null,
                    null,
                    null,
                    LabDatabaseContract
                            .LabRecordEntry
                            ._ID
                            + " ASC"
            )) {
                int idIndex = cursor.getColumnIndexOrThrow(
                        LabDatabaseContract.LabRecordEntry._ID
                );
                int nameIndex = cursor.getColumnIndexOrThrow(
                        LabDatabaseContract
                                .LabRecordEntry
                                .COLUMN_NAME
                );
                int valueIndex = cursor.getColumnIndexOrThrow(
                        LabDatabaseContract
                                .LabRecordEntry
                                .COLUMN_VALUE
                );
                int noteIndex = cursor.getColumnIndexOrThrow(
                        LabDatabaseContract
                                .LabRecordEntry
                                .COLUMN_NOTE
                );

                while (cursor.moveToNext()) {
                    result.append(getString(R.string.record_row,
                            cursor.getLong(idIndex), cursor.getString(nameIndex),
                            cursor.getString(valueIndex), cursor.getString(noteIndex)))
                            .append('\n');
                }

                Log.i(
                        TAG,
                        "Query success, count=" + cursor.getCount()
                );
            }

            String displayText =
                    result.length() == 0
                            ? getString(R.string.no_records)
                            : result.toString();

            runOnUiThread(() ->
                    binding.databaseResultText.setText(displayText)
            );
        });
    }
    @Override
    protected void onDestroy() {
        databaseExecutor.shutdown();

        if (databaseHelper != null) {
            databaseHelper.close();
        }

        binding = null;
        super.onDestroy();
    }
}