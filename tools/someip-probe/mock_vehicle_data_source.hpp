#pragma once
#include <cstdint>
#include <sstream>
#include <string>

// Port of Android MockVehicleDataSource. One shared timeline for every subscriber.
class MockVehicleDataSource {
    std::uint64_t sequence_ = 0;
    int speed_ = 0;
    bool accelerating_ = true;
    bool fault_cycle_;
public:
    explicit MockVehicleDataSource(bool fault_cycle = false) : fault_cycle_(fault_cycle) {}
    std::uint64_t sequence() const { return sequence_; }
    std::string next(std::uint64_t timestamp_ms) {
        const auto sequence = ++sequence_;
        const auto cycle = sequence % 1200;
        if (fault_cycle_ && cycle >= 700 && cycle < 1000) return {};
        speed_ += accelerating_ ? 2 : -2;
        if (speed_ >= 100) { speed_ = 100; accelerating_ = false; }
        if (speed_ <= 0) { speed_ = 0; accelerating_ = true; }
        const bool invalid = fault_cycle_ && cycle >= 600 && cycle < 700;
        const int speed = invalid ? 255 : speed_;
        const int soc = invalid ? 150 : 70;
        std::ostringstream out;
        out << std::boolalpha << "{\"version\":1,\"seq\":" << sequence
            << ",\"timestampMs\":" << timestamp_ms
            << ",\"speedKph\":" << speed << ",\"rpm\":" << (invalid ? 9999 : 800 + speed_ * 25)
            << ",\"gear\":" << (speed == 0 ? 0 : 3) << ",\"soc\":" << soc
            << ",\"turnSignal\":" << (sequence / 30) % 4
            << ",\"parkingBrake\":" << ((sequence / 600) % 2 == 0)
            << ",\"warning\":" << (sequence / 300) % 3
            << ",\"validity\":" << (invalid ? 1 : 0)
            << ",\"doorLock\":" << ((sequence / 450) % 2 == 0)
            << ",\"beltWarning\":" << ((sequence / 300) % 2 == 0)
            << ",\"headlightsState\":1,\"highBeamLightsState\":" << ((sequence / 50) % 2 == 0 ? 1 : 0)
            << ",\"engineCoolantTemp\":" << (invalid ? -999 : 90)
            << ",\"evBatteryLevel\":" << soc << ",\"dataStatus\":" << (invalid ? 1 : 0) << "}";
        return out.str();
    }
};
