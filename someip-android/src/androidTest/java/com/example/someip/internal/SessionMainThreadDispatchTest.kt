package com.example.someip.internal

import android.os.Handler
import android.os.Looper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.someip.SomeIpClient
import com.example.someip.SomeIpClientConfig
import com.example.someip.SomeIpService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Verifies the real main Looper boundary using no native libraries or network observers. */
@RunWith(AndroidJUnit4::class)
class SessionMainThreadDispatchTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @Test
    fun nativeCallbacksAreDeliveredInOrderOnAndroidMainThread() {
        val fixture = Fixture()
        try {
            assertTrue(fixture.client.start(fixture.listener))
            fixture.coordinator.onNativeEvent(0, 0)
            fixture.coordinator.onNativeEvent(1, 0)
            fixture.coordinator.onNativePayload(byteArrayOf(7))
            fixture.coordinator.onNativeEvent(3, 0)
            fixture.coordinator.onNativeEvent(4, 1)
            fixture.coordinator.onNativeEvent(2, 0)
            fixture.coordinator.onNativeEvent(5, 0)
            instrumentation.waitForIdleSync()
            assertEquals(
                listOf("registered", "available:true", "payload:7", "response:true:0", "response:false:1", "available:false", "stopped"),
                fixture.events,
            )
            assertEquals(fixture.events.size, fixture.mainThreadCallbacks.size)
            assertTrue(fixture.mainThreadCallbacks.all { it })
            assertEquals(1, fixture.nativeStops)
        } finally {
            fixture.client.close()
            instrumentation.waitForIdleSync()
        }
    }

    @Test
    fun stopUnsubscribesQueuedEventsAndRestartDoesNotReceiveOldSessionCallbacks() {
        val fixture = Fixture()
        try {
            instrumentation.runOnMainSync {
                assertTrue(fixture.client.start(fixture.listener))
                fixture.coordinator.onNativePayload(byteArrayOf(1))
                fixture.coordinator.onNativeEvent(1, 0)
                fixture.client.stop()
                fixture.coordinator.onNativePayload(byteArrayOf(2))
                // Reusing the same listener must not make the old queued session valid.
                assertTrue(fixture.client.start(fixture.listener))
                fixture.coordinator.onNativePayload(byteArrayOf(3))
            }
            instrumentation.waitForIdleSync()
            assertEquals(listOf("payload:3"), fixture.events)

            instrumentation.runOnMainSync {
                fixture.coordinator.onNativePayload(byteArrayOf(4))
                fixture.client.stop()
                fixture.coordinator.onNativeEvent(1, 0)
                fixture.client.close()
            }
            instrumentation.waitForIdleSync()
            assertEquals(listOf("payload:3", "stopped"), fixture.events)
            assertTrue(fixture.mainThreadCallbacks.all { it })
            assertEquals(2, fixture.nativeStops)
        } finally {
            fixture.client.close()
            instrumentation.waitForIdleSync()
        }
    }

    private class Fixture {
        val events = mutableListOf<String>()
        val mainThreadCallbacks = mutableListOf<Boolean>()
        var nativeStops = 0
        private val handler = Handler(Looper.getMainLooper())
        val coordinator = SessionCoordinator(
            object : SessionCoordinator.Backend {
                override fun ensureLoaded() {}
                override fun start(config: SomeIpClientConfig): Boolean = true
                override fun stop() { nativeStops++ }
                override fun send(payload: ByteArray): Boolean = true
                override fun isAvailable(): Boolean = true
            },
            SessionCoordinator.Dispatcher { callback -> handler.post { callback() } },
        )
        val client = coordinator.create(
            SomeIpClientConfig("/unused/config.json", SomeIpService(1, 1, 0x8001, 1, 1, "test")),
            SessionCoordinator.Resources { object : AutoCloseable { override fun close() {} } },
        )
        val listener = object : SomeIpClient.Listener {
            override fun onRegistered() = add("registered")
            override fun onAvailabilityChanged(available: Boolean) = add("available:$available")
            override fun onPayload(payload: ByteArray) = add("payload:${payload.single()}")
            override fun onResponse(ok: Boolean, returnCode: Int) = add("response:$ok:$returnCode")
            override fun onStopped() = add("stopped")
            private fun add(event: String) {
                mainThreadCallbacks += Looper.myLooper() === Looper.getMainLooper()
                events += event
            }
        }
    }
}
