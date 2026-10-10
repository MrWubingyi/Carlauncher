#include "SomeIpClient.h"

#include <vsomeip/vsomeip.hpp>
#include <atomic>
#include <chrono>
#include <cstdlib>
#include <exception>
#include <map>
#include <mutex>
#include <thread>
#include <unistd.h>
#include <utility>

#ifdef __ANDROID__
#include <android/log.h>
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, "VSOMEIP_CLIENT", __VA_ARGS__)
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, "VSOMEIP_CLIENT", __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, "VSOMEIP_CLIENT", __VA_ARGS__)
#else
#include <cstdio>
#define LOGI(...) std::fprintf(stderr, __VA_ARGS__)
#define LOGD(...) std::fprintf(stderr, __VA_ARGS__)
#define LOGE(...) std::fprintf(stderr, __VA_ARGS__)
#endif

namespace vehicle {
class SomeIpClient::Impl {
public:
    Impl(SomeIpServiceDefinition service, EventCallback event, PayloadCallback payload)
        : service_(std::move(service)), event_(std::move(event)), payload_(std::move(payload)) {}
    // Serialize complete start/stop transactions, including JNI callers outside the Service.
    bool start(const std::string& config_path) {
        std::lock_guard<std::mutex> lifecycle(lifecycle_mutex_);
        return init(config_path) && start_io();
    }

