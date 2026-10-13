package com.example.carlauncher.ui.lab.database

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.database.sqlite.SQLiteConstraintException
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.viewModels
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.carlauncher.data.remote.HttpRecordsRepository
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.carlauncher.R
import com.example.carlauncher.data.local.LabRecord
import com.example.carlauncher.data.local.LabRecordDao
import com.example.carlauncher.data.local.LabRoomDatabase
import com.example.carlauncher.ui.compose.designsystem.HuTheme
import java.util.concurrent.Executors
import java.io.File
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONException
import androidx.activity.result.contract.ActivityResultContracts
import android.net.Uri
import android.provider.OpenableColumns

class DatabaseLabActivity : ComponentActivity() {
    private val httpViewModel: HttpRecordsViewModel by viewModels {
        HttpRecordsViewModel.factory(HttpRecordsRepository())
    }
    private val databaseExecutor = Executors.newSingleThreadExecutor()
    private lateinit var database: LabRoomDatabase
    private lateinit var recordDao: LabRecordDao
    private var status by mutableStateOf("")
    private var resultText by mutableStateOf("")
    private var actionsEnabled by mutableStateOf(false)

    private val createSnapshotDocument =
        registerForActivityResult(
            ActivityResultContracts.CreateDocument("application/json")
        ) { uri ->
            if (uri == null) {
                pendingExportJson = null
                actionsEnabled = true
                status = "已取消导出"
            } else {
                writeSnapshotToDocument(uri)
            }
        }

    private var pendingExportJson: String? = null

