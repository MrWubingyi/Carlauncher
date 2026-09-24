package com.example.carlauncher.data

import com.example.carlauncher.model.VehicleState

interface VehicleDataSource {

    val isRunning: Boolean

    fun start(listener: Listener?)

    fun stop()

    interface Listener {

        /** 回调完整的车辆状态快照。 回调线程不保证是主线程。 */
        fun onStateChanged(state: VehicleState?)

        /** 数据源本身的连接或可用状态发生变化。 */
        fun onSourceStatusChanged(status: DataSourceStatus?)

        fun onError(exception: Exception?)
    }
}
