package com.example.carlauncher.someip

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.RouteInfo
import android.os.Build
import android.util.Log
import java.net.InetAddress

/* Observes the configured address; connectivity is never evidence of a Method response. */
internal class AndroidNetworkMonitor(
    private val address: String?,
    private val multicast: InetAddress?,
    private val sink: Sink?,
    private val registration: Registration?,
) : AutoCloseable {
    private val tracker: NetworkStateTracker?
    private var active: Boolean = false
    private var lastInterface: String? = ""
    private var lastAvailable: Boolean = false
    private var lastRoute: Boolean = false
    private var published: Boolean = false

    // Callback arguments are authoritative. Do not query synchronous network state here.
    val callback: ConnectivityManager.NetworkCallback =
        object : ConnectivityManager.NetworkCallback() {
            override fun onLinkPropertiesChanged(
                network: Network,
                properties: LinkProperties,
            ) {
                synchronized(this@AndroidNetworkMonitor) {
                    if (!active) return
                    tracker!!.link(
                        network,
                        properties!!.getInterfaceName(),
                        properties.linkAddresses.map { it.address.hostAddress }.toTypedArray(),
                        hasMulticastRoute(properties, multicast),
                    )
                    publish()
                }
            }

            override fun onBlockedStatusChanged(network: Network, isBlocked: Boolean) {
                synchronized(this@AndroidNetworkMonitor) {
                    if (!active) return
                    tracker!!.blocked(network, isBlocked)
                    publish()
                }
            }

            override fun onLost(network: Network) {
                synchronized(this@AndroidNetworkMonitor) {
                    if (!active) return
                    tracker!!.lost(network)
                    publish()
                }
            }
        }

    internal fun interface Sink {
        fun update(address: String?, iface: String?, available: Boolean, multicastRoute: Boolean)
    }

    internal interface Registration {
        fun register(callback: ConnectivityManager.NetworkCallback)

        fun unregister(callback: ConnectivityManager.NetworkCallback)
    }

    constructor(
        context: Context?,
        address: String?,
        multicast: InetAddress?,
        sink: Sink?,
    ) : this(
        address,
        multicast,
        sink,
        object : Registration {
            private val manager: ConnectivityManager? =
                context!!.getSystemService<ConnectivityManager>(ConnectivityManager::class.java)

            override fun register(callback: ConnectivityManager.NetworkCallback) {
                if (manager == null) throw IllegalStateException("ConnectivityManager unavailable")
                val request = NetworkRequest.Builder()
                request.removeCapability(NetworkCapabilities.NET_CAPABILITY_NOT_RESTRICTED)
                request.removeCapability(NetworkCapabilities.NET_CAPABILITY_TRUSTED)
                request.removeCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
                manager!!.registerNetworkCallback(request.build(), callback)
            }

            override fun unregister(callback: ConnectivityManager.NetworkCallback) {
                manager!!.unregisterNetworkCallback(callback)
            }
        },
    ) {}

    init {
        this.tracker = NetworkStateTracker(address, Build.VERSION.SDK_INT >= 29)
    }

    @Synchronized
    fun start() {
        if (active) return
        active = true
        published = false
        publish() // Unknown starts down; registration delivers current LinkProperties
        // asynchronously.
        try {
            registration!!.register(callback)
        } catch (e: RuntimeException) {
            active = false
            throw e
        }
    }

    private fun publish() {
        val state = tracker!!.snapshot()
        var iface = state!!.iface
        var route = state!!.route
        val available = active && state!!.available
        if (!available) {
            iface = ""
            route = false
        }
        if (published && iface == lastInterface && available == lastAvailable && route == lastRoute)
            return
        published = true
        lastInterface = iface
        lastAvailable = available
        lastRoute = route
        Log.i(
            "VSOMEIP_NETWORK",
            ("address=" +
                address +
                " interface=" +
                iface +
                " available=" +
                available +
                " multicastRoute=" +
                route),
        )
        sink!!.update(address, iface, available, route)
    }

    @Synchronized
    override fun close() {
        if (!active) return
        active = false // Reject queued callbacks before unregistering.
        try {
            registration!!.unregister(callback)
        } finally {
            tracker!!.clear()
            publish()
        }
    }

    companion object {

        @JvmStatic
        fun hasMulticastRoute(lp: LinkProperties?, multicast: InetAddress?): Boolean {
            // Route types became in API 33. Older devices fail closed for SD routing.
            if (multicast == null || Build.VERSION.SDK_INT < 33) return false
            var best: RouteInfo? = null
            for (route in lp!!.getRoutes()!!) {
                if (!route!!.matches(multicast)) continue
                if (
                    best == null ||
                        route!!.getDestination().getPrefixLength() >
                            best!!.getDestination().getPrefixLength()
                ) {
                    best = route
                }
            }
            return (best != null &&
                best!!.getType() == RouteInfo.RTN_UNICAST &&
                lp!!.getInterfaceName() == best!!.getInterface())
        }
    }
}
