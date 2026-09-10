#include "vehicle_request_processor.hpp"
#include <functional>
#include <iostream>
#include <thread>

using vehicle_probe::RequestProcessor;
using namespace std::chrono_literals;
void require(bool value) { if (!value) throw std::runtime_error("expectation failed"); }
std::vector<std::uint8_t> frame(std::uint8_t speed = 100) {
    return {1, 1, 2, 3, 4, 1, 2, 3, 4, 5, 6, 7, 8, speed, 3, 0};
}
int main() {
    const RequestProcessor::Clock::time_point start{};
    int passed = 0;
    auto test = [&](const char* name, const std::function<void()>& run) {
        try { run(); ++passed; std::cout << "PASS " << name << '\n'; }
        catch (const std::exception& e) { std::cerr << "FAIL " << name << ": " << e.what() << '\n'; std::exit(1); }
    };
    test("decode_and_replay_once", [&] {
        RequestProcessor p;
        auto first = p.process(0x5566, 1, frame(), start);
        auto replay = p.process(0x5566, 1, frame(), start + 1ms);
        require(first.return_code == 0 && !first.duplicate && first.applied_count == 1);
        require(replay.return_code == 0 && replay.duplicate && replay.applied_count == 1);
        auto state = p.latest();
        require(state && state->sequence == 0x01020304 && state->timestamp_ms == 0x0102030405060708ULL);
        require(state->speed == 100 && state->gear == 3 && state->status == 0);
    });
    test("invalid_frames_never_modify_snapshot", [&] {
        RequestProcessor p;
        p.process(1, 1, frame(), start);
        for (int field : {0, 13, 14, 15}) {
            auto bad = frame(); bad[field] = 255;
            auto first = p.process(1, 2, bad, start);
            auto repeated = p.process(1, 2, bad, start + 1ms);
            require(first.return_code == 1 && repeated.return_code == 1 && repeated.duplicate);
            require(repeated.applied_count == 1 && p.latest()->speed == 100);
        }
        auto zero_time = frame(); std::fill(zero_time.begin()+5, zero_time.begin()+13, 0);
        require(p.process(1, 3, zero_time, start).return_code == 1);
        require(p.process(1, 4, {}, start).return_code == 1);
        require(p.process(1, 4, std::vector<std::uint8_t>(17), start).return_code == 1);
        require(p.latest()->sequence == 0x01020304);
    });
    test("inclusive_boundaries", [&] {
        RequestProcessor p;
        auto low = frame(0); low[14]=0; low[15]=0;
        auto high = frame(200); high[14]=3; high[15]=4;
        require(p.process(1, 1, low, start).return_code == 0);
        require(p.process(1, 2, high, start).return_code == 0);
        for (int field : {13,14,15}) {
            auto bad=high; ++bad[field];
            require(p.process(1, 3, bad, start).return_code == 1);
        }
        require(p.latest()->speed == 200 && p.latest()->status == 4);
    });
    test("expiry_is_anchored_to_first_request", [&] {
        RequestProcessor p;
        p.process(1, 1, frame(), start);
        require(p.process(1, 1, frame(), start+4999ms).duplicate);
        auto expired = p.process(1, 1, frame(), start+5s);
        require(!expired.duplicate && expired.applied_count == 2);
    });
    test("capacity_fails_closed_without_live_eviction", [&] {
        RequestProcessor p(1);
        p.process(1, 1, frame(), start);
        auto full=p.process(1, 2, frame(200), start+1ms);
        require(full.return_code == 1 && std::string(full.reason) == "CACHE_FULL" && full.applied_count == 1);
        require(p.process(1, 1, frame(), start+2ms).duplicate);
        require(p.latest()->speed == 100);
        require(p.process(1, 2, frame(200), start+5s).applied_count == 2);
    });
    test("client_session_and_payload_identity", [&] {
        RequestProcessor p;
        require(p.process(1, 1, frame(), start).applied_count == 1);
        require(p.process(2, 1, frame(), start).applied_count == 2);
        require(p.process(1, 2, frame(), start).applied_count == 3);
        require(p.process(1, 1, frame(200), start).applied_count == 4);
        require(p.process(1, 1, frame(), start).duplicate);
        require(p.latest()->speed == 200);
    });
    test("concurrent_duplicates_apply_once", [&] {
        RequestProcessor p;
        std::vector<std::thread> threads;
        for (int i=0;i<16;++i) threads.emplace_back([&] { p.process(1, 1, frame(), start); });
        for (auto& thread : threads) thread.join();
        require(p.process(1, 1, frame(), start).applied_count == 1);
    });
    test("invalid_limits_rejected", [&] {
        bool invalid=false;
        try { RequestProcessor p(0); } catch (const std::invalid_argument&) { invalid=true; }
        require(invalid); invalid=false;
        try { RequestProcessor p(1, 0s); } catch (const std::invalid_argument&) { invalid=true; }
        require(invalid);
    });
    std::cout << "RESULT " << passed << " passed, 0 failed\n";
}
