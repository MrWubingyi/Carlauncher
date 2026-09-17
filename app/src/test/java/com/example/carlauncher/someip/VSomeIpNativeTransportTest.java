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

    /** VS-15: same timestamp permits only a strictly greater sequence. */
    @Test public void equalTimestamp_rejectsDuplicateAndLowerSequence() throws Exception {
        transport.onVehicleEvent(createEventPayload(10, 2000, 10));
        transport.onVehicleEvent(createEventPayload(99, 1999, 99));
        transport.onVehicleEvent(createEventPayload(10, 2000, 20));
        transport.onVehicleEvent(createEventPayload(9, 2000, 30));
        transport.onVehicleEvent(createEventPayload(11, 2000, 40));

        assertEquals(2, listener.vehicleStates.size());
        assertEquals(10, listener.vehicleStates.get(0).getVehSpeedKph());
        assertEquals(11L, listener.vehicleStates.get(1).getSequence());
        assertEquals(40, listener.vehicleStates.get(1).getVehSpeedKph());
        assertTrue(listener.errors.isEmpty());
    }

    /** VS-16: process restart need not produce an unavailable callback. */
    @Test public void newerTimestamp_acceptsRestartedSequenceWithoutDisconnect() throws Exception {
        transport.onVehicleEvent(createEventPayload(9000, 2000, 60));
        transport.onVehicleEvent(createEventPayload(1, 2100, 17));

        assertEquals(2, listener.vehicleStates.size());
        assertEquals(1L, listener.vehicleStates.get(1).getSequence());
        assertEquals(17, listener.vehicleStates.get(1).getVehSpeedKph());
    }

    /** VS-16: also cover restart after an explicit availability transition. */
    @Test public void rediscovery_acceptsNewPublisherFirstFrame() throws Exception {
        transport.onVehicleEvent(createEventPayload(9000, 2000, 60));
        transport.onAvailable(false);
        transport.onAvailable(true);
        transport.onVehicleEvent(createEventPayload(1, 2100, 17));

        assertEquals(2, listener.vehicleStates.size());
        assertEquals(1L, listener.vehicleStates.get(1).getSequence());
        assertEquals(2100L, listener.vehicleStates.get(1).getTimestampMs());
    }

    /** VS-17: UDP loss must not cause waiting for the missing sequence. */
    @Test public void sequenceGap_acceptsLatestCompleteSnapshot() throws Exception {
        transport.onVehicleEvent(createEventPayload(101, 1000, 17));
        transport.onVehicleEvent(createEventPayload(104, 1300, 83));
        assertEquals(2, listener.vehicleStates.size());
        assertEquals(104L, listener.vehicleStates.get(1).getSequence());
        assertEquals(83, listener.vehicleStates.get(1).getVehSpeedKph());
    }

    /** VS-20: detaching a consumer suppresses callbacks without invoking JNI. */
    @Test public void removedListener_receivesNoFurtherCallbacks() throws Exception {
        transport.setListener(null);
        transport.onVehicleEvent(createEventPayload(101, 1000, 17));
        transport.onAvailable(true);
        transport.onResponse(false, 1);
        assertTrue(listener.vehicleStates.isEmpty());
        assertTrue(listener.availabilities.isEmpty());
        assertTrue(listener.errors.isEmpty());
    }

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