    // ---- 阶段 1：创建 application + 注册回调 + app_->init()（非阻塞，可重入）----
    bool init(const std::string& config_path) {
        std::lock_guard<std::mutex> lock(mutex_);
        if (app_) return true;

        // vsomeip 通过环境变量找配置；Android App 无 shell env，进程内 setenv 可行。
        if (!config_path.empty()) {
            setenv("VSOMEIP_CONFIGURATION", config_path.c_str(), 1);
            // vSomeIP defaults to /tmp, which an Android application cannot write.
            // utility::base_path() caches this environment setting on first use.
            const auto separator = config_path.find_last_of('/');
            if (separator == std::string::npos) return false;
            const auto private_dir = config_path.substr(0, separator);
            if (access(private_dir.c_str(), W_OK | X_OK) != 0
                    || setenv("VSOMEIP_BASE_PATH", private_dir.c_str(), 1) != 0) {
                LOGE("cannot use private routing directory: %s", private_dir.c_str());
                return false;
            }
            LOGI("routing base path: %s", private_dir.c_str());
        } else {
            LOGE("config path is required");
            return false;
        }
        setenv("VSOMEIP_APPLICATION_NAME", service_.application_name.c_str(), 1);

        auto app = vsomeip::runtime::get()->create_application(service_.application_name.c_str());
        if (!app) {
            LOGE("create_application failed");
            return false;
        }
        LOGI("create_application ok");

        // The application owns its handler; a weak reference avoids a self-retaining cycle.
        std::weak_ptr<vsomeip::application> weak_app = app;
        app->register_state_handler([this, weak_app](vsomeip::state_type_e state) {
            auto app = weak_app.lock();
            if (!app) return;
            if (state == vsomeip::state_type_e::ST_REGISTERED) {
                LOGI("registered; request_service %04x/%04x",
                     service_.service, service_.instance);
                app->request_service(service_.service,
                                     service_.instance);
                app->request_event(service_.service, service_.instance,
                    service_.event, {service_.event_group},
                    vsomeip::event_type_e::ET_EVENT);
                app->subscribe(service_.service, service_.instance,
                    service_.event_group);
                event_(ClientEvent::Registered, 0);
            }
        });

        app->register_availability_handler(
                service_.service, service_.instance,
                [this](vsomeip::service_t, vsomeip::instance_t, bool available) {
                    if (stopping_.load()) return;
                    available_.store(available);
                    available = available && network_ready_.load();
                    LOGI("availability: %s",
                         available ? "ON_AVAILABLE" : "ON_UNAVAILABLE");
                    event_(available ? ClientEvent::Available : ClientEvent::Unavailable,
                                available ? 1 : 0);
                });

        app->register_message_handler(
                service_.service, service_.instance,
                vsomeip::ANY_METHOD,
                [this](const std::shared_ptr<vsomeip::message>& msg) {
                    if (msg->get_method() == service_.event
                            && msg->get_message_type() == vsomeip::message_type_e::MT_NOTIFICATION
                            && msg->get_return_code() == vsomeip::return_code_e::E_OK) {
                        if (!stopping_.load() && available()) {
                            auto payload = msg->get_payload();
                            if (payload && payload->get_length() > 0
                                    && payload->get_length() <= service_.max_event_payload_bytes) {
                                payload_(std::vector<std::uint8_t>(
                                    payload->get_data(), payload->get_data() + payload->get_length()));
                            }
                        }
                        return;
                    }
                    if (msg->get_method() != service_.method) return;
                    const auto type = msg->get_message_type();
                    if (type != vsomeip::message_type_e::MT_RESPONSE
                            && type != vsomeip::message_type_e::MT_ERROR) return;
                    {
                        std::lock_guard<std::mutex> lock(mutex_);
                        const auto key = (std::uint32_t(msg->get_client()) << 16) | msg->get_session();
                        auto pending = pending_.find(key);
                        if (stopping_.load() || !network_ready_.load() || pending == pending_.end()) {
                            LOGD("SOMEIP_RX_IGNORED unmatched client=%04x session=%04x", msg->get_client(), msg->get_session());
                            return;
                        }
                        const bool expired = std::chrono::steady_clock::now() - pending->second >= std::chrono::seconds(3);
                        pending_.erase(pending); // One terminal response per request, including UDP duplicates.
                        if (expired) return;
                    }
                    const auto rc = msg->get_return_code();
                    LOGI("SOMEIP_RX service=%04x method=%04x client=%04x session=%04x type=%02x rc=%02x",
                         msg->get_service(), msg->get_method(), msg->get_client(), msg->get_session(),
                         static_cast<int>(type), static_cast<int>(rc));
                    event_(type == vsomeip::message_type_e::MT_RESPONSE
                                        && rc == vsomeip::return_code_e::E_OK
                                        ? ClientEvent::ResponseOk : ClientEvent::ResponseError,
                                static_cast<int>(rc));
                });

        LOGI("handlers registered; calling app_->init()");
        if (!app->init()) {
            LOGE("app_->init() failed");
            return false;
        }
        LOGI("app_->init() ok");
        stopping_.store(false);
        app_ = std::move(app);
        return true;
    }

    // ---- 阶段 2：在专用线程上跑阻塞的 app_->start()，本函数立即返回 ----
    bool start_io() {
        std::lock_guard<std::mutex> lock(mutex_);
        if (!app_) {
            LOGE("start_io: not initialized");
            return false;
        }
        if (io_thread_.joinable()) {
            LOGI("start_io: io thread already running");
            return true;
        }
        auto app = app_;  // io 线程持有引用，stop() join 前不会 reset
        io_thread_ = std::thread([app]() {
            LOGI("vsomeip io thread enter (blocking start)");
            app->start();  // 阻塞直到 stop()
            LOGI("vsomeip io thread exited");
        });
        LOGI("vsomeip io thread spawned");
        return true;
    }

