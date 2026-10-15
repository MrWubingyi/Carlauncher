package com.example.vehicle.someip.android

import com.example.someip.SomeIpClient
import com.example.vehicle.someip.transport.NativeVehicleTransport
import org.junit.Assert.*
import org.junit.Test

/** Tests the app adapter against the public client contract without Android or JNI. */
class VSomeIpNativeTransportTest {
    private class Sink : NativeVehicleTransport.Listener {
        val payloads = mutableListOf<ByteArray?>()
        val availability = mutableListOf<Boolean>()
        val errors = mutableListOf<Throwable?>()
        override fun onPayload(payload: ByteArray?) { payloads += payload }
        override fun onAvailabilityChanged(available: Boolean) { availability += available }
        override fun onError(throwable: Throwable?) { errors += throwable }
    }

    private class Client : SomeIpClient {
        override var isAvailable = false
        lateinit var callbacks: SomeIpClient.Listener
        var starts = 0
        var stops = 0
        var startResult = true
        var startFailure: Throwable? = null
        var stopFailure: Throwable? = null
        override fun start(listener: SomeIpClient.Listener): Boolean {
            callbacks = listener
            starts++
            startFailure?.let { throw it }
            return startResult
        }
        override fun stop() {
            stops++
            isAvailable = false
            callbacks.onStopped()
            stopFailure?.let { throw it }
        }
        override fun send(payload: ByteArray) = isAvailable
    }

    private val client = Client()
    private val sink = Sink()
    private val transport = VSomeIpNativeTransport { client }.apply { setListener(sink) }

    @Test fun forwardsRawPayloadIncludingMalformedDataWithoutDecoding() {
        assertTrue(transport.start())
        val raw = byteArrayOf(0, 1, 2)
        val empty = byteArrayOf()
        client.callbacks.onPayload(raw)
        client.callbacks.onPayload(raw)
        client.callbacks.onPayload(empty)
        assertEquals(3, sink.payloads.size)
        assertSame(raw, sink.payloads[0])
        assertSame(raw, sink.payloads[1])
        assertSame(empty, sink.payloads[2])
        assertTrue(sink.errors.isEmpty())
    }

    @Test fun forwardsAvailabilityAndFailedResponse() {
        assertTrue(transport.start())
        client.callbacks.onAvailabilityChanged(true)
        client.callbacks.onAvailabilityChanged(false)
        client.callbacks.onResponse(true, 0)
        client.callbacks.onResponse(false, 7)
        assertEquals(listOf(true, false), sink.availability)
        assertEquals(1, sink.errors.size)
        assertTrue(sink.errors.single()!!.message!!.contains("7"))
    }

    @Test fun detachedListenerReceivesNothing() {
        assertTrue(transport.start())
        transport.setListener(null)
        emitCallbacks(client.callbacks)
        assertTrue(sink.payloads.isEmpty())
        assertTrue(sink.availability.isEmpty())
        assertTrue(sink.errors.isEmpty())
    }

    @Test fun spontaneousStopMarksUnavailableAndRejectsLateCallbacks() {
        assertTrue(transport.start())
        client.isAvailable = true
        client.callbacks.onStopped()
        emitCallbacks(client.callbacks)
        client.callbacks.onStopped()
        assertFalse(transport.isAvailable)
        assertEquals(listOf(false), sink.availability)
        assertTrue(sink.payloads.isEmpty())
        assertTrue(sink.errors.isEmpty())
    }

    @Test fun repeatedStartAndStopOwnOneClientSession() {
        assertFalse(transport.isAvailable)
        assertTrue(transport.start())
        assertTrue(transport.start())
        assertEquals(1, client.starts)
        client.isAvailable = true
        assertTrue(transport.isAvailable)
        transport.stop()
        transport.stop()
        emitCallbacks(client.callbacks)
        assertEquals(1, client.stops)
        assertFalse(transport.isAvailable)
        assertTrue(sink.availability.isEmpty())
        assertTrue(sink.payloads.isEmpty())
        assertTrue(sink.errors.isEmpty())
    }

    @Test fun restartedSessionIgnoresEveryCallbackFromPreviousClient() {
        val first = Client()
        val second = Client()
        val clients = ArrayDeque(listOf(first, second))
        val restarting = VSomeIpNativeTransport { clients.removeFirst() }.apply { setListener(sink) }
        assertTrue(restarting.start())
        restarting.stop()
        assertTrue(restarting.start())
        emitCallbacks(first.callbacks)
        first.callbacks.onStopped()
        second.isAvailable = true
        second.callbacks.onAvailabilityChanged(true)
        second.callbacks.onPayload(byteArrayOf(2))
        assertTrue(restarting.isAvailable)
        assertEquals(listOf(true), sink.availability)
        assertArrayEquals(byteArrayOf(2), sink.payloads.single())
        assertTrue(sink.errors.isEmpty())
    }

    @Test fun failedStartReleasesClientAndDiscardsItsCallbacks() {
        client.startResult = false
        assertFalse(transport.start())
        emitCallbacks(client.callbacks)
        assertEquals(1, client.stops)
        assertFalse(transport.isAvailable)
        assertTrue(sink.availability.isEmpty())
        assertTrue(sink.payloads.isEmpty())
        assertTrue(sink.errors.isEmpty())
        client.startResult = true
        assertTrue(transport.start())
        assertEquals(2, client.starts)
    }

    @Test fun startExceptionReportsErrorAndReleasesPartialSession() {
        val failure = IllegalStateException("configuration failed")
        client.startFailure = failure
        assertFalse(transport.start())
        emitCallbacks(client.callbacks)
        assertSame(failure, sink.errors.single())
        assertEquals(1, client.stops)
        assertTrue(sink.availability.isEmpty())
        assertTrue(sink.payloads.isEmpty())
    }

    @Test fun factoryExceptionReportsErrorWithoutCreatingSession() {
        val failure = IllegalStateException("no usable IPv4 address")
        val failing = VSomeIpNativeTransport { throw failure }.apply { setListener(sink) }
        assertFalse(failing.start())
        assertFalse(failing.isAvailable)
        assertSame(failure, sink.errors.single())
        failing.stop()
    }

    @Test fun nativeLinkageFailureReleasesClientAndPropagatesToService() {
        val failure = UnsatisfiedLinkError("missing someip_jni")
        client.startFailure = failure
        val thrown = assertThrows(UnsatisfiedLinkError::class.java) { transport.start() }
        assertSame(failure, thrown)
        assertEquals(1, client.stops)
        assertFalse(transport.isAvailable)
    }

    @Test fun cleanupFailureStillInvalidatesTheOldSession() {
        val failure = IllegalStateException("cleanup failed")
        assertTrue(transport.start())
        client.stopFailure = failure
        assertSame(failure, assertThrows(IllegalStateException::class.java) { transport.stop() })
        emitCallbacks(client.callbacks)
        transport.stop()
        assertEquals(1, client.stops)
        assertFalse(transport.isAvailable)
        assertTrue(sink.availability.isEmpty())
        assertTrue(sink.payloads.isEmpty())
        assertTrue(sink.errors.isEmpty())
    }

    private fun emitCallbacks(callbacks: SomeIpClient.Listener) {
        callbacks.onPayload(byteArrayOf(1))
        callbacks.onAvailabilityChanged(true)
        callbacks.onResponse(false, 1)
    }
}
