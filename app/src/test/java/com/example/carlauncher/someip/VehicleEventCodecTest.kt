package com.example.carlauncher.someip

import com.example.carlauncher.data.DataStatus
import com.example.carlauncher.model.*
import java.nio.charset.StandardCharsets
import org.json.JSONException
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

open class VehicleEventCodecTest {
    @Throws(Exception::class)
    private fun fixture(): JSONObject? =
        JSONObject(
            ("{\"version\":1,\"seq\":51,\"timestampMs\":1700000000000," +
                "\"speedKph\":98,\"rpm\":3250,\"gear\":3,\"soc\":70,\"turnSignal\":1," +
                "\"parkingBrake\":true,\"warning\":0,\"validity\":0,\"doorLock\":true," +
                "\"beltWarning\":true,\"headlightsState\":1,\"highBeamLightsState\":0," +
                "\"engineCoolantTemp\":90,\"evBatteryLevel\":70,\"dataStatus\":0}")
        )

    @Throws(Exception::class)
    private fun decode(value: JSONObject?): VehicleState? =
        VehicleEventCodec.decode(value!!.toString().toByteArray(StandardCharsets.UTF_8))

    @Test
    @Throws(Exception::class)
    open fun fullSnapshotPreservesDashboardFields() {
        val state = decode(fixture())
        assertEquals(51, state!!.sequence)
        assertEquals(98, state!!.vehSpeedKph.toLong())
        assertEquals(3250, state!!.engRpm.toLong())
        assertEquals(Gear.D, state!!.gear)
        assertEquals(TurnSignal.LEFT, state!!.turnSignal)
        assertEquals(java.lang.Boolean.TRUE, state!!.beltWarning)
        assertEquals(Integer.valueOf(0), state!!.highBeamLightsState)
        assertEquals(DataValidity.VALID, state!!.validity)
    }

    @Test
    @Throws(Exception::class)
    open fun invalidScenarioIsMarkedBeforeModelClamping() {
        val state =
            decode(
                fixture()!!
                    .put("speedKph", 255)
                    .put("rpm", 9999)
                    .put("soc", 150)
                    .put("engineCoolantTemp", -999)
            )
        assertEquals(DataStatus.INVALID, state!!.dataStatus)
        assertEquals(DataValidity.INVALID_SPEED, state!!.validity)
        assertEquals(255, state!!.vehSpeedKph.toLong())
    }

    @Test
    @Throws(Exception::class)
    open fun malformedIdentityAndMissingFieldsAreRejected() {
        assertThrows<JSONException?>(JSONException::class.java) {
            decode(fixture()!!.put("version", 2))
        }
        assertThrows<JSONException?>(JSONException::class.java) {
            decode(fixture()!!.put("seq", -1))
        }
        assertThrows<JSONException?>(JSONException::class.java) {
            decode(fixture()!!.put("gear", 4))
        }
        assertThrows<JSONException?>(JSONException::class.java) {
            decode(fixture()!!.put("speedKph", "98"))
        }
        assertThrows<JSONException?>(JSONException::class.java) {
            decode(fixture()!!.put("rpm", 2.5))
        }
        val missing = fixture()
        missing!!.remove("beltWarning")
        assertThrows<JSONException?>(JSONException::class.java) { decode(missing) }
    }

    @Test
    open fun eventsDriveWatchdogWithoutInventingMethodResponseCodes() {
        val monitor = SomeipConnectionMonitor()
        monitor.start()
        monitor.onAvailability(true, 0)
        monitor.onEvent(100)
        assertEquals(SomeipConnectionMonitor.State.ONLINE, monitor.snapshot().state)
        assertNull(monitor.snapshot().returnCode)
        assertFalse(monitor.checkTimeout(3099))
        assertTrue(monitor.checkTimeout(3100))
        monitor.onEvent(3200)
        assertEquals(SomeipConnectionMonitor.State.ONLINE, monitor.snapshot().state)
        monitor.onAvailability(false, 3300)
        monitor.onEvent(3400)
        assertEquals(SomeipConnectionMonitor.State.UNAVAILABLE, monitor.snapshot().state)
    }
}
