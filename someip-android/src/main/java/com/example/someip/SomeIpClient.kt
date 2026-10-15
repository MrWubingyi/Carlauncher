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
    /** Whether this client owns a started session and its remote service is available. */
    val isAvailable: Boolean

    /**
     * Starts the session, loading native libraries and acquiring Android network observation.
     * Repeated calls retain the original listener. Native/resource acquisition may throw;
     * a failed start releases acquired resources.
     *
     * @param listener Receives callbacks serialized on the main thread.
     * @return True if started, including an already started session owned by this client;
     * false if another client owns the process session or native startup fails.
     */
    fun start(listener: Listener): Boolean

    /**
     * Releases this client's session and network observation; repeated calls are harmless.
     * Queued callbacks from that session are discarded. A newer session may suppress onStopped.
     */
    fun stop()

    /** Returns submission status only. Payload bytes are passed through without a business codec. */
    fun send(payload: ByteArray): Boolean

    override fun close() = stop()

    /** Session notifications on the main thread; avoid blocking these callbacks. */
    interface Listener {
        /** The native application has registered with the routing manager. */
        fun onRegistered() {}
        /** Reports service discovery availability; this does not prove a successful method response. */
        fun onAvailabilityChanged(available: Boolean)
        /** Receives event bytes within the configured size limit, without business decoding. */
        fun onPayload(payload: ByteArray)

        /** Response status only; this API does not correlate concurrent RPC requests. */
        fun onResponse(ok: Boolean, returnCode: Int) {}
        /** Reports session shutdown if no newer session has superseded the notification. */
        fun onStopped() {}
    }
}
