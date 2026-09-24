package com.example.carlauncher.someip

import com.example.carlauncher.someip.SomeipConnectionMonitor.State.*
import org.junit.Assert.*
import org.junit.Test

open class SomeipConnectionMonitorTest {
    private fun available(now: Long): SomeipConnectionMonitor? {
        val monitor = SomeipConnectionMonitor()
        monitor.start()
        monitor.onAvailability(true, now)
        return monitor
    }

    @Test
    open fun onlySuccessfulResponseEstablishesOnline() {
        val monitor = available(100)
        assertEquals(WAITING_RESPONSE, monitor!!.snapshot().state)
        assertNull(monitor!!.snapshot().returnCode)
        monitor!!.onResponse(false, 1, 200)
        assertEquals(RESPONSE_ERROR, monitor!!.snapshot().state)
        assertEquals(Integer.valueOf(1), monitor!!.snapshot().returnCode)
        monitor!!.onResponse(true, 0, 300)
        assertEquals(ONLINE, monitor!!.snapshot().state)
    }

    @Test
    open fun firstResponseDeadlineIsInclusiveAndDuplicateOfferCannotExtendIt() {
        val monitor = available(100)
        assertFalse(monitor!!.checkTimeout(3099))
        monitor!!.onAvailability(true, 3099)
        assertTrue(monitor!!.checkTimeout(3100))
        assertEquals(RESPONSE_TIMEOUT, monitor!!.snapshot().state)
        assertTrue(monitor!!.snapshot().isAvailable)
        assertFalse(monitor!!.checkTimeout(3200))
    }

    @Test
    open fun responseRefreshesDeadlineAndRestoresAfterTimeout() {
        val monitor = available(0)
        monitor!!.onResponse(true, 0, 1000)
        assertFalse(monitor!!.checkTimeout(3999))
        assertTrue(monitor!!.checkTimeout(4000))
        monitor!!.onResponse(true, 0, 4100)
        assertEquals(ONLINE, monitor!!.snapshot().state)
        assertFalse(monitor!!.checkTimeout(7099))
        assertTrue(monitor!!.checkTimeout(7100))
    }

    @Test
    open fun errorsShowTheirCodeAndEventuallyTimeOutWithoutFurtherResponses() {
        val monitor = available(0)
        monitor!!.onResponse(true, 1, 10)
        assertEquals(RESPONSE_ERROR, monitor!!.snapshot().state)
        monitor!!.onResponse(false, 0, 20) // MT_ERROR with E_OK is still an error.
        assertEquals(RESPONSE_ERROR, monitor!!.snapshot().state)
        assertFalse(monitor!!.checkTimeout(3019))
        assertTrue(monitor!!.checkTimeout(3020))
    }

    @Test
    open fun unavailableInvalidatesSuccessAndIgnoresQueuedResponses() {
        val monitor = available(0)
        monitor!!.onResponse(true, 0, 10)
        monitor!!.onAvailability(false, 100)
        monitor!!.onResponse(true, 0, 110)
        assertEquals(UNAVAILABLE, monitor!!.snapshot().state)
        assertFalse(monitor!!.snapshot().isAvailable)
        assertNull(monitor!!.snapshot().returnCode)
        assertFalse(monitor!!.checkTimeout(10000))
        monitor!!.onAvailability(true, 11000)
        assertEquals(WAITING_RESPONSE, monitor!!.snapshot().state)
        assertFalse(monitor!!.checkTimeout(13999))
        assertTrue(monitor!!.checkTimeout(14000))
    }

    @Test
    open fun stoppedAndFailedSessionsIgnoreLateEventsAndCanRestart() {
        val monitor = available(0)
        monitor!!.stop()
        monitor!!.onAvailability(true, 1)
        monitor!!.onResponse(true, 0, 2)
        monitor!!.startFailed()
        assertEquals(STOPPED, monitor!!.snapshot().state)
        monitor!!.start()
        monitor!!.startFailed()
        monitor!!.onAvailability(true, 3)
        monitor!!.onResponse(true, 0, 4)
        assertEquals(START_FAILED, monitor!!.snapshot().state)
        monitor!!.start()
        assertEquals(STARTING, monitor!!.snapshot().state)
        assertFalse(monitor!!.checkTimeout(10000))
        monitor!!.onAvailability(true, 11000)
        monitor!!.onResponse(true, 0, 11001)
        assertEquals(ONLINE, monitor!!.snapshot().state)
    }
}
