package com.example.carlauncher.ui.lab.database

import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.carlauncher.data.local.LabRecord
import com.example.carlauncher.data.local.LabRoomDatabase
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Reads the user's existing Room data without inserting, updating, or deleting any records. */
@RunWith(AndroidJUnit4::class)
class ProviderRecordsActivityTest {
    @get:Rule val compose = createAndroidComposeRule<DatabaseLabActivity>()

    @Test fun collectionAndExistingItemMatchCurrentRoomRecords() {
        val expected = readCurrentRecords()

        compose.onNodeWithTag("providerQueryAll").performScrollTo().performClick()
        val message = if (expected.isEmpty()) "没有匹配的记录" else "查询成功，共 ${expected.size} 条记录"
        awaitMessage(message)
        if (expected.isEmpty()) {
            compose.onNodeWithTag("providerRecords").assertDoesNotExist()
        } else {
            compose.onNodeWithTag("providerRecords").assertTextEquals(expected.joinToString("\n", transform = ::display))
            val record = expected.first()
            queryById(record.id.toString())
            awaitMessage("查询成功，共 1 条记录")
            compose.onNodeWithTag("providerRecords").assertTextEquals(display(record))
        }
    }

    @Test fun absentIdShowsEmptyResultWithoutChangingDatabase() {
        val ids = readCurrentRecords().map { it.id }.toSet()
        val missingId = (0L..ids.size.toLong()).first { it !in ids }

        queryById(missingId.toString())

        awaitMessage("没有匹配的记录")
        compose.onNodeWithTag("providerRecords").assertDoesNotExist()
    }

    @Test fun malformedIdShowsValidationMessage() {
        queryById("invalid")

        awaitMessage("请输入有效的非负整数 ID")
        compose.onNodeWithTag("providerRecords").assertDoesNotExist()
    }

    private fun queryById(input: String) {
        compose.onNodeWithTag("providerId").performScrollTo().performTextReplacement(input)
        compose.onNodeWithTag("providerQueryById").performScrollTo().performClick()
    }

    private fun awaitMessage(message: String) {
        compose.waitUntil(10_000) {
            compose.onAllNodes(hasTestTag("providerStatus") and hasText(message))
                .fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("providerStatus").assertTextEquals(message)
    }

    private fun readCurrentRecords(): List<LabRecord> =
        requireNotNull(requireNotNull(LabRoomDatabase.getInstance(compose.activity)).recordDao()).queryAll()

    private fun display(record: LabRecord): String =
        "#${record.id}  ${record.name} = ${record.value}\n备注：${record.note}\n创建时间：${record.createdAt}"
}
