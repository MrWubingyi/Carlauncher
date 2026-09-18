package com.example.carlauncher.model;

import org.json.JSONObject;
import org.junit.Test;
import static org.junit.Assert.*;

/** Design section 6.1: snapshot isolation and wire compatibility. */
public class VehicleSnapshotDesignTest {
    @Test public void reusedBuilder_doesNotMutatePublishedSnapshot() throws Exception {
        VehicleState.Builder builder = new VehicleState.Builder().setSequence(1)
                .setVehSpeedKph(10).setGear(Gear.D).setSoc(70).setDoorLock(true);
        VehicleState first = builder.build();
        String original = first.toJson();
        VehicleState second = builder.setSequence(2).setVehSpeedKph(0)
                .setGear(Gear.P).setSoc(30).setDoorLock(false).build();
        assertNotSame(first, second);
        assertEquals(original, first.toJson());
        assertEquals(2, second.getSequence());
        assertEquals(0, second.getVehSpeedKph());
        assertEquals(Gear.P, second.getGear());
        assertEquals(Boolean.FALSE, second.getDoorLock());
    }

    @Test public void injectedInvalidValues_preserveRawSpeedRpmAndBatteryButClampSoc() throws Exception {
        JSONObject json = new JSONObject(new VehicleState.Builder().setVehSpeedKph(255)
                .setEngRpm(9999).setSoc(150).setEvBatteryLevel(150f)
                .setEngineCoolantTemp(-999f).setValidity(DataValidity.INVALID_SPEED)
                .build().toJson());
        assertEquals(255, json.getInt("speedKph"));
        assertEquals(9999, json.getInt("rpm"));
        assertEquals(100, json.getInt("soc"));
        assertEquals(150, json.getDouble("evBatteryLevel"), 0);
        assertEquals(-999, json.getDouble("engineCoolantTemp"), 0);
        assertEquals(1, json.getInt("validity"));
    }

    @Test public void explicitNullOptionals_areOmittedRatherThanWrittenAsFalseOrZero() throws Exception {
        JSONObject json = new JSONObject(new VehicleState.Builder().setDoorLock(null)
                .setBeltWarning(null).setHeadlightsState(null).build().toJson());
        for (String key : new String[]{"doorLock", "beltWarning", "headlightsState",
                "highBeamLightsState", "engineCoolantTemp", "evBatteryLevel", "dataStatus"}) {
            assertFalse(key, json.has(key));
        }
        assertTrue(json.has("parkingBrake"));
        assertTrue(json.has("validity"));
    }

    @Test public void sequenceAndTimestamp_preserveLongPrecisionOnWire() throws Exception {
        long sequence = 9007199254740993L;
        JSONObject json = new JSONObject(new VehicleState.Builder().setSequence(sequence)
                .setTimestampMs(Long.MAX_VALUE).setSoc(0).build().toJson());
        assertEquals(sequence, json.getLong("seq"));
        assertEquals(Long.MAX_VALUE, json.getLong("timestampMs"));
        assertEquals(0, json.getInt("soc"));
        assertEquals(100, new VehicleState.Builder().setSoc(100).build().getSoc());
    }
}
