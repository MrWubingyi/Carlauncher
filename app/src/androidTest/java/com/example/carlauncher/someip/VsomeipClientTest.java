package com.example.carlauncher.someip;

import android.os.Looper;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.util.ArrayList;
import java.util.List;
import static org.junit.Assert.*;

/** Exercises JNI event dispatch without starting native networking. */
@RunWith(AndroidJUnit4.class)
public class VsomeipClientTest {
    @Test public void callbacksUseMainThreadAndClearedListenersReceiveNoQueuedEvents() {
        List<String> events = new ArrayList<>();
        VsomeipClient.Listener listener = new VsomeipClient.Listener() {
            private void add(String event) {
                assertEquals(Looper.getMainLooper(), Looper.myLooper());
                events.add(event);
            }
            @Override public void onAvailable(boolean available) { add("available=" + available); }
            @Override public void onRegistered() { add("registered"); }
            @Override public void onResponse(boolean ok, int code) { add("response=" + ok + ":" + code); }
            @Override public void onStopped() { add("stopped"); }
        };
        try {
            VsomeipClient.setListener(listener);
            VsomeipClient.onNativeEvent(0, 0);
            VsomeipClient.onNativeEvent(1, 1);
            VsomeipClient.onNativeEvent(3, 0);
            VsomeipClient.onNativeEvent(4, 1);
            VsomeipClient.onNativeEvent(2, 0);
            VsomeipClient.onNativeEvent(5, 0);
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            assertEquals(java.util.Arrays.asList("registered", "available=true", "response=true:0",
                    "response=false:1", "available=false", "stopped"), events);

            InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
                VsomeipClient.onNativeEvent(1, 1);
                VsomeipClient.clearListener(listener);
            });
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            assertEquals(6, events.size());
        } finally {
            VsomeipClient.clearListener(listener);
        }
    }
}
