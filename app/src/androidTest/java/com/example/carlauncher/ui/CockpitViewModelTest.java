package com.example.carlauncher.ui;

import androidx.lifecycle.LiveData;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.example.carlauncher.data.DataSourceStatus;
import com.example.carlauncher.model.DataValidity;
import com.example.carlauncher.model.VehicleState;
import com.example.carlauncher.service.TcpConnectionState;
import com.example.carlauncher.testing.StubVehicleSendService;

import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * CockpitViewModel 委托 Repository 的快照流（Instrumentation）。
 */
@RunWith(AndroidJUnit4.class)
public class CockpitViewModelTest {

    @Test
    public void getUiState_neverNull_startsWithInitialState() {
        CockpitViewModel viewModel = new CockpitViewModel();

        LiveData<CockpitUiState> liveData = viewModel.getUiState();
        assertNotNull(liveData);
        CockpitUiState state = liveData.getValue();
        assertNotNull(state);
        assertFalse(state.isServiceBound());
        assertEquals(CockpitConnectionState.DISCONNECTED, state.getState());
    }

    @Test
    public void attachService_emitsBoundState() {
        CockpitViewModel viewModel = new CockpitViewModel();
        VehicleState vehicleState = new VehicleState.Builder()
                .setSequence(3L)
                .setValidity(DataValidity.VALID)
                .build();

        onMain(() -> viewModel.attachService(new StubVehicleSendService()
                .withLatestState(vehicleState)
                .withSourceStatus(DataSourceStatus.CONNECTED)
                .withTcpState(TcpConnectionState.ONLINE)
                .withValidity(DataValidity.VALID)));

        CockpitUiState state = viewModel.getUiState().getValue();
        assertTrue(state.isServiceBound());
        assertEquals(vehicleState, state.getVehicleState());
        assertEquals(CockpitConnectionState.ONLINE, state.getState());
    }

    @Test
    public void detachService_returnsToInitialState() {
        CockpitViewModel viewModel = new CockpitViewModel();
        onMain(() -> viewModel.attachService(new StubVehicleSendService()
                .withLatestState(new VehicleState.Builder().build())));

        onMain(viewModel::detachService);

        CockpitUiState state = viewModel.getUiState().getValue();
        assertFalse(state.isServiceBound());
        assertNull(state.getVehicleState());
        assertEquals(DataSourceStatus.STOPPED, state.getDataSourceStatus());
    }

    @Test
    public void refresh_withoutAttachedService_staysInitial() {
        CockpitViewModel viewModel = new CockpitViewModel();
        onMain(viewModel::refresh);

        CockpitUiState state = viewModel.getUiState().getValue();
        assertFalse(state.isServiceBound());
        assertEquals(CockpitConnectionState.DISCONNECTED, state.getState());
    }

    private static void onMain(Runnable action) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(action);
    }
}

