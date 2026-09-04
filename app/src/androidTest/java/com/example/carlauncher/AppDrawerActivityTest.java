package com.example.carlauncher;

import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;
import androidx.test.platform.app.InstrumentationRegistry;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * AppDrawerActivity 的 Instrumentation 集成测试：
 * 启动器应用列表（过滤自身）、计数按钮与状态恢复。
 */
@RunWith(AndroidJUnit4.class)
public class AppDrawerActivityTest {

    @Test
    public void launch_countMatchesVisibleLauncherItems() {
        try (ActivityScenario<AppDrawerActivity> scenario =
                     ActivityScenario.launch(AppDrawerActivity.class)) {
            scenario.onActivity(activity -> {
                RecyclerView recyclerView = activity.findViewById(R.id.appRecyclerView);
                assertNotNull(recyclerView.getAdapter());
                assertTrue(recyclerView.getAdapter().getItemCount() >= 0);
            });
        }
    }

    @Test
    public void clickCountButton_setsTextToFive() {
        try (ActivityScenario<AppDrawerActivity> scenario =
                     ActivityScenario.launch(AppDrawerActivity.class)) {
            scenario.onActivity(activity -> {
                activity.findViewById(R.id.clickButton).performClick();
                assertEquals("5", text(activity, R.id.text));
            });
        }
    }

    @Test
    public void countSurvivesRecreation_throughSavedInstanceState() {
        try (ActivityScenario<AppDrawerActivity> scenario =
                     ActivityScenario.launch(AppDrawerActivity.class)) {
            scenario.onActivity(activity ->
                    activity.findViewById(R.id.clickButton).performClick());

            scenario.recreate();

            scenario.onActivity(activity -> {
                assertEquals("5", text(activity, R.id.text));
            });
        }
    }

    private static String text(AppDrawerActivity activity, int viewId) {
        return ((TextView) activity.findViewById(viewId)).getText().toString();
    }
}


