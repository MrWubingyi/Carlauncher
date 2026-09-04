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

/**
 * FragmentLabActivity 双栏布局（sw600dp）的 Instrumentation 测试：
 * 初始即显示 Display 详情，菜单选择替换右栏详情。
 */
@RunWith(AndroidJUnit4.class)
public class FragmentLabActivityTwoPaneTest {

    private static void idleMainLooper() {
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
    }

    @Test
    public void launch_showsMenuAndInitialDisplayDetail_inTwoPane() {
        try (ActivityScenario<FragmentLabActivity> scenario =
                     ActivityScenario.launch(FragmentLabActivity.class)) {
            idleMainLooper();
            scenario.onActivity(activity -> {
                assertNotNull(activity.findViewById(R.id.detail_container));
                Fragment menu = activity.getSupportFragmentManager()
                        .findFragmentByTag(SettingsMenuFragment.TAG);
                Fragment detail = activity.getSupportFragmentManager()
                        .findFragmentByTag("settings_detail");
                assertNotNull(menu);
                assertNotNull(detail);
                assertEquals("Display Detail",
                        ((TextView) detail.getView()
                                .findViewById(R.id.detailTitleText))
                                .getText().toString());
                // 双栏下详情页不显示返回按钮
                assertEquals(View.GONE,
                        detail.getView().findViewById(R.id.detailBackButton).getVisibility());
            });
        }
    }

    @Test
    public void menuNetworkSelection_replacesDetailPane() {
        try (ActivityScenario<FragmentLabActivity> scenario =
                     ActivityScenario.launch(FragmentLabActivity.class)) {
            idleMainLooper();
            scenario.onActivity(activity ->
                    activity.findViewById(R.id.networkSettingsButton).performClick());
            idleMainLooper();

            scenario.onActivity(activity -> {
                Fragment detail = activity.getSupportFragmentManager()
                        .findFragmentByTag("settings_detail");
                assertNotNull(detail);
                assertEquals("Network Detail",
                        ((TextView) detail.getView()
                                .findViewById(R.id.detailTitleText))
                                .getText().toString());
                // 菜单 Fragment 仍在左栏
                assertNotNull(activity.getSupportFragmentManager()
                        .findFragmentByTag(SettingsMenuFragment.TAG));
            });
        }
    }
}


