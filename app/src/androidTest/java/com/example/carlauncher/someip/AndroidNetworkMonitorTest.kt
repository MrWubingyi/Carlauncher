package com.example.carlauncher.someip

import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.util.ArrayList
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
open class AndroidNetworkMonitorTest {
    @Test
    open fun registrationIsIdempotentAndEmptyOrLatePlatformCallbacksStayDown() {
        val states = ArrayList<Boolean?>()
        val calls = IntArray(2)
        val monitor =
            AndroidNetworkMonitor(
                "10.0.2.16",
                null,
                { address, iface, up, route -> states.add(up) },
                object : AndroidNetworkMonitor.Registration {
                    override fun register(cb: ConnectivityManager.NetworkCallback) {
                        calls[0]++
                    }

                    override fun unregister(cb: ConnectivityManager.NetworkCallback) {
                        calls[1]++
                    }
                },
            )
        try {
            monitor.start()
            monitor.start()
            val network = Network.fromNetworkHandle((111L shl 32) or 0xcafed00dL)
            monitor.callback.onLinkPropertiesChanged(network, LinkProperties())
            monitor.callback.onBlockedStatusChanged(network, false)
            monitor.callback.onLost(network)
            assertEquals(1, states.size.toLong())
            assertFalse(states.get(0)!!)
            monitor.close()
            monitor.callback.onLinkPropertiesChanged(network, LinkProperties())
            assertEquals(1, states.size.toLong())
        } finally {
            monitor.close()
        }
        assertArrayEquals(intArrayOf(1, 1), calls)
    }

    @Test
    open fun registrationFailureDoesNotLeakOrAcceptCallbacks() {
        val states = ArrayList<Boolean?>()
        val monitor =
            AndroidNetworkMonitor(
                "10.0.2.16",
                null,
                { address, iface, up, route -> states.add(up) },
                object : AndroidNetworkMonitor.Registration {
                    override fun register(cb: ConnectivityManager.NetworkCallback) {
                        throw SecurityException("denied")
                    }

                    override fun unregister(cb: ConnectivityManager.NetworkCallback) {
                        fail("not registered")
                    }
                },
            )
        try {
            monitor.start()
            fail("Expected registration failure")
        } catch (expected: SecurityException) {
            assertEquals("denied", expected!!.message)
        } finally {
            monitor.close()
        }
        assertEquals(1, states.size.toLong())
        assertFalse(states.get(0)!!)
    }
}
