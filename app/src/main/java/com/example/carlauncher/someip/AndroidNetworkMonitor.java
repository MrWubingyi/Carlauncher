package com.example.carlauncher.someip;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkRequest;
import android.net.NetworkCapabilities;
import android.net.RouteInfo;
import android.os.Build;
import android.util.Log;

import java.net.InetAddress;

/** Observes the configured address; connectivity is never evidence of a Method response. */
final class AndroidNetworkMonitor implements AutoCloseable {
    interface Sink {
        void update(String address, String iface, boolean available, boolean multicastRoute);
    }

    interface Registration {
        void register(ConnectivityManager.NetworkCallback callback);
        void unregister(ConnectivityManager.NetworkCallback callback);
    }

    private final Registration registration;
    private final String address;
    private final InetAddress multicast;
    private final Sink sink;
    private final NetworkStateTracker tracker;
    private boolean active;
    private String lastInterface = "";
    private boolean lastAvailable;
    private boolean lastRoute;
    private boolean published;

    AndroidNetworkMonitor(Context context, String address, InetAddress multicast, Sink sink) {
        this(address, multicast, sink, new Registration() {
            private final ConnectivityManager manager = context.getSystemService(ConnectivityManager.class);
            @Override public void register(ConnectivityManager.NetworkCallback callback) {
                if (manager == null) throw new IllegalStateException("ConnectivityManager unavailable");
                NetworkRequest.Builder request = new NetworkRequest.Builder();
                request.removeCapability(NetworkCapabilities.NET_CAPABILITY_NOT_RESTRICTED);
                request.removeCapability(NetworkCapabilities.NET_CAPABILITY_TRUSTED);
                request.removeCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN);
                manager.registerNetworkCallback(request.build(), callback);
            }
            @Override public void unregister(ConnectivityManager.NetworkCallback callback) {
                manager.unregisterNetworkCallback(callback);
            }
        });
    }

    AndroidNetworkMonitor(String address, InetAddress multicast, Sink sink, Registration registration) {
        this.registration = registration;
        this.address = address;
        this.multicast = multicast;
        this.sink = sink;
        this.tracker = new NetworkStateTracker(address, Build.VERSION.SDK_INT >= 29);
    }

    // Callback arguments are authoritative. Do not query synchronous network state here.
    final ConnectivityManager.NetworkCallback callback = new ConnectivityManager.NetworkCallback() {
        @Override public void onLinkPropertiesChanged(Network network, LinkProperties properties) {
            synchronized (AndroidNetworkMonitor.this) {
                if (!active) return;
                tracker.link(network, properties.getInterfaceName(), properties.getLinkAddresses().stream()
                        .map(link -> link.getAddress().getHostAddress()).toArray(String[]::new),
                        hasMulticastRoute(properties, multicast));
                publish();
            }
        }

        @Override public void onBlockedStatusChanged(Network network, boolean isBlocked) {
            synchronized (AndroidNetworkMonitor.this) {
                if (!active) return;
                tracker.blocked(network, isBlocked);
                publish();
            }
        }

        @Override public void onLost(Network network) {
            synchronized (AndroidNetworkMonitor.this) {
                if (!active) return;
                tracker.lost(network);
                publish();
            }
        }
    };

    synchronized void start() {
        if (active) return;
        active = true;
        published = false;
        publish(); // Unknown starts down; registration delivers current LinkProperties asynchronously.
        try {
            registration.register(callback);
        } catch (RuntimeException e) {
            active = false;
            throw e;
        }
    }

    private void publish() {
        NetworkStateTracker.State state = tracker.snapshot();
        String iface = state.iface;
        boolean route = state.route;
        boolean available = active && state.available;
        if (!available) { iface = ""; route = false; }
        if (published && iface.equals(lastInterface) && available == lastAvailable && route == lastRoute) return;
        published = true;
        lastInterface = iface;
        lastAvailable = available;
        lastRoute = route;
        Log.i("VSOMEIP_NETWORK", "address=" + address + " interface=" + iface
                + " available=" + available + " multicastRoute=" + route);
        sink.update(address, iface, available, route);
    }

    static boolean hasMulticastRoute(LinkProperties lp, InetAddress multicast) {
        // Route types became public in API 33. Older devices fail closed for SD routing.
        if (multicast == null || Build.VERSION.SDK_INT < 33) return false;
        RouteInfo best = null;
        for (RouteInfo route : lp.getRoutes()) {
            if (!route.matches(multicast)) continue;
            if (best == null || route.getDestination().getPrefixLength() > best.getDestination().getPrefixLength()) {
                best = route;
            }
        }
        return best != null && best.getType() == RouteInfo.RTN_UNICAST
                && lp.getInterfaceName().equals(best.getInterface());
    }

    @Override public synchronized void close() {
        if (!active) return;
        active = false; // Reject queued callbacks before unregistering.
        try {
            registration.unregister(callback);
        } finally {
            tracker.clear();
            publish();
        }
    }
}
