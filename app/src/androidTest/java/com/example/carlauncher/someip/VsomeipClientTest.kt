package com.example.carlauncher.someip

import android.os.Looper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.util.ArrayList
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/* Exercises JNI event dispatch without starting native networking. */
@RunWith(AndroidJUnit4::class)
open class VsomeipClientTest {
    @Test
    open fun callbacksUseMainThreadAndClearedListenersReceiveNoQueuedEvents() {
        val events = ArrayList<String?>()
        val listener =
            object : VsomeipClient.Listener {
                private fun add(event: String?) {
                    assertEquals(Looper.getMainLooper(), Looper.myLooper())
                    events.add(event)
                }

                override fun onAvailable(available: Boolean) {
                    add("available=" + available)
                }

                override fun onRegistered() {
                    add("registered")
                }

                override fun onResponse(ok: Boolean, code: Int) {
                    add("response=" + ok + ":" + code)
                }

                override fun onStopped() {
                    add("stopped")
                }
            }
        try {
            VsomeipClient.setListener(listener)
            VsomeipClient.onNativeEvent(0, 0)
            VsomeipClient.onNativeEvent(1, 1)
            VsomeipClient.onNativeEvent(3, 0)
            VsomeipClient.onNativeEvent(4, 1)
            VsomeipClient.onNativeEvent(2, 0)
            VsomeipClient.onNativeEvent(5, 0)
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            assertEquals(
                java.util.Arrays.asList<String?>(
                    "registered",
                    "available=true",
                    "response=true:0",
                    "response=false:1",
                    "available=false",
                    "stopped",
                ),
                events,
            )

            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                VsomeipClient.onNativeEvent(1, 1)
                VsomeipClient.clearListener(listener)
            }
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            assertEquals(6, events.size.toLong())
        } finally {
            VsomeipClient.clearListener(listener)
        }
    }
}
