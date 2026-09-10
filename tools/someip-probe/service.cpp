// Isolated W37 Method fixture. No LVGL or production vehicle state is modified.
#include <vsomeip/vsomeip.hpp>
#include "vehicle_request_processor.hpp"
#include <atomic>
#include <chrono>
#include <csignal>
#include <cstdint>
#include <iomanip>
#include <iostream>
#include <sstream>
#include <thread>

namespace {
std::atomic_bool stop_requested{false};
void stop_signal(int) { stop_requested.store(true); }

std::uint64_t read_be(const vsomeip::byte_t* data, std::size_t size) {
    std::uint64_t value = 0;
    for (std::size_t i = 0; i < size; ++i) value = (value << 8) | data[i];
    return value;
}

}

int main() {
    std::signal(SIGINT, stop_signal);
    std::signal(SIGTERM, stop_signal);
    auto app = vsomeip::runtime::get()->create_application("vehicle-probe-service");
    vehicle_probe::RequestProcessor processor;
    std::mutex log_mutex;
    if (!app->init()) return 1;
    app->register_state_handler([&](vsomeip::state_type_e state) {
        if (state == vsomeip::state_type_e::ST_REGISTERED) app->offer_service(0x1111, 0x2222);
    });
    app->register_message_handler(0x1111, 0x2222, 0x1001,
            [&](const std::shared_ptr<vsomeip::message>& request) {
        if (request->get_message_type() != vsomeip::message_type_e::MT_REQUEST) return;
        const auto payload = request->get_payload();
        const auto* data = payload->get_data();
        const auto size = payload->get_length();
        const auto result = processor.process(request->get_client(), request->get_session(),
                size == 16 && data ? std::vector<std::uint8_t>(data, data + size) : std::vector<std::uint8_t>{},
                vehicle_probe::RequestProcessor::Clock::now());
        const bool valid = result.return_code == 0;
        auto response = vsomeip::runtime::get()->create_response(request);
        response->set_return_code(valid ? vsomeip::return_code_e::E_OK : vsomeip::return_code_e::E_NOT_OK);
        std::ostringstream log;
        log << "PROBE_RX client=" << std::hex << request->get_client()
            << " session=" << request->get_session() << " rc=" << (valid ? "00" : "01")
            << " result=" << result.reason << std::dec << " len=" << size
            << " duplicate=" << result.duplicate << " applied=" << result.applied_count;
        if (size == 16) log << " seq=" << read_be(data + 1, 4)
                << " timestampMs=" << read_be(data + 5, 8)
                << " speed=" << unsigned(data[13]) << " gear=" << unsigned(data[14])
                << " dataStatus=" << unsigned(data[15]);
        log << " hex=" << std::hex << std::setfill('0');
        for (std::size_t i = 0; i < size; ++i) log << std::setw(2) << unsigned(data[i]);
        {
            std::lock_guard<std::mutex> lock(log_mutex);
            std::cout << log.str() << std::endl;
        }
        // Empty response payload is intentional: do not depend on nonstandard seq echoes.
        app->send(response);
    });
    std::thread io([&] { app->start(); });
    while (!stop_requested.load()) std::this_thread::sleep_for(std::chrono::milliseconds(50));
    app->stop_offer_service(0x1111, 0x2222);
    app->clear_all_handler();
    app->stop();
    io.join();
    return 0;
}
