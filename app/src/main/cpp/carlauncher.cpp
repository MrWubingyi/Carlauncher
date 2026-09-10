// W37 WP1 · carlauncher.cpp —— JNI vSomeIP Client（第三步草稿，修复阻塞式 start / 进程崩溃）
//
// 取代 2026-08-24 Provider 原型（main() + TCP:19091 ingress + Event 0x1234 发布，作废）。
// 角色：Android vSomeIP Client，调用 Ubuntu Service 0x1111/0x2222 的 Method 0x1001
//       SetVehicleState，payload 固定 16 B（schema v0.2），错误只用 vsomeip Return Code。
// 生命周期：g_client 单例归 JNI 库；start/stop 幂等；send 线程安全。
//
// 2026-09-07 崩溃修复要点（abort 0x...b39c4 = SIGABRT / "destroyed mutex"）：
//   1. vsomeip 3.7.5 的 application::start() 会【阻塞调用线程】跑 io_.run()（见
//      implementation/runtime/src/application_impl.cpp:437）。旧代码在 Service.onCreate
//      （主线程）里直接 app_->start()，把主线程卡死在 native 侧，且 vsomeip 内部任何
//      io 异常都会走 VSOMEIP_TERMINATE -> std::abort() 直接杀死进程（进程退出时再与
//      libhwui 的 hwuiTask 静态析构竞争，表现为 libhwui "pthread_mutex_lock called on
//      a destroyed mutex" 的 SIGABRT tombstone）。
//   2. 现在 start() 拆成 init()（非阻塞，创建 app + 注册回调 + app_->init()）和
//      start_io()（在专用 std::thread 上跑阻塞的 app_->start()），JNI 层立即返回；
//      stop() 由 stop 线程调用 app_->stop() 解除阻塞并 join io 线程。
//   3. send_state / stop / is_available 全程持 g_client.mutex_，杜绝 10 Hz 发送线程与
//      stop 线程的 app_ 生命周期竞争。
//   4. 增加阶段日志，便于下一次运行精确定位死在哪个阶段。
//
// 依赖：libvsomeip3.so（IMPORTED，单库 monolithic 构建，见 CMakeLists.txt）；
//       configuration 与 service-discovery 均已编入核心库，运行期不再 dlopen 插件。

#include <jni.h>
#include <android/log.h>

#include "protocol_ids.hpp"

#include <vsomeip/vsomeip.hpp>

#include <atomic>
#include <cinttypes>
#include <cstdlib>
#include <exception>
#include <chrono>
#include <map>
#include <memory>
#include <mutex>
#include <string>
#include <thread>
#include <unistd.h>
#include <vector>

// Exported by the reproducible Android connector patch in tools/vsomeip-android.
extern "C" void vsomeip_android_set_network_state(const char*, const char*, bool, bool);

#define LOG_TAG "VSOMEIP_JNI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

// 回调种类（与 VsomeipClient.onNativeEvent(int,int) 的约定一致）
enum JavaEventKind {
    kEvtRegistered     = 0,
    kEvtAvailable      = 1,
    kEvtUnavailable    = 2,
    kEvtResponseOk     = 3,
    kEvtResponseError  = 4,
    kEvtStopped        = 5
};

namespace {

JavaVM* g_jvm = nullptr;
jclass g_client_cls = nullptr;          // global ref
jmethodID g_on_native_event = nullptr;  // static void onNativeEvent(int kind, int arg)

// 从 vsomeip 内部线程安全回调 Java；回调里不碰 UI，由 Java 侧转主线程。
void notify_java(int kind, int arg) {
    if (!g_jvm || !g_client_cls || !g_on_native_event) return;
    JNIEnv* env = nullptr;
    bool attached = false;
    if (g_jvm->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6) == JNI_EDETACHED) {
        if (g_jvm->AttachCurrentThread(&env, nullptr) != JNI_OK) return;
        attached = true;
    }
    if (!env) return;
    env->CallStaticVoidMethod(g_client_cls, g_on_native_event, kind, arg);
    if (env->ExceptionCheck()) env->ExceptionClear();  // 回调异常不回抛到 C++
    if (attached) g_jvm->DetachCurrentThread();
}

class VsomeipClient {
public:
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
        setenv("VSOMEIP_APPLICATION_NAME", "soc-vehicle-client", 1);

