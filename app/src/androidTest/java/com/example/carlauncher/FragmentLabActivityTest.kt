package com.example.carlauncher

import android.os.Looper
import android.view.View
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

/** FragmentLabActivity（单栏布局）的 Instrumentation 测试： 菜单导航到详情、防抖与重复选择忽略、返回栈弹出。 */
@RunWith(AndroidJUnit4::class)
open class FragmentLabActivityTest {

    private fun idleMainLooper() {
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
    }

    @Test
    open fun recreate_keepsSelectedDetailAndBackStackWithoutAddingInitialFragments() {
        ActivityScenario.launch<FragmentLabActivity?>(FragmentLabActivity::class.java)!!.use {
            scenario ->
            scenario!!.onActivity { activity ->
                activity!!.onSettingSelected("Network")
                activity!!.getSupportFragmentManager().executePendingTransactions()
            }
            val backStack = java.util.concurrent.atomic.AtomicInteger()
            scenario!!.onActivity { activity ->
                backStack.set(activity!!.getSupportFragmentManager().getBackStackEntryCount())
            }
            scenario!!.recreate()
            scenario!!.onActivity { activity ->
                assertEquals("Network Detail", detailTitle(detailFragment(activity)))
                assertEquals(
                    backStack.get().toLong(),
                    activity!!.getSupportFragmentManager().getBackStackEntryCount().toLong(),
                )
                assertEquals(
                    1,
                    activity!!
                        .getSupportFragmentManager()
                        .getFragments()
                        .stream()
                        .filter { fragment -> "settings_detail" == fragment!!.getTag() }
                        .count(),
                )
            }
        }
    }

    private fun detailFragment(activity: FragmentLabActivity?): Fragment? =
        activity!!.getSupportFragmentManager().findFragmentByTag("settings_detail")

    private fun detailTitle(detail: Fragment?): String? =
        (detail!!.getView()!!.findViewById<View?>(R.id.detailTitleText) as TextView)
            .getText()
            .toString()

    @Test
    open fun launch_showsMenuFragment_inSinglePane() {
        ActivityScenario.launch<FragmentLabActivity?>(FragmentLabActivity::class.java)!!.use {
            scenario ->
            idleMainLooper()
            scenario!!.onActivity { activity ->
                val menu =
                    activity!!
                        .getSupportFragmentManager()
                        .findFragmentByTag(SettingsMenuFragment.TAG)
                assertNotNull(menu)
                if (activity!!.findViewById<View?>(R.id.detail_container) == null) {
                    assertNull(detailFragment(activity))
                } else {
                    assertNotNull(detailFragment(activity))
                }
            }
        }
    }

    @Test
    open fun menuButton_navigatesToDetailWithBackButton() {
        ActivityScenario.launch<FragmentLabActivity?>(FragmentLabActivity::class.java)!!.use {
            scenario ->
            idleMainLooper()
            scenario!!.onActivity { activity ->
                activity!!.findViewById<View?>(R.id.displaySettingsButton)!!.performClick()
            }
            idleMainLooper()

            scenario!!.onActivity { activity ->
                val detail = detailFragment(activity)
                assertNotNull(detail)
                assertEquals("Display Detail", detailTitle(detail))
                val expectedVisibility =
                    if (activity!!.findViewById<View?>(R.id.detail_container) == null) View.VISIBLE
                    else View.GONE
                assertEquals(
                    expectedVisibility.toLong(),
                    detail!!
                        .getView()!!
                        .findViewById<View?>(R.id.detailBackButton)!!
                        .getVisibility()
                        .toLong(),
                )
            }
        }
    }

    @Test
    open fun rapidSecondSelection_isDebounced() {
        ActivityScenario.launch<FragmentLabActivity?>(FragmentLabActivity::class.java)!!.use {
            scenario ->
            idleMainLooper()
            scenario!!.onActivity { activity ->
                activity!!.onSettingSelected("Network")
                // 同一时刻再次选择应被 500ms 防抖忽略
                activity!!.onSettingSelected("About")
            }
            idleMainLooper()

            scenario!!.onActivity { activity ->
                assertEquals("Network Detail", detailTitle(detailFragment(activity)))
            }
        }
    }

    @Test
    open fun duplicateSelection_isIgnored() {
        ActivityScenario.launch<FragmentLabActivity?>(FragmentLabActivity::class.java)!!.use {
            scenario ->
            idleMainLooper()
            scenario!!.onActivity { activity -> activity!!.onSettingSelected("Network") }
            idleMainLooper()

            // 重复选择不应改变当前详情。
            scenario!!.onActivity { activity -> activity!!.onSettingSelected("Network") }
            idleMainLooper()

            scenario!!.onActivity { activity ->
                assertEquals("Network Detail", detailTitle(detailFragment(activity)))
            }
        }
    }

    @Test
    open fun detailBackButton_popsBackToMenu() {
        ActivityScenario.launch<FragmentLabActivity?>(FragmentLabActivity::class.java)!!.use {
            scenario ->
            idleMainLooper()
            scenario!!.onActivity { activity -> activity!!.onSettingSelected("Display") }
            idleMainLooper()

            scenario!!.onActivity { activity ->
                val detail = detailFragment(activity)
                assertNotNull(detail)
                val backButton = detail!!.getView()!!.findViewById<View?>(R.id.detailBackButton)
                if (backButton!!.getVisibility() == View.VISIBLE) {
                    backButton!!.performClick()
                }
            }
            idleMainLooper()

            scenario!!.onActivity { activity ->
                assertNotNull(
                    activity!!
                        .getSupportFragmentManager()
                        .findFragmentByTag(SettingsMenuFragment.TAG)
                )
                if (activity!!.findViewById<View?>(R.id.detail_container) == null) {
                    assertNull(detailFragment(activity))
                } else {
                    assertNotNull(detailFragment(activity))
                }
            }
        }
    }
}
