package com.example.carlauncher.testing;

import com.example.carlauncher.data.DataSourceStatus;
import com.example.carlauncher.model.DataValidity;
import com.example.carlauncher.model.VehicleState;
import com.example.carlauncher.service.TcpConnectionState;
import com.example.carlauncher.service.VehicleSendService;
import com.example.carlauncher.someip.SomeipConnectionMonitor;

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
    private final SomeipConnectionMonitor someip = new SomeipConnectionMonitor();

    public StubVehicleSendService withSomeipResponse(boolean ok, int code, long now) {
        someip.onResponse(ok, code, now);
        return this;
    }

    public StubVehicleSendService withSomeipAvailable(boolean available, long now) {
        if (someip.snapshot().getState() == SomeipConnectionMonitor.State.STOPPED) someip.start();
        someip.onAvailability(available, now);
        return this;
    }

    public StubVehicleSendService checkSomeipTimeout(long now) {
        someip.checkTimeout(now);
        return this;
    }

    @Override public SomeipConnectionMonitor.Snapshot getSomeipStatus() {
        return someip.snapshot();
    }

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