        auto app = vsomeip::runtime::get()->create_application("soc-vehicle-client");
        if (!app) {
            LOGE("create_application failed");
            return false;
        }
        LOGI("create_application ok");

        // The application owns its handler; a weak reference avoids a self-retaining cycle.
        std::weak_ptr<vsomeip::application> weak_app = app;
        app->register_state_handler([weak_app](vsomeip::state_type_e state) {
            auto app = weak_app.lock();
            if (!app) return;
            if (state == vsomeip::state_type_e::ST_REGISTERED) {
                LOGI("registered; request_service %04x/%04x",
                     vehicle_someip::kServiceId, vehicle_someip::kInstanceId);
                app->request_service(vehicle_someip::kServiceId,
                                     vehicle_someip::kInstanceId);
                notify_java(kEvtRegistered, 0);
            }
        });

        app->register_availability_handler(
                vehicle_someip::kServiceId, vehicle_someip::kInstanceId,
                [this](vsomeip::service_t, vsomeip::instance_t, bool available) {
                    if (stopping_.load()) return;
                    available_.store(available);
                    available = available && network_ready_.load();
                    LOGI("availability: %s",
                         available ? "ON_AVAILABLE" : "ON_UNAVAILABLE");
                    notify_java(available ? kEvtAvailable : kEvtUnavailable,
                                available ? 1 : 0);
                });

