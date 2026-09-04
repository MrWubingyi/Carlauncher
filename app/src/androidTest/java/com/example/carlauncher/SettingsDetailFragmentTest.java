package com.example.carlauncher;

import android.content.Context;
import android.os.Looper;
import android.view.View;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.appcompat.widget.SwitchCompat;
import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;
import androidx.test.platform.app.InstrumentationRegistry;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * SettingsDetailFragment 的 Instrumentation 测试（以 FragmentLabActivity 为宿主）：
 * 标题渲染、Display 专属控件、主题下拉、欢迎开关与恢复默认设置。
 */
@RunWith(AndroidJUnit4.class)
public class SettingsDetailFragmentTest {

    private static void idleMainLooper() {
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
    }

    /** 启动 FragmentLabActivity 并导航到指定标题的详情页。 */
    private static void navigateToDetail(
            ActivityScenario<FragmentLabActivity> scenario,
            String title
    ) {
        idleMainLooper();
        scenario.onActivity(activity -> activity.onSettingSelected(title));
        idleMainLooper();
    }

    private static SettingsDetailFragment detailOf(FragmentLabActivity activity) {
        return (SettingsDetailFragment) activity.getSupportFragmentManager()
                .findFragmentByTag("settings_detail");
    }

    @Test
    public void displayDetail_showsTitleAndDisplayControls() {
        try (ActivityScenario<FragmentLabActivity> scenario =
                     ActivityScenario.launch(FragmentLabActivity.class)) {
            navigateToDetail(scenario, "Display");

            scenario.onActivity(activity -> {
                SettingsDetailFragment detail = detailOf(activity);
                View view = detail.requireView();
                assertEquals("Display Detail",
                        ((TextView) view.findViewById(R.id.detailTitleText))
                                .getText().toString());
                assertEquals(View.VISIBLE,
                        view.findViewById(R.id.themeLabelText).getVisibility());
                assertEquals(View.VISIBLE,
                        view.findViewById(R.id.themeSpinner).getVisibility());
                assertEquals(View.VISIBLE,
                        view.findViewById(R.id.welcomeSwitch).getVisibility());
                assertEquals(View.VISIBLE,
                        view.findViewById(R.id.resetSettingsButton).getVisibility());
                assertEquals(ThemePreferences.themeToPosition(
                                ThemePreferences.getColorTheme(activity)),
                        ((Spinner) view.findViewById(R.id.themeSpinner))
                                .getSelectedItemPosition());
            });
        }
    }

    @Test
    public void networkDetail_showsTitle_andHidesThemeControls() {
        try (ActivityScenario<FragmentLabActivity> scenario =
                     ActivityScenario.launch(FragmentLabActivity.class)) {
            navigateToDetail(scenario, "Network");

            scenario.onActivity(activity -> {
                SettingsDetailFragment detail = detailOf(activity);
                View view = detail.requireView();
                assertEquals("Network Detail",
                        ((TextView) view.findViewById(R.id.detailTitleText))
                                .getText().toString());
                // 主题相关控件仅在 Display 详情页由代码显示，其他标题保持布局默认的隐藏态
                assertEquals(View.GONE,
                        view.findViewById(R.id.themeLabelText).getVisibility());
                assertEquals(View.GONE,
                        view.findViewById(R.id.themeSpinner).getVisibility());
                int expectedBackVisibility = activity.findViewById(R.id.detail_container) == null
                        ? View.VISIBLE : View.GONE;
                assertEquals(expectedBackVisibility,
                        view.findViewById(R.id.detailBackButton).getVisibility());
            });
        }
    }

    @Test
    public void welcomeSwitchToggle_persistsDisabledState() {
        Context context = ApplicationProvider.getApplicationContext();
        assertTrue("前置条件：默认启用欢迎提示",
                ThemePreferences.isWelcomeEnabled(context));

        try (ActivityScenario<FragmentLabActivity> scenario =
                     ActivityScenario.launch(FragmentLabActivity.class)) {
            navigateToDetail(scenario, "Display");

            scenario.onActivity(activity -> {
                SettingsDetailFragment detail = detailOf(activity);
                SwitchCompat welcomeSwitch =
                        detail.requireView().findViewById(R.id.welcomeSwitch);
                welcomeSwitch.performClick();

                assertFalse(ThemePreferences.isWelcomeEnabled(context));
                assertFalse(welcomeSwitch.isChecked());
            });
        }
    }

    @Test
    public void resetButton_restoresDefaultsAndUpdatesUi() {
        Context context = ApplicationProvider.getApplicationContext();
        ThemePreferences.saveAndApplyColorTheme(
                context, ThemePreferences.THEME_COLOR_DARK);
        ThemePreferences.setWelcomeEnabled(context, false);

        try (ActivityScenario<FragmentLabActivity> scenario =
                     ActivityScenario.launch(FragmentLabActivity.class)) {
            navigateToDetail(scenario, "Display");

            scenario.onActivity(activity -> {
                SettingsDetailFragment detail = detailOf(activity);
                detail.requireView()
                        .findViewById(R.id.resetSettingsButton)
                        .performClick();

                assertEquals(ThemePreferences.THEME_COLOR_SYSTEM,
                        ThemePreferences.getColorTheme(context));
                assertTrue(ThemePreferences.isWelcomeEnabled(context));

                Spinner spinner = detail.requireView().findViewById(R.id.themeSpinner);
                forceLayout(spinner);
                assertEquals(0, spinner.getSelectedItemPosition());
                SwitchCompat welcomeSwitch =
                        detail.requireView().findViewById(R.id.welcomeSwitch);
                assertTrue(welcomeSwitch.isChecked());
            });
        }
    }

    /** Spinner 的选择回调在布局阶段触发，这里强制走一次 measure/layout。 */
    private static void forceLayout(View view) {
        view.measure(
                View.MeasureSpec.makeMeasureSpec(280, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        view.layout(0, 0, 280, 100);
    }
}


