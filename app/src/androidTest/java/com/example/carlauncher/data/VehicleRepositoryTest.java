package com.example.carlauncher.data;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.example.carlauncher.model.DataValidity;
import com.example.carlauncher.model.VehicleState;
import com.example.carlauncher.service.TcpConnectionState;
import com.example.carlauncher.testing.StubVehicleSendService;
import com.example.carlauncher.ui.CockpitConnectionState;
import com.example.carlauncher.ui.CockpitUiState;

import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * VehicleRepository 在“服务快照 → CockpitUiState”上的状态流测试（Instrumentation）。
 */
@RunWith(AndroidJUnit4.class)
public class VehicleRepositoryTest {

    @Test
    public void initial_returnsUnboundDisconnectedState() {
        VehicleRepository repository = new VehicleRepository();
        CockpitUiState state = repository.getUiState().getValue();

        assertFalse(state.isServiceBound());
        assertNull(state.getVehicleState());
        assertEquals(DataSourceStatus.STOPPED, state.getDataSourceStatus());
        assertEquals(CockpitConnectionState.DISCONNECTED, state.getState());
    }

    @Test
    public void refresh_withoutService_keepsInitialState() {
        VehicleRepository repository = new VehicleRepository();
        onMain(repository::refresh);

        CockpitUiState state = repository.getUiState().getValue();
        assertFalse(state.isServiceBound());
        assertEquals(CockpitConnectionState.DISCONNECTED, state.getState());
    }

    @Test
    public void attachService_exposesServiceSnapshotAsUiState() {
        VehicleRepository repository = new VehicleRepository();
        VehicleState vehicleState = new VehicleState.Builder()
                .setSequence(7L)
                .setVehSpeedKph(66)
                .setValidity(DataValidity.VALID)
                .build();

        onMain(() -> repository.attachService(new StubVehicleSendService()
                .withLatestState(vehicleState)
                .withSourceStatus(DataSourceStatus.CONNECTED)
                .withTcpState(TcpConnectionState.ONLINE)
                .withValidity(DataValidity.VALID)));

        CockpitUiState state = repository.getUiState().getValue();
        assertTrue(state.isServiceBound());
        assertEquals(vehicleState, state.getVehicleState());
        assertEquals(DataSourceStatus.CONNECTED, state.getDataSourceStatus());
        assertEquals(CockpitConnectionState.ONLINE, state.getState());
    }

    @Test
    public void attachService_withOnlineButInvalidValidity_mapsToInvalidData() {
        VehicleRepository repository = new VehicleRepository();

        onMain(() -> repository.attachService(new StubVehicleSendService()
                .withLatestState(new VehicleState.Builder().build())
                .withTcpState(TcpConnectionState.ONLINE)
                .withValidity(DataValidity.INVALID_SPEED)));

        assertEquals(CockpitConnectionState.INVALID_DATA,
                repository.getUiState().getValue().getState());
    }

    @Test
    public void refresh_afterAttach_reflectsUpdatedSnapshot() {
        VehicleRepository repository = new VehicleRepository();
        StubVehicleSendService service = new StubVehicleSendService()
                .withLatestState(new VehicleState.Builder().build())
                .withTcpState(TcpConnectionState.ONLINE)
                .withValidity(DataValidity.VALID);
        onMain(() -> repository.attachService(service));
        assertEquals(CockpitConnectionState.ONLINE,
                repository.getUiState().getValue().getState());

        // 服务状态变化后由外部触发刷新
        service.withTcpState(TcpConnectionState.RECOVERING);
        onMain(repository::refresh);

        CockpitUiState refreshed = repository.getUiState().getValue();
        assertEquals(CockpitConnectionState.RECOVERING, refreshed.getState());
    }

    @Test
    public void detachService_returnsToInitialState() {
        VehicleRepository repository = new VehicleRepository();
        onMain(() -> repository.attachService(new StubVehicleSendService()
                .withLatestState(new VehicleState.Builder().build())
                .withTcpState(TcpConnectionState.ONLINE)));

        onMain(repository::detachService);

        CockpitUiState state = repository.getUiState().getValue();
        assertFalse(state.isServiceBound());
        assertNull(state.getVehicleState());
        assertEquals(DataSourceStatus.STOPPED, state.getDataSourceStatus());
        assertEquals(CockpitConnectionState.DISCONNECTED, state.getState());
    }

    private static void onMain(Runnable action) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(action);
    }
}

