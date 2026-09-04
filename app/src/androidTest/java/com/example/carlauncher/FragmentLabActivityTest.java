package com.example.carlauncher;

import android.os.Looper;
import android.view.View;
import android.widget.TextView;

import androidx.fragment.app.Fragment;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;
import androidx.test.platform.app.InstrumentationRegistry;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

/**
 * FragmentLabActivity（单栏布局）的 Instrumentation 测试：
 * 菜单导航到详情、防抖与重复选择忽略、返回栈弹出。
 */
@RunWith(AndroidJUnit4.class)
public class FragmentLabActivityTest {

    private static void idleMainLooper() {
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
    }

    private static Fragment detailFragment(FragmentLabActivity activity) {
        return activity.getSupportFragmentManager()
                .findFragmentByTag("settings_detail");
    }

    private static String detailTitle(Fragment detail) {
        return ((TextView) detail.getView().findViewById(R.id.detailTitleText))
                .getText().toString();
    }

    @Test
    public void launch_showsMenuFragment_inSinglePane() {
        try (ActivityScenario<FragmentLabActivity> scenario =
                     ActivityScenario.launch(FragmentLabActivity.class)) {
            idleMainLooper();
            scenario.onActivity(activity -> {
                Fragment menu = activity.getSupportFragmentManager()
                        .findFragmentByTag(SettingsMenuFragment.TAG);
                assertNotNull(menu);
                if (activity.findViewById(R.id.detail_container) == null) {
                    assertNull(detailFragment(activity));
                } else {
                    assertNotNull(detailFragment(activity));
                }
            });
        }
    }

    @Test
    public void menuButton_navigatesToDetailWithBackButton() {
        try (ActivityScenario<FragmentLabActivity> scenario =
                     ActivityScenario.launch(FragmentLabActivity.class)) {
            idleMainLooper();
            scenario.onActivity(activity ->
                    activity.findViewById(R.id.displaySettingsButton).performClick());
            idleMainLooper();

            scenario.onActivity(activity -> {
                Fragment detail = detailFragment(activity);
                assertNotNull(detail);
                assertEquals("Display Detail", detailTitle(detail));
                int expectedVisibility = activity.findViewById(R.id.detail_container) == null
                        ? View.VISIBLE : View.GONE;
                assertEquals(expectedVisibility,
                        detail.getView().findViewById(R.id.detailBackButton).getVisibility());
            });
        }
    }

    @Test
    public void rapidSecondSelection_isDebounced() {
        try (ActivityScenario<FragmentLabActivity> scenario =
                     ActivityScenario.launch(FragmentLabActivity.class)) {
            idleMainLooper();
            scenario.onActivity(activity -> {
                activity.onSettingSelected("Network");
                // 同一时刻再次选择应被 500ms 防抖忽略
                activity.onSettingSelected("About");
            });
            idleMainLooper();

            scenario.onActivity(activity ->
                    assertEquals("Network Detail", detailTitle(detailFragment(activity))));

        }
    }

    @Test
    public void duplicateSelection_isIgnored() {
        try (ActivityScenario<FragmentLabActivity> scenario =
                     ActivityScenario.launch(FragmentLabActivity.class)) {
            idleMainLooper();
            scenario.onActivity(activity -> activity.onSettingSelected("Network"));
            idleMainLooper();

            // 重复选择不应改变当前详情。
            scenario.onActivity(activity -> activity.onSettingSelected("Network"));
            idleMainLooper();

            scenario.onActivity(activity ->
                    assertEquals("Network Detail", detailTitle(detailFragment(activity))));
        }
    }

    @Test
    public void detailBackButton_popsBackToMenu() {
        try (ActivityScenario<FragmentLabActivity> scenario =
                     ActivityScenario.launch(FragmentLabActivity.class)) {
            idleMainLooper();
            scenario.onActivity(activity -> activity.onSettingSelected("Display"));
            idleMainLooper();

            scenario.onActivity(activity -> {
                Fragment detail = detailFragment(activity);
                assertNotNull(detail);
                View backButton = detail.getView().findViewById(R.id.detailBackButton);
                if (backButton.getVisibility() == View.VISIBLE) {
                    backButton.performClick();
                }
            });
            idleMainLooper();

            scenario.onActivity(activity -> {
                assertNotNull(activity.getSupportFragmentManager()
                        .findFragmentByTag(SettingsMenuFragment.TAG));
                if (activity.findViewById(R.id.detail_container) == null) {
                    assertNull(detailFragment(activity));
                } else {
                    assertNotNull(detailFragment(activity));
                }
            });
        }
    }
}


