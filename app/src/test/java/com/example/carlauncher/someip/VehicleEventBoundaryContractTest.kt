package com.example.carlauncher.someip

import com.example.carlauncher.data.DataStatus
import com.example.carlauncher.model.DataValidity
import com.example.carlauncher.model.VehicleState
import java.util.ArrayList
import org.json.JSONException
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/* VS-08: approved Q1 ranges; decoding only, not watchdog acceptance. */
@RunWith(Parameterized::class)
open class VehicleEventBoundaryContractTest(
    private val field: String,
    private val value: Int,
    private val accepted: Boolean,
) {

    @Test
    @Throws(Exception::class)
    open fun decode_enforcesApprovedBoundary() {
        val payload = ApprovedEventFixture.bytes(ApprovedEventFixture.frame()!!.put(field, value))
        if (!accepted && (field == "headlightsState" || field == "highBeamLightsState")) {
            assertThrows<JSONException?>(JSONException::class.java) {
                VehicleEventCodec.decode(payload)
            }
            return
        }
        val state = VehicleEventCodec.decode(payload)
        assertEquals(if (accepted) DataStatus.NORMAL else DataStatus.INVALID, state!!.dataStatus)
        assertEquals(
            if (accepted) DataValidity.VALID
            else if (field == "speedKph") DataValidity.INVALID_SPEED else DataValidity.INCOMPLETE,
            state!!.validity,
        )
    }

    companion object {
        @Parameterized.Parameters(name = "{0}={1}, accepted={2}")
        @JvmStatic
        fun cases(): Collection<Array<Any?>?>? {
            val rows = ArrayList<Array<Any?>?>()
            boundary(rows, "speedKph", 0, 200)
            boundary(rows, "rpm", 0, 8000)
            boundary(rows, "soc", 0, 100)
            boundary(rows, "engineCoolantTemp", -50, 200)
            boundary(rows, "evBatteryLevel", 0, 100)
            boundary(rows, "headlightsState", 0, 100)
            boundary(rows, "highBeamLightsState", 0, 100)
            return rows
        }

        @JvmStatic
        private fun boundary(
            rows: MutableCollection<Array<Any?>?>?,
            field: String?,
            min: Int,
            max: Int,
        ) {
            rows!!.add(arrayOf<Any?>(field, min - 1, false))
            rows!!.add(arrayOf<Any?>(field, min, true))
            rows!!.add(arrayOf<Any?>(field, max, true))
            rows!!.add(arrayOf<Any?>(field, max + 1, false))
        }
    }
}
