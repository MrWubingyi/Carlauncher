package com.example.someip

/**
 * One configured service, event subscription and request method.
 *
 * The process may own one active client. A competing client's [start] returns false.
 * Callbacks are serialized on Android's main thread. Repeated start/stop calls are idempotent;
 * a repeated start retains the original listener. Call [close] when the owner is destroyed.
 * This vSomeIP build caches its runtime base directory per process. Later sessions may change
 * configuration files and protocol parameters within the first configuration directory; switching
 * to a different directory requires a fresh process.
 */
interface SomeIpClient : AutoCloseable {
    val isAvailable: Boolean

    fun start(listener: Listener): Boolean

    fun stop()

    /** Returns submission status only. Payload bytes are passed through without a business codec. */
    fun send(payload: ByteArray): Boolean

    override fun close() = stop()

    interface Listener {
        fun onRegistered() {}
        fun onAvailabilityChanged(available: Boolean)
        fun onPayload(payload: ByteArray)

        /** Response status only; this API does not correlate concurrent RPC requests. */
        fun onResponse(ok: Boolean, returnCode: Int) {}
        fun onStopped() {}
    }
}
