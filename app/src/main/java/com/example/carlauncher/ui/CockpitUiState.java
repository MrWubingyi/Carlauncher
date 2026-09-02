package com.example.carlauncher.ui;

import android.util.Log;

import com.example.carlauncher.data.DataSourceStatus;
import com.example.carlauncher.model.DataValidity;
import com.example.carlauncher.model.VehicleState;
import com.example.carlauncher.service.TcpConnectionState;


/**
 * Cockpit 首页某一时刻的完整 UI 状态快照。
 * <p>
 * 该对象只保存状态：
 * - 不访问 Service
 * - 不操作 Android View
 * - 创建后不可修改
 */
public final class CockpitUiState {

    private static final String TAG = "CockpitUiState";

    private final VehicleState vehicleState;
    private final DataSourceStatus dataSourceStatus;

    private final boolean serviceBound;
    private final CockpitConnectionState state;

    public CockpitUiState(
            VehicleState vehicleState,
            DataSourceStatus dataSourceStatus,
            boolean serviceBound,
            TcpConnectionState tcpconnectstate,
            DataValidity validity
    ) {
        this.vehicleState = vehicleState;
        this.dataSourceStatus = dataSourceStatus;
        this.serviceBound = serviceBound;
        state = mapState(validity, tcpconnectstate);
        Log.d(TAG, "Created CockpitUiState: state=" + state
                + ", serviceBound=" + serviceBound
                + ", dataSourceStatus=" + dataSourceStatus
                + ", tcpState=" + tcpconnectstate
                + ", validity=" + validity
                + ", vehicleState=" + (vehicleState != null ? "non-null" : "null"));
    }

    public CockpitConnectionState mapState(
            DataValidity validity,
            TcpConnectionState tcpstate
    ) {
        CockpitConnectionState mappedState;
        if (tcpstate == TcpConnectionState.DISCONNECTED) {
            mappedState = CockpitConnectionState.DISCONNECTED;
        } else if (tcpstate == TcpConnectionState.CONNECTING
                || tcpstate == TcpConnectionState.RECOVERING) {
            mappedState = CockpitConnectionState.RECOVERING;
        } else if (validity != DataValidity.VALID) {
            mappedState = CockpitConnectionState.INVALID_DATA;
        } else {
            mappedState = CockpitConnectionState.ONLINE;
        }
        Log.d(TAG, "mapState: validity=" + validity + ", tcpState=" + tcpstate + " -> mappedState=" + mappedState);
        return mappedState;
    }

    /**
     * App 尚未绑定或启动 Service 时的初始状态。
     */
    public static CockpitUiState initial() {
        Log.d(TAG, "Creating initial state");
        return new CockpitUiState(
                null,
                DataSourceStatus.STOPPED,
                false,
                TcpConnectionState.DISCONNECTED,
                DataValidity.VALID
        );
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


    /**
     * 页面当前是否具有可以显示的车辆数据。
     * <p>
     * TCP 断开不等于车辆数据不存在：
     * Mock/VHAL 数据源仍可能继续产生状态。
     */
    public boolean hasVehicleData() {
        return vehicleState != null;
    }

    /**
     * 根据底层状态生成顶部状态文字。
     */
    public String getConnectionLabel() {
        if (!serviceBound) {
            return "SERVICE UNBOUND";
        }
        switch (state) {
            case ONLINE:
                return "SENDING";
            case CONNECTING:
                return "CONNECTING";
            case RECOVERING:
                return "RECOVERING";
            case DISCONNECTED:
                return "DISCONNECTED";
            case INVALID_DATA:
                return "INVALID_DATA";
            default:
                return "UNKNOWN";
        }
    }

    /**
     * 根据状态生成按钮文字。
     */
    public String getActionLabel() {
        return state.getActionLabel();
    }

    public boolean isActionEnabled() {
        return state.isActionEnabled();
    }

    public String getTransportLabel() {
        return state == CockpitConnectionState.ONLINE
                || state == CockpitConnectionState.INVALID_DATA
                ? "Transport: ONLINE"
                : "Transport: OFFLINE";
    }

    public boolean isStopAction() {
        return state.isStopAction();
    }

    public String getConnectionState() {
        return state == CockpitConnectionState.ONLINE
                ? "Connection: ONLINE"
                : "Connection: OFFLINE";
    }

    public CockpitConnectionState getState() {
        return state;
    }
}