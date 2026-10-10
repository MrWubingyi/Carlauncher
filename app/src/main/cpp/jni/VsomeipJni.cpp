// JNI marshaling and Android host composition only. Transport lives in vehicle/native.
#include <jni.h>
#include <android/log.h>
#include "../config/VehicleSomeIpConfig.hpp"
#include "someip/SomeIpClient.h"
#include <exception>
#include <vector>

extern "C" void vsomeip_android_set_network_state(const char*, const char*, bool, bool);

#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, "VSOMEIP_JNI", __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, "VSOMEIP_JNI", __VA_ARGS__)

namespace {
JavaVM* g_jvm = nullptr;
jclass g_client_cls = nullptr;          // global ref
jmethodID g_on_native_event = nullptr;  // static void onNativeEvent(int kind, int arg)
jmethodID g_on_vehicle_event = nullptr;

void notify_vehicle(const std::vector<std::uint8_t>& payload) {
    if (payload.empty() || payload.size() > 4096
            || !g_jvm || !g_client_cls || !g_on_vehicle_event) return;
    JNIEnv* env = nullptr;
    bool attached = false;
    if (g_jvm->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6) == JNI_EDETACHED) {
        if (g_jvm->AttachCurrentThread(&env, nullptr) != JNI_OK) return;
        attached = true;
    }
    if (!env) return;
    auto bytes = env->NewByteArray(static_cast<jsize>(payload.size()));
    if (bytes) {
        env->SetByteArrayRegion(bytes, 0, payload.size(),
                              reinterpret_cast<const jbyte*>(payload.data()));
        if (!env->ExceptionCheck()) env->CallStaticVoidMethod(g_client_cls, g_on_vehicle_event, bytes);
        env->DeleteLocalRef(bytes);
    }
    if (env->ExceptionCheck()) env->ExceptionClear();
    if (attached) g_jvm->DetachCurrentThread();
}

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

// Keep the existing Java event ABI explicit rather than depending on enum ordinals.
void notify_client_event(vehicle::ClientEvent event, int arg) {
    switch (event) {
        case vehicle::ClientEvent::Registered: notify_java(0, arg); break;
        case vehicle::ClientEvent::Available: notify_java(1, arg); break;
        case vehicle::ClientEvent::Unavailable: notify_java(2, arg); break;
        case vehicle::ClientEvent::ResponseOk: notify_java(3, arg); break;
        case vehicle::ClientEvent::ResponseError: notify_java(4, arg); break;
        case vehicle::ClientEvent::Stopped: notify_java(5, arg); break;
    }
}

vehicle::SomeIpClient g_client({
    vehicle_someip::kServiceId, vehicle_someip::kInstanceId,
    vehicle_someip::kStateEventId, vehicle_someip::kStateEventGroupId,
    vehicle_someip::kSetVehicleStateMethodId, "soc-vehicle-client"
}, notify_client_event, notify_vehicle);
} // namespace
extern "C" JNIEXPORT jboolean JNICALL
Java_com_example_vehicle_someip_android_jni_VsomeipClient_nativeStart(
        JNIEnv* env, jclass, jstring config_path) {
    LOGI("nativeStart: begin");
    try {
        if (!g_jvm) env->GetJavaVM(&g_jvm);
        if (!g_client_cls) {
            jclass local = env->FindClass("com/example/vehicle/someip/android/jni/VsomeipClient");
            if (local) {
                g_client_cls = static_cast<jclass>(env->NewGlobalRef(local));
                env->DeleteLocalRef(local);
            }
        }
        if (g_client_cls && !g_on_native_event) {
            g_on_native_event = env->GetStaticMethodID(g_client_cls, "onNativeEvent", "(II)V");
        }
        if (g_client_cls && !g_on_vehicle_event) {
            g_on_vehicle_event = env->GetStaticMethodID(g_client_cls, "onNativeVehicleEvent", "([B)V");
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
Java_com_example_vehicle_someip_android_jni_VsomeipClient_nativeStop(JNIEnv*, jclass) {
    g_client.stop();
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_example_vehicle_someip_android_jni_VsomeipClient_nativeSendState(
        JNIEnv* env, jclass, jbyteArray payload) {
    if (!payload) return JNI_FALSE;
    const jsize len = env->GetArrayLength(payload);
    if (len != static_cast<jsize>(vehicle_someip::kPayloadSize)) {
        LOGE("nativeSendState: expect %zu bytes, got %d",
             vehicle_someip::kPayloadSize, static_cast<int>(len));
        return JNI_FALSE;
    }
    std::vector<std::uint8_t> buf(static_cast<std::size_t>(len));
    env->GetByteArrayRegion(payload, 0, len, reinterpret_cast<jbyte*>(buf.data()));
    if (env->ExceptionCheck()) return JNI_FALSE;
    try {
        return g_client.send(buf.data(), buf.size()) ? JNI_TRUE : JNI_FALSE;
    } catch (const std::exception& e) {
        LOGE("nativeSendState: %s", e.what());
        return JNI_FALSE;
    }
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_example_vehicle_someip_android_jni_VsomeipClient_nativeIsAvailable(JNIEnv*, jclass) {
    return g_client.available() ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT void JNICALL
Java_com_example_vehicle_someip_android_jni_VsomeipClient_nativeNetworkState(
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