        app->register_message_handler(
                vehicle_someip::kServiceId, vehicle_someip::kInstanceId,
                vsomeip::ANY_METHOD,
                [this](const std::shared_ptr<vsomeip::message>& msg) {
                    if (msg->get_method() != vehicle_someip::kSetVehicleStateMethodId) return;
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
                    notify_java(type == vsomeip::message_type_e::MT_RESPONSE
                                        && rc == vsomeip::return_code_e::E_OK
                                        ? kEvtResponseOk : kEvtResponseError,
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
            app->release_service(vehicle_someip::kServiceId,
                                 vehicle_someip::kInstanceId);
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
        notify_java(kEvtStopped, 0);
    }

    // Java 发送线程调用；len 必须 == 16（schema v0.2）。与 stop() 并发安全。
    bool send_state(const vsomeip::byte_t* data, std::size_t len) {
        std::lock_guard<std::mutex> lock(mutex_);
        auto app = app_;
        if (!app) {
            LOGE("send_state: not started");
            return false;
        }
        if (!available()) {
            LOGD("send_state: service unavailable, skip");
            return false;
        }
        if (len != vehicle_someip::kPayloadSize) {
            LOGE("send_state: bad len=%zu, expect %zu",
                 len, vehicle_someip::kPayloadSize);
            return false;
        }

        auto payload = vsomeip::runtime::get()->create_payload();
        std::vector<vsomeip::byte_t> bytes(data, data + len);
        payload->set_data(bytes);

        auto request = vsomeip::runtime::get()->create_request();
        request->set_service(vehicle_someip::kServiceId);
        request->set_instance(vehicle_someip::kInstanceId);
        request->set_method(vehicle_someip::kSetVehicleStateMethodId);
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
        std::uint32_t seq = 0;
        for (std::size_t i = 1; i < 5; ++i) seq = (seq << 8) | data[i];
        char hex[vehicle_someip::kPayloadSize * 2 + 1];
        static constexpr char digits[] = "0123456789abcdef";
        for (std::size_t i = 0; i < len; ++i) {
            hex[i * 2] = digits[data[i] >> 4];
            hex[i * 2 + 1] = digits[data[i] & 15];
        }
        hex[len * 2] = '\0';
        LOGI("SOMEIP_TX submitted service=%04x method=%04x client=%04x session=%04x seq=%" PRIu32 " hex=%s",
             request->get_service(), request->get_method(), request->get_client(), request->get_session(), seq, hex);
        return true;
    }

    bool available() const { return available_.load() && network_ready_.load(); }

    void network_state(bool ready) {
        std::lock_guard<std::mutex> lock(mutex_);
        if (!ready) pending_.clear();
        const bool previous = network_ready_.exchange(ready);
        if (previous != ready && !stopping_.load()) {
            const bool effective = available();
            notify_java(effective ? kEvtAvailable : kEvtUnavailable, effective ? 1 : 0);
        }
    }

private:
    std::mutex lifecycle_mutex_;
    std::mutex mutex_;
    std::shared_ptr<vsomeip::application> app_;
    std::thread io_thread_;  // 运行阻塞的 app_->start() 的专用线程
    std::atomic_bool available_{false};
    std::atomic_bool network_ready_{false};
    std::atomic_bool stopping_{true};
    std::map<std::uint32_t, std::chrono::steady_clock::time_point> pending_;
};

VsomeipClient g_client;  // 单例：生命周期归 JNI 库（第三步约定：单次 start/stop）

}  // namespace

// ---- JNI 导出：com.example.carlauncher.someip.VsomeipClient ----

extern "C" JNIEXPORT jboolean JNICALL
Java_com_example_carlauncher_someip_VsomeipClient_nativeStart(
        JNIEnv* env, jclass, jstring config_path) {
    LOGI("nativeStart: begin");
    try {
        if (!g_jvm) env->GetJavaVM(&g_jvm);
        if (!g_client_cls) {
            jclass local = env->FindClass("com/example/carlauncher/someip/VsomeipClient");
            if (local) {
                g_client_cls = static_cast<jclass>(env->NewGlobalRef(local));
                env->DeleteLocalRef(local);
            }
        }
        if (g_client_cls && !g_on_native_event) {
            g_on_native_event = env->GetStaticMethodID(g_client_cls, "onNativeEvent", "(II)V");
        }

        const char* path = config_path ? env->GetStringUTFChars(config_path, nullptr) : nullptr;
        const bool ok_start = g_client.start(path ? path : "");
        if (path) env->ReleaseStringUTFChars(config_path, path);
        if (!ok_start) {
            LOGE("nativeStart: start failed");
            return JNI_FALSE;
        }
        LOGI("nativeStart: ok (non-blocking; io loop on dedicated thread)");
        return JNI_TRUE;
    } catch (const std::exception& e) {
        LOGE("nativeStart: C++ exception: %s", e.what());
        return JNI_FALSE;
    } catch (...) {
        LOGE("nativeStart: unknown C++ exception");
        return JNI_FALSE;
    }
}

extern "C" JNIEXPORT void JNICALL
Java_com_example_carlauncher_someip_VsomeipClient_nativeStop(JNIEnv*, jclass) {
    g_client.stop();
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_example_carlauncher_someip_VsomeipClient_nativeSendState(
        JNIEnv* env, jclass, jbyteArray payload) {
    if (!payload) return JNI_FALSE;
    const jsize len = env->GetArrayLength(payload);
    if (len != static_cast<jsize>(vehicle_someip::kPayloadSize)) {
        LOGE("nativeSendState: expect %zu bytes, got %d",
             vehicle_someip::kPayloadSize, static_cast<int>(len));
        return JNI_FALSE;
    }
    std::vector<vsomeip::byte_t> buf(static_cast<std::size_t>(len));
    env->GetByteArrayRegion(payload, 0, len, reinterpret_cast<jbyte*>(buf.data()));
    if (env->ExceptionCheck()) return JNI_FALSE;
    try {
        return g_client.send_state(buf.data(), buf.size()) ? JNI_TRUE : JNI_FALSE;
    } catch (const std::exception& e) {
        LOGE("nativeSendState: %s", e.what());
        return JNI_FALSE;
    }
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_example_carlauncher_someip_VsomeipClient_nativeIsAvailable(JNIEnv*, jclass) {
    return g_client.available() ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT void JNICALL
Java_com_example_carlauncher_someip_VsomeipClient_nativeNetworkState(
        JNIEnv* env, jclass, jstring address, jstring iface, jboolean available, jboolean route) {
    if (!address || !iface) return;
    const char* address_chars = env->GetStringUTFChars(address, nullptr);
    if (!address_chars) return;
    const char* iface_chars = env->GetStringUTFChars(iface, nullptr);
    if (!iface_chars) { env->ReleaseStringUTFChars(address, address_chars); return; }
    const bool up = available == JNI_TRUE && iface_chars[0] != '\0';
    g_client.network_state(up);
    vsomeip_android_set_network_state(address_chars, iface_chars, up, up && route == JNI_TRUE);
    env->ReleaseStringUTFChars(iface, iface_chars);
    env->ReleaseStringUTFChars(address, address_chars);
}
