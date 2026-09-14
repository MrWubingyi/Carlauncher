#include <vsomeip/vsomeip.hpp>
#include "mock_vehicle_data_source.hpp"
#include <atomic>
#include <chrono>
#include <csignal>
#include <iostream>
#include <thread>

namespace { volatile std::sig_atomic_t stopped = 0; void stop(int) { stopped = 1; } }
int main(int argc, char** argv) {
    bool fault_cycle = false;
    for (int i = 1; i < argc; ++i) {
        const std::string argument(argv[i]);
        if (argument == "--fault-cycle") fault_cycle = true;
        else if (argument == "--help") {
            std::cout << "Usage: vehicle_mock_service [--fault-cycle]\n"
                      << "Default: continuous 100 ms snapshots. --fault-cycle enables invalid/silent/recovery tests.\n";
            return 0;
        } else {
            std::cerr << "Unknown argument: " << argument << std::endl;
            return 2;
        }
    }
    std::cout << "MOCK_MODE " << (fault_cycle ? "fault-cycle" : "continuous") << std::endl;
    std::signal(SIGINT, stop);
    std::signal(SIGTERM, stop);
    auto app = vsomeip::runtime::get()->create_application("vehicle-mock-service");
    if (!app->init()) return 1;
    std::atomic_bool registered{false};
    app->offer_event(0x1111, 0x2222, 0x8001, {0x0001}, vsomeip::event_type_e::ET_EVENT);
    app->register_state_handler([&](vsomeip::state_type_e state) {
        const bool ready = state == vsomeip::state_type_e::ST_REGISTERED;
        if (ready) app->offer_service(0x1111, 0x2222);
        registered.store(ready);
    });
    app->register_subscription_handler(0x1111, 0x2222, 0x0001,
        [](vsomeip::client_t client, vsomeip::uid_t, vsomeip::gid_t, bool subscribed) {
            std::cout << "MOCK_SUB client=" << std::hex << client << std::dec
                      << " subscribed=" << subscribed << std::endl;
            return true;
        });
    std::thread io([&] { app->start(); });
    MockVehicleDataSource source(fault_cycle);
    bool silent = false;
    while (!stopped) {
        if (registered.load()) {
            const auto now = std::chrono::duration_cast<std::chrono::milliseconds>(
                std::chrono::system_clock::now().time_since_epoch()).count();
            const auto json = source.next(now);
            if (json.empty() != silent) {
                silent = json.empty();
                std::cout << "MOCK_PHASE " << (silent ? "SILENT fault injection; no events for about 30 seconds" : "RESUMED")
                          << " seq=" << source.sequence() << std::endl;
            }
            if (!json.empty()) {
                auto payload = vsomeip::runtime::get()->create_payload();
                payload->set_data(std::vector<vsomeip::byte_t>(json.begin(), json.end()));
                app->notify(0x1111, 0x2222, 0x8001, payload);
                std::cout << "MOCK_EVENT " << json << std::endl;
            }
        }
        std::this_thread::sleep_for(std::chrono::milliseconds(100));
    }
    app->stop_offer_service(0x1111, 0x2222);
    app->stop_offer_event(0x1111, 0x2222, 0x8001);
    app->clear_all_handler();
    app->stop();
    io.join();
}