    private fun exportSnapshot() {
        Log.d(TAG, "exportSnapshot called")
        if (!actionsEnabled) return
        actionsEnabled = false

        databaseExecutor.execute {
            try {
                val content = snapshotFile.readText(Charsets.UTF_8)

                updateUi {
                    pendingExportJson = content
                    createSnapshotDocument.launch("records_snapshot.json")
                }
            } catch (exception: Exception) {
                Log.e(TAG, "Prepare export failed", exception)
                updateUi {
                    actionsEnabled = true
                    status = "请先保存记录快照，再导出"
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "onCreate called")
        enableEdgeToEdge()
        database = requireNotNull(LabRoomDatabase.getInstance(this))
        recordDao = requireNotNull(database.recordDao())
        status = getString(R.string.db_opening)
        resultText = getString(R.string.no_records)
        setResult(Activity.RESULT_CANCELED)
        setContent {
            val httpState by httpViewModel.uiState.collectAsStateWithLifecycle()
            HuTheme {
                DatabaseLabScreen(
                    status, resultText, actionsEnabled,
                    onInsert = { name, value, note ->
                        Log.d(TAG, "onInsert called: name=$name, value=$value, note=$note")
                        if (name.isEmpty() || value.isEmpty()) {
                            status = getString(R.string.db_fields_required)
                        } else executeDatabaseOperation(R.string.db_query_failed) {
                            val id = recordDao.insert(LabRecord().apply {
                                this.name = name
                                this.value = value
                                this.note = note
                                createdAt = System.currentTimeMillis()
                            })
                            getString(R.string.db_inserted, id) to readRecords()
                        }
                    },
                    onUpdate = { name, value, note ->
                        Log.d(TAG, "onUpdate called: name=$name, value=$value, note=$note")
                        if (name.isEmpty() || value.isEmpty()) {
                            status = getString(R.string.db_update_fields_required)
                        } else executeDatabaseOperation(R.string.db_update_failed) {
                            val count = recordDao.updateByName(name, value, note)
                            val message = if (count > 0) getString(R.string.db_updated, count)
                            else getString(R.string.db_update_missing)
                            message to readRecords()
                        }
                    },
                    onDelete = { name ->
                        Log.d(TAG, "onDelete called: name=$name")
                        if (name.isEmpty()) status = getString(R.string.db_delete_name_required)
                        else executeDatabaseOperation(R.string.db_delete_failed) {
                            val count = recordDao.deleteByName(name)
                            val message = if (count > 0) getString(R.string.db_deleted, count)
                            else getString(R.string.db_delete_missing)
                            message to readRecords()
                        }
                    },
                    onQuery = {
                        Log.d(TAG, "onQuery called")
                        executeDatabaseOperation(R.string.db_query_failed) { null to readRecords() }
                    },
                    onFinish = ::finishWithResult,
                    onSaveSnapshot = ::saveSnapshot,
                    onReadSnapshot = ::readSnapshot,
                    onDeleteSnapshot = ::deleteSnapshot,
                    onExportSnapshot = ::exportSnapshot,
                    onReadDocument = ::selectJsonDocument,
                    networkContent = {
                        HttpRecordsSection(
                            state = httpState,
                            onLoad = httpViewModel::loadRecords,
                            onCancel = httpViewModel::cancel,
                        )
                    },
                )
            }
        }
        databaseExecutor.execute {
            try {
                val version = database.openHelper.writableDatabase.version
                Log.i(TAG, "Database ready, version=$version")
                updateUi {
                    status = getString(R.string.db_opened, version)
                    actionsEnabled = true
                }
            } catch (exception: RuntimeException) {
                Log.e(TAG, "Database open failed", exception)
                updateUi { status = getString(R.string.db_open_failed) }
            }
        }
    }

    private fun readRecords(): String {
        val records = recordDao.queryAll()
        Log.i(TAG, "Query success, count=${records.size}")
        return if (records.isEmpty()) getString(R.string.no_records)
        else records.joinToString("\n") {
            getString(R.string.record_row, it.id, it.name, it.value, it.note)
        }
    }

    /** Keep DAO work and result return in one serial queue; Compose state changes on the UI thread. */
    private fun executeDatabaseOperation(
        failureMessage: Int,
        operation: () -> Pair<String?, String>
    ) {
        if (!actionsEnabled) return
        databaseExecutor.execute {
            try {
                val (message, records) = operation()
                updateUi {
                    if (message != null) status = message
                    resultText = records
                }
            } catch (exception: SQLiteConstraintException) {
                Log.w(TAG, "Database constraint failed", exception)
                updateUi { status = getString(R.string.db_duplicate) }
            } catch (exception: RuntimeException) {
                Log.e(TAG, "Database operation failed", exception)
                updateUi { status = getString(failureMessage) }
            }
        }
    }

    private fun finishWithResult() {
        Log.d(TAG, "finishWithResult called")
        if (!actionsEnabled) return
        actionsEnabled = false
        databaseExecutor.execute {
            try {
                val count = recordDao.queryAll().size
                updateUi {
                    setResult(Activity.RESULT_OK, Intent().putExtra(EXTRA_RECORD_COUNT, count))
                    Log.i(TAG, "Return result: recordCount=$count")
                    finish()
                }
            } catch (exception: RuntimeException) {
                Log.e(TAG, "Read result failed", exception)
                updateUi {
                    actionsEnabled = true
                    status = getString(R.string.db_result_failed)
                }
            }
        }
    }

    private fun updateUi(update: () -> Unit) {
        runOnUiThread { if (!isFinishing && !isDestroyed) update() }
    }

    override fun onDestroy() {
        Log.d(TAG, "onDestroy called")
        databaseExecutor.shutdown()
        super.onDestroy()
    }

    override fun onStart() {
        super.onStart()
        Log.d(TAG, "onStart called")
    }

    override fun onResume() {
        super.onResume()
        Log.d(TAG, "onResume called")
    }

    override fun onPause() {
        super.onPause()
        Log.d(TAG, "onPause called")
    }

    override fun onStop() {
        super.onStop()
        Log.d(TAG, "onStop called")
    }

    private val snapshotFile: File
        get() = File(filesDir, "records_snapshot.json")

    private fun runSnapshotOperation(operation: () -> Pair<String, String?>) {
        if (!actionsEnabled) return

        databaseExecutor.execute {
            try {
                val (message, content) = operation()
                updateUi {
                    status = message
                    if (content != null) resultText = content
                }
            } catch (exception: Exception) {
                Log.e(TAG, "Snapshot operation failed", exception)
                updateUi {
                    status = "快照操作失败，请重试"
                }
            }
        }
    }

    private fun writeSnapshotToDocument(uri: Uri) {
        Log.d(TAG, "writeSnapshotToDocument called with uri: $uri")
        val content = pendingExportJson

        if (content == null) {
            actionsEnabled = true
            status = "导出内容已失效，请重新导出"
            return
        }

        databaseExecutor.execute {
            try {
                val stream = contentResolver.openOutputStream(uri, "wt")
                    ?: error("无法打开目标文档")

                stream.bufferedWriter(Charsets.UTF_8).use { writer ->
                    writer.write(content)
                }

                updateUi {
                    pendingExportJson = null
                    actionsEnabled = true
                    status = "导出成功"
                }
            } catch (exception: Exception) {
                Log.e(TAG, "Export failed", exception)
                updateUi {
                    pendingExportJson = null
                    actionsEnabled = true
                    status = "导出失败，请重试"
                }
            }
        }
    }

    private fun saveSnapshot() {
        Log.d(TAG, "saveSnapshot called")
        runSnapshotOperation {
            val records = recordDao.queryAll()
            val array = JSONArray()

            records.forEach { record ->
                array.put(JSONObject().apply {
                    put("id", record.id)
                    put("name", record.name)
                    put("value", record.value)
                    put("note", record.note)
                    put("createdAt", record.createdAt)
                })
            }

            snapshotFile.writeText(array.toString(2), Charsets.UTF_8)
            "已保存 ${records.size} 条记录快照" to null
        }
    }

    private fun readSnapshot() {
        Log.d(TAG, "readSnapshot called")
        runSnapshotOperation {
            if (snapshotFile.exists()) {
                "快照读取成功" to snapshotFile.readText(Charsets.UTF_8)
            } else {
                "文件不存在" to "暂无快照"
            }
        }
    }

    private fun deleteSnapshot() {
        Log.d(TAG, "deleteSnapshot called")
        runSnapshotOperation {
            when {
                !snapshotFile.exists() ->
                    "文件不存在" to "暂无快照"

                snapshotFile.delete() ->
                    "快照已删除" to "暂无快照"

                else ->
                    "删除失败，请重试" to null
            }
        }
    }

    private val openJsonDocument =
        registerForActivityResult(
            ActivityResultContracts.OpenDocument()
        ) { uri ->
            if (uri == null) {
                actionsEnabled = true
                status = "已取消选择"
                // 保留原 resultText
            } else {
                readJsonDocument(uri)
            }
        }

    private fun selectJsonDocument() {
        Log.d(TAG, "selectJsonDocument called")
        if (!actionsEnabled) return
        actionsEnabled = false

        try {
            openJsonDocument.launch(
                arrayOf("application/json", "text/plain")
            )
        } catch (exception: ActivityNotFoundException) {
            actionsEnabled = true
            status = "当前系统没有可用的文档选择器"
        }
    }

    private fun readJsonDocument(uri: Uri) {
        Log.d(TAG, "readJsonDocument called with uri: $uri")
        databaseExecutor.execute {
            try {
                var displayName = "未知文件"
                var sizeBytes: Long? = null

                // 查询元数据，不读取文件正文
                contentResolver.query(
                    uri,
                    arrayOf(
                        OpenableColumns.DISPLAY_NAME,
                        OpenableColumns.SIZE,
                    ),
                    null,
                    null,
                    null,
                )?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val nameIndex = cursor.getColumnIndex(
                            OpenableColumns.DISPLAY_NAME
                        )
                        val sizeIndex = cursor.getColumnIndex(
                            OpenableColumns.SIZE
                        )

                        if (nameIndex >= 0 && !cursor.isNull(nameIndex)) {
                            displayName = cursor.getString(nameIndex)
                        }
                        if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) {
                            sizeBytes = cursor.getLong(sizeIndex)
                        }
                    }
                }

                // 读取文件正文
                val stream = contentResolver.openInputStream(uri)
                    ?: error("无法打开文档")

                val json = stream.bufferedReader(Charsets.UTF_8).use {
                    it.readText()
                }

                // 当前快照格式为 JSON 数组
                val records = JSONArray(json)
                val sizeText = sizeBytes?.let { "$it B" } ?: "未知"

                val display = """
                文件名：$displayName
                大小：$sizeText
                记录数：${records.length()}

                ${records.toString(2)}
            """.trimIndent()

                updateUi {
                    resultText = display
                    status = "文档读取成功"
                    actionsEnabled = true
                }
            } catch (exception: JSONException) {
                Log.w(TAG, "Invalid JSON document", exception)
                updateUi {
                    status = "文件不是有效的 JSON 数组，请重新选择"
                    actionsEnabled = true
                }
            } catch (exception: Exception) {
                Log.e(TAG, "Document read failed", exception)
                updateUi {
                    status = "文档读取失败，请重新选择"
                    actionsEnabled = true
                }
            }
        }
    }

    companion object {
        const val EXTRA_RECORD_COUNT = "com.example.carlauncher.extra.RECORD_COUNT"
        private const val TAG = "DATABASE_LAB"
    }
}

