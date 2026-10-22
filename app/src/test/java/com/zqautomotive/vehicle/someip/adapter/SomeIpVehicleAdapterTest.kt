package com.zqautomotive.vehicle.someip.adapter

import com.zqautomotive.launcher.data.someip.VehicleSignalMapper
import com.zqautomotive.vehicle.someip.transport.NativeVehicleTransport

import com.zqautomotive.vehicle.model.VehicleSignals
import com.zqautomotive.vehicle.model.SignalValidity
import com.zqautomotive.vehicle.someip.codec.SomeIpEventCodec
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class SomeIpVehicleAdapterTest {
    private class Transport : NativeVehicleTransport {
        var sink: NativeVehicleTransport.Listener? = null
        var starts = 0
        var stops = 0
        var succeeds = true
        var failure: Error? = null
        override val isAvailable = true
        override fun setListener(listener: NativeVehicleTransport.Listener?) { sink = listener }
        override fun start(): Boolean { starts++; failure?.let { throw it }; return succeeds }
        override fun stop() { stops++ }
    }
    private class Sink : SomeIpVehicleAdapter.Listener {
        val signals = mutableListOf<VehicleSignals>()
        val statuses = mutableListOf<SomeIpVehicleAdapter.Status>()
        val errors = mutableListOf<Throwable>()
        override fun onSignals(signals: VehicleSignals) { this.signals += signals }
        override fun onStatus(status: SomeIpVehicleAdapter.Status) { statuses += status }
        override fun onError(error: Throwable) { errors += error }
    }
    private val transport = Transport()
    private val monitor = SomeipConnectionMonitor()
    private val sink = Sink()
    private var now = 0L
    private val adapter = SomeIpVehicleAdapter(transport, monitor) { now }

    private fun connect() {
        assertTrue(adapter.start(sink))
        transport.sink!!.onAvailabilityChanged(true)
    }

    private fun payload(seq: Long = 1, timestamp: Long = 1000, speed: Int = 60): ByteArray =
        JSONObject("""{"version":1,"seq":1,"timestampMs":1000,"speedKph":60,
            "rpm":2500,"soc":90,"gear":3,"turnSignal":0,"warning":0,"validity":0,
            "dataStatus":0,"parkingBrake":false,"doorLock":true,"beltWarning":false,
            "headlightsState":0,"highBeamLightsState":0,"engineCoolantTemp":88,"evBatteryLevel":90}""")
            .put("seq", seq).put("timestampMs", timestamp).put("speedKph", speed)
            .toString().toByteArray(Charsets.UTF_8)

    @Test fun rejectsOlderTimestampDuplicateAndLowerSequenceButAllowsGaps() {
        connect()
        transport.sink!!.onPayload(payload(10, 2000))
        transport.sink!!.onPayload(payload(99, 1999))
        transport.sink!!.onPayload(payload(10, 2000))
        transport.sink!!.onPayload(payload(9, 2000))
        transport.sink!!.onPayload(payload(14, 2000, 83))
        assertEquals(listOf(10L, 14L), sink.signals.map { it.sequence })
        assertEquals(83, VehicleSignalMapper.toVehicleState(sink.signals.last()).vehSpeedKph)
        assertTrue(sink.errors.isEmpty())
    }

    @Test fun publisherRestartWithNewTimestampAndRediscoveryResetOrdering() {
        connect()
        transport.sink!!.onPayload(payload(9000, 2000))
        transport.sink!!.onPayload(payload(1, 2100))
        transport.sink!!.onAvailabilityChanged(false)
        transport.sink!!.onPayload(payload(2, 2200))
        transport.sink!!.onAvailabilityChanged(true)
        transport.sink!!.onPayload(payload(1, 1000))
        assertEquals(listOf(2000L, 2100L, 1000L), sink.signals.map { it.sourceTimestampMs })
    }

    @Test fun duplicateAndMalformedFramesDoNotRefreshDeadlineAndNewEventRecovers() {
        connect()
        now = 100
        transport.sink!!.onPayload(payload())
        now = 3099
        transport.sink!!.onPayload(payload())
        transport.sink!!.onPayload(byteArrayOf(0))
        adapter.checkTimeout()
        assertEquals(1, sink.errors.size)
        now = 3100
        adapter.checkTimeout()
        assertEquals(SomeIpVehicleAdapter.Status.TIMED_OUT, adapter.status)
        val count = sink.statuses.size
        adapter.checkTimeout()
        assertEquals(count, sink.statuses.size)
        transport.sink!!.onPayload(payload(2, 1100))
        assertEquals(SomeIpVehicleAdapter.Status.AVAILABLE, adapter.status)
        assertNull(monitor.snapshot().returnCode)
    }

    @Test fun noEventTimeoutAndDuplicateAvailabilityUseOriginalDeadline() {
        connect()
        now = 2999
        transport.sink!!.onAvailabilityChanged(true)
        adapter.checkTimeout()
        assertEquals(SomeIpVehicleAdapter.Status.CONNECTING, adapter.status)
        now = 3000
        adapter.checkTimeout()
        assertEquals(SomeIpVehicleAdapter.Status.TIMED_OUT, adapter.status)
    }

    @Test fun stopAndRestartRejectCallbacksFromPreviousSession() {
        connect()
        val old = transport.sink!!
        old.onPayload(payload())
        adapter.stop()
        adapter.stop()
        connect()
        old.onAvailabilityChanged(true)
        old.onPayload(payload(2, 2000))
        old.onError(IllegalStateException("late"))
        transport.sink!!.onPayload(payload(1, 500))
        assertEquals(listOf(1000L, 500L), sink.signals.map { it.sourceTimestampMs })
        assertTrue(sink.errors.isEmpty())
        assertEquals(1, transport.stops)
    }

    @Test fun failedStartCleansUpAndCanBeRetried() {
        transport.succeeds = false
        assertFalse(adapter.start(sink))
        assertFalse(adapter.isRunning)
        assertNull(transport.sink)
        assertEquals(1, transport.stops)
        assertEquals(SomeipConnectionMonitor.State.START_FAILED, monitor.snapshot().state)
        transport.succeeds = true
        connect()
        assertTrue(adapter.start(sink))
        assertEquals(2, transport.starts)
        transport.sink!!.onPayload(payload())
        assertEquals(1, sink.signals.size)
    }

    @Test fun nativeLoadFailureIsReportedAsStartFailure() {
        transport.failure = UnsatisfiedLinkError("missing test library")
        assertFalse(adapter.start(sink))
        assertFalse(adapter.isRunning)
        assertEquals(SomeIpVehicleAdapter.Status.ERROR, adapter.status)
        assertEquals(1, sink.errors.size)
        assertNull(transport.sink)
    }

    @Test fun semanticUnitsAndInvalidValuesSurviveUntilLauncherMapping() {
        val signals = SomeIpEventCodec.decode(payload(speed = 108))
        assertEquals(30.0, signals.speedMetersPerSecond, 0.000001)
        assertEquals(108, VehicleSignalMapper.toVehicleState(signals).vehSpeedKph)
        val invalid = SomeIpEventCodec.decode(payload(speed = 255))
        assertEquals(SignalValidity.INVALID_SPEED, invalid.validity)
        assertEquals(255, VehicleSignalMapper.toVehicleState(invalid).vehSpeedKph)
    }

    @Test fun allIntegerSpeedsRoundTripAcrossUnitConversion() {
        for (speed in listOf(Int.MIN_VALUE, -1, 0, 1, 17, 83, 108, 199, 200, 255, Int.MAX_VALUE)) {
            assertEquals(speed, VehicleSignalMapper.toVehicleState(SomeIpEventCodec.decode(payload(speed = speed))).vehSpeedKph)
        }
    }
}
