package com.example.vehicle.someip.adapter

import com.example.vehicle.control.*
import com.example.vehicle.model.VehicleSignals
import com.example.vehicle.someip.transport.NativeVehicleTransport
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

/** Real adapter, codec and command policy with a byte-level SOME/IP peer boundary. */
class VehicleGatewayTest {
    private var now = 100L
    private var received: VehicleSignals? = null
    private val sent = mutableListOf<JSONObject>()
    private var submit = true
    private var sendFailure = false
    private inner class Transport : NativeVehicleTransport {
        var sink: NativeVehicleTransport.Listener? = null
        override val isAvailable = true
        override fun start() = true
        override fun stop() {}
        override fun setListener(listener: NativeVehicleTransport.Listener?) { this.sink = listener }
        override fun send(payload: ByteArray): Boolean {
            if (sendFailure) throw IllegalStateException("transport failure")
            sent += JSONObject(String(payload)); return submit
        }
    }
    private val transport = Transport()
    private val adapter = SomeIpVehicleAdapter(transport, SomeipConnectionMonitor()) { now }
    private fun connect() {
        adapter.start(object : SomeIpVehicleAdapter.Listener {
            override fun onSignals(signals: VehicleSignals) { received = signals }
            override fun onStatus(status: SomeIpVehicleAdapter.Status) {}
            override fun onError(error: Throwable) {}
        })
        transport.sink!!.onAvailabilityChanged(true)
    }
    private fun emit(frame: JSONObject) = transport.sink!!.onPayload(frame.toString().toByteArray())
    private fun vehicle(speed: Int = 0, gear: Int = 0, brake: Boolean = true, seq: Int = 1) = JSONObject(
        """{"version":1,"seq":1,"timestampMs":1000,"speedKph":0,
        "rpm":0,"soc":80,"gear":0,"turnSignal":0,"warning":0,"validity":0,
        "dataStatus":0,"parkingBrake":true,"doorLock":true,"beltWarning":false,
        "headlightsState":0,"highBeamLightsState":0,"engineCoolantTemp":30,"evBatteryLevel":80}"""
    ).put("speedKph", speed).put("gear", gear).put("parkingBrake", brake).put("seq", seq)
    private fun challenge() = sent.first { it.getString("type") == "getCapabilities" }.getString("challenge")
    private fun caps(seq: Int = 1, token: String = challenge()) = JSONObject()
        .put("version", 1).put("type", "capabilities").put("challenge", token)
        .put("seq", seq).put("leaseMs", 3000).put("actions", org.json.JSONArray(listOf("lock", "unlock", "find", "brake", "headlights")))
    private fun ready() { connect(); emit(vehicle()); emit(caps()) }
    private fun result(status: String, id: String = adapter.controlSnapshot().requestId!!,
        action: String = "unlock") = JSONObject().put("version", 1).put("type", "controlResult")
        .put("challenge", challenge()).put("requestId", id).put("action", action).put("status", status)

    @Test fun dataDecodesAndUnknownActionsNeverBecomeCapabilities() {
        ready()
        assertEquals(80, received!!.stateOfChargePercent)
        assertEquals(VehicleAction.entries.toSet(), adapter.controlSnapshot().enabledActions)
        assertTrue(adapter.requestControl(VehicleAction.UNLOCK))
        assertEquals("unlock", sent.last().getString("action"))
        assertEquals(ControlOutcome.SUBMITTED, adapter.controlSnapshot().outcome)
        assertTrue(received!!.doorLocked) // No optimistic hardware mutation.
        emit(result("accepted"))
        assertEquals(ControlOutcome.ACCEPTED, adapter.controlSnapshot().outcome)
        emit(result("succeeded"))
        assertEquals(ControlOutcome.SUCCEEDED, adapter.controlSnapshot().outcome)
        assertTrue(received!!.doorLocked)
    }

    @Test fun noCapabilitiesNoDataMotionGearBrakeAndInvalidDataBlockCommands() {
        connect()
        assertFalse(adapter.requestControl(VehicleAction.LOCK))
        emit(caps())
        assertFalse(adapter.requestControl(VehicleAction.LOCK))
        emit(vehicle(speed = 1)); assertFalse(adapter.requestControl(VehicleAction.LOCK))
        emit(vehicle(gear = 3, seq = 2)); assertFalse(adapter.requestControl(VehicleAction.LOCK))
        emit(vehicle(brake = false, seq = 3)); assertFalse(adapter.requestControl(VehicleAction.LOCK))
        emit(vehicle(seq = 4).put("validity", 2)); assertFalse(adapter.requestControl(VehicleAction.LOCK))
        emit(vehicle(seq = 5).put("dataStatus", 2)); assertFalse(adapter.requestControl(VehicleAction.LOCK))
        assertEquals(1, sent.size) // Discovery only.
    }

    @Test fun replayCannotRenewLeaseAndControlFramesDoNotRefreshVehicleHealth() {
        ready()
        now = 3100
        emit(caps())
        assertTrue(adapter.controlSnapshot().enabledActions.isEmpty())
        emit(caps(2))
        assertTrue(adapter.controlSnapshot().enabledActions.isEmpty()) // Data still stale.
        adapter.checkTimeout()
        assertEquals(SomeIpVehicleAdapter.Status.TIMED_OUT, adapter.status)
        emit(vehicle(seq = 2))
        assertTrue(adapter.controlSnapshot().enabledActions.isNotEmpty())
    }

