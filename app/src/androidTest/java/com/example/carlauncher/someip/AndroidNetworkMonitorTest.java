package com.example.carlauncher.someip;

import android.net.ConnectivityManager;
import android.net.LinkProperties;
import android.net.Network;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.util.ArrayList;
import java.util.List;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class AndroidNetworkMonitorTest {
    @Test public void registrationIsIdempotentAndEmptyOrLatePlatformCallbacksStayDown() {
        List<Boolean> states = new ArrayList<>();
        int[] calls = new int[2];
        AndroidNetworkMonitor monitor = new AndroidNetworkMonitor("10.0.2.16", null,
                (address, iface, up, route) -> states.add(up), new AndroidNetworkMonitor.Registration() {
                    @Override public void register(ConnectivityManager.NetworkCallback cb) { calls[0]++; }
                    @Override public void unregister(ConnectivityManager.NetworkCallback cb) { calls[1]++; }
                });
        try {
            monitor.start();
            monitor.start();
            Network network = Network.fromNetworkHandle((111L << 32) | 0xcafed00dL);
            monitor.callback.onLinkPropertiesChanged(network, new LinkProperties());
            monitor.callback.onBlockedStatusChanged(network, false);
            monitor.callback.onLost(network);
            assertEquals(1, states.size());
            assertFalse(states.get(0));
            monitor.close();
            monitor.callback.onLinkPropertiesChanged(network, new LinkProperties());
            assertEquals(1, states.size());
        } finally { monitor.close(); }
        assertArrayEquals(new int[]{1, 1}, calls);
    }

    @Test public void registrationFailureDoesNotLeakOrAcceptCallbacks() {
        List<Boolean> states = new ArrayList<>();
        AndroidNetworkMonitor monitor = new AndroidNetworkMonitor("10.0.2.16", null,
                (address, iface, up, route) -> states.add(up), new AndroidNetworkMonitor.Registration() {
                    @Override public void register(ConnectivityManager.NetworkCallback cb) { throw new SecurityException("denied"); }
                    @Override public void unregister(ConnectivityManager.NetworkCallback cb) { fail("not registered"); }
                });
        try { monitor.start(); fail("Expected registration failure"); }
        catch (SecurityException expected) { assertEquals("denied", expected.getMessage()); }
        finally { monitor.close(); }
        assertEquals(1, states.size());
        assertFalse(states.get(0));
    }
}
