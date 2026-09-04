package com.example.carlauncher.ui;

import com.example.carlauncher.data.DataSourceStatus;
import com.example.carlauncher.model.DataValidity;
import com.example.carlauncher.model.VehicleState;
import com.example.carlauncher.service.TcpConnectionState;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * CockpitUiState 的状态映射优先级与展示文案决策测试。
 */
public class CockpitUiStateTest {

    private static CockpitUiState buildState(
            DataValidity validity,
            TcpConnectionState tcp,
            boolean serviceBound,
            VehicleState vehicleState
    ) {
        return new CockpitUiState(
                vehicleState,
                DataSourceStatus.CONNECTED,
                serviceBound,
                tcp,
                validity
        );
    }

    @Test
    public void mapState_disconnectedWinsOverValidity() {
        assertEquals(CockpitConnectionState.DISCONNECTED,
                buildState(DataValidity.VALID, TcpConnectionState.DISCONNECTED, true, null).getState());
    }

    @Test
    public void mapState_connectingMapsToRecovering() {
        assertEquals(CockpitConnectionState.RECOVERING,
                buildState(DataValidity.VALID, TcpConnectionState.CONNECTING, true, null).getState());
    }

    @Test
    public void mapState_recoveringMapsToRecovering() {
        assertEquals(CockpitConnectionState.RECOVERING,
                buildState(DataValidity.VALID, TcpConnectionState.RECOVERING, true, null).getState());
    }

    @Test
    public void mapState_onlineWithInvalidValidityMapsToInvalidData() {
        assertEquals(CockpitConnectionState.INVALID_DATA,
                buildState(DataValidity.INVALID_SPEED, TcpConnectionState.ONLINE, true, null).getState());
        assertEquals(CockpitConnectionState.INVALID_DATA,
                buildState(DataValidity.STALE, TcpConnectionState.ONLINE, true, null).getState());
        assertEquals(CockpitConnectionState.INVALID_DATA,
                buildState(DataValidity.INCOMPLETE, TcpConnectionState.ONLINE, true, null).getState());
    }

    @Test
    public void mapState_onlineWithValidValidityMapsToOnline() {
        assertEquals(CockpitConnectionState.ONLINE,
                buildState(DataValidity.VALID, TcpConnectionState.ONLINE, true, null).getState());
    }

    @Test
    public void getConnectionLabel_unboundReturnsServiceUnbound() {
        CockpitUiState state = buildState(DataValidity.VALID, TcpConnectionState.ONLINE, false, null);
        assertEquals("SERVICE UNBOUND", state.getConnectionLabel());
    }

    @Test
    public void getConnectionLabel_mapsEachState() {
        assertEquals("SENDING",
                buildState(DataValidity.VALID, TcpConnectionState.ONLINE, true, null).getConnectionLabel());
        assertEquals("RECOVERING",
                buildState(DataValidity.VALID, TcpConnectionState.CONNECTING, true, null).getConnectionLabel());
        assertEquals("DISCONNECTED",
                buildState(DataValidity.VALID, TcpConnectionState.DISCONNECTED, true, null).getConnectionLabel());
        assertEquals("INVALID_DATA",
                buildState(DataValidity.STALE, TcpConnectionState.ONLINE, true, null).getConnectionLabel());
    }

    @Test
    public void getTransportLabel_onlineAndInvalidDataReportOnline() {
        assertEquals("Transport: ONLINE",
                buildState(DataValidity.VALID, TcpConnectionState.ONLINE, true, null).getTransportLabel());
        assertEquals("Transport: ONLINE",
                buildState(DataValidity.INVALID_SPEED, TcpConnectionState.ONLINE, true, null).getTransportLabel());
        assertEquals("Transport: OFFLINE",
                buildState(DataValidity.VALID, TcpConnectionState.DISCONNECTED, true, null).getTransportLabel());
        assertEquals("Transport: OFFLINE",
                buildState(DataValidity.VALID, TcpConnectionState.CONNECTING, true, null).getTransportLabel());
    }

    @Test
    public void getConnectionState_reportsOnlineOnlyWhenOnline() {
        assertEquals("Connection: ONLINE",
                buildState(DataValidity.VALID, TcpConnectionState.ONLINE, true, null).getConnectionState());
        assertEquals("Connection: OFFLINE",
                buildState(DataValidity.VALID, TcpConnectionState.DISCONNECTED, true, null).getConnectionState());
    }

    @Test
    public void initial_isUnboundWithNoDataAndDisconnected() {
        CockpitUiState state = CockpitUiState.initial();

        assertFalse(state.isServiceBound());
        assertNull(state.getVehicleState());
        assertEquals(DataSourceStatus.STOPPED, state.getDataSourceStatus());
        assertEquals(CockpitConnectionState.DISCONNECTED, state.getState());
    }

    @Test
    public void hasVehicleData_reflectsVehicleStatePresence() {
        VehicleState vs = new VehicleState.Builder().build();
        assertTrue(buildState(DataValidity.VALID, TcpConnectionState.ONLINE, true, vs).hasVehicleData());
        assertFalse(buildState(DataValidity.VALID, TcpConnectionState.ONLINE, true, null).hasVehicleData());
    }

    @Test
    public void actionDelegatesToMappedState() {
        CockpitUiState online = buildState(DataValidity.VALID, TcpConnectionState.ONLINE, true, null);
        assertEquals("STOP SEND", online.getActionLabel());
        assertTrue(online.isActionEnabled());
        assertTrue(online.isStopAction());

        CockpitUiState disconnected = buildState(DataValidity.VALID, TcpConnectionState.DISCONNECTED, true, null);
        assertEquals("START SEND", disconnected.getActionLabel());
        assertTrue(disconnected.isActionEnabled());
        assertFalse(disconnected.isStopAction());
    }
}
