#include "mock_vehicle_data_source.hpp"
#include <iostream>
#include <stdexcept>
void expect(bool condition) { if (!condition) throw std::runtime_error("mock timeline mismatch"); }
int main() {
    MockVehicleDataSource source(true);
    for (int i = 1; i <= 1201; ++i) {
        const auto json = source.next(1700000000000ULL + i * 100);
        if (i >= 700 && i < 1000) { expect(json.empty()); continue; }
        expect(!json.empty());
        if (i == 1) expect(json.find("\"speedKph\":2,") != std::string::npos);
        if (i == 50) expect(json.find("\"speedKph\":100,") != std::string::npos);
        if (i == 100) expect(json.find("\"speedKph\":0,") != std::string::npos);
        expect((json.find("\"dataStatus\":1") != std::string::npos) == (i >= 600 && i < 700));
    }
    std::cout << "PASS normal, invalid, silence, recovery, cycle rollover\n";
    MockVehicleDataSource continuous;
    for (int i = 1; i <= 2401; ++i) {
        const auto json = continuous.next(1700000000000ULL + i * 100);
        expect(!json.empty());
        expect(json.find("\"dataStatus\":0") != std::string::npos);
        expect(json.find("\"speedKph\":255") == std::string::npos);
    }
    std::cout << "PASS default continuous mode across two former silence windows\n";
}
