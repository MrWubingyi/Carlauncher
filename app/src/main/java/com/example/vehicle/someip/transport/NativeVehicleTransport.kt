package com.example.vehicle.someip.transport

/** Raw transport boundary. No decoding, replay policy or Launcher domain objects. */
interface NativeVehicleTransport {
    val isAvailable: Boolean
    fun start(): Boolean
    /** Submission only; execution confirmation arrives as a correlated gateway event. */
    fun send(payload: ByteArray): Boolean = false
    fun stop()
    fun setListener(listener: Listener?)

    interface Listener {
        fun onAvailabilityChanged(available: Boolean)
        fun onPayload(payload: ByteArray?)
        fun onError(throwable: Throwable?)
    }
}
