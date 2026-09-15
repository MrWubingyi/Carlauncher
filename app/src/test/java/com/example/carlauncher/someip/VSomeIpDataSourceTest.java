package com.example.carlauncher.someip;

import com.example.carlauncher.data.DataSourceStatus;
import com.example.carlauncher.data.DataStatus;
import com.example.carlauncher.data.VehicleDataSource;
import com.example.carlauncher.model.DataValidity;
import com.example.carlauncher.model.Gear;
import com.example.carlauncher.model.TurnSignal;
import com.example.carlauncher.model.VehicleState;
import com.example.carlauncher.model.WarningState;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class VSomeIpDataSourceTest {

    private FakeNativeVehicleTransport fakeTransport;
    private SomeipConnectionMonitor connectionMonitor;
    private VSomeIpDataSource dataSource;
    private TestVehicleDataSourceListener listener;

    @Before
    public void setUp() {
        fakeTransport = new FakeNativeVehicleTransport();
        connectionMonitor = new SomeipConnectionMonitor();
        dataSource = new VSomeIpDataSource(fakeTransport, connectionMonitor);
        listener = new TestVehicleDataSourceListener();
    }

    @Test
    public void start_subscribesToTransportAndNotifiesConnecting() {
        dataSource.start(listener);

        assertTrue(dataSource.isRunning());
        assertEquals(DataSourceStatus.CONNECTING, dataSource.getStatus());
        assertTrue(listener.sourceStatuses.contains(DataSourceStatus.CONNECTING));
    }

    @Test
    public void availabilityChanged_updatesStatusAndMonitor() {
        dataSource.start(listener);

        fakeTransport.notifyAvailability(true);
        assertEquals(DataSourceStatus.CONNECTING, dataSource.getStatus());

        fakeTransport.notifyAvailability(false);
        assertEquals(DataSourceStatus.DISCONNECTED, dataSource.getStatus());
        assertNull(dataSource.getLatestState());
    }

    @Test
    public void vehicleState_updatesLatestStateAndStatusConnected() {
        dataSource.start(listener);
        fakeTransport.notifyAvailability(true);

        VehicleState sampleState = createSampleVehicleState(1, 1000L, 60, DataStatus.NORMAL);
        fakeTransport.notifyVehicleState(sampleState);

        assertEquals(sampleState, dataSource.getLatestState());
        assertEquals(DataSourceStatus.CONNECTED, dataSource.getStatus());
        assertEquals(1, listener.receivedStates.size());
        assertEquals(sampleState, listener.receivedStates.get(0));
    }

    @Test
    public void vehicleState_withNoData_updatesStatusNoData() {
        dataSource.start(listener);
        fakeTransport.notifyAvailability(true);

        VehicleState sampleState = createSampleVehicleState(2, 2000L, 0, DataStatus.NO_DATA);
        fakeTransport.notifyVehicleState(sampleState);

        assertEquals(DataSourceStatus.NO_DATA, dataSource.getStatus());
    }

    @Test
    public void error_updatesStatusErrorAndNotifiesListener() {
        dataSource.start(listener);

        Exception testError = new RuntimeException("Test protocol error");
        fakeTransport.notifyError(testError);

        assertEquals(DataSourceStatus.ERROR, dataSource.getStatus());
        assertEquals(1, listener.receivedErrors.size());
        assertEquals(testError, listener.receivedErrors.get(0));
    }

    @Test
    public void stop_unsubscribesAndResetsStatus() {
        dataSource.start(listener);
        fakeTransport.notifyAvailability(true);

        dataSource.stop();

        assertFalse(dataSource.isRunning());
        assertEquals(DataSourceStatus.STOPPED, dataSource.getStatus());
        assertNull(dataSource.getLatestState());
    }

    // ---- Helper Classes ----

    private static VehicleState createSampleVehicleState(long seq, long timestamp, int speed, DataStatus status) {
        return new VehicleState.Builder()
                .setVersion(1)
                .setSequence(seq)
                .setTimestampMs(timestamp)
                .setVehSpeedKph(speed)
                .setEngRpm(2000)
                .setSoc(80)
                .setGear(Gear.D)
                .setTurnSignal(TurnSignal.NONE)
                .setWarning(WarningState.NONE)
                .setValidity(DataValidity.VALID)
                .setDataStatus(status)
                .setParkingBrake(false)
                .setDoorLock(true)
                .setBeltWarning(false)
                .setHeadlightsState(0)
                .setHighBeamLightsState(0)
                .setEngineCoolantTemp(90.0f)
                .setEvBatteryLevel(80.0f)
                .build();
    }

    private static class FakeNativeVehicleTransport implements NativeVehicleTransport {
        private Listener listener;
        private boolean available = false;
        private boolean started = false;

        @Override
        public boolean start() {
            started = true;
            return true;
        }

        @Override
        public void stop() {
            started = false;
            available = false;
        }

        @Override
        public boolean isAvailable() {
            return available;
        }

        @Override
        public void setListener(Listener listener) {
            this.listener = listener;
        }

        void notifyAvailability(boolean available) {
            this.available = available;
            if (listener != null) {
                listener.onAvailabilityChanged(available);
            }
        }

        void notifyVehicleState(VehicleState state) {
            if (listener != null) {
                listener.onVehicleState(state);
            }
        }

        void notifyError(Throwable throwable) {
            if (listener != null) {
                listener.onError(throwable);
            }
        }
    }

    private static class TestVehicleDataSourceListener implements VehicleDataSource.Listener {
        final List<VehicleState> receivedStates = new ArrayList<>();
        final List<DataSourceStatus> sourceStatuses = new ArrayList<>();
        final List<Exception> receivedErrors = new ArrayList<>();

        @Override
        public void onStateChanged(VehicleState state) {
            receivedStates.add(state);
        }

        @Override
        public void onSourceStatusChanged(DataSourceStatus status) {
            sourceStatuses.add(status);
        }

        @Override
        public void onError(Exception exception) {
            receivedErrors.add(exception);
        }
    }
}
