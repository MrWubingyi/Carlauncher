package com.example.carlauncher.model;

import com.example.carlauncher.data.DataStatus;

import org.json.JSONObject;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * VehicleState 的 Builder 默认值、越界收敛与 JSON 序列化契约。
 */
public class VehicleStateTest {

    @Test
    public void build_defaultsAreApplied() {
        VehicleState state = new VehicleState.Builder().build();

        assertEquals(Gear.P, state.getGear());
        assertEquals(0, state.getSoc());
        assertEquals(TurnSignal.NONE, state.getTurnSignal());
        assertEquals(WarningState.NONE, state.getWarning());
        assertEquals(DataValidity.INCOMPLETE, state.getValidity());
        assertEquals(Boolean.FALSE, state.getDoorLock());
        assertEquals(Boolean.FALSE, state.getBeltWarning());
        assertEquals(Integer.valueOf(0), state.getHeadlightsState());
        assertNull(state.getHighBeamLightsState());
        assertNull(state.getEngineCoolantTemp());
        assertNull(state.getEvBatteryLevel());
        assertNull(state.getDataStatus());
    }

    @Test
    public void build_socIsClampedToValidRange() {
        assertEquals(0, new VehicleState.Builder().setSoc(-5).build().getSoc());
        assertEquals(100, new VehicleState.Builder().setSoc(150).build().getSoc());
        assertEquals(50, new VehicleState.Builder().setSoc(50).build().getSoc());
    }

    @Test
    public void build_nullEnumInputsFallbackToDefaults() {
        VehicleState state = new VehicleState.Builder()
                .setGear((Gear) null)
                .setTurnSignal(null)
                .setWarning(null)
                .setValidity(null)
                .build();

        assertEquals(Gear.P, state.getGear());
        assertEquals(TurnSignal.NONE, state.getTurnSignal());
        assertEquals(WarningState.NONE, state.getWarning());
        assertEquals(DataValidity.INCOMPLETE, state.getValidity());
    }

    @Test
    public void build_gearStringOverloadMapsValue() {
        VehicleState state = new VehicleState.Builder().setGear("r").build();
        assertEquals(Gear.R, state.getGear());
    }

    @Test
    public void build_allFieldsAreRoundTrippedThroughGetters() {
        VehicleState state = new VehicleState.Builder()
                .setVersion(1)
                .setSequence(42L)
                .setTimestampMs(123456789L)
                .setVehSpeedKph(88)
                .setEngRpm(2500)
                .setGear(Gear.D)
                .setSoc(70)
                .setTurnSignal(TurnSignal.LEFT)
                .setParkingBrake(true)
                .setWarning(WarningState.CRITICAL)
                .setValidity(DataValidity.VALID)
                .setDoorLock(true)
                .setBeltWarning(true)
                .setHeadlightsState(1)
                .setHighBeamLightsState(1)
                .setEngineCoolantTemp(90.5f)
                .setEvBatteryLevel(70.0f)
                .setDataStatus(DataStatus.NORMAL)
                .build();

        assertEquals(1, state.getVersion());
        assertEquals(42L, state.getSequence());
        assertEquals(123456789L, state.getTimestampMs());
        assertEquals(88, state.getVehSpeedKph());
        assertEquals(2500, state.getEngRpm());
        assertEquals(Gear.D, state.getGear());
        assertEquals(70, state.getSoc());
        assertEquals(TurnSignal.LEFT, state.getTurnSignal());
        assertTrue(state.isParkingBrake());
        assertEquals(WarningState.CRITICAL, state.getWarning());
        assertEquals(DataValidity.VALID, state.getValidity());
        assertEquals(Boolean.TRUE, state.getDoorLock());
        assertEquals(Boolean.TRUE, state.getBeltWarning());
        assertEquals(Integer.valueOf(1), state.getHeadlightsState());
        assertEquals(Integer.valueOf(1), state.getHighBeamLightsState());
        assertEquals(Float.valueOf(90.5f), state.getEngineCoolantTemp());
        assertEquals(Float.valueOf(70.0f), state.getEvBatteryLevel());
        assertEquals(DataStatus.NORMAL, state.getDataStatus());
    }

    @Test
    public void toJson_containsAllPopulatedFields() throws Exception {
        VehicleState state = new VehicleState.Builder()
                .setVersion(1)
                .setSequence(7L)
                .setTimestampMs(100L)
                .setVehSpeedKph(60)
                .setEngRpm(2300)
                .setGear(Gear.D)
                .setSoc(80)
                .setTurnSignal(TurnSignal.RIGHT)
                .setParkingBrake(true)
                .setWarning(WarningState.GENERAL_WARNING)
                .setValidity(DataValidity.VALID)
                .setDoorLock(true)
                .setBeltWarning(false)
                .setHeadlightsState(1)
                .setHighBeamLightsState(0)
                .setEngineCoolantTemp(90.0f)
                .setEvBatteryLevel(80.0f)
                .setDataStatus(DataStatus.NORMAL)
                .build();

        JSONObject json = new JSONObject(state.toJson());

        assertEquals(1, json.getInt("version"));
        assertEquals(7L, json.getLong("seq"));
        assertEquals(100L, json.getLong("timestampMs"));
        assertEquals(60, json.getInt("speedKph"));
        assertEquals(2300, json.getInt("rpm"));
        assertEquals(3, json.getInt("gear"));          // Gear.D
        assertEquals(80, json.getInt("soc"));
        assertEquals(2, json.getInt("turnSignal"));    // TurnSignal.RIGHT
        assertTrue(json.getBoolean("parkingBrake"));
        assertEquals(1, json.getInt("warning"));       // WarningState.GENERAL_WARNING
        assertEquals(0, json.getInt("validity"));      // DataValidity.VALID
        assertTrue(json.getBoolean("doorLock"));
        assertFalse(json.getBoolean("beltWarning"));
        assertEquals(1, json.getInt("headlightsState"));
        assertEquals(0, json.getInt("highBeamLightsState"));
        assertEquals(90.0, json.getDouble("engineCoolantTemp"), 0.0001);
        assertEquals(80.0, json.getDouble("evBatteryLevel"), 0.0001);
        assertEquals(0, json.getInt("dataStatus"));    // DataStatus.NORMAL
    }

    @Test
    public void toJson_omitsNullFields() throws Exception {
        VehicleState state = new VehicleState.Builder()
                .setVersion(0)
                .setSequence(1L)
                .setTimestampMs(2L)
                .build();

        JSONObject json = new JSONObject(state.toJson());

        assertFalse("highBeamLightsState 为 null 时应省略",
                json.has("highBeamLightsState"));
        assertFalse("engineCoolantTemp 为 null 时应省略",
                json.has("engineCoolantTemp"));
        assertFalse("evBatteryLevel 为 null 时应省略",
                json.has("evBatteryLevel"));
        assertFalse("dataStatus 为 null 时应省略",
                json.has("dataStatus"));

        // 非 null 字段应保留
        assertTrue(json.has("version"));
        assertTrue(json.has("gear"));
        assertTrue(json.has("doorLock"));
    }
}
