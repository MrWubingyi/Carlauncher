package com.example.vehicle.someip.android

import com.example.vehicle.someip.transport.NativeVehicleTransport

import org.junit.Assert.*
import org.junit.Test

/** JNI-facing transport passes bytes unchanged; validation is an adapter responsibility. */
class VSomeIpNativeTransportTest {
    private class Sink : NativeVehicleTransport.Listener {
        val payloads = mutableListOf<ByteArray?>()
        val availability = mutableListOf<Boolean>()
        val errors = mutableListOf<Throwable?>()
        override fun onPayload(payload: ByteArray?) { payloads += payload }
        override fun onAvailabilityChanged(available: Boolean) { availability += available }
        override fun onError(throwable: Throwable?) { errors += throwable }
    }

    @Test fun forwardsRawPayloadIncludingMalformedDataWithoutDecoding() {
        val transport = VSomeIpNativeTransport(null)
        val sink = Sink()
        transport.setListener(sink)
        val raw = byteArrayOf(0, 1, 2)
        transport.onVehicleEvent(raw)
        transport.onVehicleEvent(raw)
        transport.onVehicleEvent(null)
        assertEquals(3, sink.payloads.size)
        assertSame(raw, sink.payloads[0])
        assertSame(raw, sink.payloads[1])
        assertNull(sink.payloads[2])
        assertTrue(sink.errors.isEmpty())
    }

    @Test fun forwardsAvailabilityAndFailedResponse() {
        val transport = VSomeIpNativeTransport(null)
        val sink = Sink()
        transport.setListener(sink)
        transport.onAvailable(true)
        transport.onAvailable(false)
        transport.onResponse(true, 0)
        transport.onResponse(false, 7)
        assertEquals(listOf(true, false), sink.availability)
        assertEquals(1, sink.errors.size)
        assertTrue(sink.errors.single()!!.message!!.contains("7"))
    }

    @Test fun detachedListenerReceivesNothing() {
        val transport = VSomeIpNativeTransport(null)
        val sink = Sink()
        transport.setListener(sink)
        transport.setListener(null)
        transport.onVehicleEvent(byteArrayOf(1))
        transport.onAvailable(true)
        transport.onResponse(false, 1)
        assertTrue(sink.payloads.isEmpty())
        assertTrue(sink.availability.isEmpty())
        assertTrue(sink.errors.isEmpty())
    }

    @Test fun stoppedTransportRejectsLateCallbacks() {
        val transport = VSomeIpNativeTransport(null)
        val sink = Sink()
        transport.setListener(sink)
        transport.onStopped()
        transport.onVehicleEvent(byteArrayOf(1))
        transport.onAvailable(true)
        transport.onResponse(false, 1)
        assertEquals(listOf(false), sink.availability)
        assertTrue(sink.payloads.isEmpty())
        assertTrue(sink.errors.isEmpty())
    }
}