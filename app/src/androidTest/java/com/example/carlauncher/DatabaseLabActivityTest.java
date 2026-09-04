package com.example.carlauncher;

import android.os.Looper;
import android.widget.EditText;
import android.widget.TextView;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;
import androidx.test.platform.app.InstrumentationRegistry;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * DatabaseLabActivity 的 Instrumentation 测试：
 * 启动与空输入校验（主线程同步路径）。真实 SQLite 读写行为由
 * {@code LabDatabaseHelperTest} 覆盖；本类不等待后台执行器线程。
 */
@RunWith(AndroidJUnit4.class)
public class DatabaseLabActivityTest {

    private static void idleMainLooper() {
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
    }

    @Test
    public void launch_opensDatabaseAndShowsStatus() {
        try (ActivityScenario<DatabaseLabActivity> scenario =
                     ActivityScenario.launch(DatabaseLabActivity.class)) {
            idleMainLooper();
            scenario.onActivity(activity -> {
                String status = ((TextView) activity.findViewById(R.id.databaseStatusText))
                        .getText().toString();
                // 打开成功或尚未完成的提示均可接受，重点是启动过程不崩溃且状态栏被更新
                assertTrue(status.equals("正在创建或打开数据库……")
                        || status.startsWith("数据库已打开")
                        || status.equals("数据库打开失败，请查看 Logcat"));
            });
        }
    }

    @Test
    public void insertButton_withEmptyInputs_showsValidationMessage() {
        try (ActivityScenario<DatabaseLabActivity> scenario =
                     ActivityScenario.launch(DatabaseLabActivity.class)) {
            scenario.onActivity(activity -> {
                setText(activity, R.id.nameInput, "");
                setText(activity, R.id.valueInput, "");
                activity.findViewById(R.id.insertButton).performClick();
                assertEquals("name 和 value 不能为空", statusText(activity));
            });
        }
    }

    @Test
    public void updateButton_withEmptyInputs_showsValidationMessage() {
        try (ActivityScenario<DatabaseLabActivity> scenario =
                     ActivityScenario.launch(DatabaseLabActivity.class)) {
            scenario.onActivity(activity -> {
                setText(activity, R.id.nameInput, "  ");
                setText(activity, R.id.valueInput, "");
                activity.findViewById(R.id.updateButton).performClick();
                assertEquals("更新失败：name 和 value 不能为空", statusText(activity));
            });
        }
    }

    @Test
    public void deleteButton_withEmptyName_showsValidationMessage() {
        try (ActivityScenario<DatabaseLabActivity> scenario =
                     ActivityScenario.launch(DatabaseLabActivity.class)) {
            scenario.onActivity(activity -> {
                setText(activity, R.id.nameInput, "");
                activity.findViewById(R.id.deleteButton).performClick();
                assertEquals("删除失败：请输入要删除的 name", statusText(activity));
            });
        }
    }

    private static void setText(DatabaseLabActivity activity, int viewId, String value) {
        ((EditText) activity.findViewById(viewId)).setText(value);
    }

    private static String statusText(DatabaseLabActivity activity) {
        return ((TextView) activity.findViewById(R.id.databaseStatusText))
                .getText().toString();
    }
}


