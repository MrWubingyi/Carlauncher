package com.example.carlauncher

import android.content.Context
import android.os.Looper
import android.view.View
import android.widget.Spinner
import android.widget.TextView
import androidx.appcompat.widget.SwitchCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * SettingsDetailFragment 的 Instrumentation 测试（以 FragmentLabActivity 为宿主）： 标题渲染、Display
 * 专属控件、主题下拉、欢迎开关与恢复默认设置。
 */
@RunWith(AndroidJUnit4::class)
open class SettingsDetailFragmentTest {

    private fun idleMainLooper() {
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
    }

    /* 启动 FragmentLabActivity 并导航到指定标题的详情页。 */
    private fun navigateToDetail(
        scenario: ActivityScenario<FragmentLabActivity?>?,
        title: String?,
    ) {
        idleMainLooper()
        scenario!!.onActivity { activity -> activity!!.onSettingSelected(title) }
        idleMainLooper()
    }

    private fun detailOf(activity: FragmentLabActivity?): SettingsDetailFragment? =
        activity!!.getSupportFragmentManager().findFragmentByTag("settings_detail")
            as SettingsDetailFragment?

    @Test
    open fun displayDetail_showsTitleAndDisplayControls() {
        ActivityScenario.launch<FragmentLabActivity?>(FragmentLabActivity::class.java)!!.use {
            scenario ->
            navigateToDetail(scenario, "Display")

            scenario!!.onActivity { activity ->
                val detail = detailOf(activity)
                val view = detail!!.requireView()
                assertEquals(
                    "Display Detail",
                    (view!!.findViewById<View?>(R.id.detailTitleText) as TextView)
                        .getText()
                        .toString(),
                )
                assertEquals(
                    View.VISIBLE.toLong(),
                    view!!.findViewById<View?>(R.id.themeLabelText)!!.getVisibility().toLong(),
                )
                assertEquals(
                    View.VISIBLE.toLong(),
                    view!!.findViewById<View?>(R.id.themeSpinner)!!.getVisibility().toLong(),
                )
                assertEquals(
                    View.VISIBLE.toLong(),
                    view!!.findViewById<View?>(R.id.welcomeSwitch)!!.getVisibility().toLong(),
                )
                assertEquals(
                    View.VISIBLE.toLong(),
                    view!!.findViewById<View?>(R.id.resetSettingsButton)!!.getVisibility().toLong(),
                )
                assertEquals(
                    ThemePreferences.themeToPosition(ThemePreferences.getColorTheme(activity))
                        .toLong(),
                    (view!!.findViewById<View?>(R.id.themeSpinner) as Spinner)
                        .getSelectedItemPosition()
                        .toLong(),
                )
            }
        }
    }

    @Test
    open fun networkDetail_showsTitle_andHidesThemeControls() {
        ActivityScenario.launch<FragmentLabActivity?>(FragmentLabActivity::class.java)!!.use {
            scenario ->
            navigateToDetail(scenario, "Network")

            scenario!!.onActivity { activity ->
                val detail = detailOf(activity)
                val view = detail!!.requireView()
                assertEquals(
                    "Network Detail",
                    (view!!.findViewById<View?>(R.id.detailTitleText) as TextView)
                        .getText()
                        .toString(),
                )
                // 主题相关控件仅在 Display 详情页由代码显示，其他标题保持布局默认的隐藏态
                assertEquals(
                    View.GONE.toLong(),
                    view!!.findViewById<View?>(R.id.themeLabelText)!!.getVisibility().toLong(),
                )
                assertEquals(
                    View.GONE.toLong(),
                    view!!.findViewById<View?>(R.id.themeSpinner)!!.getVisibility().toLong(),
                )
                val expectedBackVisibility =
                    if (activity!!.findViewById<View?>(R.id.detail_container) == null) View.VISIBLE
                    else View.GONE
                assertEquals(
                    expectedBackVisibility.toLong(),
                    view!!.findViewById<View?>(R.id.detailBackButton)!!.getVisibility().toLong(),
                )
            }
        }
    }

    @Test
    open fun welcomeSwitchToggle_persistsDisabledState() {
        val context = ApplicationProvider.getApplicationContext<Context?>()
        assertTrue(
            "前置条件：默认启用欢迎提示",
            ThemePreferences.isWelcomeEnabled(context),
        )

        ActivityScenario.launch<FragmentLabActivity?>(FragmentLabActivity::class.java)!!.use {
            scenario ->
            navigateToDetail(scenario, "Display")

            scenario!!.onActivity { activity ->
                val detail = detailOf(activity)
                val welcomeSwitch =
                    detail!!.requireView().findViewById<SwitchCompat?>(R.id.welcomeSwitch)
                welcomeSwitch!!.performClick()

                assertFalse(ThemePreferences.isWelcomeEnabled(context))
                assertFalse(welcomeSwitch!!.isChecked())
            }
        }
    }

    @Test
    open fun resetButton_restoresDefaultsAndUpdatesUi() {
        val context = ApplicationProvider.getApplicationContext<Context?>()
        ThemePreferences.saveAndApplyColorTheme(
            context,
            ThemePreferences.THEME_COLOR_DARK,
        )
        ThemePreferences.setWelcomeEnabled(context, false)

        ActivityScenario.launch<FragmentLabActivity?>(FragmentLabActivity::class.java)!!.use {
            scenario ->
            navigateToDetail(scenario, "Display")

            scenario!!.onActivity { activity ->
                val detail = detailOf(activity)
                detail!!
                    .requireView()
                    .findViewById<View?>(R.id.resetSettingsButton)!!
                    .performClick()

                assertEquals(
                    ThemePreferences.THEME_COLOR_SYSTEM,
                    ThemePreferences.getColorTheme(context),
                )
                assertTrue(ThemePreferences.isWelcomeEnabled(context))

                val spinner = detail!!.requireView().findViewById<Spinner?>(R.id.themeSpinner)
                forceLayout(spinner)
                assertEquals(0, spinner!!.getSelectedItemPosition().toLong())
                val welcomeSwitch =
                    detail!!.requireView().findViewById<SwitchCompat?>(R.id.welcomeSwitch)
                assertTrue(welcomeSwitch!!.isChecked())
            }
        }
    }

    /* Spinner 的选择回调在布局阶段触发，这里强制走一次 measure/layout。 */
    private fun forceLayout(view: View?) {
        view!!.measure(
            View.MeasureSpec.makeMeasureSpec(280, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
        )
        view!!.layout(0, 0, 280, 100)
    }
}
