package com.example.carlauncher.model

import com.example.carlauncher.data.DataStatus
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** VehicleState 的 Builder 默认值、越界收敛与 JSON 序列化契约。 */
open class VehicleStateTest {

    @Test
    open fun build_defaultsAreApplied() {
        val state = VehicleState.Builder().build()

        assertEquals(Gear.P, state!!.gear)
        assertEquals(0, state!!.soc.toLong())
        assertEquals(TurnSignal.NONE, state!!.turnSignal)
        assertEquals(WarningState.NONE, state!!.warning)
        assertEquals(DataValidity.INCOMPLETE, state!!.validity)
        assertEquals(java.lang.Boolean.FALSE, state!!.doorLock)
        assertEquals(java.lang.Boolean.FALSE, state!!.beltWarning)
        assertEquals(Integer.valueOf(0), state!!.headlightsState)
        assertNull(state!!.highBeamLightsState)
        assertNull(state!!.engineCoolantTemp)
        assertNull(state!!.evBatteryLevel)
        assertNull(state!!.dataStatus)
    }

    @Test
    open fun build_socIsClampedToValidRange() {
        assertEquals(0, VehicleState.Builder().setSoc(-5).build().soc.toLong())
        assertEquals(100, VehicleState.Builder().setSoc(150).build().soc.toLong())
        assertEquals(50, VehicleState.Builder().setSoc(50).build().soc.toLong())
    }

    @Test
    open fun build_nullEnumInputsFallbackToDefaults() {
        val state =
            VehicleState.Builder()
                .setGear(null as Gear?)
                .setTurnSignal(null)
                .setWarning(null)
                .setValidity(null)
                .build()

        assertEquals(Gear.P, state!!.gear)
        assertEquals(TurnSignal.NONE, state!!.turnSignal)
        assertEquals(WarningState.NONE, state!!.warning)
        assertEquals(DataValidity.INCOMPLETE, state!!.validity)
    }

    @Test
    open fun build_gearStringOverloadMapsValue() {
        val state = VehicleState.Builder().setGear("r").build()
        assertEquals(Gear.R, state!!.gear)
    }

    @Test
    open fun build_allFieldsAreRoundTrippedThroughGetters() {
        val state =
            VehicleState.Builder()
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
                .build()

        assertEquals(1, state!!.version.toLong())
        assertEquals(42L, state!!.sequence)
        assertEquals(123456789L, state!!.timestampMs)
        assertEquals(88, state!!.vehSpeedKph.toLong())
        assertEquals(2500, state!!.engRpm.toLong())
        assertEquals(Gear.D, state!!.gear)
        assertEquals(70, state!!.soc.toLong())
        assertEquals(TurnSignal.LEFT, state!!.turnSignal)
        assertTrue(state!!.isParkingBrake)
        assertEquals(WarningState.CRITICAL, state!!.warning)
        assertEquals(DataValidity.VALID, state!!.validity)
        assertEquals(java.lang.Boolean.TRUE, state!!.doorLock)
        assertEquals(java.lang.Boolean.TRUE, state!!.beltWarning)
        assertEquals(Integer.valueOf(1), state!!.headlightsState)
        assertEquals(Integer.valueOf(1), state!!.highBeamLightsState)
        assertEquals(java.lang.Float.valueOf(90.5f), state!!.engineCoolantTemp)
        assertEquals(java.lang.Float.valueOf(70.0f), state!!.evBatteryLevel)
        assertEquals(DataStatus.NORMAL, state!!.dataStatus)
    }

    @Test
    @Throws(Exception::class)
    open fun toJson_containsAllPopulatedFields() {
        val state =
            VehicleState.Builder()
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
                .build()

        val json = JSONObject(state!!.toJson())

        assertEquals(1, json.getInt("version").toLong())
        assertEquals(7L, json.getLong("seq"))
        assertEquals(100L, json.getLong("timestampMs"))
        assertEquals(60, json.getInt("speedKph").toLong())
        assertEquals(2300, json.getInt("rpm").toLong())
        assertEquals(3, json.getInt("gear").toLong()) // Gear.D
        assertEquals(80, json.getInt("soc").toLong())
        assertEquals(2, json.getInt("turnSignal").toLong()) // TurnSignal.RIGHT
        assertTrue(json.getBoolean("parkingBrake"))
        assertEquals(1, json.getInt("warning").toLong()) // WarningState.GENERAL_WARNING
        assertEquals(0, json.getInt("validity").toLong()) // DataValidity.VALID
        assertTrue(json.getBoolean("doorLock"))
        assertFalse(json.getBoolean("beltWarning"))
        assertEquals(1, json.getInt("headlightsState").toLong())
        assertEquals(0, json.getInt("highBeamLightsState").toLong())
        assertEquals(90.0, json.getDouble("engineCoolantTemp"), 0.0001)
        assertEquals(80.0, json.getDouble("evBatteryLevel"), 0.0001)
        assertEquals(0, json.getInt("dataStatus").toLong()) // DataStatus.NORMAL
    }

    @Test
    @Throws(Exception::class)
    open fun toJson_omitsNullFields() {
        val state = VehicleState.Builder().setVersion(0).setSequence(1L).setTimestampMs(2L).build()

        val json = JSONObject(state!!.toJson())

        assertFalse(
            "highBeamLightsState 为 null 时应省略",
            json.has("highBeamLightsState"),
        )
        assertFalse(
            "engineCoolantTemp 为 null 时应省略",
            json.has("engineCoolantTemp"),
        )
        assertFalse(
            "evBatteryLevel 为 null 时应省略",
            json.has("evBatteryLevel"),
        )
        assertFalse(
            "dataStatus 为 null 时应省略",
            json.has("dataStatus"),
        )

        // 非 null 字段应保留
        assertTrue(json.has("version"))
        assertTrue(json.has("gear"))
        assertTrue(json.has("doorLock"))
    }
}
