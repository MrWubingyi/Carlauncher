package com.example.carlauncher.ui;

import com.example.carlauncher.data.DataSourceStatus;
import com.example.carlauncher.model.DataValidity;
import com.example.carlauncher.model.VehicleState;
import com.example.carlauncher.someip.SomeipConnectionMonitor;
import com.example.carlauncher.someip.SomeipConnectionMonitor.Snapshot;


/**
 * Immutable cockpit snapshot. Transport health is determined only by SOME/IP.
 */
public final class CockpitUiState {
    private final VehicleState vehicleState;
    private final DataSourceStatus dataSourceStatus;
    private final boolean serviceBound;
    private final Snapshot someip;
    private final CockpitConnectionState state;

    public CockpitUiState(VehicleState vehicleState, DataSourceStatus dataSourceStatus,
                          boolean serviceBound, Snapshot someip, DataValidity validity) {
        this.vehicleState = vehicleState;
        this.dataSourceStatus = dataSourceStatus;
        this.serviceBound = serviceBound;
        this.someip = someip;
        this.state = serviceBound ? mapState(validity, someip) : CockpitConnectionState.DISCONNECTED;
    }

    private static CockpitConnectionState mapState(DataValidity validity, Snapshot someip) {
        switch (someip.getState()) {
            case STARTING:
                return CockpitConnectionState.CONNECTING;
            case WAITING_RESPONSE:
                return CockpitConnectionState.RECOVERING;
            case ONLINE:
                return validity == DataValidity.VALID
                        ? CockpitConnectionState.ONLINE : CockpitConnectionState.INVALID_DATA;
            case RESPONSE_ERROR:
                return CockpitConnectionState.INVALID_DATA;
            default:
                return CockpitConnectionState.DISCONNECTED;
        }
    }

    public static CockpitUiState initial() {
        return new CockpitUiState(null, DataSourceStatus.STOPPED, false,
                new SomeipConnectionMonitor().snapshot(), DataValidity.VALID);
    }

    public VehicleState getVehicleState() {
        return vehicleState;
    }

    public DataSourceStatus getDataSourceStatus() {
        return dataSourceStatus;
    }

    public boolean isServiceBound() {
        return serviceBound;
    }

    public Snapshot getTransportSnapshot() {
        return someip;
    }

    public boolean hasVehicleData() {
        return vehicleState != null;
    }

    public CockpitConnectionState getState() {
        return state;
    }

    public String getConnectionLabel() {
        if (!serviceBound) return "SERVICE UNBOUND";
        if (someip.getState() == SomeipConnectionMonitor.State.ONLINE
                && state == CockpitConnectionState.INVALID_DATA) {
            return "SOME/IP ONLINE / INVALID DATA";
        }
        return "SOME/IP " + eventStateLabel();
    }

    // Service ownership, rather than network health, determines whether STOP is possible.
    public String getActionLabel() {
        return serviceBound ? "STOP RECEIVE" : "START RECEIVE";
    }

    public boolean isActionEnabled() {
        return true;
    }

    public boolean isStopAction() {
        return serviceBound;
    }

    public String getTransportLabel() {
        if (!serviceBound) return "SOME/IP: STOPPED";
        return "SOME/IP: " + (someip.isAvailable() ? "AVAILABLE" : "UNAVAILABLE")
                + " | " + eventStateLabel() + " | EVENT 0x8001";
    }

    private String eventStateLabel() {
        switch (someip.getState()) {
            case WAITING_RESPONSE:
                return "WAITING EVENT";
            case RESPONSE_TIMEOUT:
                return "EVENT TIMEOUT";
            default:
                return someip.getState().name();
        }
    }

    public String getConnectionState() {
        return serviceBound && someip.getState() == SomeipConnectionMonitor.State.ONLINE
                ? "Connection: ONLINE" : "Connection: OFFLINE";
    }
}
