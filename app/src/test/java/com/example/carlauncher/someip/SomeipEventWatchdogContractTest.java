package com.example.carlauncher.someip;

import org.junit.Test;
import static org.junit.Assert.*;
import static com.example.carlauncher.someip.SomeipConnectionMonitor.State.*;

/** VS-01/13/20: explicit monotonic times, no sleeping or Android scheduler. */
public class SomeipEventWatchdogContractTest {
    private SomeipConnectionMonitor availableAt(long time) {
        SomeipConnectionMonitor monitor = new SomeipConnectionMonitor();
        monitor.start();
        monitor.onAvailability(true, time);
        return monitor;
    }

    @Test public void availableWithoutEvent_timesOutAtExactDeadline() {
        SomeipConnectionMonitor monitor = availableAt(100);
        assertEquals(WAITING_RESPONSE, monitor.snapshot().getState());
        assertFalse(monitor.checkTimeout(3099));
        assertTrue(monitor.checkTimeout(3100));
        assertEquals(RESPONSE_TIMEOUT, monitor.snapshot().getState());
        assertFalse(monitor.checkTimeout(3101));
    }

    @Test public void eventRefreshesDeadline_andRecoversAfterTimeoutWithoutMethodCode() {
        SomeipConnectionMonitor monitor = availableAt(100);
        monitor.onEvent(200);
        assertEquals(ONLINE, monitor.snapshot().getState());
        assertNull(monitor.snapshot().getReturnCode());
        assertFalse(monitor.checkTimeout(3199));
        assertTrue(monitor.checkTimeout(3200));
        assertFalse(monitor.checkTimeout(3201));
        monitor.onEvent(4000);
        assertEquals(ONLINE, monitor.snapshot().getState());
        assertNull(monitor.snapshot().getReturnCode());
        assertFalse(monitor.checkTimeout(6999));
        assertTrue(monitor.checkTimeout(7000));
    }

    @Test public void duplicateAvailability_doesNotExtendLastEventDeadline() {
        SomeipConnectionMonitor monitor = availableAt(100);
        monitor.onEvent(200);
        monitor.onAvailability(true, 3199);
        assertTrue(monitor.checkTimeout(3200));
    }

    @Test public void unavailable_ignoresQueuedEventUntilRediscovered() {
        SomeipConnectionMonitor monitor = availableAt(100);
        monitor.onEvent(200);
        monitor.onAvailability(false, 300);
        monitor.onEvent(400);
        assertEquals(UNAVAILABLE, monitor.snapshot().getState());
        assertFalse(monitor.checkTimeout(10000));
        monitor.onAvailability(true, 11000);
        assertEquals(WAITING_RESPONSE, monitor.snapshot().getState());
        monitor.onEvent(11100);
        assertEquals(ONLINE, monitor.snapshot().getState());
        assertFalse(monitor.checkTimeout(14099));
        assertTrue(monitor.checkTimeout(14100));
    }

    @Test public void stopped_ignoresLateEventAndAvailability() {
        SomeipConnectionMonitor monitor = availableAt(100);
        monitor.onEvent(200);
        monitor.stop();
        monitor.onEvent(300);
        monitor.onAvailability(true, 400);
        assertEquals(STOPPED, monitor.snapshot().getState());
        assertFalse(monitor.snapshot().isAvailable());
        assertFalse(monitor.checkTimeout(10000));
    }

    @Test public void startFailure_ignoresEventsUntilExplicitRestart() {
        SomeipConnectionMonitor monitor = new SomeipConnectionMonitor();
        monitor.start();
        monitor.startFailed();
        monitor.onAvailability(true, 100);
        monitor.onEvent(200);
        assertEquals(START_FAILED, monitor.snapshot().getState());
        monitor.start();
        monitor.onAvailability(true, 300);
        assertEquals(WAITING_RESPONSE, monitor.snapshot().getState());
        monitor.onEvent(400);
        assertEquals(ONLINE, monitor.snapshot().getState());
    }
}
