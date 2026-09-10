package com.example.carlauncher.someip;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/** Android-free reduction of address, route and per-app blocking observations. */
final class NetworkStateTracker {
    static final class State {
        final String iface;
        final boolean available;
        final boolean route;
        State(String iface, boolean route) {
            this.iface = iface;
            this.available = !iface.isEmpty();
            this.route = available && route;
        }
    }
    private final String address;
    private final boolean requireBlockedVerdict;
    private final Map<Object, State> links = new HashMap<>();
    private final Map<Object, Boolean> blocked = new HashMap<>();

    NetworkStateTracker(String address, boolean requireBlockedVerdict) {
        this.address = address;
        this.requireBlockedVerdict = requireBlockedVerdict;
    }
    void link(Object network, String iface, String[] addresses, boolean route) {
        links.put(network, new State(iface != null && Arrays.asList(addresses).contains(address) ? iface : "", route));
    }
    void blocked(Object network, boolean value) { blocked.put(network, value); }
    void lost(Object network) { links.remove(network); blocked.remove(network); }
    void clear() { links.clear(); blocked.clear(); }
    State snapshot() {
        State selected = new State("", false);
        for (Map.Entry<Object, State> entry : links.entrySet()) {
            Boolean denied = blocked.get(entry.getKey());
            if (Boolean.TRUE.equals(denied) || (requireBlockedVerdict && denied == null)) continue;
            State candidate = entry.getValue();
            if (!candidate.available) continue;
            if (!selected.available || (candidate.route && !selected.route)
                    || (candidate.route == selected.route && candidate.iface.compareTo(selected.iface) < 0)) {
                selected = candidate;
            }
        }
        return selected;
    }
}
