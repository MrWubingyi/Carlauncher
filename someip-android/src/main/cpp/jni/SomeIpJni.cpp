// Android JNI marshaling and ownership. Payloads remain opaque to this library.
#include <jni.h>
#include <android/log.h>
#include "someip/SomeIpClient.h"

#include <exception>
#include <limits>
#include <memory>
#include <mutex>
#include <utility>
#include <vector>

extern "C" void vsomeip_android_set_network_state(const char*, const char*, bool, bool);

#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, "SOMEIP_JNI", __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, "SOMEIP_JNI", __VA_ARGS__)

namespace {
JavaVM* g_jvm = nullptr;
jclass g_bridge_class = nullptr;
jmethodID g_on_native_event = nullptr;
jmethodID g_on_native_payload = nullptr;

// start/stop are transactions. Short state locks protect shared snapshots, never IO joins.
std::mutex g_lifecycle_mutex;
std::mutex g_state_mutex;
std::mutex g_network_mutex;
bool g_network_ready = false;

class UtfChars {
public:
    UtfChars(JNIEnv* env, jstring value)
        : env_(env), value_(value), chars_(value ? env->GetStringUTFChars(value, nullptr) : nullptr) {}
    ~UtfChars() { if (chars_) env_->ReleaseStringUTFChars(value_, chars_); }
    const char* get() const { return chars_; }
private:
    JNIEnv* env_;
    jstring value_;
    const char* chars_;
};

class CallbackEnvironment {
public:
    CallbackEnvironment() {
        if (!g_jvm) return;
        const auto result = g_jvm->GetEnv(reinterpret_cast<void**>(&env_), JNI_VERSION_1_6);
        if (result == JNI_EDETACHED) {
            if (g_jvm->AttachCurrentThread(&env_, nullptr) == JNI_OK) attached_ = true;
            else env_ = nullptr;
        } else if (result != JNI_OK) {
            env_ = nullptr;
        }
    }
    ~CallbackEnvironment() {
        if (env_ && env_->ExceptionCheck()) {
            LOGE("Java callback threw; discarding exception on transport thread");
            env_->ExceptionClear();
        }
        if (attached_) g_jvm->DetachCurrentThread();
    }
    JNIEnv* get() const { return env_; }
private:
    JNIEnv* env_ = nullptr;
    bool attached_ = false;
};

void notify_payload(const std::vector<std::uint8_t>& payload) {
    if (!g_bridge_class || !g_on_native_payload
            || payload.size() > static_cast<std::size_t>(std::numeric_limits<jsize>::max())) return;
    CallbackEnvironment callback;
    auto* env = callback.get();
    if (!env) return;
    const auto size = static_cast<jsize>(payload.size());
    auto bytes = env->NewByteArray(size);
    if (!bytes) return;
    if (size > 0) {
        env->SetByteArrayRegion(bytes, 0, size, reinterpret_cast<const jbyte*>(payload.data()));
    }
    if (!env->ExceptionCheck()) env->CallStaticVoidMethod(g_bridge_class, g_on_native_payload, bytes);
    env->DeleteLocalRef(bytes);
}

void notify_java(int kind, int arg) {
    if (!g_bridge_class || !g_on_native_event) return;
    CallbackEnvironment callback;
    auto* env = callback.get();
    if (env) env->CallStaticVoidMethod(g_bridge_class, g_on_native_event, kind, arg);
}

// Explicit ABI: never rely on enum ordinals matching Kotlin event constants.
void notify_client_event(someip::ClientEvent event, int arg) {
    switch (event) {
        case someip::ClientEvent::Registered: notify_java(0, arg); break;
        case someip::ClientEvent::Available: notify_java(1, arg); break;
        case someip::ClientEvent::Unavailable: notify_java(2, arg); break;
        case someip::ClientEvent::ResponseOk: notify_java(3, arg); break;
        case someip::ClientEvent::ResponseError: notify_java(4, arg); break;
        case someip::ClientEvent::Stopped: notify_java(5, arg); break;
    }
}

struct Session {
    explicit Session(someip::SomeIpServiceDefinition definition)
        : max_request_payload_bytes(definition.max_request_payload_bytes),
          client(std::move(definition), notify_client_event, notify_payload) {}
    const std::size_t max_request_payload_bytes;
    someip::SomeIpClient client;
};

std::shared_ptr<Session> g_session;

std::shared_ptr<Session> session_snapshot() {
    std::lock_guard<std::mutex> lock(g_state_mutex);
    return g_session;
}

void remove_session(const std::shared_ptr<Session>& session) {
    std::lock_guard<std::mutex> lock(g_state_mutex);
    if (g_session == session) g_session.reset();
}

bool initialize_callbacks(JNIEnv* env, jclass bridge_class) {
    if (g_bridge_class && g_on_native_event && g_on_native_payload) return true;
    if (env->GetJavaVM(&g_jvm) != JNI_OK) return false;
    const auto event = env->GetStaticMethodID(bridge_class, "onNativeEvent", "(II)V");
    if (!event || env->ExceptionCheck()) return false;
    const auto payload = env->GetStaticMethodID(bridge_class, "onNativePayload", "([B)V");
    if (!payload || env->ExceptionCheck()) return false;
    auto global = static_cast<jclass>(env->NewGlobalRef(bridge_class));
    if (!global) return false;
    g_on_native_event = event;
    g_on_native_payload = payload;
    g_bridge_class = global;
    return true;
}

bool valid_configuration(jint service, jint instance, jint event, jint event_group, jint method,
                         jint max_event_payload, jint max_request_payload) {
    constexpr jint maximum_payload_bytes = 1024 * 1024;
    const auto identifier = [](jint value) { return value >= 0 && value <= 0xfffe; };
    return identifier(service) && identifier(instance) && identifier(event_group)
            && event >= 0x8000 && event <= 0xfffe && method >= 0 && method <= 0x7fff
            && max_event_payload > 0 && max_event_payload <= maximum_payload_bytes
            && max_request_payload > 0 && max_request_payload <= maximum_payload_bytes;
}
} // namespace

