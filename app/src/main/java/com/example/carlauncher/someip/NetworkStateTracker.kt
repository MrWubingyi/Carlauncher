package com.example.carlauncher.someip

import java.util.Arrays
import java.util.HashMap

/* Android-free reduction of address, route and per-app blocking observations. */
internal class NetworkStateTracker(
    private val address: String?,
    private val requireBlockedVerdict: Boolean,
) {
    private val links: MutableMap<Any?, State?> = HashMap<Any?, State?>()
    private val blocked: MutableMap<Any?, Boolean?> = HashMap<Any?, Boolean?>()

    internal class State(val iface: String, route: Boolean) {
        val available: Boolean
        val route: Boolean

        init {
            this.available = !iface!!.isEmpty()
            this.route = available && route
        }
    }

    fun link(network: Any?, iface: String?, addresses: Array<out String?>, route: Boolean) {
        links.put(
            network,
            State(
                if (iface != null && Arrays.asList<String?>(*addresses)!!.contains(address)) iface
                else "",
                route,
            ),
        )
    }

    fun blocked(network: Any?, value: Boolean) {
        blocked.put(network, value)
    }

    fun lost(network: Any?) {
        links.remove(network)
        blocked.remove(network)
    }

    fun clear() {
        links.clear()
        blocked.clear()
    }

    fun snapshot(): State {
        var selected: State = State("", false)
        for (entry in links.entries) {
            val denied = blocked.get(entry!!.key)
            if (java.lang.Boolean.TRUE == denied || (requireBlockedVerdict && denied == null))
                continue
            val candidate = entry!!.value
            if (!candidate!!.available) continue
            if (
                (!selected!!.available ||
                    (candidate!!.route && !selected!!.route) ||
                    (candidate!!.route == selected!!.route &&
                        candidate!!.iface!!.compareTo(selected!!.iface) < 0))
            ) {
                selected = candidate
            }
        }
        return selected
    }
}
