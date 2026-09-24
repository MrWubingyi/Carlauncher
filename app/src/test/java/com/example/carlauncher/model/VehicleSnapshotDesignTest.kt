package com.example.carlauncher.model

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

/* Design section 6.1: snapshot isolation and wire compatibility. */
open class VehicleSnapshotDesignTest {
    @Test
    @Throws(Exception::class)
    open fun reusedBuilder_doesNotMutatePublishedSnapshot() {
        val builder =
            VehicleState.Builder()
                .setSequence(1)
                .setVehSpeedKph(10)
                .setGear(Gear.D)
                .setSoc(70)
                .setDoorLock(true)
        val first = builder!!.build()
        val original = first!!.toJson()
        val second =
            builder!!
                .setSequence(2)
                .setVehSpeedKph(0)
                .setGear(Gear.P)
                .setSoc(30)
                .setDoorLock(false)
                .build()
        assertNotSame(first, second)
        assertEquals(original, first!!.toJson())
        assertEquals(2, second!!.sequence)
        assertEquals(0, second!!.vehSpeedKph.toLong())
        assertEquals(Gear.P, second!!.gear)
        assertEquals(java.lang.Boolean.FALSE, second!!.doorLock)
    }

    @Test
    @Throws(Exception::class)
    open fun injectedInvalidValues_preserveRawSpeedRpmAndBatteryButClampSoc() {
        val json =
            JSONObject(
                VehicleState.Builder()
                    .setVehSpeedKph(255)
                    .setEngRpm(9999)
                    .setSoc(150)
                    .setEvBatteryLevel(150f)
                    .setEngineCoolantTemp(-999f)
                    .setValidity(DataValidity.INVALID_SPEED)
                    .build()
                    .toJson()
            )
        assertEquals(255, json.getInt("speedKph").toLong())
        assertEquals(9999, json.getInt("rpm").toLong())
        assertEquals(100, json.getInt("soc").toLong())
        assertEquals(150.0, json.getDouble("evBatteryLevel"), 0.0)
        assertEquals(-999.0, json.getDouble("engineCoolantTemp"), 0.0)
        assertEquals(1, json.getInt("validity").toLong())
    }

    @Test
    @Throws(Exception::class)
    open fun explicitNullOptionals_areOmittedRatherThanWrittenAsFalseOrZero() {
        val json =
            JSONObject(
                VehicleState.Builder()
                    .setDoorLock(null)
                    .setBeltWarning(null)
                    .setHeadlightsState(null)
                    .build()
                    .toJson()
            )
        for (key in
            arrayOf<String>(
                "doorLock",
                "beltWarning",
                "headlightsState",
                "highBeamLightsState",
                "engineCoolantTemp",
                "evBatteryLevel",
                "dataStatus",
            )) {
            assertFalse(key, json.has(key))
        }
        assertTrue(json.has("parkingBrake"))
        assertTrue(json.has("validity"))
    }

    @Test
    @Throws(Exception::class)
    open fun sequenceAndTimestamp_preserveLongPrecisionOnWire() {
        val sequence = 9007199254740993L
        val json =
            JSONObject(
                VehicleState.Builder()
                    .setSequence(sequence)
                    .setTimestampMs(java.lang.Long.MAX_VALUE)
                    .setSoc(0)
                    .build()
                    .toJson()
            )
        assertEquals(sequence, json.getLong("seq"))
        assertEquals(java.lang.Long.MAX_VALUE, json.getLong("timestampMs"))
        assertEquals(0, json.getInt("soc").toLong())
        assertEquals(100, VehicleState.Builder().setSoc(100).build().soc.toLong())
    }
}
