package com.example.someip

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.example.someip.internal.AndroidSessionResources
import com.example.someip.internal.NativeBridge
import com.example.someip.internal.SessionCoordinator
import java.io.File

/** Factory for process-coordinated SOME/IP clients using the application context. */
object VSomeIp {
    /** Read and validate JSON before creating the client. Recreate after editing a stopped client's file. */
    @JvmStatic
    fun create(context: Context, configFile: File): SomeIpClient =
        create(context, SomeIpClientConfig.fromFile(configFile))

    /** Creating a client does not load native libraries or start network observation. */
    @JvmStatic
    fun create(context: Context, config: SomeIpClientConfig): SomeIpClient =
        coordinator.create(config, AndroidSessionResources(context.applicationContext))

    private val coordinator: SessionCoordinator by lazy {
        val handler = Handler(Looper.getMainLooper())
        SessionCoordinator(
            object : SessionCoordinator.Backend {
                override fun ensureLoaded() = NativeBridge.ensureLoaded()
                override fun start(config: SomeIpClientConfig): Boolean = with(config.service) {
                    NativeBridge.nativeStart(
                        config.configPath, applicationName, serviceId, instanceId, eventId,
                        eventGroupId, methodId, maxEventPayloadBytes, maxRequestPayloadBytes,
                    )
                }
                override fun stop() = NativeBridge.nativeStop()
                override fun send(payload: ByteArray): Boolean = NativeBridge.nativeSend(payload)
                override fun isAvailable(): Boolean = NativeBridge.nativeIsAvailable()
            },
            SessionCoordinator.Dispatcher { callback -> handler.post { callback() } },
        ).also {
            NativeBridge.eventSink = it::onNativeEvent
            NativeBridge.payloadSink = it::onNativePayload
        }
    }
}
