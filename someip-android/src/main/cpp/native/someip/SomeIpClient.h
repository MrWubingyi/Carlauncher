#pragma once

#include <cstdint>
#include <cstddef>
#include <functional>
#include <memory>
#include <string>
#include <vector>

namespace someip {

// Deployment identity is supplied by the host; no Launcher, JNI or VHAL types.
struct SomeIpServiceDefinition {
    std::uint16_t service;
    std::uint16_t instance;
    std::uint16_t event;
    std::uint16_t event_group;
    std::uint16_t method;
    std::string application_name;
    std::size_t max_event_payload_bytes = 4096;
    std::size_t max_request_payload_bytes = 4096;
};

enum class ClientEvent { Registered, Available, Unavailable, ResponseOk, ResponseError, Stopped };

class SomeIpClient {
public:
    using EventCallback = std::function<void(ClientEvent, int)>;
    using PayloadCallback = std::function<void(const std::vector<std::uint8_t>&)>;

    // Callbacks run on transport threads. They must not synchronously call start/stop/send.
    // The host must call stop before destroying the client or its callback receivers.
    SomeIpClient(SomeIpServiceDefinition service, EventCallback event, PayloadCallback payload);
    ~SomeIpClient();
    SomeIpClient(const SomeIpClient&) = delete;
    SomeIpClient& operator=(const SomeIpClient&) = delete;

    bool start(const std::string& config_path);
    void stop();
    bool send(const std::uint8_t* data, std::size_t size);
    bool available() const;
    void network_state(bool ready);

private:
    class Impl;
    std::unique_ptr<Impl> impl_;
};

} // namespace someip
