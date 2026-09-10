// CarLauncher Android adapter for vSomeIP 3.7.5. Included inside namespace vsomeip_v3.
// Real ConnectivityManager observations only; absent state is unavailable.
#if defined(__ANDROID__)
namespace {
struct android_network_state {
    std::string iface;
    bool up = false;
    bool route = false;
};
std::mutex android_network_mutex;
std::map<std::string, android_network_state> android_networks;
std::vector<std::weak_ptr<netlink_connector>> android_connectors;
}

extern "C" __attribute__((visibility("default")))
void vsomeip_android_set_network_state(const char* address, const char* iface, bool up, bool route) {
    if (!address || !iface) return;
    std::lock_guard<std::mutex> lock(android_network_mutex);
    const android_network_state state{iface, up && iface[0] != '\0', up && route};
    android_networks[address] = state;
    for (auto it = android_connectors.begin(); it != android_connectors.end();) {
        if (auto connector = it->lock()) {
            connector->android_update(address, state.iface, state.up, state.route);
            ++it;
        } else {
            it = android_connectors.erase(it);
        }
    }
}

void netlink_connector::android_start() {
    std::lock_guard<std::mutex> registry_lock(android_network_mutex);
    {
        std::lock_guard<std::mutex> lock(socket_mutex_);
        if (android_running_) return;
        android_running_ = true;
        android_published_ = false;
        ++android_generation_;
    }
    android_connectors.emplace_back(shared_from_this());
    const auto address = address_.to_string();
    const auto state = android_networks[address];
    VSOMEIP_INFO << "Android network connector: waiting for ConnectivityManager, address=" << address;
    android_update(address, state.iface, state.up, state.route);
}

void netlink_connector::android_stop() {
    std::lock_guard<std::mutex> lock(socket_mutex_);
    android_running_ = false;
    ++android_generation_; // Reject queued observations from an earlier start.
}

void netlink_connector::android_update(const std::string& address, const std::string& iface, bool up, bool route) {
    if (address != address_.to_string()) return;
    std::uint64_t generation;
    {
        std::lock_guard<std::mutex> lock(socket_mutex_);
        if (!android_running_) return;
        generation = android_generation_;
    }
    std::weak_ptr<netlink_connector> weak = shared_from_this();
    boost::asio::post(android_strand_, [weak, generation, iface, up, route]() {
        auto self = weak.lock();
        if (!self) return;
        net_if_changed_handler_t handler;
        bool interface_changed;
        {
            std::lock_guard<std::mutex> lock(self->socket_mutex_);
            if (!self->android_running_ || generation != self->android_generation_) return;
            if (self->android_published_ && self->android_iface_ == iface
                    && self->android_up_ == up && self->android_route_ == route) return;
            interface_changed = !self->android_published_ || self->android_iface_ != iface || self->android_up_ != up;
            self->android_published_ = true;
            self->android_iface_ = iface;
            self->android_up_ = up;
            self->android_route_ = route;
            handler = self->handler_;
        }
        // Routing callbacks run on the vSomeIP io strand, never on an Android callback thread.
        // stop() drains the application io threads before destroying the routing manager.
        if (handler) {
            if (interface_changed || !route) handler(false, iface, false);
            handler(true, iface, up);
            handler(false, iface, up && route);
        }
    });
}
#endif
