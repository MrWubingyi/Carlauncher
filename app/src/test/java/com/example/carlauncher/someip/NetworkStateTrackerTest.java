package com.example.carlauncher.someip;

import org.junit.Test;
import static org.junit.Assert.*;

public class NetworkStateTrackerTest {
    @Test public void requiresConfiguredAddressAndUnblockedVerdict() {
        NetworkStateTracker tracker = new NetworkStateTracker("10.0.2.16", true);
        tracker.link(1, "eth0", new String[]{"192.168.1.2"}, true);
        tracker.blocked(1, false);
        assertFalse(tracker.snapshot().available);
        tracker.link(2, "wlan0", new String[]{"10.0.2.16"}, true);
        assertFalse(tracker.snapshot().available);
        tracker.blocked(2, false);
        assertTrue(tracker.snapshot().available);
        assertTrue(tracker.snapshot().route);
        tracker.blocked(2, true);
        assertFalse(tracker.snapshot().available);
        tracker.blocked(2, false);
        assertTrue(tracker.snapshot().available);
    }
    @Test public void addressChangeLossAndClearRevokeAvailability() {
        NetworkStateTracker tracker = new NetworkStateTracker("10.0.2.16", false);
        tracker.link(1, "wlan0", new String[]{"10.0.2.16"}, false);
        assertTrue(tracker.snapshot().available);
        assertFalse(tracker.snapshot().route);
        tracker.link(1, "wlan0", new String[]{"10.0.2.17"}, true);
        assertFalse(tracker.snapshot().available);
        tracker.link(1, "eth0", new String[]{"10.0.2.16"}, true);
        assertEquals("eth0", tracker.snapshot().iface);
        tracker.lost(1);
        assertFalse(tracker.snapshot().available);
        tracker.link(1, null, new String[]{"10.0.2.16"}, true);
        assertFalse(tracker.snapshot().available);
        tracker.link(1, "eth0", new String[]{"10.0.2.16"}, true);
        tracker.clear();
        assertFalse(tracker.snapshot().available);
    }
    @Test public void unrelatedLossPreservesAddressAndChoiceIsDeterministic() {
        NetworkStateTracker tracker = new NetworkStateTracker("10.0.2.16", false);
        tracker.link(1, "wlan0", new String[]{"10.0.2.16"}, true);
        tracker.link(2, "eth0", new String[]{"10.0.2.16"}, false);
        assertEquals("wlan0", tracker.snapshot().iface);
        tracker.link(2, "eth0", new String[]{"10.0.2.16"}, true);
        assertEquals("eth0", tracker.snapshot().iface);
        tracker.lost(3);
        assertEquals("eth0", tracker.snapshot().iface);
        tracker.lost(2);
        assertEquals("wlan0", tracker.snapshot().iface);
    }
}
