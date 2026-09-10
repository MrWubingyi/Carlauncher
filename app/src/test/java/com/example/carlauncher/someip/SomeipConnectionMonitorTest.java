package com.example.carlauncher.someip;

import org.junit.Test;
import static org.junit.Assert.*;
import static com.example.carlauncher.someip.SomeipConnectionMonitor.State.*;

public class SomeipConnectionMonitorTest {
    private SomeipConnectionMonitor available(long now) {
        SomeipConnectionMonitor monitor = new SomeipConnectionMonitor();
        monitor.start();
        monitor.onAvailability(true, now);
        return monitor;
    }

    @Test public void onlySuccessfulResponseEstablishesOnline() {
        SomeipConnectionMonitor monitor = available(100);
        assertEquals(WAITING_RESPONSE, monitor.snapshot().getState());
        assertNull(monitor.snapshot().getReturnCode());
        monitor.onResponse(false, 1, 200);
        assertEquals(RESPONSE_ERROR, monitor.snapshot().getState());
        assertEquals(Integer.valueOf(1), monitor.snapshot().getReturnCode());
        monitor.onResponse(true, 0, 300);
        assertEquals(ONLINE, monitor.snapshot().getState());
    }

    @Test public void firstResponseDeadlineIsInclusiveAndDuplicateOfferCannotExtendIt() {
        SomeipConnectionMonitor monitor = available(100);
        assertFalse(monitor.checkTimeout(3099));
        monitor.onAvailability(true, 3099);
        assertTrue(monitor.checkTimeout(3100));
        assertEquals(RESPONSE_TIMEOUT, monitor.snapshot().getState());
        assertTrue(monitor.snapshot().isAvailable());
        assertFalse(monitor.checkTimeout(3200));
    }

    @Test public void responseRefreshesDeadlineAndRestoresAfterTimeout() {
        SomeipConnectionMonitor monitor = available(0);
        monitor.onResponse(true, 0, 1000);
        assertFalse(monitor.checkTimeout(3999));
        assertTrue(monitor.checkTimeout(4000));
        monitor.onResponse(true, 0, 4100);
        assertEquals(ONLINE, monitor.snapshot().getState());
        assertFalse(monitor.checkTimeout(7099));
        assertTrue(monitor.checkTimeout(7100));
    }

    @Test public void errorsShowTheirCodeAndEventuallyTimeOutWithoutFurtherResponses() {
        SomeipConnectionMonitor monitor = available(0);
        monitor.onResponse(true, 1, 10);
        assertEquals(RESPONSE_ERROR, monitor.snapshot().getState());
        monitor.onResponse(false, 0, 20); // MT_ERROR with E_OK is still an error.
        assertEquals(RESPONSE_ERROR, monitor.snapshot().getState());
        assertFalse(monitor.checkTimeout(3019));
        assertTrue(monitor.checkTimeout(3020));
    }

    @Test public void unavailableInvalidatesSuccessAndIgnoresQueuedResponses() {
        SomeipConnectionMonitor monitor = available(0);
        monitor.onResponse(true, 0, 10);
        monitor.onAvailability(false, 100);
        monitor.onResponse(true, 0, 110);
        assertEquals(UNAVAILABLE, monitor.snapshot().getState());
        assertFalse(monitor.snapshot().isAvailable());
        assertNull(monitor.snapshot().getReturnCode());
        assertFalse(monitor.checkTimeout(10000));
        monitor.onAvailability(true, 11000);
        assertEquals(WAITING_RESPONSE, monitor.snapshot().getState());
        assertFalse(monitor.checkTimeout(13999));
        assertTrue(monitor.checkTimeout(14000));
    }

    @Test public void stoppedAndFailedSessionsIgnoreLateEventsAndCanRestart() {
        SomeipConnectionMonitor monitor = available(0);
        monitor.stop();
        monitor.onAvailability(true, 1);
        monitor.onResponse(true, 0, 2);
        monitor.startFailed();
        assertEquals(STOPPED, monitor.snapshot().getState());
        monitor.start();
        monitor.startFailed();
        monitor.onAvailability(true, 3);
        monitor.onResponse(true, 0, 4);
        assertEquals(START_FAILED, monitor.snapshot().getState());
        monitor.start();
        assertEquals(STARTING, monitor.snapshot().getState());
        assertFalse(monitor.checkTimeout(10000));
        monitor.onAvailability(true, 11000);
        monitor.onResponse(true, 0, 11001);
        assertEquals(ONLINE, monitor.snapshot().getState());
    }
}
