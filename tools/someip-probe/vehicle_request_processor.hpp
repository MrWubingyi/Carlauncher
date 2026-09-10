#pragma once

#include <array>
#include <algorithm>
#include <chrono>
#include <cstdint>
#include <map>
#include <mutex>
#include <optional>
#include <stdexcept>
#include <tuple>
#include <vector>

namespace vehicle_probe {
struct Snapshot {
    std::uint32_t sequence;
    std::uint64_t timestamp_ms;
    std::uint8_t speed;
    std::uint8_t gear;
    std::uint8_t status;
};

// Process-local bounded replay protection. Client IDs must be unique per deployment.
// Identity includes the complete payload so a restart reusing a Session is not suppressed.
class RequestProcessor {
public:
    using Clock = std::chrono::steady_clock;
    struct Result {
        std::uint8_t return_code;
        const char* reason;
        bool duplicate;
        std::uint64_t applied_count;
    };

    explicit RequestProcessor(std::size_t capacity = 256,
                              Clock::duration window = std::chrono::seconds(5))
        : capacity_(capacity), window_(window) {
        if (capacity == 0 || window <= Clock::duration::zero()) throw std::invalid_argument("invalid replay limits");
    }

    Result process(std::uint16_t client, std::uint16_t session,
                   const std::vector<std::uint8_t>& payload, Clock::time_point now) {
        std::lock_guard<std::mutex> lock(mutex_);
        // No unbounded payload is retained in the cache.
        if (payload.size() != 16) return {1, "BAD_LENGTH", false, applied_count_};
        for (auto it = cache_.begin(); it != cache_.end();) {
            if (now >= it->second.expires) it = cache_.erase(it);
            else ++it;
        }
        std::array<std::uint8_t, 16> bytes{};
        std::copy(payload.begin(), payload.end(), bytes.begin());
        Key key{client, session, bytes};
        auto found = cache_.find(key);
        if (found != cache_.end()) {
            return {found->second.code, found->second.reason, true, applied_count_};
        }
        // Never evict a live identity: that could execute a duplicate twice inside the window.
        if (cache_.size() >= capacity_) return {1, "CACHE_FULL", false, applied_count_};
        const char* reason = validate(bytes);
        const std::uint8_t code = reason == nullptr ? 0 : 1;
        const char* result = reason == nullptr ? "OK" : reason;
        // Reserve replay identity before applying a state change (allocation may throw).
        cache_.emplace(key, Entry{now + window_, code, result});
        if (code == 0) {
            latest_ = Snapshot{static_cast<std::uint32_t>(read_be(bytes.data() + 1, 4)),
                    read_be(bytes.data() + 5, 8), bytes[13], bytes[14], bytes[15]};
            ++applied_count_;
        }
        return {code, result, false, applied_count_};
    }

    std::optional<Snapshot> latest() const {
        std::lock_guard<std::mutex> lock(mutex_);
        return latest_;
    }

private:
    using Key = std::tuple<std::uint16_t, std::uint16_t, std::array<std::uint8_t, 16>>;
    struct Entry { Clock::time_point expires; std::uint8_t code; const char* reason; };
    static std::uint64_t read_be(const std::uint8_t* bytes, std::size_t size) {
        std::uint64_t value = 0;
        for (std::size_t i = 0; i < size; ++i) value = (value << 8) | bytes[i];
        return value;
    }
    static const char* validate(const std::array<std::uint8_t, 16>& data) {
        if (data[0] != 1) return "BAD_SCHEMA";
        if (read_be(data.data() + 5, 8) == 0) return "BAD_TIMESTAMP";
        if (data[13] > 200 || data[14] > 3 || data[15] > 4) return "BAD_RANGE";
        return nullptr;
    }
    mutable std::mutex mutex_;
    const std::size_t capacity_;
    const Clock::duration window_;
    std::map<Key, Entry> cache_;
    std::optional<Snapshot> latest_;
    std::uint64_t applied_count_ = 0;
};
}
