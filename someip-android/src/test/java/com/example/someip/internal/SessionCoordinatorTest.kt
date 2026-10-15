package com.example.someip.internal

import com.example.someip.SomeIpClient
import com.example.someip.SomeIpClientConfig
import com.example.someip.SomeIpService
import java.util.ArrayDeque
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionCoordinatorTest {
    private val backend = FakeBackend()
    private val dispatcher = QueuedDispatcher()
    private val coordinator = SessionCoordinator(backend, dispatcher)
    private val resources = FakeResources()
    private val config = SomeIpClientConfig(
        "/private/client.json",
        SomeIpService(0x1111, 0x2222, 0x8001, 1, 1, "test", 8, 8),
    )
    private val client = coordinator.create(config, resources)
    private val listener = RecordingListener()

    @Test
    fun unusedClientDoesNotLoadNativeLibrariesOrObserveNetwork() {
        assertFalse(client.isAvailable)
        assertFalse(client.send(byteArrayOf(1)))
        client.close()
        assertEquals(0, backend.loads)
        assertEquals(0, backend.stops)
        assertEquals(0, resources.acquisitions)
    }

    @Test
    fun onlyOneProcessOwnerMayStartSendOrStop() {
        val competitor = coordinator.create(config, resources)
        assertTrue(client.start(listener))
        assertFalse(competitor.start(RecordingListener()))
        assertFalse(competitor.isAvailable)
        assertFalse(competitor.send(byteArrayOf(1)))
        competitor.stop()
        assertEquals(0, backend.stops)
        assertTrue(client.isAvailable)
        client.stop()
        assertTrue(competitor.start(RecordingListener()))
        assertEquals(2, backend.starts)
    }

    @Test
    fun repeatedStartAndStopRetainOneSubscriptionAndOneStoppedCallback() {
        val ignoredListener = RecordingListener()
        assertTrue(client.start(listener))
        assertTrue(client.start(ignoredListener))
        coordinator.onNativeEvent(1, 0)
        dispatcher.drain()
        assertEquals(listOf("availability:true"), listener.events)
        assertTrue(ignoredListener.events.isEmpty())
        client.stop()
        client.close()
        dispatcher.drain()
        assertEquals(listOf("availability:true", "stopped"), listener.events)
        assertEquals(1, backend.starts)
        assertEquals(1, backend.stops)
        assertEquals(1, resources.acquisitions)
        assertEquals(1, resources.closes)
    }

    @Test
    fun failedNativeStartRollsBackAndAllowsAnotherOwner() {
        backend.acceptStart = false
        backend.onStart = { coordinator.onNativeEvent(0, 0) }
        assertFalse(client.start(listener))
        assertFalse(client.isAvailable)
        dispatcher.drain()
        assertTrue(listener.events.isEmpty())
        assertEquals(1, resources.closes)
        assertEquals(1, backend.stops)
        backend.acceptStart = true
        assertTrue(coordinator.create(config, resources).start(RecordingListener()))
    }

    @Test
    fun immediateDispatcherRetainsCallbacksUntilNativeStartSucceeds() {
        val immediateCoordinator = SessionCoordinator(backend, SessionCoordinator.Dispatcher { it() })
        val immediateClient = immediateCoordinator.create(config, resources)
        backend.onStart = {
            immediateCoordinator.onNativeEvent(0, 0)
            immediateCoordinator.onNativeEvent(1, 0)
            immediateCoordinator.onNativePayload(byteArrayOf(7))
            assertTrue(listener.events.isEmpty())
        }
        assertTrue(immediateClient.start(listener))
        assertEquals(listOf("registered", "availability:true", "payload"), listener.events)
    }

    @Test
    fun immediateDispatcherDiscardsBufferedCallbacksWhenNativeStartFails() {
        val immediateCoordinator = SessionCoordinator(backend, SessionCoordinator.Dispatcher { it() })
        val immediateClient = immediateCoordinator.create(config, resources)
        backend.acceptStart = false
        backend.onStart = {
            immediateCoordinator.onNativeEvent(0, 0)
            immediateCoordinator.onNativePayload(byteArrayOf(7))
        }
        assertFalse(immediateClient.start(listener))
        assertTrue(listener.events.isEmpty())
        assertEquals(1, resources.closes)
    }

    @Test
    fun libraryLoadFailureDoesNotAcquireResourcesOrRetainOwner() {
        backend.loadFailure = UnsatisfiedLinkError("missing library")
        assertThrows(UnsatisfiedLinkError::class.java) { client.start(listener) }
        assertEquals(0, resources.acquisitions)
        assertEquals(0, backend.stops)
        backend.loadFailure = null
        assertTrue(coordinator.create(config, resources).start(RecordingListener()))
    }

    @Test
    fun resourceAcquisitionFailureDoesNotStartNativeOrRetainOwner() {
        resources.failure = SecurityException("network denied")
        assertThrows(SecurityException::class.java) { client.start(listener) }
        assertEquals(0, backend.starts)
        assertEquals(0, backend.stops)
        resources.failure = null
        assertTrue(coordinator.create(config, resources).start(RecordingListener()))
    }

    @Test
    fun throwingNativeStartStillReleasesResourcesAndSession() {
        backend.onStart = { throw IllegalStateException("init failed") }
        assertThrows(IllegalStateException::class.java) { client.start(listener) }
        assertEquals(1, resources.closes)
        assertEquals(1, backend.stops)
        backend.onStart = {}
        assertTrue(client.start(listener))
    }

    @Test
    fun payloadsPassThroughWithoutVehicleSchemaAndRespectConfiguredRequestLimit() {
        assertTrue(client.start(listener))
        val binary = byteArrayOf(0, -1, -128, 1, 42, 0, 99, 127)
        assertTrue(client.send(binary))
        assertArrayEquals(binary, backend.sent.last())
        assertTrue(client.send(byteArrayOf()))
        assertArrayEquals(byteArrayOf(), backend.sent.last())
        assertFalse(client.send(ByteArray(9)))
        assertEquals(2, backend.sent.size)
        backend.acceptSend = false
        assertFalse(client.send(byteArrayOf(42)))
    }

    @Test
    fun callbacksAreDispatchedInOrderAndEventPayloadLimitIsEnforced() {
        assertTrue(client.start(listener))
        coordinator.onNativeEvent(0, 0)
        coordinator.onNativeEvent(1, 0)
        val bytes = byteArrayOf(0, -1, 7, -128, 0, 1, 2, 3)
        coordinator.onNativePayload(bytes)
        coordinator.onNativePayload(ByteArray(9))
        coordinator.onNativeEvent(3, 0)
        coordinator.onNativeEvent(4, 7)
        coordinator.onNativeEvent(2, 0)
        coordinator.onNativeEvent(999, 0)
        assertTrue(listener.events.isEmpty())
        dispatcher.drain()
        assertEquals(
            listOf("registered", "availability:true", "payload", "response:true:0", "response:false:7", "availability:false"),
            listener.events,
        )
        assertEquals(1, listener.payloads.size)
        assertArrayEquals(bytes, listener.payloads.single())
    }

    @Test
    fun sustainedPayloadQueueYieldsToOtherDispatcherTasksAndPreservesFifo() {
        assertTrue(client.start(listener))
        val payloadCount = 64
        repeat(payloadCount) { coordinator.onNativePayload(byteArrayOf(it.toByte())) }
        var payloadsBeforeOtherTask = -1
        dispatcher.post { payloadsBeforeOtherTask = listener.payloads.size }
        dispatcher.drain()
        assertTrue(payloadsBeforeOtherTask in 1 until payloadCount)
        assertEquals((0 until payloadCount).toList(), listener.payloads.map { it.single().toInt() })
    }

    @Test
    fun stopDropsQueuedAndLateNativeCallbacks() {
        assertTrue(client.start(listener))
        coordinator.onNativeEvent(1, 0)
        coordinator.onNativePayload(byteArrayOf(1))
        client.stop()
        coordinator.onNativeEvent(0, 0)
        coordinator.onNativePayload(byteArrayOf(2))
        dispatcher.drain()
        assertEquals(listOf("stopped"), listener.events)
    }

    @Test
    fun restartDropsOldPayloadAndStoppedEvenWhenListenerIsReused() {
        assertTrue(client.start(listener))
        coordinator.onNativePayload(byteArrayOf(1))
        client.stop()
        assertTrue(client.start(listener))
        coordinator.onNativePayload(byteArrayOf(2))
        dispatcher.drain()
        assertEquals(listOf("payload"), listener.events)
        assertArrayEquals(byteArrayOf(2), listener.payloads.single())
    }

    @Test
    fun nativeStoppedReleasesOwnershipAndResourcesOnDispatcher() {
        assertTrue(client.start(listener))
        coordinator.onNativeEvent(5, 0)
        assertEquals(0, backend.stops)
        dispatcher.drain()
        assertEquals(1, resources.closes)
        assertEquals(1, backend.stops)
        assertFalse(client.isAvailable)
        assertEquals(listOf("stopped"), listener.events)
        assertTrue(coordinator.create(config, resources).start(RecordingListener()))
    }

    @Test
    fun cleanupAllowsCallbackThreadsToFinishWithoutHoldingStateLock() {
        val executor = Executors.newSingleThreadExecutor()
        try {
            val callbackAndJoin = {
                executor.submit { coordinator.onNativeEvent(5, 0) }.get(2, TimeUnit.SECONDS)
                Unit
            }
            resources.onClose = callbackAndJoin
            backend.onStop = callbackAndJoin
            assertTrue(client.start(listener))
            client.stop()
            dispatcher.drain()
            assertEquals(listOf("stopped"), listener.events)
        } finally {
            executor.shutdownNow()
        }
    }

    @Test
    fun nativeStopRunsEvenWhenPlatformCleanupFails() {
        assertTrue(client.start(listener))
        resources.onClose = { throw IllegalStateException("unregister failed") }
        assertThrows(IllegalStateException::class.java) { client.close() }
        assertEquals(1, backend.stops)
        assertFalse(client.isAvailable)
        dispatcher.drain()
        assertEquals(listOf("stopped"), listener.events)
    }

    private class FakeBackend : SessionCoordinator.Backend {
        var loads = 0
        var starts = 0
        var stops = 0
        var acceptStart = true
        var acceptSend = true
        var loadFailure: LinkageError? = null
        var onStart: () -> Unit = {}
        var onStop: () -> Unit = {}
        val sent = mutableListOf<ByteArray>()
        override fun ensureLoaded() {
            loads++
            loadFailure?.let { throw it }
        }
        override fun start(config: SomeIpClientConfig): Boolean {
            starts++
            onStart()
            return acceptStart
        }
        override fun stop() {
            stops++
            onStop()
        }
        override fun send(payload: ByteArray): Boolean {
            sent += payload.copyOf()
            return acceptSend
        }
        override fun isAvailable(): Boolean = true
    }

    private class FakeResources : SessionCoordinator.Resources {
        var acquisitions = 0
        var closes = 0
        var failure: Exception? = null
        var onClose: () -> Unit = {}
        override fun acquire(config: SomeIpClientConfig): AutoCloseable {
            acquisitions++
            failure?.let { throw it }
            return AutoCloseable {
                closes++
                onClose()
            }
        }
    }

    private class QueuedDispatcher : SessionCoordinator.Dispatcher {
        private val callbacks = ArrayDeque<() -> Unit>()
        override fun post(callback: () -> Unit) {
            callbacks.add(callback)
        }
        fun drain() {
            while (callbacks.isNotEmpty()) callbacks.removeFirst().invoke()
        }
    }

    private class RecordingListener : SomeIpClient.Listener {
        val events = mutableListOf<String>()
        val payloads = mutableListOf<ByteArray>()
        override fun onRegistered() { events += "registered" }
        override fun onAvailabilityChanged(available: Boolean) { events += "availability:$available" }
        override fun onPayload(payload: ByteArray) {
            events += "payload"
            payloads += payload
        }
        override fun onResponse(ok: Boolean, returnCode: Int) { events += "response:$ok:$returnCode" }
        override fun onStopped() { events += "stopped" }
    }
}
