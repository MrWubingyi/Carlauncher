package com.example.carlauncher.testing;

import com.example.carlauncher.data.DataSourceStatus;
import com.example.carlauncher.model.DataValidity;
import com.example.carlauncher.model.VehicleState;
import com.example.carlauncher.service.TcpConnectionState;
import com.example.carlauncher.service.VehicleSendService;

/**
 * 测试替身：{@link VehicleSendService} 的子类。
 * <p>
 * 仅为 Repository / ViewModel / MainActivity 提供可控的服务快照读取，
 * 不触发 onCreate、前台通知、TCP 或数据源等真实副作用。
 */
public final class StubVehicleSendService extends VehicleSendService {

    private VehicleState latestState;
    private DataSourceStatus sourceStatus = DataSourceStatus.CONNECTED;
    private TcpConnectionState tcpState = TcpConnectionState.ONLINE;
    private DataValidity dataValidity = DataValidity.VALID;

    public StubVehicleSendService withLatestState(VehicleState state) {
        this.latestState = state;
        return this;
    }

    public StubVehicleSendService withSourceStatus(DataSourceStatus status) {
        this.sourceStatus = status;
        return this;
    }

    public StubVehicleSendService withTcpState(TcpConnectionState state) {
        this.tcpState = state;
        return this;
    }

    public StubVehicleSendService withValidity(DataValidity validity) {
        this.dataValidity = validity;
        return this;
    }

    @Override
    public VehicleState getLatestVehicleState() {
        return latestState;
    }

    @Override
    public DataSourceStatus getSourceStatus() {
        return sourceStatus;
    }

    @Override
    public TcpConnectionState getTcpState() {
        return tcpState;
    }

    @Override
    public DataValidity getDataValidity() {
        return dataValidity;
    }
}
