#include "vehicle_someip_client.h"
extern "C" {
#include "vehicle_data.h"
#include "vehicle_state.h"
}
#include <vsomeip/vsomeip.hpp>
#include <atomic>
#include <iostream>
#include <mutex>
#include <string>
#include <thread>

namespace {
std::shared_ptr<vsomeip::application> app;
std::thread io;
std::atomic_bool stopping{true};
std::mutex receive_mutex;
vehicle_data_t *target = nullptr;
std::uint64_t last_timestamp = 0, last_sequence = 0;
bool available = false;
}

bool vehicle_someip_client_start(vehicle_data_t *data) {
    if (!data) return false;
    if (app) return true;
    app = vsomeip::runtime::get()->create_application("lvgl-vehicle-client");
    if (!app->init()) { app.reset(); return false; }
    target = data;
    stopping.store(false);
    app->register_state_handler([](vsomeip::state_type_e state) {
        if (state != vsomeip::state_type_e::ST_REGISTERED || stopping.load()) return;
        app->request_service(0x1111, 0x2222);
        app->request_event(0x1111, 0x2222, 0x8001, {0x0001}, vsomeip::event_type_e::ET_EVENT);
        app->subscribe(0x1111, 0x2222, 0x0001);
    });
    app->register_availability_handler(0x1111, 0x2222,
        [](vsomeip::service_t, vsomeip::instance_t, bool value) {
            std::lock_guard<std::mutex> lock(receive_mutex);
            if (stopping.load()) return;
            available = value;
            if (!value) {
                last_timestamp = last_sequence = 0;
                vehicle_data_set_tcp_disconnected(target);
            }
        });
    app->register_message_handler(0x1111, 0x2222, 0x8001,
        [](const std::shared_ptr<vsomeip::message>& message) {
            if (message->get_message_type() != vsomeip::message_type_e::MT_NOTIFICATION
                    || message->get_return_code() != vsomeip::return_code_e::E_OK) return;
            const auto payload = message->get_payload();
            if (!payload || !payload->get_length() || payload->get_length() > 4096) return;
            const std::string json(reinterpret_cast<const char*>(payload->get_data()), payload->get_length());
            std::lock_guard<std::mutex> lock(receive_mutex);
            if (stopping.load() || !available) return;
            vehicle_state_t state{};
            if (json.find('\0') != std::string::npos || !vehicle_state_parse_json(json.c_str(), &state)
                    || state.version != 1 || state.timestamp_ms == 0) {
                vehicle_data_report_invalid_frame(target);
                return;
            }
            if (state.timestamp_ms < last_timestamp
                    || (state.timestamp_ms == last_timestamp && state.sequence <= last_sequence)) return;
            last_timestamp = state.timestamp_ms;
            last_sequence = state.sequence;
            // Existing mutex-protected model; only ui_bridge's LVGL timer touches widgets.
            vehicle_data_update_from_tcp(target, &state);
            std::cout << "LVGL_EVENT seq=" << state.sequence << " speed=" << state.speed_kph
                      << " status=" << state.data_status << std::endl;
        });
    io = std::thread([] { app->start(); });
    return true;
}

void vehicle_someip_client_stop() {
    if (!app) return;
    stopping.store(true);
    app->unsubscribe(0x1111, 0x2222, 0x0001);
    app->release_event(0x1111, 0x2222, 0x8001);
    app->release_service(0x1111, 0x2222);
    app->clear_all_handler();
    app->stop();
    if (io.joinable()) io.join();
    std::lock_guard<std::mutex> lock(receive_mutex);
    vehicle_data_set_tcp_disconnected(target);
    target = nullptr;
    available = false;
    last_timestamp = last_sequence = 0;
    app.reset();
}
