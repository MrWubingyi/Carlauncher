package com.example.carlauncher.someip

import com.example.carlauncher.model.VehicleState
import java.util.ArrayList
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

open class VSomeIpNativeTransportTest {

    private var listener: TestNativeListener? = null
    private var transport: VSomeIpNativeTransport? = null

    @Before
    open fun setUp() {
        listener = TestNativeListener()
        // Context is null in pure JVM unit test; we test listener handling, decoding & filtering
        // logic
        transport =
            object : VSomeIpNativeTransport(null) {
                override fun start(): Boolean {
                    setListener(listener)
                    return true
                }
            }
        transport!!.setListener(listener)
    }

    @Test
    open fun onAvailable_notifiesListener() {
        transport!!.onAvailable(true)
        assertEquals(1, listener!!.availabilities.size.toLong())
        assertTrue(listener!!.availabilities.get(0)!!)

        transport!!.onAvailable(false)
        assertEquals(2, listener!!.availabilities.size.toLong())
        assertFalse(listener!!.availabilities.get(1)!!)
    }

    @Test
    @Throws(Exception::class)
    open fun onVehicleEvent_decodesAndFiltersOutStaleSequences() {
        val payloadSeq1 = createEventPayload(1, 1000L, 60)
        val payloadSeq2 = createEventPayload(2, 2000L, 70)
        val payloadStaleSeq =
            createEventPayload(1, 1500L, 65) // Sequence 1 <= last sequence 2 -> rejected

        transport!!.onVehicleEvent(payloadSeq1)
        transport!!.onVehicleEvent(payloadSeq2)
        transport!!.onVehicleEvent(payloadStaleSeq)

        assertEquals(2, listener!!.vehicleStates.size.toLong())
        assertEquals(1, listener!!.vehicleStates.get(0)!!.sequence)
        assertEquals(2, listener!!.vehicleStates.get(1)!!.sequence)
    }

    @Test
    @Throws(Exception::class)
    open fun onVehicleEvent_filtersOutStaleTimestamps() {
        val payloadTime1000 = createEventPayload(5, 1000L, 60)
        val payloadTime500 =
            createEventPayload(6, 500L, 70) // Timestamp 500 < last timestamp 1000 -> rejected

        transport!!.onVehicleEvent(payloadTime1000)
        transport!!.onVehicleEvent(payloadTime500)

        assertEquals(1, listener!!.vehicleStates.size.toLong())
        assertEquals(1000L, listener!!.vehicleStates.get(0)!!.timestampMs)
    }

    @Test
    @Throws(Exception::class)
    open fun stop_discardsLateCallbacks() {
        transport!!.stop()

        val payload = createEventPayload(10, 5000L, 80)
        transport!!.onVehicleEvent(payload)
        transport!!.onAvailable(true)

        assertEquals(0, listener!!.vehicleStates.size.toLong())
        assertEquals(0, listener!!.availabilities.size.toLong())
    }

    // ---- Helpers ----

    /* VS-15: same timestamp permits only a strictly greater sequence. */
    @Test
    @Throws(Exception::class)
    open fun equalTimestamp_rejectsDuplicateAndLowerSequence() {
        transport!!.onVehicleEvent(createEventPayload(10, 2000, 10))
        transport!!.onVehicleEvent(createEventPayload(99, 1999, 99))
        transport!!.onVehicleEvent(createEventPayload(10, 2000, 20))
        transport!!.onVehicleEvent(createEventPayload(9, 2000, 30))
        transport!!.onVehicleEvent(createEventPayload(11, 2000, 40))

        assertEquals(2, listener!!.vehicleStates.size.toLong())
        assertEquals(10, listener!!.vehicleStates.get(0)!!.vehSpeedKph.toLong())
        assertEquals(11L, listener!!.vehicleStates.get(1)!!.sequence)
        assertEquals(40, listener!!.vehicleStates.get(1)!!.vehSpeedKph.toLong())
        assertTrue(listener!!.errors.isEmpty())
    }

