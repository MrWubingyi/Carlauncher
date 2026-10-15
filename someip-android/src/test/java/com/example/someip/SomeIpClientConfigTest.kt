package com.example.someip

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class SomeIpClientConfigTest {
    private val service = SomeIpService(0x1111, 0x2222, 0x8001, 1, 1, "test-client")

    @Test
    fun protocolBoundaryIdentifiersAreAccepted() {
        val low = SomeIpService(0, 0, 0x8000, 0, 0, "low", 1, 1)
        val high = SomeIpService(0xfffe, 0xfffe, 0xfffe, 0xfffe, 0x7fff, "high", 1_048_576, 1_048_576)
        assertEquals(0x8000, low.eventId)
        assertEquals(0x7fff, high.methodId)
    }

    @Test
    fun negativeOverflowAndWildcardIdentifiersAreRejected() {
        for (invalid in listOf(-1, 0xffff, 0x10000)) {
            assertThrows(IllegalArgumentException::class.java) { service.copy(serviceId = invalid) }
            assertThrows(IllegalArgumentException::class.java) { service.copy(instanceId = invalid) }
            assertThrows(IllegalArgumentException::class.java) { service.copy(eventGroupId = invalid) }
        }
        for (invalid in listOf(-1, 0x7fff, 0xffff, 0x10000)) {
            assertThrows(IllegalArgumentException::class.java) { service.copy(eventId = invalid) }
        }
        for (invalid in listOf(-1, 0x8000, 0xffff, 0x10000)) {
            assertThrows(IllegalArgumentException::class.java) { service.copy(methodId = invalid) }
        }
    }

    @Test
    fun payloadLimitsCannotDisableBoundsOrOverflowNativeAllocation() {
        for (invalid in listOf(-1, 0, 1_048_577, Int.MAX_VALUE)) {
            assertThrows(IllegalArgumentException::class.java) {
                service.copy(maxEventPayloadBytes = invalid)
            }
            assertThrows(IllegalArgumentException::class.java) {
                service.copy(maxRequestPayloadBytes = invalid)
            }
        }
    }

    @Test
    fun namesAndPathsCannotBeBlankOrContainNativeStringTerminator() {
        for (invalid in listOf("", " \t", "client\u0000other")) {
            assertThrows(IllegalArgumentException::class.java) { service.copy(applicationName = invalid) }
            assertThrows(IllegalArgumentException::class.java) { SomeIpClientConfig(invalid, service) }
        }
    }
}
