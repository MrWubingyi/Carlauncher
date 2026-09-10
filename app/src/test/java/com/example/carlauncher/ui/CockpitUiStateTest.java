package com.example.carlauncher.ui;

import com.example.carlauncher.data.DataSourceStatus;
import com.example.carlauncher.model.DataValidity;
import com.example.carlauncher.model.VehicleState;
import com.example.carlauncher.someip.SomeipConnectionMonitor;
import org.junit.Test;
import static org.junit.Assert.*;

public class CockpitUiStateTest {
    private CockpitUiState ui(SomeipConnectionMonitor monitor, DataValidity validity) {
        return new CockpitUiState(new VehicleState.Builder().build(),
                DataSourceStatus.CONNECTED, true, monitor.snapshot(), validity);
    }

    @Test public void initialAllowsStartAndHasNoVehicleData() {
        CockpitUiState state = CockpitUiState.initial();
        assertFalse(state.isServiceBound());
        assertFalse(state.hasVehicleData());
        assertEquals(DataSourceStatus.STOPPED, state.getDataSourceStatus());
        assertEquals(CockpitConnectionState.DISCONNECTED, state.getState());
        assertEquals("SERVICE UNBOUND", state.getConnectionLabel());
        assertEquals("SOME/IP: STOPPED", state.getTransportLabel());
        assertEquals("START SEND", state.getActionLabel());
        assertTrue(state.isActionEnabled());
        assertFalse(state.isStopAction());
    }

    @Test public void availabilityAloneDoesNotMeanOnlineAndWaitingCanBeStopped() {
        SomeipConnectionMonitor monitor = new SomeipConnectionMonitor();
        monitor.start();
        assertEquals(CockpitConnectionState.CONNECTING, ui(monitor, DataValidity.VALID).getState());
        monitor.onAvailability(true, 0);
        CockpitUiState state = ui(monitor, DataValidity.VALID);
        assertEquals(CockpitConnectionState.RECOVERING, state.getState());
        assertEquals("SOME/IP WAITING_RESPONSE", state.getConnectionLabel());
        assertEquals("Connection: OFFLINE", state.getConnectionState());
        assertEquals("STOP SEND", state.getActionLabel());
        assertTrue(state.isActionEnabled());
        assertTrue(state.isStopAction());
    }

    @Test public void successfulResponseIsOnlineButLocalInvalidityRemainsVisible() {
        SomeipConnectionMonitor monitor = new SomeipConnectionMonitor();
        monitor.start();
        monitor.onAvailability(true, 0);
        monitor.onResponse(true, 0, 10);
        CockpitUiState state = ui(monitor, DataValidity.VALID);
        assertEquals(CockpitConnectionState.ONLINE, state.getState());
        assertEquals("SOME/IP ONLINE", state.getConnectionLabel());
        assertEquals("SOME/IP: AVAILABLE | ONLINE | Last RC: 0x00", state.getTransportLabel());
        assertEquals("Connection: ONLINE", state.getConnectionState());
        assertTrue(state.hasVehicleData());
        for (DataValidity validity : new DataValidity[]{DataValidity.INVALID_SPEED,
                DataValidity.STALE, DataValidity.INCOMPLETE}) {
            CockpitUiState invalid = ui(monitor, validity);
            assertEquals(CockpitConnectionState.INVALID_DATA, invalid.getState());
            assertEquals("SOME/IP ONLINE / INVALID DATA", invalid.getConnectionLabel());
            assertEquals("Connection: ONLINE", invalid.getConnectionState());
        }
    }

    @Test public void errorAndTimeoutCannotDisplayPreviousSuccess() {
        SomeipConnectionMonitor monitor = new SomeipConnectionMonitor();
        monitor.start();
        monitor.onAvailability(true, 0);
        monitor.onResponse(true, 0, 1);
        monitor.onResponse(false, 1, 100);
        CockpitUiState error = ui(monitor, DataValidity.VALID);
        assertEquals(CockpitConnectionState.INVALID_DATA, error.getState());
        assertEquals("SOME/IP RESPONSE_ERROR", error.getConnectionLabel());
        assertTrue(error.getTransportLabel().contains("Last RC: 0x01"));
        assertEquals("Connection: OFFLINE", error.getConnectionState());
        monitor.checkTimeout(3100);
        CockpitUiState timeout = ui(monitor, DataValidity.VALID);
        assertEquals("SOME/IP RESPONSE_TIMEOUT", timeout.getConnectionLabel());
        assertEquals(CockpitConnectionState.DISCONNECTED, timeout.getState());
        assertTrue(timeout.isStopAction());
        // Previously emitted snapshots are immutable.
        assertEquals("SOME/IP RESPONSE_ERROR", error.getConnectionLabel());
    }

    @Test public void unavailableAndStartFailureAreDistinctAndCanBeStopped() {
        SomeipConnectionMonitor monitor = new SomeipConnectionMonitor();
        monitor.start();
        monitor.onAvailability(false, 0);
        assertEquals("SOME/IP UNAVAILABLE", ui(monitor, DataValidity.VALID).getConnectionLabel());
        monitor.startFailed();
        CockpitUiState failed = ui(monitor, DataValidity.VALID);
        assertEquals("SOME/IP START_FAILED", failed.getConnectionLabel());
        assertTrue(failed.isActionEnabled());
        assertTrue(failed.isStopAction());
    }
}
