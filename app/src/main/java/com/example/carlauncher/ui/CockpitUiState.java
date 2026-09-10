package com.example.carlauncher.ui;

import com.example.carlauncher.data.DataSourceStatus;
import com.example.carlauncher.model.DataValidity;
import com.example.carlauncher.model.VehicleState;
import com.example.carlauncher.someip.SomeipConnectionMonitor;
import com.example.carlauncher.someip.SomeipConnectionMonitor.Snapshot;

import java.util.Locale;

/** Immutable cockpit snapshot. Transport health is determined only by SOME/IP. */
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

    public VehicleState getVehicleState() { return vehicleState; }
    public DataSourceStatus getDataSourceStatus() { return dataSourceStatus; }
    public boolean isServiceBound() { return serviceBound; }
    public boolean hasVehicleData() { return vehicleState != null; }
    public Snapshot getSomeipStatus() { return someip; }
    public CockpitConnectionState getState() { return state; }

    public String getConnectionLabel() {
        if (!serviceBound) return "SERVICE UNBOUND";
        if (someip.getState() == SomeipConnectionMonitor.State.ONLINE
                && state == CockpitConnectionState.INVALID_DATA) {
            return "SOME/IP ONLINE / INVALID DATA";
        }
        return "SOME/IP " + someip.getState().name();
    }

    // Service ownership, rather than network health, determines whether STOP is possible.
    public String getActionLabel() { return serviceBound ? "STOP SEND" : "START SEND"; }
    public boolean isActionEnabled() { return true; }
    public boolean isStopAction() { return serviceBound; }

    public String getTransportLabel() {
        if (!serviceBound) return "SOME/IP: STOPPED";
        String code = someip.getReturnCode() == null ? "--"
                : String.format(Locale.ROOT, "0x%02X", someip.getReturnCode());
        return "SOME/IP: " + (someip.isAvailable() ? "AVAILABLE" : "UNAVAILABLE")
                + " | " + someip.getState().name() + " | Last RC: " + code;
    }

    public String getConnectionState() {
        return serviceBound && someip.getState() == SomeipConnectionMonitor.State.ONLINE
                ? "Connection: ONLINE" : "Connection: OFFLINE";
    }
}
