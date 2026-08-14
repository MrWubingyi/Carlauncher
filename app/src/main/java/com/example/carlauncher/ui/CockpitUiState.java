package com.example.carlauncher.ui;

import com.example.carlauncher.data.DataSourceStatus;
import com.example.carlauncher.model.VehicleState;

/**
 * Cockpit 首页某一时刻的完整 UI 状态快照。
 *
 * 该对象只保存状态：
 * - 不访问 Service
 * - 不操作 Android View
 * - 创建后不可修改
 */
public final class CockpitUiState {

    private final VehicleState vehicleState;
    private final DataSourceStatus dataSourceStatus;

    private final boolean serviceBound;
    private final boolean connecting;
    private final boolean tcpConnected;
    private final boolean sending;

    public CockpitUiState(
            VehicleState vehicleState,
            DataSourceStatus dataSourceStatus,
            boolean serviceBound,
            boolean connecting,
            boolean tcpConnected,
            boolean sending
    ) {
        this.vehicleState = vehicleState;
        this.dataSourceStatus = dataSourceStatus;
        this.serviceBound = serviceBound;
        this.connecting = connecting;
        this.tcpConnected = tcpConnected;
        this.sending = sending;
    }

    /**
     * App 尚未绑定或启动 Service 时的初始状态。
     */
    public static CockpitUiState initial() {
        return new CockpitUiState(
                null,
                DataSourceStatus.STOPPED,
                false,
                false,
                false,
                false
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

    public boolean isConnecting() {
        return connecting;
    }

    public boolean isTcpConnected() {
        return tcpConnected;
    }

    public boolean isSending() {
        return sending;
    }

    /**
     * 页面当前是否具有可以显示的车辆数据。
     *
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

        if (sending) {
            return "SENDING";
        }

        if (connecting) {
            return "CONNECTING";
        }

        if (tcpConnected) {
            return "CONNECTED";
        }

        return "DISCONNECTED";
    }

    /**
     * 根据状态生成按钮文字。
     */
    public String getActionLabel() {
        if (sending) {
            return "STOP SEND";
        }

        if (connecting) {
            return "PLEASE WAIT";
        }

        return "START SEND";
    }

    public boolean isActionEnabled() {
        return !connecting;
    }

    public String getTransportLabel() {
        return tcpConnected
                ? "Transport: ONLINE"
                : "Transport: OFFLINE";
    }
}