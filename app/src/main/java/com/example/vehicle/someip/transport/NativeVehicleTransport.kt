package com.example.vehicle.someip.transport

/** Raw transport boundary. No decoding, replay policy or Launcher domain objects. */
interface NativeVehicleTransport {
    val isAvailable: Boolean
    fun start(): Boolean
    fun stop()
    fun setListener(listener: Listener?)

    interface Listener {
        fun onAvailabilityChanged(available: Boolean)
        fun onPayload(payload: ByteArray?)
        fun onError(throwable: Throwable?)
    }
}