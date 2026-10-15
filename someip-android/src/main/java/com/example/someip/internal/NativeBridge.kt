package com.example.someip.internal

/** JNI surface is internal. Keep its static names/signatures in consumer-rules.pro. */
internal object NativeBridge {
    private var loaded = false
    @Volatile var eventSink: ((Int, Int) -> Unit)? = null
    @Volatile var payloadSink: ((ByteArray) -> Unit)? = null

    @Synchronized
    fun ensureLoaded() {
        if (loaded) return
        System.loadLibrary("vsomeip3")
        System.loadLibrary("someip_jni")
        loaded = true
    }

    @JvmStatic external fun nativeStart(
        configPath: String,
        applicationName: String,
        serviceId: Int,
        instanceId: Int,
        eventId: Int,
        eventGroupId: Int,
        methodId: Int,
        maxEventPayloadBytes: Int,
        maxRequestPayloadBytes: Int,
    ): Boolean

    @JvmStatic external fun nativeStop()
    @JvmStatic external fun nativeSend(payload: ByteArray?): Boolean
    @JvmStatic external fun nativeIsAvailable(): Boolean
    @JvmStatic external fun nativeNetworkState(
        address: String?,
        iface: String?,
        available: Boolean,
        multicastRoute: Boolean,
    )

    @JvmStatic fun onNativeEvent(kind: Int, argument: Int) {
        eventSink?.invoke(kind, argument)
    }

    @JvmStatic fun onNativePayload(payload: ByteArray) {
        payloadSink?.invoke(payload)
    }
}
