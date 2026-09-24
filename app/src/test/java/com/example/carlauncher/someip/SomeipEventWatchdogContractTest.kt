package com.example.carlauncher.someip

import com.example.carlauncher.someip.SomeipConnectionMonitor.State.*
import org.junit.Assert.*
import org.junit.Test

/* VS-01/13/20: explicit monotonic times, no sleeping or Android scheduler. */
open class SomeipEventWatchdogContractTest {
    private fun availableAt(time: Long): SomeipConnectionMonitor? {
        val monitor = SomeipConnectionMonitor()
        monitor.start()
        monitor.onAvailability(true, time)
        return monitor
    }

    @Test
    open fun availableWithoutEvent_timesOutAtExactDeadline() {
        val monitor = availableAt(100)
        assertEquals(WAITING_RESPONSE, monitor!!.snapshot().state)
        assertFalse(monitor!!.checkTimeout(3099))
        assertTrue(monitor!!.checkTimeout(3100))
        assertEquals(RESPONSE_TIMEOUT, monitor!!.snapshot().state)
        assertFalse(monitor!!.checkTimeout(3101))
    }

    @Test
    open fun eventRefreshesDeadline_andRecoversAfterTimeoutWithoutMethodCode() {
        val monitor = availableAt(100)
        monitor!!.onEvent(200)
        assertEquals(ONLINE, monitor!!.snapshot().state)
        assertNull(monitor!!.snapshot().returnCode)
        assertFalse(monitor!!.checkTimeout(3199))
        assertTrue(monitor!!.checkTimeout(3200))
        assertFalse(monitor!!.checkTimeout(3201))
        monitor!!.onEvent(4000)
        assertEquals(ONLINE, monitor!!.snapshot().state)
        assertNull(monitor!!.snapshot().returnCode)
        assertFalse(monitor!!.checkTimeout(6999))
        assertTrue(monitor!!.checkTimeout(7000))
    }

    @Test
    open fun duplicateAvailability_doesNotExtendLastEventDeadline() {
        val monitor = availableAt(100)
        monitor!!.onEvent(200)
        monitor!!.onAvailability(true, 3199)
        assertTrue(monitor!!.checkTimeout(3200))
    }

    @Test
    open fun unavailable_ignoresQueuedEventUntilRediscovered() {
        val monitor = availableAt(100)
        monitor!!.onEvent(200)
        monitor!!.onAvailability(false, 300)
        monitor!!.onEvent(400)
        assertEquals(UNAVAILABLE, monitor!!.snapshot().state)
        assertFalse(monitor!!.checkTimeout(10000))
        monitor!!.onAvailability(true, 11000)
        assertEquals(WAITING_RESPONSE, monitor!!.snapshot().state)
        monitor!!.onEvent(11100)
        assertEquals(ONLINE, monitor!!.snapshot().state)
        assertFalse(monitor!!.checkTimeout(14099))
        assertTrue(monitor!!.checkTimeout(14100))
    }

    @Test
    open fun stopped_ignoresLateEventAndAvailability() {
        val monitor = availableAt(100)
        monitor!!.onEvent(200)
        monitor!!.stop()
        monitor!!.onEvent(300)
        monitor!!.onAvailability(true, 400)
        assertEquals(STOPPED, monitor!!.snapshot().state)
        assertFalse(monitor!!.snapshot().isAvailable)
        assertFalse(monitor!!.checkTimeout(10000))
    }

    @Test
    open fun startFailure_ignoresEventsUntilExplicitRestart() {
        val monitor = SomeipConnectionMonitor()
        monitor.start()
        monitor.startFailed()
        monitor.onAvailability(true, 100)
        monitor.onEvent(200)
        assertEquals(START_FAILED, monitor.snapshot().state)
        monitor.start()
        monitor.onAvailability(true, 300)
        assertEquals(WAITING_RESPONSE, monitor.snapshot().state)
        monitor.onEvent(400)
        assertEquals(ONLINE, monitor.snapshot().state)
    }
}
