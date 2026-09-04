package com.example.carlauncher;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Looper;
import android.widget.TextView;

import androidx.lifecycle.Lifecycle;
import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;
import androidx.test.platform.app.InstrumentationRegistry;

import static org.junit.Assert.assertEquals;

/**
 * BroadcastLabActivity 的 Instrumentation 测试：
 * 电源广播接收与 onStop 时的反注册。
 */
@RunWith(AndroidJUnit4.class)
public class BroadcastLabActivityTest {

    private static void idleMainLooper() {
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
    }

    @Test
    public void powerBroadcasts_updateStatusText() {
        try (ActivityScenario<BroadcastLabActivity> scenario =
                     ActivityScenario.launch(BroadcastLabActivity.class)) {
            scenario.onActivity(activity -> {
                assertEquals("Waiting for power broadcast",
                        text(activity));
            });

            scenario.onActivity(activity -> {
                dispatchPowerAction(activity, Intent.ACTION_POWER_CONNECTED);
                assertEquals("Power connected", text(activity));
                dispatchPowerAction(activity, Intent.ACTION_POWER_DISCONNECTED);
                assertEquals("Power disconnected", text(activity));
            });
        }
    }

    @Test
    public void receiver_isUnregisteredAfterStop() {
        try (ActivityScenario<BroadcastLabActivity> scenario =
                     ActivityScenario.launch(BroadcastLabActivity.class)) {
            scenario.onActivity(activity -> assertEquals(true, receiverRegistered(activity)));
            scenario.moveToState(Lifecycle.State.CREATED);
            scenario.onActivity(activity -> assertEquals(false, receiverRegistered(activity)));
        }
    }

    private static void dispatchPowerAction(BroadcastLabActivity activity, String action) {
        try {
            java.lang.reflect.Field field = BroadcastLabActivity.class
                    .getDeclaredField("powerReceiver");
            field.setAccessible(true);
            ((BroadcastReceiver) field.get(activity)).onReceive(activity, new Intent(action));
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError(exception);
        }
    }

    private static boolean receiverRegistered(BroadcastLabActivity activity) {
        try {
            java.lang.reflect.Field field = BroadcastLabActivity.class
                    .getDeclaredField("receiverRegistered");
            field.setAccessible(true);
            return field.getBoolean(activity);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError(exception);
        }
    }

    private static String text(BroadcastLabActivity activity) {
        return ((TextView) activity.findViewById(R.id.broadcastStatusText))
                .getText().toString();
    }
}


