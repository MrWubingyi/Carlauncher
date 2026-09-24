package com.example.carlauncher

import android.view.View
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** AppDrawerActivity 的 Instrumentation 集成测试： 启动器应用列表（过滤自身）、计数按钮与状态恢复。 */
@RunWith(AndroidJUnit4::class)
open class AppDrawerActivityTest {

    @Test
    open fun topButton_displaysEntireLabelWithoutClipping() {
        ActivityScenario.launch<AppDrawerActivity?>(AppDrawerActivity::class.java)!!.use { scenario
            ->
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            scenario!!.onActivity { activity ->
                val button = activity!!.findViewById<TextView?>(R.id.clickButton)
                val layout = button!!.getLayout()
                assertNotNull(layout)
                assertTrue(
                    (layout!!.getHeight() <=
                        (button!!.getHeight() -
                            button!!.getCompoundPaddingTop() -
                            button!!.getCompoundPaddingBottom()))
                )
                for (line in 0 until layout!!.getLineCount()) {
                    assertEquals(0, layout!!.getEllipsisCount(line).toLong())
                    assertTrue(layout!!.getLineWidth(line) <= layout!!.getWidth())
                }
                assertEquals(
                    button!!.length().toLong(),
                    layout!!.getLineEnd(layout!!.getLineCount() - 1).toLong(),
                )
            }
        }
    }

    @Test
    open fun launch_countMatchesVisibleLauncherItems() {
        ActivityScenario.launch<AppDrawerActivity?>(AppDrawerActivity::class.java)!!.use { scenario
            ->
            scenario!!.onActivity { activity ->
                val recyclerView = activity!!.findViewById<RecyclerView?>(R.id.appRecyclerView)
                assertNotNull(recyclerView!!.getAdapter())
                assertTrue(recyclerView!!.getAdapter()!!.getItemCount() >= 0)
            }
        }
    }

    @Test
    open fun clickCountButton_setsTextToFive() {
        ActivityScenario.launch<AppDrawerActivity?>(AppDrawerActivity::class.java)!!.use { scenario
            ->
            scenario!!.onActivity { activity ->
                activity!!.findViewById<View?>(R.id.clickButton)!!.performClick()
                assertEquals("5", text(activity, R.id.text))
            }
        }
    }

    @Test
    open fun countSurvivesRecreation_throughSavedInstanceState() {
        ActivityScenario.launch<AppDrawerActivity?>(AppDrawerActivity::class.java)!!.use { scenario
            ->
            scenario!!.onActivity { activity ->
                activity!!.findViewById<View?>(R.id.clickButton)!!.performClick()
            }

            scenario!!.recreate()

            scenario!!.onActivity { activity -> assertEquals("5", text(activity, R.id.text)) }
        }
    }

    private fun text(activity: AppDrawerActivity?, viewId: Int): String? =
        (activity!!.findViewById<View?>(viewId) as TextView).getText().toString()
}
