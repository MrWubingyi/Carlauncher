package com.example.vehicle.someip.android

import com.example.vehicle.someip.android.jni.VsomeipClient
import com.example.vehicle.someip.transport.NativeVehicleTransport

import android.content.Context
import android.util.Log
import java.io.File

/** JNI transport and Android configuration. Payload decoding belongs to SomeIpVehicleAdapter. */
open class VSomeIpNativeTransport(context: Context?) :
    NativeVehicleTransport, VsomeipClient.Listener {

    private val context: Context?
    @Volatile private var listener: NativeVehicleTransport.Listener? = null
    @Volatile private var stopping: Boolean = false
    private var nativeListener: VsomeipClient.Listener? = null

    override val isAvailable: Boolean
        get() {
            try {
                return VsomeipClient.isAvailable
            } catch (expectedNativeUnavailable: UnsatisfiedLinkError) {
                // A missing library or JNI entry point means this capability is unavailable.
                // Startup reports native loading failures through the owning Service.
                return false
            }
        }

    init {
        this.context = if (context != null) context!!.getApplicationContext() else null
    }

    @Synchronized
    override fun start(): Boolean {
        stopping = false
        try {
            val config = SomeIpConfigFile(context).prepare()
            // A new callback identity prevents queued JNI events from a previous start
            // being delivered to this instance after a stop/start cycle.
            val callbacks = nativeListener ?: object : VsomeipClient.Listener {
                override fun onAvailable(available: Boolean) = this@VSomeIpNativeTransport.onAvailable(available)
                override fun onVehicleEvent(payload: ByteArray?) = this@VSomeIpNativeTransport.onVehicleEvent(payload)
                override fun onResponse(ok: Boolean, returnCode: Int) = this@VSomeIpNativeTransport.onResponse(ok, returnCode)
                override fun onStopped() = this@VSomeIpNativeTransport.onStopped()
            }
            nativeListener = callbacks
            VsomeipClient.setListener(callbacks)
            val ok = VsomeipClient.start(context, config.getAbsolutePath())
            Log.i(TAG, "VSomeIpNativeTransport start ok=" + ok)
            return ok
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start VSomeIpNativeTransport", e)
            val l = listener
            if (l != null) {
                l!!.onError(e)
            }
            return false
        }
    }

    @Synchronized
    override fun stop() {
        stopping = true
        try {
            nativeListener?.let { VsomeipClient.clearListener(it) }
            nativeListener = null
            VsomeipClient.stop()
        } catch (e: UnsatisfiedLinkError) {
            Log.w(TAG, "Native library not available during stop", e)
        } finally {
            Log.i(TAG, "VSomeIpNativeTransport stopped")
        }
    }

    @Synchronized
    override fun setListener(listener: NativeVehicleTransport.Listener?) {
        this.listener = listener
    }

    // ---- VsomeipClient.Listener 回调实现 ----

    override fun onAvailable(available: Boolean) {
        if (stopping) {
            return
        }
        val l = listener
        if (l != null) {
            l!!.onAvailabilityChanged(available)
        }
    }

    override fun onVehicleEvent(payload: ByteArray?) {
        if (!stopping) listener?.onPayload(payload)
    }

    override fun onResponse(ok: Boolean, returnCode: Int) {
        if (stopping) {
            return
        }
        if (!ok) {
            val l = listener
            if (l != null) {
                l!!.onError(Exception("SOME/IP response error code=" + returnCode))
            }
        }
    }

    override fun onStopped() {
        if (stopping) {
            return
        }
        stopping = true
        val l = listener
        if (l != null) {
            l!!.onAvailabilityChanged(false)
        }
    }

    companion object {
        private const val TAG = "VSOMEIP_TRANSPORT"

        // Retain the existing test/probe entry point; configuration has a single implementation.
        @JvmStatic
        internal fun rewriteUnicast(config: File?, ip: String?) =
            SomeIpConfigFile.rewriteUnicast(config, ip)
    }
}
