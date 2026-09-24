package com.example.carlauncher.someip

import java.nio.charset.StandardCharsets
import java.util.ArrayList
import java.util.Arrays
import org.json.JSONException
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/* VS-09/10/27: invalid wire data must be rejected, not coerced. */
@RunWith(Parameterized::class)
open class VehicleEventRejectionContractTest(name: String?, private val payload: ByteArray?) {

    @Test
    open fun decode_rejectsInvalidWireData() {
        assertThrows<JSONException?>(JSONException::class.java) {
            VehicleEventCodec.decode(payload)
        }
    }

    companion object {
        @Parameterized.Parameters(name = "{0}")
        @Throws(Exception::class)
        @JvmStatic
        fun cases(): Collection<Array<Any?>?>? {
            val rows = ArrayList<Array<Any?>?>()
            val baseline = ApprovedEventFixture.frame()
            val fields = baseline!!.keys()
            while (fields!!.hasNext()) {
                val field = fields!!.next()
                val missing = ApprovedEventFixture.frame()
                missing!!.remove(field)
                rows.add(arrayOf<Any?>("missing " + field, ApprovedEventFixture.bytes(missing)))
                rows.add(arrayOf<Any?>("null " + field, changed(field, JSONObject.NULL)))
                rows.add(arrayOf<Any?>("string " + field, changed(field, "wrong-type")))
            }
            for (field in arrayOf<String>("parkingBrake", "doorLock", "beltWarning")) {
                rows.add(arrayOf<Any?>("numeric boolean " + field, changed(field, 1)))
            }
            val invalid =
                arrayOf<Array<Any?>?>(
                    arrayOf<Any?>("version", 2),
                    arrayOf<Any?>("seq", -1),
                    arrayOf<Any?>("seq", 1.5),
                    arrayOf<Any?>("timestampMs", 0),
                    arrayOf<Any?>("timestampMs", -1),
                    arrayOf<Any?>("timestampMs", 1.5),
                    arrayOf<Any?>("speedKph", 1.5),
                    arrayOf<Any?>("rpm", 1.5),
                    arrayOf<Any?>("soc", 1.5),
                    arrayOf<Any?>("gear", -1),
                    arrayOf<Any?>("gear", 4),
                    arrayOf<Any?>("turnSignal", -1),
                    arrayOf<Any?>("turnSignal", 4),
                    arrayOf<Any?>("warning", -1),
                    arrayOf<Any?>("warning", 3),
                    arrayOf<Any?>("validity", -1),
                    arrayOf<Any?>("validity", 4),
                    arrayOf<Any?>("dataStatus", -1),
                    arrayOf<Any?>("dataStatus", 5),
                    arrayOf<Any?>("rpm", 2147483648L),
                )
            for (pair in invalid!!) {
                rows.add(
                    arrayOf<Any?>(
                        "${pair!![0]}=${pair[1]}",
                        changed(pair!![0] as String, pair!![1]),
                    )
                )
            }
            rows.add(arrayOf<Any?>("null payload", null))
            rows.add(arrayOf<Any?>("empty payload", ByteArray(0)))
            rows.add(arrayOf<Any?>("array root", "[]".toByteArray(StandardCharsets.UTF_8)))
            rows.add(
                arrayOf<Any?>(
                    "truncated object",
                    "{\"version\":1".toByteArray(StandardCharsets.UTF_8),
                )
            )
            val valid = ApprovedEventFixture.bytes(baseline)
            val oversized = Arrays.copyOf(valid, 4097)
            Arrays.fill(oversized, valid!!.size, oversized!!.size, ' '.code.toByte())
            rows.add(arrayOf<Any?>("4097 bytes", oversized))
            // NUL followed by content must not be treated as a valid JSON terminator.
            rows.add(
                arrayOf<Any?>(
                    "embedded NUL",
                    (baseline!!.toString()!! + "\u0000garbage").toByteArray(StandardCharsets.UTF_8),
                )
            )
            return rows
        }

        @Throws(Exception::class)
        @JvmStatic
        private fun changed(field: String, value: Any?): ByteArray? =
            ApprovedEventFixture.bytes(ApprovedEventFixture.frame()!!.put(field, value))
    }
}