    // ---- 阶段 3：停用。stop() 可被任意线程调用（一般由 Java 单线程执行器触发）----
    void stop() {
        std::lock_guard<std::mutex> lifecycle(lifecycle_mutex_);
        std::shared_ptr<vsomeip::application> app;
        std::thread io;
        {
            std::unique_lock<std::mutex> lock(mutex_);
            if (!app_) return;
            stopping_.store(true);
            available_.store(false);
            pending_.clear();
            app = std::move(app_);
            io.swap(io_thread_);
        }
        // The send lock has drained; app_ is now empty so no new sends can race with stop.
        LOGI("stopping vsomeip app");
        try {
            app->clear_all_handler();
            app->unsubscribe(service_.service, service_.instance,
                             service_.event_group);
            app->release_event(service_.service, service_.instance,
                               service_.event);
            app->release_service(service_.service,
                                 service_.instance);
            app->stop();
        } catch (const std::exception& e) {
            LOGE("stop() exception: %s", e.what());
        }
        if (io.joinable()) {
            io.join();
        }
        {
            std::lock_guard<std::mutex> lock(mutex_);
            available_.store(false);
        }
        LOGI("vsomeip app stopped");
        event_(ClientEvent::Stopped, 0);
    }

    // Opaque request bytes; the host codec is responsible for payload schema validation.
    bool send(const vsomeip::byte_t* data, std::size_t len) {
        std::lock_guard<std::mutex> lock(mutex_);
        auto app = app_;
        if (!app) {
            LOGE("send: not started");
            return false;
        }
        if (!available()) {
            LOGD("send: service unavailable, skip");
            return false;
        }
        if (!data || len == 0) return false;

        auto payload = vsomeip::runtime::get()->create_payload();
        std::vector<vsomeip::byte_t> bytes(data, data + len);
        payload->set_data(bytes);

        auto request = vsomeip::runtime::get()->create_request();
        request->set_service(service_.service);
        request->set_instance(service_.instance);
        request->set_method(service_.method);
        request->set_payload(payload);
        try {
            app->send(request);
        } catch (const std::exception& e) {
            LOGE("send() exception: %s", e.what());
            return false;
        }
        const auto now = std::chrono::steady_clock::now();
        for (auto it = pending_.begin(); it != pending_.end();) {
            if (now - it->second >= std::chrono::seconds(3)) it = pending_.erase(it);
            else ++it;
        }
        // send() assigns Client/Session synchronously. The receive handler shares mutex_,
        // so even a fast response cannot race ahead of this insertion.
        pending_[(std::uint32_t(request->get_client()) << 16) | request->get_session()] = now;
        LOGI("SOMEIP_TX submitted service=%04x method=%04x client=%04x session=%04x bytes=%zu",
             request->get_service(), request->get_method(), request->get_client(), request->get_session(), len);
        return true;
    }

    bool available() const { return available_.load() && network_ready_.load(); }

    void network_state(bool ready) {
        std::lock_guard<std::mutex> lock(mutex_);
        if (!ready) pending_.clear();
        const bool previous = network_ready_.exchange(ready);
        if (previous != ready && !stopping_.load()) {
            const bool effective = available();
            event_(effective ? ClientEvent::Available : ClientEvent::Unavailable, effective ? 1 : 0);
        }
    }

private:
    const SomeIpServiceDefinition service_;
    const EventCallback event_;
    const PayloadCallback payload_;
    std::mutex lifecycle_mutex_;
    std::mutex mutex_;
    std::shared_ptr<vsomeip::application> app_;
    std::thread io_thread_;  // 运行阻塞的 app_->start() 的专用线程
    std::atomic_bool available_{false};
    std::atomic_bool network_ready_{false};
    std::atomic_bool stopping_{true};
    std::map<std::uint32_t, std::chrono::steady_clock::time_point> pending_;
};


SomeIpClient::SomeIpClient(SomeIpServiceDefinition service, EventCallback event, PayloadCallback payload)
    : impl_(new Impl(std::move(service), std::move(event), std::move(payload))) {}
SomeIpClient::~SomeIpClient() = default;
bool SomeIpClient::start(const std::string& path) { return impl_->start(path); }
void SomeIpClient::stop() { impl_->stop(); }
bool SomeIpClient::send(const std::uint8_t* data, std::size_t size) { return impl_->send(data, size); }
bool SomeIpClient::available() const { return impl_->available(); }
void SomeIpClient::network_state(bool ready) { impl_->network_state(ready); }
} // namespace vehicle
