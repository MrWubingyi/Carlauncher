package com.example.carlauncher.someip;

import com.example.carlauncher.data.DataStatus;
import com.example.carlauncher.model.*;
import org.json.JSONObject;
import org.json.JSONException;
import org.junit.Test;
import java.nio.charset.StandardCharsets;
import static org.junit.Assert.*;

public class VehicleEventCodecTest {
    private JSONObject fixture() throws Exception {
        return new JSONObject("{\"version\":1,\"seq\":51,\"timestampMs\":1700000000000,"
                + "\"speedKph\":98,\"rpm\":3250,\"gear\":3,\"soc\":70,\"turnSignal\":1,"
                + "\"parkingBrake\":true,\"warning\":0,\"validity\":0,\"doorLock\":true,"
                + "\"beltWarning\":true,\"headlightsState\":1,\"highBeamLightsState\":0,"
                + "\"engineCoolantTemp\":90,\"evBatteryLevel\":70,\"dataStatus\":0}");
    }
    private VehicleState decode(JSONObject value) throws Exception {
        return VehicleEventCodec.decode(value.toString().getBytes(StandardCharsets.UTF_8));
    }
    @Test public void fullSnapshotPreservesDashboardFields() throws Exception {
        VehicleState state = decode(fixture());
        assertEquals(51, state.getSequence());
        assertEquals(98, state.getVehSpeedKph());
        assertEquals(3250, state.getEngRpm());
        assertEquals(Gear.D, state.getGear());
        assertEquals(TurnSignal.LEFT, state.getTurnSignal());
        assertEquals(Boolean.TRUE, state.getBeltWarning());
        assertEquals(Integer.valueOf(0), state.getHighBeamLightsState());
        assertEquals(DataValidity.VALID, state.getValidity());
    }
    @Test public void invalidScenarioIsMarkedBeforeModelClamping() throws Exception {
        VehicleState state = decode(fixture().put("speedKph",255).put("rpm",9999)
                .put("soc",150).put("engineCoolantTemp",-999));
        assertEquals(DataStatus.INVALID, state.getDataStatus());
        assertEquals(DataValidity.INVALID_SPEED, state.getValidity());
        assertEquals(255, state.getVehSpeedKph());
    }
    @Test public void malformedIdentityAndMissingFieldsAreRejected() throws Exception {
        assertThrows(JSONException.class, () -> decode(fixture().put("version",2)));
        assertThrows(JSONException.class, () -> decode(fixture().put("seq",-1)));
        assertThrows(JSONException.class, () -> decode(fixture().put("gear",4)));
        assertThrows(JSONException.class, () -> decode(fixture().put("speedKph","98")));
        assertThrows(JSONException.class, () -> decode(fixture().put("rpm",2.5)));
        JSONObject missing = fixture(); missing.remove("beltWarning");
        assertThrows(JSONException.class, () -> decode(missing));
    }
    @Test public void eventsDriveWatchdogWithoutInventingMethodResponseCodes() {
        SomeipConnectionMonitor monitor = new SomeipConnectionMonitor();
        monitor.start(); monitor.onAvailability(true,0); monitor.onEvent(100);
        assertEquals(SomeipConnectionMonitor.State.ONLINE,monitor.snapshot().getState());
        assertNull(monitor.snapshot().getReturnCode());
        assertFalse(monitor.checkTimeout(3099)); assertTrue(monitor.checkTimeout(3100));
        monitor.onEvent(3200);
        assertEquals(SomeipConnectionMonitor.State.ONLINE,monitor.snapshot().getState());
        monitor.onAvailability(false,3300); monitor.onEvent(3400);
        assertEquals(SomeipConnectionMonitor.State.UNAVAILABLE,monitor.snapshot().getState());
    }
}
