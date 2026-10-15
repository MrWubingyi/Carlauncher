package com.example.carlauncher.data.someip

import com.example.vehicle.control.*
import com.example.vehicle.someip.adapter.SomeIpVehicleAdapter
import com.example.vehicle.someip.adapter.SomeipConnectionMonitor
import com.example.vehicle.someip.transport.NativeVehicleTransport

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import com.example.carlauncher.data.DataSourceStatus
import com.example.carlauncher.data.DataStatus
import com.example.carlauncher.data.VehicleDataSource
import com.example.carlauncher.model.VehicleState
import com.example.vehicle.model.VehicleSignals

/** Launcher boundary: schedules health checks and maps semantic signals/status for consumers. */
open class VSomeIpDataSource(
    open val transport: NativeVehicleTransport?,
    open val connectionMonitor: SomeipConnectionMonitor?,
) : VehicleDataSource, VehicleControl {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val adapter = SomeIpVehicleAdapter(
        requireNotNull(transport),
        requireNotNull(connectionMonitor),
        SystemClock::elapsedRealtime,
    )
    @Volatile private var listener: VehicleDataSource.Listener? = null

    @Volatile
    final override var isRunning = false
        private set

    @Volatile
    var latestState: VehicleState? = null
        private set

    @Volatile
    var status: DataSourceStatus? = DataSourceStatus.STOPPED
        private set

    override fun controlSnapshot(): VehicleControlSnapshot = adapter.controlSnapshot()
    override fun requestControl(action: VehicleAction): Boolean = isRunning && adapter.requestControl(action)

    private val watchdogRunnable = object : Runnable {
        override fun run() {
            if (!isRunning) return
            adapter.checkTimeout()
            if (isRunning) mainHandler.postDelayed(this, 100)
        }
    }

    @Synchronized
    override fun start(listener: VehicleDataSource.Listener?) {
        if (isRunning) return
        this.listener = listener
        isRunning = true
        val started = adapter.start(object : SomeIpVehicleAdapter.Listener {
            override fun onControlChanged() {
                if (isRunning) this@VSomeIpDataSource.listener?.onControlChanged()
            }

            override fun onSignals(signals: VehicleSignals) {
                if (!isRunning) return
                val state = VehicleSignalMapper.toVehicleState(signals)
                latestState = state
                this@VSomeIpDataSource.listener?.onStateChanged(state)
            }

            override fun onStatus(status: SomeIpVehicleAdapter.Status) {
                if (!isRunning) return
                val mapped = when (status) {
                    SomeIpVehicleAdapter.Status.STOPPED -> DataSourceStatus.STOPPED
                    SomeIpVehicleAdapter.Status.CONNECTING -> DataSourceStatus.CONNECTING
                    SomeIpVehicleAdapter.Status.AVAILABLE ->
                        if (latestState?.dataStatus == DataStatus.NO_DATA) DataSourceStatus.NO_DATA
                        else DataSourceStatus.CONNECTED
                    SomeIpVehicleAdapter.Status.UNAVAILABLE -> DataSourceStatus.DISCONNECTED
                    SomeIpVehicleAdapter.Status.TIMED_OUT -> DataSourceStatus.NO_DATA
                    SomeIpVehicleAdapter.Status.ERROR -> DataSourceStatus.ERROR
                }
                if (status != SomeIpVehicleAdapter.Status.AVAILABLE) latestState = null
                this@VSomeIpDataSource.status = mapped
                this@VSomeIpDataSource.listener?.onSourceStatusChanged(mapped)
            }

            override fun onError(error: Throwable) {
                if (isRunning) {
                    this@VSomeIpDataSource.listener?.onError(
                        if (error is Exception) error else Exception(error)
                    )
                }
            }
        })
        isRunning = started
        if (started) mainHandler.post(watchdogRunnable)
        else this.listener = null
    }

    @Synchronized
    override fun stop() {
        if (!isRunning) return
        isRunning = false
        mainHandler.removeCallbacks(watchdogRunnable)
        try {
            adapter.stop()
        } finally {
            latestState = null
            status = DataSourceStatus.STOPPED
            listener?.onSourceStatusChanged(status)
            listener = null
        }
    }
}