    @Test fun onlyMatchingResultsCompleteOnePendingCommand() {
        ready(); assertTrue(adapter.requestControl(VehicleAction.UNLOCK))
        val id = adapter.controlSnapshot().requestId!!
        assertFalse(adapter.requestControl(VehicleAction.FIND))
        emit(result("succeeded", "wrong")); emit(result("succeeded", id, "lock"))
        assertEquals(ControlOutcome.SUBMITTED, adapter.controlSnapshot().outcome)
        emit(result("failed", id))
        emit(result("succeeded", id))
        assertEquals(ControlOutcome.FAILED, adapter.controlSnapshot().outcome)
    }

    @Test fun timeoutAndDisconnectBecomeUnknownAndNeverResendActuation() {
        ready(); adapter.requestControl(VehicleAction.UNLOCK)
        val id = adapter.controlSnapshot().requestId!!
        now = 5100; adapter.checkTimeout()
        emit(result("succeeded", id))
        assertEquals(ControlOutcome.UNKNOWN, adapter.controlSnapshot().outcome)
        assertEquals(1, sent.count { it.getString("type") == "control" })
        transport.sink!!.onAvailabilityChanged(false)
        transport.sink!!.onAvailabilityChanged(true)
        emit(vehicle()); emit(caps(token = challenge())) // Previous challenge rejected.
        assertTrue(adapter.controlSnapshot().enabledActions.isEmpty())
    }

    @Test fun invalidNumbersSessionAndFailedSubmissionDoNotGrantOrFakeSuccess() {
        connect(); emit(vehicle())
        emit(caps().put("leaseMs", 3000.9)); assertTrue(adapter.controlSnapshot().enabledActions.isEmpty())
        emit(caps().put("version", 1.9)); assertTrue(adapter.controlSnapshot().enabledActions.isEmpty())
        emit(caps(token = "old")); assertTrue(adapter.controlSnapshot().enabledActions.isEmpty())
        emit(caps()); submit = false
        assertFalse(adapter.requestControl(VehicleAction.FIND))
        assertEquals(ControlOutcome.NOT_SENT, adapter.controlSnapshot().outcome)
    }

    @Test fun stoppedGenerationCannotRestoreControlOrData() {
        ready(); adapter.requestControl(VehicleAction.UNLOCK)
        val old = transport.sink!!
        adapter.stop()
        old.onAvailabilityChanged(true); old.onPayload(vehicle(seq = 2).toString().toByteArray())
        assertFalse(adapter.requestControl(VehicleAction.LOCK))
        assertTrue(adapter.controlSnapshot().enabledActions.isEmpty())
        assertEquals(ControlOutcome.UNKNOWN, adapter.controlSnapshot().outcome)
    }
    @Test fun acceptedResultCannotExtendDeadlineAndRevocationDisablesFutureCommands() {
        ready(); adapter.requestControl(VehicleAction.UNLOCK)
        now = 5000; emit(result("accepted"))
        assertEquals(ControlOutcome.ACCEPTED, adapter.controlSnapshot().outcome)
        now = 5100; adapter.checkTimeout()
        assertEquals(ControlOutcome.UNKNOWN, adapter.controlSnapshot().outcome)
        emit(vehicle(seq = 2))
        emit(caps(2).put("actions", org.json.JSONArray()))
        assertFalse(adapter.requestControl(VehicleAction.FIND))
    }

    @Test fun sendExceptionIsUnknownAndRepeatedQueriesNeverRepeatAnAction() {
        ready(); sendFailure = true
        assertFalse(adapter.requestControl(VehicleAction.UNLOCK))
        assertEquals(ControlOutcome.UNKNOWN, adapter.controlSnapshot().outcome)
        now = 6100; adapter.checkTimeout()
        assertEquals(ControlOutcome.UNKNOWN, adapter.controlSnapshot().outcome)
        sendFailure = false; adapter.checkTimeout()
        assertEquals(0, sent.count { it.getString("type") == "control" })
    }

    @Test fun freshNewSessionMustReceiveNewCapabilitiesAndVehicleData() {
        ready()
        val old = challenge()
        transport.sink!!.onAvailabilityChanged(false)
        transport.sink!!.onAvailabilityChanged(true)
        val newChallenge = sent.last().getString("challenge")
        assertNotEquals(old, newChallenge)
        emit(caps(token = old))
        emit(vehicle())
        assertTrue(adapter.controlSnapshot().enabledActions.isEmpty())
        emit(caps(token = newChallenge))
        assertTrue(adapter.requestControl(VehicleAction.LOCK))
    }

    @Test fun discoveryFailureCannotPreventPendingRequestTimeout() {
        ready(); assertTrue(adapter.requestControl(VehicleAction.UNLOCK))
        sendFailure = true; now = 5100
        adapter.checkTimeout()
        assertEquals(ControlOutcome.UNKNOWN, adapter.controlSnapshot().outcome)
    }

}
