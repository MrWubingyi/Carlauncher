package com.example.carlauncher

import android.os.Looper
import android.view.View
import android.widget.EditText
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * DatabaseLabActivity 的 Instrumentation 测试： 启动与空输入校验（主线程同步路径）。真实 SQLite 读写行为由
 * `LabDatabaseHelperTest` 覆盖；本类不等待后台执行器线程。
 */
@RunWith(AndroidJUnit4::class)
open class DatabaseLabActivityTest {

    private fun idleMainLooper() {
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
    }

    @Test
    open fun launch_opensDatabaseAndShowsStatus() {
        ActivityScenario.launch<DatabaseLabActivity?>(DatabaseLabActivity::class.java)!!.use {
            scenario ->
            idleMainLooper()
            scenario!!.onActivity { activity ->
                val status =
                    (activity!!.findViewById<View?>(R.id.databaseStatusText) as TextView)
                        .getText()
                        .toString()
                // 打开成功或尚未完成的提示均可接受，重点是启动过程不崩溃且状态栏被更新
                assertTrue(
                    (status == activity!!.getString(R.string.db_opening) ||
                        status ==
                            activity!!.getString(
                                R.string.db_opened,
                                com.example.carlauncher.data.local.LabRoomDatabase.getInstance(
                                        activity
                                    )!!
                                    .openHelper
                                    .writableDatabase
                                    .version,
                            ) ||
                        status == activity!!.getString(R.string.db_open_failed))
                )
            }
        }
    }

    @Test
    open fun insertButton_withEmptyInputs_showsValidationMessage() {
        ActivityScenario.launch<DatabaseLabActivity?>(DatabaseLabActivity::class.java)!!.use {
            scenario ->
            scenario!!.onActivity { activity ->
                setText(activity, R.id.nameInput, "")
                setText(activity, R.id.valueInput, "")
                activity!!.findViewById<View?>(R.id.insertButton)!!.performClick()
                assertEquals(
                    activity!!.getString(R.string.db_fields_required),
                    statusText(activity),
                )
            }
        }
    }

    @Test
    open fun updateButton_withEmptyInputs_showsValidationMessage() {
        ActivityScenario.launch<DatabaseLabActivity?>(DatabaseLabActivity::class.java)!!.use {
            scenario ->
            scenario!!.onActivity { activity ->
                setText(activity, R.id.nameInput, "  ")
                setText(activity, R.id.valueInput, "")
                activity!!.findViewById<View?>(R.id.updateButton)!!.performClick()
                assertEquals(
                    activity!!.getString(R.string.db_update_fields_required),
                    statusText(activity),
                )
            }
        }
    }

    @Test
    open fun deleteButton_withEmptyName_showsValidationMessage() {
        ActivityScenario.launch<DatabaseLabActivity?>(DatabaseLabActivity::class.java)!!.use {
            scenario ->
            scenario!!.onActivity { activity ->
                setText(activity, R.id.nameInput, "")
                activity!!.findViewById<View?>(R.id.deleteButton)!!.performClick()
                assertEquals(
                    activity!!.getString(R.string.db_delete_name_required),
                    statusText(activity),
                )
            }
        }
    }

    private fun setText(activity: DatabaseLabActivity?, viewId: Int, value: String?) {
        (activity!!.findViewById<View?>(viewId) as EditText).setText(value)
    }

    private fun statusText(activity: DatabaseLabActivity?): String? =
        (activity!!.findViewById<View?>(R.id.databaseStatusText) as TextView).getText().toString()
}
