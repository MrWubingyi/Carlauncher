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
import org.junit.Test
import org.junit.runner.RunWith

/** FragmentLabActivity 双栏布局（sw600dp）的 Instrumentation 测试： 初始即显示 Display 详情，菜单选择替换右栏详情。 */
@RunWith(AndroidJUnit4::class)
open class FragmentLabActivityTwoPaneTest {

    private fun idleMainLooper() {
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
    }

    @Test
    open fun launch_showsMenuAndInitialDisplayDetail_inTwoPane() {
        ActivityScenario.launch<FragmentLabActivity?>(FragmentLabActivity::class.java)!!.use {
            scenario ->
            idleMainLooper()
            scenario!!.onActivity { activity ->
                assertNotNull(activity!!.findViewById<View?>(R.id.detail_container))
                val menu =
                    activity!!
                        .getSupportFragmentManager()
                        .findFragmentByTag(SettingsMenuFragment.TAG)
                val detail =
                    activity!!.getSupportFragmentManager().findFragmentByTag("settings_detail")
                assertNotNull(menu)
                assertNotNull(detail)
                assertEquals(
                    "Display Detail",
                    (detail!!.getView()!!.findViewById<View?>(R.id.detailTitleText) as TextView)
                        .getText()
                        .toString(),
                )
                // 双栏下详情页不显示返回按钮
                assertEquals(
                    View.GONE.toLong(),
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
    open fun menuNetworkSelection_replacesDetailPane() {
        ActivityScenario.launch<FragmentLabActivity?>(FragmentLabActivity::class.java)!!.use {
            scenario ->
            idleMainLooper()
            scenario!!.onActivity { activity ->
                activity!!.findViewById<View?>(R.id.networkSettingsButton)!!.performClick()
            }
            idleMainLooper()

            scenario!!.onActivity { activity ->
                val detail =
                    activity!!.getSupportFragmentManager().findFragmentByTag("settings_detail")
                assertNotNull(detail)
                assertEquals(
                    "Network Detail",
                    (detail!!.getView()!!.findViewById<View?>(R.id.detailTitleText) as TextView)
                        .getText()
                        .toString(),
                )
                // 菜单 Fragment 仍在左栏
                assertNotNull(
                    activity!!
                        .getSupportFragmentManager()
                        .findFragmentByTag(SettingsMenuFragment.TAG)
                )
            }
        }
    }
}