extern "C" JNIEXPORT jboolean JNICALL
Java_com_example_someip_internal_NativeBridge_nativeStart(
        JNIEnv* env, jclass bridge_class, jstring config_path, jstring application_name,
        jint service, jint instance, jint event, jint event_group, jint method,
        jint max_event_payload, jint max_request_payload) {
    std::lock_guard<std::mutex> lifecycle(g_lifecycle_mutex);
    if (session_snapshot()) {
        LOGE("nativeStart: a process session is already active");
        return JNI_FALSE;
    }
    if (!valid_configuration(service, instance, event, event_group, method,
                             max_event_payload, max_request_payload)) return JNI_FALSE;
    std::shared_ptr<Session> session;
    try {
        if (!initialize_callbacks(env, bridge_class)) return JNI_FALSE;
        UtfChars path(env, config_path);
        if (!path.get() || path.get()[0] == '\0') return JNI_FALSE;
        UtfChars name(env, application_name);
        if (!name.get() || name.get()[0] == '\0') return JNI_FALSE;
        session = std::make_shared<Session>(someip::SomeIpServiceDefinition{
            static_cast<std::uint16_t>(service), static_cast<std::uint16_t>(instance),
            static_cast<std::uint16_t>(event), static_cast<std::uint16_t>(event_group),
            static_cast<std::uint16_t>(method), name.get(),
            static_cast<std::size_t>(max_event_payload),
            static_cast<std::size_t>(max_request_payload)
        });
        {
            // Preserve observations delivered before start, without losing a concurrent update.
            // No IO loop is running yet, and network_state cannot emit a callback before start.
            std::lock_guard<std::mutex> network(g_network_mutex);
            session->client.network_state(g_network_ready);
            std::lock_guard<std::mutex> state(g_state_mutex);
            g_session = session;
        }
        if (session->client.start(path.get())) {
            LOGI("nativeStart: ok (IO loop on dedicated thread)");
            return JNI_TRUE;
        }
        LOGE("nativeStart: start failed");
    } catch (const std::exception& error) {
        LOGE("nativeStart: %s", error.what());
    } catch (...) {
        LOGE("nativeStart: unknown C++ exception");
    }
    remove_session(session);
    if (session) session->client.stop();
    return JNI_FALSE;
}

extern "C" JNIEXPORT void JNICALL
Java_com_example_someip_internal_NativeBridge_nativeStop(JNIEnv*, jclass) {
    std::lock_guard<std::mutex> lifecycle(g_lifecycle_mutex);
    auto session = session_snapshot();
    if (!session) return;
    remove_session(session);
    // A ConnectivityManager callback may still hold a session snapshot. It never takes
    // the lifecycle lock, and stop/join never holds either state or network locks.
    try {
        session->client.stop();
    } catch (const std::exception& error) {
        LOGE("nativeStop: %s", error.what());
    } catch (...) {
        LOGE("nativeStop: unknown C++ exception");
    }
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_example_someip_internal_NativeBridge_nativeSend(JNIEnv* env, jclass, jbyteArray payload) {
    if (!payload) return JNI_FALSE;
    auto session = session_snapshot();
    if (!session) return JNI_FALSE;
    const auto size = env->GetArrayLength(payload);
    if (static_cast<std::size_t>(size) > session->max_request_payload_bytes) return JNI_FALSE;
    try {
        std::vector<std::uint8_t> bytes(static_cast<std::size_t>(size));
        if (size > 0) {
            env->GetByteArrayRegion(payload, 0, size, reinterpret_cast<jbyte*>(bytes.data()));
            if (env->ExceptionCheck()) return JNI_FALSE;
        }
        return session->client.send(bytes.data(), bytes.size()) ? JNI_TRUE : JNI_FALSE;
    } catch (const std::exception& error) {
        LOGE("nativeSend: %s", error.what());
        return JNI_FALSE;
    } catch (...) {
        LOGE("nativeSend: unknown C++ exception");
        return JNI_FALSE;
    }
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_example_someip_internal_NativeBridge_nativeIsAvailable(JNIEnv*, jclass) {
    auto session = session_snapshot();
    return session && session->client.available() ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT void JNICALL
Java_com_example_someip_internal_NativeBridge_nativeNetworkState(
        JNIEnv* env, jclass, jstring address, jstring iface, jboolean available, jboolean route) {
    UtfChars address_chars(env, address);
    if (!address_chars.get()) return;
    UtfChars iface_chars(env, iface);
    if (!iface_chars.get()) return;
    try {
        std::lock_guard<std::mutex> network(g_network_mutex);
        const bool up = available == JNI_TRUE && iface_chars.get()[0] != '\0';
        g_network_ready = up;
        auto session = session_snapshot();
        if (session) session->client.network_state(up);
        vsomeip_android_set_network_state(address_chars.get(), iface_chars.get(),
                                         up, up && route == JNI_TRUE);
    } catch (const std::exception& error) {
        LOGE("nativeNetworkState: %s", error.what());
    } catch (...) {
        LOGE("nativeNetworkState: unknown C++ exception");
    }
}
