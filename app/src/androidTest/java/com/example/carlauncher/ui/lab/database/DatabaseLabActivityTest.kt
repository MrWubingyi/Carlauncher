package com.example.carlauncher.ui.lab.database

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.carlauncher.R
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DatabaseLabActivityTest {
    @get:Rule val compose = createAndroidComposeRule<DatabaseLabActivity>()

    private fun awaitDatabase() {
        compose.waitUntil(10_000) {
            compose.onAllNodes(hasTestTag("insertButton") and isEnabled())
                .fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test fun launch_opensDatabaseAndShowsStatus() {
        awaitDatabase()
        compose.onNodeWithTag("databaseStatus").assertTextEquals(
            compose.activity.getString(R.string.db_opened, 3))
    }

    @Test fun insert_withEmptyInputs_showsValidationMessage() {
        awaitDatabase()
        compose.onNodeWithTag("insertButton").performScrollTo().performClick()
        compose.onNodeWithTag("databaseStatus").assertTextEquals(
            compose.activity.getString(R.string.db_fields_required))
    }

    @Test fun update_withBlankInputs_showsValidationMessage() {
        awaitDatabase()
        compose.onNodeWithTag("nameInput").performTextInput("  ")
        compose.onNodeWithTag("updateButton").performScrollTo().performClick()
        compose.onNodeWithTag("databaseStatus").assertTextEquals(
            compose.activity.getString(R.string.db_update_fields_required))
    }

    @Test fun delete_withEmptyName_showsValidationMessage() {
        awaitDatabase()
        compose.onNodeWithTag("deleteButton").performScrollTo().performClick()
        compose.onNodeWithTag("databaseStatus").assertTextEquals(
            compose.activity.getString(R.string.db_delete_name_required))
    }

    @Test fun recreation_preservesInput() {
        awaitDatabase()
        compose.onNodeWithTag("nameInput").performTextInput("compose_record")
        compose.onNodeWithTag("valueInput").performTextInput("42")
        compose.activityRule.scenario.recreate()
        awaitDatabase()
        compose.onNodeWithTag("nameInput").assertTextContains("compose_record")
        compose.onNodeWithTag("valueInput").assertTextContains("42")
    }
}
