package com.example.carlauncher.someip;

import com.example.carlauncher.model.VehicleState;


import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class VSomeIpNativeTransportTest {

    private TestNativeListener listener;
    private VSomeIpNativeTransport transport;

    @Before
    public void setUp() {
        listener = new TestNativeListener();
        // Context is null in pure JVM unit test; we test listener handling, decoding & filtering logic
        transport = new VSomeIpNativeTransport(null) {
            @Override
            public boolean start() {
                setListener(listener);
                return true;
            }
        };
        transport.setListener(listener);
    }

    @Test
    public void onAvailable_notifiesListener() {
        transport.onAvailable(true);
        assertEquals(1, listener.availabilities.size());
        assertTrue(listener.availabilities.get(0));

        transport.onAvailable(false);
        assertEquals(2, listener.availabilities.size());
        assertFalse(listener.availabilities.get(1));
    }

    @Test
    public void onVehicleEvent_decodesAndFiltersOutStaleSequences() throws Exception {
        byte[] payloadSeq1 = createEventPayload(1, 1000L, 60);
        byte[] payloadSeq2 = createEventPayload(2, 2000L, 70);
        byte[] payloadStaleSeq = createEventPayload(1, 1500L, 65); // Sequence 1 <= last sequence 2 -> rejected

        transport.onVehicleEvent(payloadSeq1);
        transport.onVehicleEvent(payloadSeq2);
        transport.onVehicleEvent(payloadStaleSeq);

        assertEquals(2, listener.vehicleStates.size());
        assertEquals(1, listener.vehicleStates.get(0).getSequence());
        assertEquals(2, listener.vehicleStates.get(1).getSequence());
    }

    @Test
    public void onVehicleEvent_filtersOutStaleTimestamps() throws Exception {
        byte[] payloadTime1000 = createEventPayload(5, 1000L, 60);
        byte[] payloadTime500 = createEventPayload(6, 500L, 70); // Timestamp 500 < last timestamp 1000 -> rejected

        transport.onVehicleEvent(payloadTime1000);
        transport.onVehicleEvent(payloadTime500);

        assertEquals(1, listener.vehicleStates.size());
        assertEquals(1000L, listener.vehicleStates.get(0).getTimestampMs());
    }

    @Test
    public void stop_discardsLateCallbacks() throws Exception {
        transport.stop();

        byte[] payload = createEventPayload(10, 5000L, 80);
        transport.onVehicleEvent(payload);
        transport.onAvailable(true);

        assertEquals(0, listener.vehicleStates.size());
        assertEquals(0, listener.availabilities.size());
    }

    // ---- Helpers ----

    private byte[] createEventPayload(long seq, long timestampMs, int speedKph) throws Exception {
        JSONObject j = new JSONObject();
        j.put("version", 1);
        j.put("seq", seq);
        j.put("timestampMs", timestampMs);
        j.put("speedKph", speedKph);
        j.put("rpm", 2500);
        j.put("soc", 90);
        j.put("gear", 3);
        j.put("turnSignal", 0);
        j.put("warning", 0);
        j.put("validity", 0);
        j.put("dataStatus", 0);
        j.put("parkingBrake", false);
        j.put("doorLock", true);
        j.put("beltWarning", false);
        j.put("headlightsState", 0);
        j.put("highBeamLightsState", 0);
        j.put("engineCoolantTemp", 88.0);
        j.put("evBatteryLevel", 90.0);
        return j.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }

    private static class TestNativeListener implements NativeVehicleTransport.Listener {
        final List<Boolean> availabilities = new ArrayList<>();
        final List<VehicleState> vehicleStates = new ArrayList<>();
        final List<Throwable> errors = new ArrayList<>();

        @Override
        public void onAvailabilityChanged(boolean available) {
            availabilities.add(available);
        }

        @Override
        public void onVehicleState(VehicleState state) {
            vehicleStates.add(state);
        }

        @Override
        public void onError(Throwable throwable) {
            errors.add(throwable);
        }
    }
}