    /* VS-16: process restart need not produce an unavailable callback. */
    @Test
    @Throws(Exception::class)
    open fun newerTimestamp_acceptsRestartedSequenceWithoutDisconnect() {
        transport!!.onVehicleEvent(createEventPayload(9000, 2000, 60))
        transport!!.onVehicleEvent(createEventPayload(1, 2100, 17))

        assertEquals(2, listener!!.vehicleStates.size.toLong())
        assertEquals(1L, listener!!.vehicleStates.get(1)!!.sequence)
        assertEquals(17, listener!!.vehicleStates.get(1)!!.vehSpeedKph.toLong())
    }

    /* VS-16: also cover restart after an explicit availability transition. */
    @Test
    @Throws(Exception::class)
    open fun rediscovery_acceptsNewPublisherFirstFrame() {
        transport!!.onVehicleEvent(createEventPayload(9000, 2000, 60))
        transport!!.onAvailable(false)
        transport!!.onAvailable(true)
        transport!!.onVehicleEvent(createEventPayload(1, 2100, 17))

        assertEquals(2, listener!!.vehicleStates.size.toLong())
        assertEquals(1L, listener!!.vehicleStates.get(1)!!.sequence)
        assertEquals(2100L, listener!!.vehicleStates.get(1)!!.timestampMs)
    }

    /* VS-17: UDP loss must not cause waiting for the missing sequence. */
    @Test
    @Throws(Exception::class)
    open fun sequenceGap_acceptsLatestCompleteSnapshot() {
        transport!!.onVehicleEvent(createEventPayload(101, 1000, 17))
        transport!!.onVehicleEvent(createEventPayload(104, 1300, 83))
        assertEquals(2, listener!!.vehicleStates.size.toLong())
        assertEquals(104L, listener!!.vehicleStates.get(1)!!.sequence)
        assertEquals(83, listener!!.vehicleStates.get(1)!!.vehSpeedKph.toLong())
    }

    /* VS-20: detaching a consumer suppresses callbacks without invoking JNI. */
    @Test
    @Throws(Exception::class)
    open fun removedListener_receivesNoFurtherCallbacks() {
        transport!!.setListener(null)
        transport!!.onVehicleEvent(createEventPayload(101, 1000, 17))
        transport!!.onAvailable(true)
        transport!!.onResponse(false, 1)
        assertTrue(listener!!.vehicleStates.isEmpty())
        assertTrue(listener!!.availabilities.isEmpty())
        assertTrue(listener!!.errors.isEmpty())
    }

    @Throws(Exception::class)
    private fun createEventPayload(seq: Long, timestampMs: Long, speedKph: Int): ByteArray? {
        val j = JSONObject()
        j.put("version", 1)
        j.put("seq", seq)
        j.put("timestampMs", timestampMs)
        j.put("speedKph", speedKph)
        j.put("rpm", 2500)
        j.put("soc", 90)
        j.put("gear", 3)
        j.put("turnSignal", 0)
        j.put("warning", 0)
        j.put("validity", 0)
        j.put("dataStatus", 0)
        j.put("parkingBrake", false)
        j.put("doorLock", true)
        j.put("beltWarning", false)
        j.put("headlightsState", 0)
        j.put("highBeamLightsState", 0)
        j.put("engineCoolantTemp", 88.0)
        j.put("evBatteryLevel", 90.0)
        return j.toString().toByteArray(java.nio.charset.StandardCharsets.UTF_8)
    }

    private open class TestNativeListener : NativeVehicleTransport.Listener {
        internal val availabilities: MutableList<Boolean?> = ArrayList<Boolean?>()
        internal val vehicleStates: MutableList<VehicleState?> = ArrayList<VehicleState?>()
        internal val errors: MutableList<Throwable?> = ArrayList<Throwable?>()

        override fun onAvailabilityChanged(available: Boolean) {
            availabilities.add(available)
        }

        override fun onVehicleState(state: VehicleState?) {
            vehicleStates.add(state)
        }

        override fun onError(throwable: Throwable?) {
            errors.add(throwable)
        }
    }
}
