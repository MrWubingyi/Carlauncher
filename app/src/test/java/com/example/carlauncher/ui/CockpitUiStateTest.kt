package com.example.carlauncher.ui

import com.example.carlauncher.data.DataSourceStatus
import com.example.carlauncher.model.DataValidity
import com.example.carlauncher.model.VehicleState
import com.example.carlauncher.someip.SomeipConnectionMonitor
import org.junit.Assert.*
import org.junit.Test

open class CockpitUiStateTest {
    private fun ui(monitor: SomeipConnectionMonitor?, validity: DataValidity?): CockpitUiState? =
        CockpitUiState(
            VehicleState.Builder().build(),
            DataSourceStatus.CONNECTED,
            true,
            monitor!!.snapshot(),
            validity,
        )

    @Test
    open fun initialAllowsStartAndHasNoVehicleData() {
        val state = CockpitUiState.initial()
        assertFalse(state!!.isServiceBound)
        assertFalse(state!!.hasVehicleData())
        assertEquals(DataSourceStatus.STOPPED, state!!.dataSourceStatus)
        assertEquals(CockpitConnectionState.DISCONNECTED, state!!.state)
        assertEquals("SERVICE UNBOUND", state!!.connectionLabel)
        assertEquals("SOME/IP: STOPPED", state!!.transportLabel)
        assertEquals("START RECEIVE", state!!.actionLabel)
        assertTrue(state!!.isActionEnabled)
        assertFalse(state!!.isStopAction())
    }

    @Test
    open fun availabilityAloneDoesNotMeanOnlineAndWaitingCanBeStopped() {
        val monitor = SomeipConnectionMonitor()
        monitor.start()
        assertEquals(CockpitConnectionState.CONNECTING, ui(monitor, DataValidity.VALID)!!.state)
        monitor.onAvailability(true, 0)
        val state = ui(monitor, DataValidity.VALID)
        assertEquals(CockpitConnectionState.RECOVERING, state!!.state)
        assertEquals("SOME/IP WAITING EVENT", state!!.connectionLabel)
        assertEquals("Connection: OFFLINE", state!!.connectionState)
        assertEquals("STOP RECEIVE", state!!.actionLabel)
        assertTrue(state!!.isActionEnabled)
        assertTrue(state!!.isStopAction())
    }

    @Test
    open fun successfulResponseIsOnlineButLocalInvalidityRemainsVisible() {
        val monitor = SomeipConnectionMonitor()
        monitor.start()
        monitor.onAvailability(true, 0)
        monitor.onResponse(true, 0, 10)
        val state = ui(monitor, DataValidity.VALID)
        assertEquals(CockpitConnectionState.ONLINE, state!!.state)
        assertEquals("SOME/IP ONLINE", state!!.connectionLabel)
        assertEquals("SOME/IP: AVAILABLE | ONLINE | EVENT 0x8001", state!!.transportLabel)
        assertEquals("Connection: ONLINE", state!!.connectionState)
        assertTrue(state!!.hasVehicleData())
        for (validity in
            arrayOf<DataValidity?>(
                DataValidity.INVALID_SPEED,
                DataValidity.STALE,
                DataValidity.INCOMPLETE,
            )) {
            val invalid = ui(monitor, validity)
            assertEquals(CockpitConnectionState.INVALID_DATA, invalid!!.state)
            assertEquals("SOME/IP ONLINE / INVALID DATA", invalid!!.connectionLabel)
            assertEquals("Connection: ONLINE", invalid!!.connectionState)
        }
    }

    @Test
    open fun errorAndTimeoutCannotDisplayPreviousSuccess() {
        val monitor = SomeipConnectionMonitor()
        monitor.start()
        monitor.onAvailability(true, 0)
        monitor.onResponse(true, 0, 1)
        monitor.onResponse(false, 1, 100)
        val error = ui(monitor, DataValidity.VALID)
        assertEquals(CockpitConnectionState.INVALID_DATA, error!!.state)
        assertEquals("SOME/IP RESPONSE_ERROR", error!!.connectionLabel)
        assertTrue(error!!.transportLabel!!.contains("EVENT 0x8001"))
        assertEquals("Connection: OFFLINE", error!!.connectionState)
        monitor.checkTimeout(3100)
        val timeout = ui(monitor, DataValidity.VALID)
        assertEquals("SOME/IP EVENT TIMEOUT", timeout!!.connectionLabel)
        assertEquals(CockpitConnectionState.DISCONNECTED, timeout!!.state)
        assertTrue(timeout!!.isStopAction())
        // Previously emitted snapshots are immutable.
        assertEquals("SOME/IP RESPONSE_ERROR", error!!.connectionLabel)
    }

    @Test
    open fun unavailableAndStartFailureAreDistinctAndCanBeStopped() {
        val monitor = SomeipConnectionMonitor()
        monitor.start()
        monitor.onAvailability(false, 0)
        assertEquals("SOME/IP UNAVAILABLE", ui(monitor, DataValidity.VALID)!!.connectionLabel)
        monitor.startFailed()
        val failed = ui(monitor, DataValidity.VALID)
        assertEquals("SOME/IP START_FAILED", failed!!.connectionLabel)
        assertTrue(failed!!.isActionEnabled)
        assertTrue(failed!!.isStopAction())
    }
}
