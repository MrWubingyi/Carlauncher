package com.example.vehicle.someip.adapter

import com.example.vehicle.someip.transport.NativeVehicleTransport

import com.example.vehicle.control.*
import org.json.JSONObject
import com.example.vehicle.model.VehicleSignals
import com.example.vehicle.someip.codec.SomeIpEventCodec

/** Stream policy independent of Android scheduling and Launcher models.
 * The owner calls checkTimeout periodically. Only accepted events refresh the monotonic deadline.
 */
class SomeIpVehicleAdapter(
    private val transport: NativeVehicleTransport,
    val connectionMonitor: SomeipConnectionMonitor,
    private val elapsedRealtime: () -> Long,
) {
    enum class Status { STOPPED, CONNECTING, AVAILABLE, UNAVAILABLE, TIMED_OUT, ERROR }

    interface Listener {
        fun onSignals(signals: VehicleSignals)
        fun onStatus(status: Status)
        fun onControlChanged() {}
        fun onError(error: Throwable)
    }

    private var listener: Listener? = null
    private val control = VehicleControlSession(elapsedRealtime, transport::send) { listener?.onControlChanged() }

    @Synchronized fun controlSnapshot(): VehicleControlSnapshot = control.snapshot()
    @Synchronized fun requestControl(action: VehicleAction): Boolean {
        if (!isRunning) return false
        return try { control.request(action) } catch (error: Exception) { reportError(error); false }
    }
    private var generation = 0L
    private var lastTimestamp = 0L
    private var lastSequence = -1L
    var isRunning = false
        private set
    var status = Status.STOPPED
        private set

    @Synchronized
    fun start(listener: Listener): Boolean {
        if (isRunning) return true
        this.listener = listener
        isRunning = true
        val session = ++generation
        resetOrdering()
        connectionMonitor.start()
        publishStatus(Status.CONNECTING)
        transport.setListener(object : NativeVehicleTransport.Listener {
            override fun onAvailabilityChanged(available: Boolean) = synchronized(this@SomeIpVehicleAdapter) {
                if (!active(session)) return@synchronized
                val wasAvailable = connectionMonitor.snapshot().isAvailable
                connectionMonitor.onAvailability(available, elapsedRealtime())
                control.onAvailability(available)
                if (available && !wasAvailable) {
                    try { control.queryCapabilities() } catch (error: Exception) { reportError(error) }
                }
                if (!available) {
                    resetOrdering()
                    publishStatus(Status.UNAVAILABLE)
                } else if (status == Status.UNAVAILABLE) {
                    publishStatus(Status.CONNECTING)
                }
            }

            override fun onPayload(payload: ByteArray?) = synchronized(this@SomeIpVehicleAdapter) {
                if (!active(session) || !connectionMonitor.snapshot().isAvailable) return@synchronized
                val signals = try {
                    require(payload != null && payload.size in 1..4096 && payload.none { it == 0.toByte() }) { "Invalid gateway payload" }
                    val frame = JSONObject(String(payload, Charsets.UTF_8))
                    if (frame.has("type")) {
                        control.onFrame(frame)
                        return@synchronized
                    }
                    SomeIpEventCodec.decode(payload)
                } catch (error: Exception) {
                    reportError(error)
                    return@synchronized
                }
                if (signals.sourceTimestampMs < lastTimestamp ||
                    (signals.sourceTimestampMs == lastTimestamp && signals.sequence <= lastSequence)) {
                    return@synchronized
                }
                lastTimestamp = signals.sourceTimestampMs
                lastSequence = signals.sequence
                connectionMonitor.onEvent(elapsedRealtime())
                control.onSignals(signals)
                status = Status.AVAILABLE
                this@SomeIpVehicleAdapter.listener?.onSignals(signals)
                if (active(session)) this@SomeIpVehicleAdapter.listener?.onStatus(status)
            }

            override fun onError(throwable: Throwable?) = synchronized(this@SomeIpVehicleAdapter) {
                if (active(session)) reportError(throwable ?: IllegalStateException("Transport error"))
            }
        })
        val started = try {
            transport.start()
        } catch (error: Exception) {
            reportError(error)
            false
        } catch (error: LinkageError) {
            reportError(error)
            false
        }
        if (!started) {
            isRunning = false
            control.onAvailability(false)
            generation++
            transport.setListener(null)
            connectionMonitor.startFailed()
            try {
                transport.stop()
            } catch (error: Exception) {
                listener.onError(error)
            } catch (error: LinkageError) {
                listener.onError(error)
            }
            publishStatus(Status.ERROR)
            this.listener = null
        }
        return started
    }

    @Synchronized
    fun checkTimeout() {
        try { control.checkTimeout() } catch (error: Exception) { reportError(error) }
        if (isRunning && connectionMonitor.checkTimeout(elapsedRealtime())) {
            publishStatus(Status.TIMED_OUT)
        }
    }

    // Do not hold the callback lock while joining the native IO thread.
    // The owner (Service executor) serializes complete start/stop transactions.
    fun stop() {
        synchronized(this) {
            if (!isRunning) return
            isRunning = false
            control.onAvailability(false)
            generation++
            transport.setListener(null)
            connectionMonitor.stop()
            resetOrdering()
            publishStatus(Status.STOPPED)
            listener = null
        }
        transport.stop()
    }

    private fun active(session: Long) = isRunning && generation == session

    private fun resetOrdering() {
        lastTimestamp = 0
        lastSequence = -1
    }

    private fun publishStatus(next: Status) {
        status = next
        listener?.onStatus(next)
    }

    private fun reportError(error: Throwable) {
        listener?.onError(error)
        publishStatus(Status.ERROR)
    }
}