/** The former ScrollView becomes a scrolling Column; input values survive activity recreation. */
@Composable
internal fun DatabaseLabScreen(
    status: String,
    resultText: String,
    actionsEnabled: Boolean,
    onInsert: (String, String, String) -> Unit,
    onUpdate: (String, String, String) -> Unit,
    onDelete: (String) -> Unit,
    onQuery: () -> Unit,
    onFinish: () -> Unit,
    onSaveSnapshot: () -> Unit,
    onReadSnapshot: () -> Unit,
    onDeleteSnapshot: () -> Unit,
    onExportSnapshot: () -> Unit,
    onReadDocument: () -> Unit,
    networkContent: @Composable () -> Unit = {},
) {
    var name by rememberSaveable { mutableStateOf("") }
    var value by rememberSaveable { mutableStateOf("") }
    var note by rememberSaveable { mutableStateOf("") }
    Surface(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 48.dp, vertical = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                stringResource(R.string.database_title),
                style = MaterialTheme.typography.headlineMedium
            )
            Text(
                status,
                Modifier.testTag("databaseStatus"),
                style = MaterialTheme.typography.bodyLarge
            )
            BoxWithConstraints {
                @Composable
                fun Fields(horizontal: Boolean) {
                    @Composable
                    fun Field(
                        text: String,
                        change: (String) -> Unit,
                        label: Int,
                        tag: String,
                        modifier: Modifier
                    ) {
                        OutlinedTextField(
                            text, change, modifier.testTag(tag),
                            label = { Text(stringResource(label)) }, singleLine = true
                        )
                    }
                    if (horizontal) Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Field(
                            name,
                            { name = it },
                            R.string.field_name,
                            "nameInput",
                            Modifier.weight(1f)
                        )
                        Field(
                            value,
                            { value = it },
                            R.string.field_value,
                            "valueInput",
                            Modifier.weight(1f)
                        )
                        Field(
                            note,
                            { note = it },
                            R.string.field_note,
                            "noteInput",
                            Modifier.weight(1f)
                        )
                    } else Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Field(
                            name,
                            { name = it },
                            R.string.field_name,
                            "nameInput",
                            Modifier.fillMaxWidth()
                        )
                        Field(
                            value,
                            { value = it },
                            R.string.field_value,
                            "valueInput",
                            Modifier.fillMaxWidth()
                        )
                        Field(
                            note,
                            { note = it },
                            R.string.field_note,
                            "noteInput",
                            Modifier.fillMaxWidth()
                        )
                    }
                }
                Fields(this.maxWidth >= 600.dp)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    { onInsert(name.trim(), value.trim(), note.trim()) },
                    Modifier
                        .weight(1f)
                        .testTag("insertButton"),
                    enabled = actionsEnabled
                ) {
                    Text(stringResource(R.string.insert_record))
                }
                OutlinedButton(
                    { onUpdate(name.trim(), value.trim(), note.trim()) },
                    Modifier
                        .weight(1f)
                        .testTag("updateButton"),
                    enabled = actionsEnabled
                ) {
                    Text(stringResource(R.string.update_record))
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    { onDelete(name.trim()) },
                    Modifier
                        .weight(1f)
                        .testTag("deleteButton"),
                    enabled = actionsEnabled
                ) {
                    Text(stringResource(R.string.delete_record))
                }
                OutlinedButton(
                    onQuery,
                    Modifier
                        .weight(1f)
                        .testTag("queryButton"),
                    enabled = actionsEnabled
                ) {
                    Text(stringResource(R.string.query_all))
                }
            }
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = onSaveSnapshot,
                    enabled = actionsEnabled,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("保存记录快照")
                }
                OutlinedButton(
                    onClick = onExportSnapshot,
                    enabled = actionsEnabled,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("导出记录")
                }
                OutlinedButton(
                    onClick = onReadSnapshot,
                    enabled = actionsEnabled,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("读取快照")
                }

                OutlinedButton(
                    onClick = onDeleteSnapshot,
                    enabled = actionsEnabled,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("删除快照")
                }
                OutlinedButton(
                    onClick = onReadDocument,
                    enabled = actionsEnabled,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("选择文档读取")
                }
            }

            networkContent()

            Text(
                stringResource(R.string.result_label),
                style = MaterialTheme.typography.titleMedium
            )
            SelectionContainer {
                Text(
                    resultText,
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 160.dp)
                        .padding(16.dp)
                        .testTag("databaseResult"),
                    fontFamily = FontFamily.Monospace
                )
            }
            Button(
                onFinish,
                Modifier
                    .fillMaxWidth()
                    .testTag("finishButton"),
                enabled = actionsEnabled
            ) {
                Text(stringResource(R.string.db_finish_and_return))
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 1000, heightDp = 700)
@Composable
private fun DatabaseLabPreview() {
    HuTheme {
        DatabaseLabScreen(
            stringResource(R.string.db_opened, 3),
            stringResource(R.string.no_records),
            true,
            { _, _, _ -> },
            { _, _, _ -> },
            {},
            {},
            {},
            onSaveSnapshot = {},
            onReadSnapshot = {},
            onDeleteSnapshot = {},
            onExportSnapshot = {},
            onReadDocument = {},
        )
    }
}
