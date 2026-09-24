package com.example.carlauncher.someip

import com.example.carlauncher.model.VehicleState
import java.util.Arrays
import org.junit.Assert.*
import org.junit.Test

open class VehicleEventLengthContractTest {
    /* VS-10: exact inclusive byte limit, with otherwise valid JSON. */
    @Test
    @Throws(Exception::class)
    open fun decode_acceptsExactly4096Bytes() {
        val original = ApprovedEventFixture.bytes(ApprovedEventFixture.frame())
        val padded = Arrays.copyOf(original, 4096)
        Arrays.fill(padded, original!!.size, padded!!.size, ' '.code.toByte())
        val state = VehicleEventCodec.decode(padded)
        assertEquals(101L, state!!.sequence)
        assertEquals(1700000000100L, state!!.timestampMs)
        assertEquals(36, state!!.vehSpeedKph.toLong())
    }
}
