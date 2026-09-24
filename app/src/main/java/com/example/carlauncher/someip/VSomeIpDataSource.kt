package com.example.carlauncher.someip

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import com.example.carlauncher.data.DataSourceStatus
import com.example.carlauncher.data.DataStatus
import com.example.carlauncher.data.VehicleDataSource
import com.example.carlauncher.model.VehicleState

/**
 * 持有 NativeVehicleTransport 的 VehicleDataSource 实现。 负责接收传输层解包后的 Event 数据与可用性变化，
 * 更新连接监视器（SomeipConnectionMonitor），并推送到 Service / Repository 层。
 */
open class VSomeIpDataSource(
    open val transport: NativeVehicleTransport?,
    open val connectionMonitor: SomeipConnectionMonitor?,
) : VehicleDataSource {
    private val mainHandler: Handler = Handler(Looper.getMainLooper())

    private var listener: VehicleDataSource.Listener? = null
    @Volatile
    @get:Synchronized
    final override var isRunning: Boolean = false
        private set

    @Volatile
    var latestState: VehicleState? = null
        private set

    @Volatile
    var status: DataSourceStatus? = DataSourceStatus.STOPPED
        private set

    private val watchdogRunnable: Runnable =
        object : Runnable {
            override fun run() {
                if (!isRunning) return
                if (connectionMonitor!!.checkTimeout(SystemClock.elapsedRealtime())) {
                    latestState = null
                    status = DataSourceStatus.NO_DATA
                    Log.w(TAG, "SOMEIP_STATE EVENT_TIMEOUT (3000 ms without vehicle event)")
                    if (listener != null) {
                        listener!!.onSourceStatusChanged(status)
                    }
                }
                mainHandler.postDelayed(this, 100)
            }
        }

    @Synchronized
    override fun start(listener: VehicleDataSource.Listener?) {
        if (isRunning) {
            return
        }
        this.listener = listener
        this.isRunning = true
        this.status = DataSourceStatus.CONNECTING

        connectionMonitor!!.start()
        mainHandler.post(watchdogRunnable)

        if (listener != null) {
            listener!!.onSourceStatusChanged(status)
        }

        transport!!.setListener(
            object : NativeVehicleTransport.Listener {
                override fun onAvailabilityChanged(available: Boolean) {
                    if (!isRunning) return
                    val now = SystemClock.elapsedRealtime()
                    connectionMonitor!!.onAvailability(available, now)
                    if (!available) {
                        latestState = null
                        status = DataSourceStatus.DISCONNECTED
                    } else if (latestState == null) {
                        status = DataSourceStatus.CONNECTING
                    }
                    if (listener != null) {
                        listener!!.onSourceStatusChanged(status)
                    }
                }

                override fun onVehicleState(state: VehicleState?) {
                    if (!isRunning || !connectionMonitor!!.snapshot().isAvailable) return
                    latestState = state
                    connectionMonitor!!.onEvent(SystemClock.elapsedRealtime())
                    status =
                        if (state!!.dataStatus == DataStatus.NO_DATA) DataSourceStatus.NO_DATA
                        else DataSourceStatus.CONNECTED
                    if (listener != null) {
                        listener!!.onStateChanged(state)
                        listener!!.onSourceStatusChanged(status)
                    }
                }

                override fun onError(throwable: Throwable?) {
                    if (!isRunning) return
                    latestState = null
                    status = DataSourceStatus.ERROR
                    Log.w(TAG, "Transport error", throwable)
                    if (listener != null) {
                        if (throwable is Exception) {
                            listener!!.onError(throwable as Exception?)
                        } else {
                            listener!!.onError(Exception(throwable))
                        }
                        listener!!.onSourceStatusChanged(status)
                    }
                }
            }
        )

        val started = transport!!.start()
        if (!started) {
            connectionMonitor!!.startFailed()
            status = DataSourceStatus.ERROR
            if (listener != null) {
                listener!!.onSourceStatusChanged(status)
            }
        }
    }

    @Synchronized
    override fun stop() {
        if (!isRunning) {
            return
        }
        isRunning = false
        mainHandler.removeCallbacks(watchdogRunnable)
        transport!!.setListener(null)
        transport!!.stop()
        connectionMonitor!!.stop()
        latestState = null
        status = DataSourceStatus.STOPPED
        if (listener != null) {
            listener!!.onSourceStatusChanged(status)
            listener = null
        }
    }

    companion object {
        private const val TAG: String = "VSOMEIP_DATA_SOURCE"
    }
}
