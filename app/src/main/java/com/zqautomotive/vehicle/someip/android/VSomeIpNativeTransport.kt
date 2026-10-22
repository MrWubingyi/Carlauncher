package com.zqautomotive.vehicle.someip.android

import android.content.Context
import com.example.someip.SomeIpClient
import com.zqautomotive.vehicle.someip.transport.NativeVehicleTransport
import java.io.File

/** Adapts the reusable SOME/IP client to the vehicle byte transport contract. */
open class VSomeIpNativeTransport internal constructor(
    private val createClient: () -> SomeIpClient,
) : NativeVehicleTransport {
    constructor(context: Context?) : this({ VehicleSomeIpClientFactory.create(context) })

    @Volatile private var listener: NativeVehicleTransport.Listener? = null
    @Volatile private var client: SomeIpClient? = null
    @Volatile private var callbacks: ClientCallbacks? = null

    override val isAvailable: Boolean
        get() = callbacks != null && client?.isAvailable == true

    @Synchronized
    override fun start(): Boolean {
        if (callbacks != null) return true
        var started = false
        try {
            val created = createClient()
            client = created
            val current = ClientCallbacks()
            callbacks = current
            started = created.start(current)
            return started
        } catch (error: Exception) {
            listener?.onError(error)
            return false
        } finally {
            if (!started) stop()
        }
    }

    override fun send(payload: ByteArray): Boolean =
        if (isAvailable) client?.send(payload) == true else false

    @Synchronized
    override fun stop() {
        // Invalidate callbacks before releasing a session, including a synchronous onStopped.
        callbacks = null
        val previous = client
        client = null
        previous?.close()
    }

    @Synchronized
    override fun setListener(listener: NativeVehicleTransport.Listener?) {
        this.listener = listener
    }

    private inner class ClientCallbacks : SomeIpClient.Listener {
        override fun onAvailabilityChanged(available: Boolean) {
            if (callbacks === this) listener?.onAvailabilityChanged(available)
        }

        override fun onPayload(payload: ByteArray) {
            if (callbacks === this) listener?.onPayload(payload)
        }

        override fun onResponse(ok: Boolean, returnCode: Int) {
            if (callbacks === this && !ok) {
                listener?.onError(IllegalStateException("SOME/IP response error code=$returnCode"))
            }
        }

        override fun onStopped() {
            synchronized(this@VSomeIpNativeTransport) {
                if (callbacks !== this) return
                callbacks = null
                client = null
                listener?.onAvailabilityChanged(false)
            }
        }
    }

    companion object {
        @JvmStatic
        internal fun rewriteUnicast(config: File?, ip: String?) =
            SomeIpConfigFile.rewriteUnicast(config, ip)
    }
}
